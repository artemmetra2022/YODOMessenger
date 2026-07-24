const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

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
