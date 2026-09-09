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
 *
 * НОВОЕ (push о модерации): вторым, независимым шагом также вычитывает
 * корневую коллекцию moderationNotifications (notified == false) — это очередь
 * отдельных событий модерации (глобальный бан/разбан пользователя), которые
 * кладёт туда UserRepositoryImpl.queueModerationNotification при действиях
 * Админки. Формат payload другой (нет chatId/senderName — есть title/body),
 * поэтому обрабатывается отдельным циклом, а не смешивается с сообщениями чата.
 *
 * НОВОЕ (push о новостях и опросах школы): последним шагом вычитывает
 * schoolNews и schoolPolls с notified == false и рассылает их всем
 * пользователям с users.schoolPushEnabled == true (тумблер в настройках
 * раздела «Школа»; отсутствие поля = подписан).
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

// НОВОЕ (раздел «Школа», push учителю): очередь новых вопросов учеников.
// Клиент пишет вопрос в schoolTeacherProfiles/{имя}/questions с notified=false
// (см. SchoolRepositoryImpl.askTeacherQuestion); воркер находит такие вопросы
// через collectionGroup, берёт linkedUserId профиля учителя из родительского
// документа и отправляет учителю уведомление с type=school.
// collectionGroup("questions") безопасен: подколлекция с таким именем есть
// только у учительских страниц.
async function sendTeacherQuestionNotifications(db, messaging) {
  console.log("Ищу новые вопросы учеников для учителей...");

  const pendingSnapshot = await db
    .collectionGroup("questions")
    .where("notified", "==", false)
    .limit(200)
    .get();

  if (pendingSnapshot.empty) {
    console.log("Нет новых вопросов для учителей.");
    return;
  }

  console.log(`Найдено вопросов: ${pendingSnapshot.size}`);

  const batch = db.batch();
  let sentCount = 0;
  let errorCount = 0;

  for (const questionDoc of pendingSnapshot.docs) {
    try {
      // .../schoolTeacherProfiles/{имя учителя}/questions/{id}
      const teacherDocRef = questionDoc.ref.parent.parent;
      const teacherName = teacherDocRef ? teacherDocRef.id : "";
      if (!teacherName) {
        batch.update(questionDoc.ref, { notified: true });
        continue;
      }

      const teacherDoc = await teacherDocRef.get();
      const teacherData = teacherDoc.exists ? teacherDoc.data() : null;
      const teacherUid = teacherData && teacherData.linkedUserId;
      if (!teacherUid) {
        // Профиль не привязан к аккаунту — уведомлять некого.
        batch.update(questionDoc.ref, { notified: true });
        continue;
      }

      const question = questionDoc.data();
      const userDoc = await db.collection("users").doc(teacherUid).get();
      const token = userDoc.exists ? userDoc.data().fcmToken : null;
      if (token) {
        const response = await messaging.sendEachForMulticast({
          tokens: [token],
          data: {
            type: "school",
            title: `Вопрос ученика · ${teacherName}`,
            body: (question.text || "").substring(0, 200),
          },
          android: { priority: "high" },
        });
        sentCount += response.successCount;
        errorCount += response.failureCount;
      }

      batch.update(questionDoc.ref, { notified: true });
    } catch (err) {
      console.error(`Ошибка обработки вопроса ${questionDoc.id}:`, err.message);
      batch.update(questionDoc.ref, { notified: true });
      errorCount++;
    }
  }

  await batch.commit();
  console.log(`Вопросы учителям: успешно отправлено ${sentCount}, ошибок ${errorCount}`);
}

// НОВОЕ (раздел «Школа», push подписчикам): очередь обновлений файла урока.
// Когда привязанный учитель обновляет файл урока, клиент пишет в подколлекцию
// schoolTeacherProfiles/{имя}/fileNotifications документ {notified: false,
// subscribers: [...uid]} (см. SchoolRepositoryImpl.setTeacherFile) — воркер
// рассылает уведомление каждому подписчику с type=school.
async function sendLessonFileNotifications(db, messaging) {
  console.log("Ищу обновления файлов уроков...");

  const pendingSnapshot = await db
    .collectionGroup("fileNotifications")
    .where("notified", "==", false)
    .limit(200)
    .get();

  if (pendingSnapshot.empty) {
    console.log("Нет обновлений файлов уроков.");
    return;
  }

  console.log(`Найдено обновлений файла урока: ${pendingSnapshot.size}`);

  const batch = db.batch();
  let sentCount = 0;
  let errorCount = 0;

  for (const notifDoc of pendingSnapshot.docs) {
    try {
      const notif = notifDoc.data();
      const teacherDocRef = notifDoc.ref.parent.parent; // профиль учителя
      const teacherName = teacherDocRef ? teacherDocRef.id : "";
      const subscribers = Array.isArray(notif.subscribers) ? notif.subscribers : [];
      if (!teacherName || subscribers.length === 0) {
        batch.update(notifDoc.ref, { notified: true });
        continue;
      }

      const tokens = [];
      for (const uid of subscribers) {
        const userDoc = await db.collection("users").doc(uid).get();
        const token = userDoc.exists ? userDoc.data().fcmToken : null;
        if (token) tokens.push(token);
      }

      if (tokens.length > 0) {
        const response = await messaging.sendEachForMulticast({
          tokens,
          data: {
            type: "school",
            title: `Файл урока обновлён · ${teacherName}`,
            body: notif.fileNote
              ? `${notif.fileNote} — откройте страницу учителя, чтобы скачать`
              : "Откройте страницу учителя, чтобы посмотреть файл",
          },
          android: { priority: "high" },
        });
        sentCount += response.successCount;
        errorCount += response.failureCount;
      }

      batch.update(notifDoc.ref, { notified: true });
    } catch (err) {
      console.error(`Ошибка обработки обновления файла ${notifDoc.id}:`, err.message);
      batch.update(notifDoc.ref, { notified: true });
      errorCount++;
    }
  }

  await batch.commit();
  console.log(`Файлы уроков: успешно отправлено ${sentCount}, ошибок ${errorCount}`);
}

// НОВОЕ (отложенная публикация новостей): админ-панель создаёт новость с
// published == false и publishAt (мс) — публикация в указанное время без
// Cloud Functions. Воркер на каждом прогоне (каждые ~5 минут) находит
// неопубликованные новости с наступившим publishAt и переключает
// published -> true; push подписчикам уходит сразу после этого обычным
// шагом sendSchoolBroadcastNotifications ниже (notified у них ещё false).
// Новости без поля published (созданные до этой функции) считаются
// опубликованными — фильтр where("published","==",false) их не находит.
async function publishScheduledSchoolNews(db) {
  console.log("Проверяю отложенные новости...");

  const pendingSnapshot = await db
    .collection("schoolNews")
    .where("published", "==", false)
    .limit(200)
    .get();

  if (pendingSnapshot.empty) {
    console.log("Отложенных новостей нет.");
    return;
  }

  const now = Date.now();
  const batch = db.batch();
  let published = 0;
  for (const doc of pendingSnapshot.docs) {
    const news = doc.data();
    // publishAt отсутствует = черновик без даты — публикуется только вручную
    // из админ-панели (кнопка «Опубликовать сейчас»), воркер его не трогает.
    if (news.publishAt && news.publishAt <= now) {
      batch.update(doc.ref, { published: true });
      published++;
    }
  }
  if (published > 0) {
    await batch.commit();
  }
  console.log(`Отложенные новости: опубликовано ${published}`);
}

// НОВОЕ (push о новостях и опросах школы): рассылка всем подписчикам.
// Клиент создаёт новость/опрос с notified=false (SchoolRepositoryImpl.addNews/
// addPoll — право есть только у админов). Подписчики — пользователи с
// users/{uid}.schoolPushEnabled == true (флаг выключает тумблер в настройках
// раздела «Школа»; до инициализации поле отсутствует = подписан). Отдельная
// выборка через where("schoolPushEnabled", "==", true) не требует составного
// индекса и читает только документы-подписки.
// Отправка чанками по 500 токенов (лимит multicast).
async function sendSchoolBroadcastNotifications(db, messaging) {
  console.log("Ищу новые новости и опросы школы...");

  const subscribersSnapshot = await db
    .collection("users")
    .where("schoolPushEnabled", "==", true)
    .get();

  const subscriberTokens = subscribersSnapshot.docs
    .map((doc) => doc.data().fcmToken)
    .filter(Boolean);

  if (subscriberTokens.length === 0) {
    console.log("Подписчиков на школьные пуши нет — пропускаю.");
  }

  // Рассылает один документ (новость или опрос) всем подписчикам.
  // chunked multicast: FCM ограничивает sendEachForMulticast 500 токенами.
  async function broadcast(title, body) {
    if (subscriberTokens.length === 0) return;
    for (let i = 0; i < subscriberTokens.length; i += 500) {
      const tokens = subscriberTokens.slice(i, i + 500);
      await messaging.sendEachForMulticast({
        tokens,
        data: { type: "school", title, body },
        android: { priority: "high" },
      });
    }
  }

  // ── Новости (schoolNews, notified == false)
  const newsSnapshot = await db
    .collection("schoolNews")
    .where("notified", "==", false)
    .limit(200)
    .get();

  if (newsSnapshot.empty) {
    console.log("Новых новостей нет.");
  } else {
    const batch = db.batch();
    let sent = 0;
    for (const doc of newsSnapshot.docs) {
      try {
        const news = doc.data();
        // НОВОЕ (отложенная публикация): ещё не опубликованные новости
        // (черновики/запланированные) не рассылаем и notified не трогаем —
        // push уйдёт в том же прогоне, когда publishScheduledSchoolNews
        // их опубликует.
        if (news.published === false) continue;
        await broadcast(
          "📰 Новая новость школы",
          (news.text || "").substring(0, 200)
        );
        sent++;
      } catch (err) {
        console.error(`Ошибка рассылки новости ${doc.id}:`, err.message);
      } finally {
        // Помечаем обработанным даже при ошибке — чтобы не зависало в очереди.
        batch.update(doc.ref, { notified: true });
      }
    }
    await batch.commit();
    console.log(`Новости: отправлено ${sent}`);
  }

  // ── Опросы (schoolPolls, notified == false)
  const pollsSnapshot = await db
    .collection("schoolPolls")
    .where("notified", "==", false)
    .limit(200)
    .get();

  if (pollsSnapshot.empty) {
    console.log("Новых опросов нет.");
  } else {
    const batch = db.batch();
    let sent = 0;
    for (const doc of pollsSnapshot.docs) {
      try {
        const poll = doc.data();
        await broadcast(
          "📊 Новый опрос школы",
          (poll.question || "").substring(0, 200)
        );
        sent++;
      } catch (err) {
        console.error(`Ошибка рассылки опроса ${doc.id}:`, err.message);
      } finally {
        batch.update(doc.ref, { notified: true });
      }
    }
    await batch.commit();
    console.log(`Опросы: отправлено ${sent}`);
  }
}

async function main() {
  initFirebase();
  const db = getFirestore();
  const messaging = getMessaging();

  await sendChatMessageNotifications(db, messaging);
  await sendModerationNotifications(db, messaging);
  await sendTeacherQuestionNotifications(db, messaging);
  await sendLessonFileNotifications(db, messaging);
  await publishScheduledSchoolNews(db);
  await sendSchoolBroadcastNotifications(db, messaging);
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Критическая ошибка воркера:", err);
    process.exit(1);
  });
