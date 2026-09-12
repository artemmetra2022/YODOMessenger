/**
 * GitHub Actions (.github/scripts/send-push-notifications.js)
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
 *
 * НОВОЕ (push о модерации): вторым, независимым шагом также вычитывает
 * корневую коллекцию moderationNotifications (notified == false) — это очередь
 * отдельных событий модерации (глобальный бан/разбан пользователя), которые
 * кладёт туда UserRepositoryImpl.queueModerationNotification при действиях
 * Админки. Формат payload другой (нет chatId/senderName — есть title/body),
 * поэтому обрабатывается отдельным циклом, а не смешивается с сообщениями чата.
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

async function sendChatMessageNotifications(db, messaging) {
  console.log("Ищу неотправленные push-уведомления о сообщениях...");

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
        // НОВОЕ (быстрые действия "Прочитано"/"Ответить" в уведомлении):
        // клиенту нужно знать тип чата (только 1-на-1 показываем кнопки) и id
        // отправителя (чтобы отметить прочитанным и корректно сформировать ответ).
        const response = await messaging.sendEachForMulticast({
          tokens,
          data: {
            chatId,
            senderId: senderId || "",
            senderName,
            messageText: message.text || "",
            chatType: chatData.type || "PRIVATE",
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
  console.log(`Сообщения: успешно отправлено ${sentCount}, ошибок ${errorCount}`);
}

// НОВОЕ (push о модерации): отдельная очередь для событий модерации —
// глобальный бан/разбан пользователя (см. UserRepositoryImpl.setGlobalBlock/
// removeGlobalBlock). Каждая запись адресована одному конкретному userId и
// несёт готовый title/body (не требует join с другими коллекциями, в отличие
// от сообщений чата).
async function sendModerationNotifications(db, messaging) {
  console.log("Ищу неотправленные push-уведомления о модерации...");

  const pendingSnapshot = await db
    .collection("moderationNotifications")
    .where("notified", "==", false)
    .limit(200)
    .get();

  if (pendingSnapshot.empty) {
    console.log("Нет новых уведомлений о модерации.");
    return;
  }

  console.log(`Найдено уведомлений о модерации: ${pendingSnapshot.size}`);

  const userCache = new Map();
  const batch = db.batch();
  let sentCount = 0;
  let errorCount = 0;

  for (const notifDoc of pendingSnapshot.docs) {
    try {
      const notif = notifDoc.data();
      const userId = notif.userId;
      if (!userId) {
        batch.update(notifDoc.ref, { notified: true });
        continue;
      }

      let cached = userCache.get(userId);
      if (!cached) {
        const doc = await db.collection("users").doc(userId).get();
        cached = doc.exists ? doc.data() : {};
        userCache.set(userId, cached);
      }

      const token = cached.fcmToken;
      if (token) {
        // data-only payload (как и для сообщений) — клиент сам решает, как
        // показать уведомление, с учётом своих настроек (mute/quiet hours и т.д.).
        const response = await messaging.sendEachForMulticast({
          tokens: [token],
          data: {
            type: "moderation",
            title: notif.title || "Yodo Messenger",
            body: notif.body || "",
          },
          android: { priority: "high" },
        });
        sentCount += response.successCount;
        errorCount += response.failureCount;
      }

      batch.update(notifDoc.ref, { notified: true });
    } catch (err) {
      console.error(`Ошибка обработки уведомления о модерации ${notifDoc.id}:`, err.message);
      batch.update(notifDoc.ref, { notified: true });
      errorCount++;
    }
  }

  await batch.commit();
  console.log(`Модерация: успешно отправлено ${sentCount}, ошибок ${errorCount}`);
}

function hashUserId(id) {
  let hash = 0;
  for (let i = 0; i < String(id).length; i++) hash = ((hash << 5) - hash + String(id).charCodeAt(i)) | 0;
  return Math.abs(hash);
}

function inTimeWindow(windowStartMinute, windowEndMinute, now = new Date()) {
  if (windowStartMinute == null || windowEndMinute == null) return true;
  const minute = now.getUTCHours() * 60 + now.getUTCMinutes();
  if (windowStartMinute <= windowEndMinute) return minute >= windowStartMinute && minute <= windowEndMinute;
  return minute >= windowStartMinute || minute <= windowEndMinute;
}

function audienceMatches(user, campaign) {
  const type = campaign.audienceType || "ALL";
  if (type === "ALL") return true;
  if (type === "ONLINE") return user.online === true || user.isOnline === true;
  if (type === "CLASSES") {
    const ids = campaign.audienceClassIds || [];
    return ids.length === 0 || ids.includes(user.classId) || ids.includes(user.class_id);
  }
  if (type === "GROUPS") {
    const ids = campaign.audienceClassIds || campaign.audienceGroupIds || [];
    return ids.length === 0 || ids.includes(user.groupId) || ids.includes(user.group_id);
  }
  return true;
}

async function sendNewsCampaigns(db, messaging) {
  const nowMs = Date.now();
  const snap = await db.collection("newsCampaigns")
    .where("status", "==", "SCHEDULED")
    .limit(50)
    .get();
  if (snap.empty) return;

  for (const doc of snap.docs) {
    const c = doc.data();
    if (c.scheduledAtMillis && nowMs < Number(c.scheduledAtMillis)) continue;
    if (!inTimeWindow(c.windowStartMinute, c.windowEndMinute)) continue;
    if (c.newsSent === true) continue;

    const users = await db.collection("users").limit(5000).get();
    let recipients = users.docs.filter(u => audienceMatches(u.data(), c));
    if (!recipients.length) continue;

    const batch = db.batch();
    let sent = 0;
    let sentA = 0;
    let sentB = 0;
    for (const u of recipients) {
      const data = u.data();
      if (!data.fcmToken) continue;
      const variant = c.abEnabled ? (hashUserId(u.id) % 2 === 0 ? "A" : "B") : "A";
      const title = variant === "B" && c.titleB ? c.titleB : (c.titleA || "Yodo Messenger");
      try {
        await messaging.send({
          token: data.fcmToken,
          data: { type: "news", campaignId: doc.id, variant, title, body: c.body || "" },
          android: { priority: "high" }
        });
        sent++;
        if (variant === "A") sentA++; else sentB++;
      } catch (e) {
        console.error(`News push ${doc.id} -> ${u.id}:`, e.message);
      }
    }
    batch.update(doc.ref, { newsSent: true, sentCount: sent, sentA, sentB, newsSentAtMillis: nowMs });
    await batch.commit();
  }
}

async function sendDelayedNewsPushes(db, messaging) {
  const nowMs = Date.now();
  const snap = await db.collection("newsCampaigns")
    .where("status", "==", "SCHEDULED")
    .limit(50)
    .get();
  for (const doc of snap.docs) {
    const c = doc.data();
    if (!c.pushEnabled || c.pushSent === true) continue;
    if (c.pushScheduledAtMillis && nowMs < Number(c.pushScheduledAtMillis)) continue;
    if (!inTimeWindow(c.pushWindowStartMinute, c.pushWindowEndMinute)) continue;

    const users = await db.collection("users").limit(5000).get();
    const recipients = users.docs.filter(u => audienceMatches(u.data(), c));
    let sent = 0;
    for (const u of recipients) {
      const token = u.data().fcmToken;
      if (!token) continue;
      const variant = c.abEnabled ? (hashUserId(u.id) % 2 === 0 ? "A" : "B") : "A";
      const title = variant === "B" && c.titleB ? c.titleB : (c.titleA || "Yodo Messenger");
      try {
        await messaging.send({
          token,
          data: { type: "news", campaignId: doc.id, variant, title, body: c.body || "" },
          android: { priority: "high" }
        });
        sent++;
      } catch (e) { console.error(`Delayed news push ${doc.id} -> ${u.id}:`, e.message); }
    }
    await doc.ref.update({ pushSent: true, pushSentAtMillis: nowMs, pushCount: sent });
  }
}


async function sendTeacherNotifications(db, messaging) {
  console.log("Ищу push-уведомления учителям...");
  const snap = await db.collection("teacherNotificationQueue")
    .where("notified", "==", false).limit(200).get();
  if (snap.empty) return;
  const batch = db.batch();
  let sent = 0, failed = 0;
  for (const doc of snap.docs) {
    const n = doc.data();
    try {
      const teacherId = n.teacherId;
      const profile = teacherId ? await db.collection("teacherProfiles").doc(teacherId).get() : null;
      const user = teacherId ? await db.collection("users").doc(teacherId).get() : null;
      const enabled = !profile.exists || (n.type === "question" ? profile.data().notifyNewQuestions !== false : profile.data().notifyMessages !== false);
      const token = user.exists ? user.data().fcmToken : null;
      if (enabled && token) {
        const r = await messaging.send({ token, data: { type: "teacher", notificationId: doc.id, title: n.title || "Yodo Messenger", body: n.body || "", action: n.action || "" }, android: { priority: "high" } });
        if (r) sent++;
      }
      batch.update(doc.ref, { notified: true, processedAtMillis: Date.now() });
    } catch (e) {
      console.error(`Ошибка teacher notification ${doc.id}:`, e.message);
      batch.update(doc.ref, { notified: true, processedAtMillis: Date.now(), error: e.message });
      failed++;
    }
  }
  await batch.commit();
  console.log(`Учителя: отправлено ${sent}, ошибок ${failed}`);
}

async function main() {
  initFirebase();
  const db = getFirestore();
  const messaging = getMessaging();

  await sendChatMessageNotifications(db, messaging);
  await sendModerationNotifications(db, messaging);
  await sendTeacherNotifications(db, messaging);
  await sendNewsCampaigns(db, messaging);
  await sendDelayedNewsPushes(db, messaging);
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Критическая ошибка воркера:", err);
    process.exit(1);
  });
