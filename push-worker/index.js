/**
 * push-worker/index.js
 *
 * Одноразовый скрипт (не постоянный сервер!) — запускается по расписанию через
 * GitHub Actions каждые несколько минут. Не требует платного плана Firebase (Blaze),
 * так как использует только Admin SDK (Firestore + FCM), а не Cloud Functions.
 *
 * Логика:
 * 1. Ищет все сообщения во всех чатах с полем notified == false
 * 2. Для каждого — находит участников чата (кроме отправителя) и их FCM-токены
 * 3. Отправляет push через Firebase Cloud Messaging
 * 4. Помечает сообщение notified = true (даже при ошибке отправки — чтобы не зависало навсегда)
 */

const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const fs = require("fs");

function initFirebase() {
  const credsPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!credsPath || !fs.existsSync(credsPath)) {
    throw new Error(
      "GOOGLE_APPLICATION_CREDENTIALS не задан или файл не найден. " +
        "Проверь Secret FIREBASE_SERVICE_ACCOUNT в GitHub."
    );
  }
  const serviceAccount = JSON.parse(fs.readFileSync(credsPath, "utf8"));
  initializeApp({ credential: cert(serviceAccount) });
}

async function main() {
  initFirebase();
  const db = getFirestore();
  const messaging = getMessaging();

  console.log("Ищу неотправленные push-уведомления...");

  // collectionGroup — ищет во ВСЕХ подколлекциях "messages" сразу, во всех чатах
  const pendingSnapshot = await db
    .collectionGroup("messages")
    .where("notified", "==", false)
    .limit(200) // защита от неограниченной выборки за один прогон
    .get();

  if (pendingSnapshot.empty) {
    console.log("Нет новых сообщений для уведомления.");
    return;
  }

  console.log(`Найдено сообщений: ${pendingSnapshot.size}`);

  // Кэшируем данные чатов и пользователей, чтобы не дёргать Firestore повторно
  const chatCache = new Map();
  const userCache = new Map();

  const batch = db.batch();
  let sentCount = 0;
  let errorCount = 0;

  for (const messageDoc of pendingSnapshot.docs) {
    try {
      const message = messageDoc.data();
      const chatRef = messageDoc.ref.parent.parent; // .../chats/{chatId}/messages/{id} -> chats/{chatId}
      const chatId = chatRef.id;

      let chatData = chatCache.get(chatId);
      if (!chatData) {
        const chatDoc = await chatRef.get();
        if (!chatDoc.exists) {
          batch.update(messageDoc.ref, { notified: true });
          continue;
        }
        chatData = chatDoc.data();
        chatCache.set(chatId, chatData);
      }

      const participantIds = chatData.participantIds || [];
      const senderId = message.senderId;
      const mutedMap = chatData.muted || {};
      const recipientIds = participantIds.filter(
        (uid) => uid !== senderId && mutedMap[uid] !== true
      );

      if (recipientIds.length === 0) {
        batch.update(messageDoc.ref, { notified: true });
        continue;
      }

      // Имя отправителя (с кэшем)
      let senderName = userCache.get(senderId)?.displayName;
      if (!senderName) {
        const senderDoc = await db.collection("users").doc(senderId).get();
        senderName = senderDoc.exists
          ? senderDoc.data().displayName || "Yodo Messenger"
          : "Yodo Messenger";
        userCache.set(senderId, { displayName: senderName });
      }

      // Токены получателей (с кэшем)
      const tokens = [];
      for (const uid of recipientIds) {
        let cached = userCache.get(uid);
        if (!cached) {
          const doc = await db.collection("users").doc(uid).get();
          cached = doc.exists ? doc.data() : {};
          userCache.set(uid, cached);
        }
        if (cached.fcmToken) tokens.push(cached.fcmToken);
      }

      if (tokens.length > 0) {
        const response = await messaging.sendEachForMulticast({
          tokens,
          data: {
            chatId,
            senderName,
            messageText: message.text || "",
          },
          android: { priority: "high" },
        });
        sentCount += response.successCount;
        errorCount += response.failureCount;
      }

      batch.update(messageDoc.ref, { notified: true });
    } catch (err) {
      console.error(`Ошибка обработки сообщения ${messageDoc.id}:`, err.message);
      // Всё равно помечаем notified, чтобы битое сообщение не блокировало очередь навсегда
      batch.update(messageDoc.ref, { notified: true });
      errorCount++;
    }
  }

  await batch.commit();
  console.log(`Готово. Успешно отправлено: ${sentCount}, ошибок: ${errorCount}`);
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Критическая ошибка воркера:", err);
    process.exit(1);
  });
