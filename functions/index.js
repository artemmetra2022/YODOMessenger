const { onDocumentCreated, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { getAuth } = require("firebase-admin/auth");

initializeApp();
const db = getFirestore();
const messaging = getMessaging();
const auth = getAuth();

/**
 * Срабатывает при создании нового документа в chats/{chatId}/messages/{messageId}.
 * Рассылает push-уведомление всем участникам чата, кроме отправителя.
 */
exports.onNewMessage = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const message = snapshot.data();
    const chatId = event.params.chatId;
    const senderId = message.senderId;
    const text = message.text || "";

    const chatDoc = await db.collection("chats").doc(chatId).get();
    if (!chatDoc.exists) return;

    const chatData = chatDoc.data();
    const participantIds = chatData.participantIds || [];
    const recipientIds = participantIds.filter((uid) => uid !== senderId);

    if (recipientIds.length === 0) return;

    // Достаём имя отправителя и FCM-токены получателей
    const senderDoc = await db.collection("users").doc(senderId).get();
    const senderName = senderDoc.exists
      ? senderDoc.data().displayName || "Yodo Messenger"
      : "Yodo Messenger";

    const recipientDocs = await db.getAll(
      ...recipientIds.map((uid) => db.collection("users").doc(uid))
    );

    const tokens = recipientDocs
      .map((doc) => (doc.exists ? doc.data().fcmToken : null))
      .filter((token) => !!token);

    if (tokens.length === 0) return;

    const payload = {
      tokens,
      data: {
        chatId,
        senderName,
        messageText: text,
      },
      android: {
        priority: "high",
      },
    };

    try {
      const response = await messaging.sendEachForMulticast(payload);
      console.log(
        `Push отправлен: ${response.successCount} успешно, ${response.failureCount} с ошибкой`
      );
    } catch (error) {
      console.error("Ошибка отправки push:", error);
    }
  }
);

/**
 * Срабатывает при удалении документа users/{uid}/sessions/{sessionId} — то есть когда
 * пользователь нажимает "Завершить сеанс" (или это делает сам владелец сессии при выходе).
 *
 * Одного удаления документа в Firestore недостаточно: Firebase Auth токен на удалённом
 * устройстве при этом никак не аннулируется — устройство может пользоваться приложением,
 * пока клиент сам не проверит существование своего документа сессии (см. клиентскую
 * проверку в SessionRepositoryImpl.observeCurrentSessionExists). Эта функция — надёжный
 * серверный уровень поверх клиентской проверки: реально отзывает refresh-токены аккаунта,
 * так что после следующего обновления ID-токена (обычно в течение часа, а на практике
 * почти сразу — Firebase SDK сверяет токен при первом же запросе) устройство будет
 * принудительно разлогинено, даже если клиентский код на нём был изменён или отключён.
 *
 * Важно: revokeRefreshTokens отзывает ВСЕ refresh-токены пользователя, а не только токен
 * удалённого устройства (Admin SDK не различает токены разных устройств) — то есть при
 * завершении одного чужого сеанса переавторизоваться придётся и текущему устройству тоже.
 * Firebase Auth на клиенте эту переавторизацию обычно проходит прозрачно (silent refresh),
 * так что заметного разлогина владелец действия не увидит.
 */
exports.onSessionDeleted = onDocumentDeleted(
  "users/{userId}/sessions/{sessionId}",
  async (event) => {
    const userId = event.params.userId;
    try {
      await auth.revokeRefreshTokens(userId);
      console.log(`Refresh-токены отозваны для пользователя ${userId} (удалена сессия ${event.params.sessionId})`);
    } catch (error) {
      // Пользователь мог быть уже удалён (например, вместе с аккаунтом) — это не ошибка.
      if (error.code === "auth/user-not-found") {
        console.log(`Пользователь ${userId} не найден — пропускаем отзыв токенов`);
        return;
      }
      console.error(`Не удалось отозвать токены для ${userId}:`, error);
    }
  }
);

const LINK_PREVIEW_CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 часа
const LINK_PREVIEW_FETCH_TIMEOUT_MS = 5000;
const LINK_PREVIEW_MAX_BYTES = 500 * 1024; // не читаем больше 500 КБ HTML

function extractMetaTag(html, property) {
  // og:title, og:description, og:image и т.п. — атрибуты property/content
  // могут идти в любом порядке, поэтому два варианта регулярки.
  const patterns = [
    new RegExp(`<meta[^>]+property=["']${property}["'][^>]+content=["']([^"']*)["']`, "i"),
    new RegExp(`<meta[^>]+content=["']([^"']*)["'][^>]+property=["']${property}["']`, "i"),
  ];
  for (const pattern of patterns) {
    const match = html.match(pattern);
    if (match) return match[1];
  }
  return null;
}

function extractTitleTag(html) {
  const match = html.match(/<title[^>]*>([^<]*)<\/title>/i);
  return match ? match[1].trim() : null;
}

/**
 * Достаёт og:title/og:description/og:image для превью ссылки в чате.
 * Выполняется на сервере (не с клиента), чтобы не светить IP пользователя
 * перед произвольным сайтом и не тащить в приложение HTML-парсер.
 * Результат кэшируется в Firestore (link_previews/{urlHash}) на LINK_PREVIEW_CACHE_TTL_MS.
 */
exports.getLinkPreview = onCall(async (request) => {
  const url = request.data?.url;
  if (typeof url !== "string" || url.length === 0) {
    throw new HttpsError("invalid-argument", "Параметр url обязателен");
  }

  let parsedUrl;
  try {
    parsedUrl = new URL(url);
  } catch {
    throw new HttpsError("invalid-argument", "Некорректный URL");
  }
  if (parsedUrl.protocol !== "http:" && parsedUrl.protocol !== "https:") {
    throw new HttpsError("invalid-argument", "Поддерживаются только http/https ссылки");
  }

  const cacheId = Buffer.from(url).toString("base64url").slice(0, 200);
  const cacheRef = db.collection("link_previews").doc(cacheId);

  const cached = await cacheRef.get();
  if (cached.exists) {
    const data = cached.data();
    if (Date.now() - (data.fetchedAt || 0) < LINK_PREVIEW_CACHE_TTL_MS) {
      return data.preview;
    }
  }

  let preview = { url, title: null, description: null, imageUrl: null, siteName: null };

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), LINK_PREVIEW_FETCH_TIMEOUT_MS);
    const response = await fetch(url, {
      signal: controller.signal,
      redirect: "follow",
      headers: { "User-Agent": "Mozilla/5.0 (compatible; YodoLinkPreviewBot/1.0)" },
    });
    clearTimeout(timeoutId);

    const contentType = response.headers.get("content-type") || "";
    if (response.ok && contentType.includes("text/html")) {
      const reader = response.body.getReader();
      let received = 0;
      let html = "";
      const decoder = new TextDecoder();
      while (received < LINK_PREVIEW_MAX_BYTES) {
        const { done, value } = await reader.read();
        if (done) break;
        received += value.length;
        html += decoder.decode(value, { stream: true });
      }
      await reader.cancel().catch(() => {});

      preview = {
        url,
        title: extractMetaTag(html, "og:title") || extractTitleTag(html),
        description: extractMetaTag(html, "og:description"),
        imageUrl: extractMetaTag(html, "og:image"),
        siteName: extractMetaTag(html, "og:site_name") || parsedUrl.hostname,
      };
    }
  } catch (error) {
    console.error(`Ошибка получения превью для ${url}:`, error.message);
    // Возвращаем пустое превью (только url/hostname) вместо ошибки —
    // клиент просто не покажет карточку, но не сломает отправку сообщения.
    preview.siteName = parsedUrl.hostname;
  }

  await cacheRef.set({ preview, fetchedAt: Date.now() });
  return preview;
});
