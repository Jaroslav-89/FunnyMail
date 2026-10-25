const admin = require("firebase-admin");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");

admin.initializeApp();

const db = admin.firestore();
const INVALID_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

exports.notifyChatMembersOnNewLetter = onDocumentCreated(
  {
    document: "chats/{chatId}/letters/{letterId}",
    region: "europe-west1",
  },
  async (event) => {
    const letterSnapshot = event.data;
    if (!letterSnapshot) return;

    const letter = letterSnapshot.data();
    const chatId = event.params.chatId;
    const letterId = event.params.letterId;
    const chatSnapshot = await db.collection("chats").doc(chatId).get();
    if (!chatSnapshot.exists) return;

    const chat = chatSnapshot.data() || {};
    const title = typeof chat.title === "string" && chat.title.trim()
      ? chat.title.trim()
      : "чат";
    const body = `Новое сообщение в чате (${title})`;
    const recipientIds = Array.isArray(chat.memberIds)
      ? [...new Set(chat.memberIds)].filter(
          (userId) => typeof userId === "string" &&
            userId.length > 0 &&
            userId !== letter.authorUserId,
        )
      : [];

    for (const recipientId of recipientIds) {
      const tokenSnapshot = await db
        .collection("users")
        .doc(recipientId)
        .collection("pushTokens")
        .get();

      const tokenDocuments = tokenSnapshot.docs.filter((document) => {
        const token = document.data().token;
        return typeof token === "string" && token.length > 0;
      });

      for (let offset = 0; offset < tokenDocuments.length; offset += 500) {
        const batch = tokenDocuments.slice(offset, offset + 500);
        const tokens = batch.map((document) => document.data().token);

        const response = await admin.messaging().sendEachForMulticast({
          tokens,
          data: {
            type: "new_chat_message",
            chatId,
            chatTitle: title,
            letterId,
          },
          android: {
            // A high-priority data-only payload lets Android's messaging service
            // update the launcher badge as well as post the visible notification.
            priority: "high",
            ttl: 60 * 60 * 1000,
          },
          apns: {
            payload: {
              aps: {
                alert: {
                  title: "FunnyMail",
                  body,
                },
                sound: "default",
                badge: 1,
              },
            },
          },
        });

        const staleTokenRemovals = [];
        response.responses.forEach((result, index) => {
          if (!result.success && INVALID_TOKEN_CODES.has(result.error?.code)) {
            staleTokenRemovals.push(batch[index].ref.delete());
          }
        });
        if (staleTokenRemovals.length > 0) {
          await Promise.all(staleTokenRemovals);
        }
      }
    }
  },
);

async function pruneChatDrawings(chatId) {
  const chatRef = db.collection("chats").doc(chatId);
  const chatSnapshot = await chatRef.get();
  if (!chatSnapshot.exists) return;

  const chat = chatSnapshot.data() || {};
  const limit = chat.maxLettersCount;
  if (!Number.isInteger(limit) || limit < 1) return;

  const snapshot = await chatRef.collection("letters")
    .orderBy("createdAt", "asc")
    .get();
  const now = Date.now();
  const activeLetters = snapshot.docs.filter((document) => {
    const expiresAt = document.data().expiresAt;
    return expiresAt && typeof expiresAt.toMillis === "function" &&
      expiresAt.toMillis() > now;
  });
  const numberToDelete = Math.max(0, activeLetters.length - limit);
  const toDelete = activeLetters.slice(0, numberToDelete);

  for (let offset = 0; offset < toDelete.length; offset += 500) {
    const batch = db.batch();
    toDelete.slice(offset, offset + 500).forEach((document) => batch.delete(document.ref));
    await batch.commit();
  }
}

// Enforce the configured maximum immediately after a drawing is saved.
exports.enforceDrawingLimitOnNewLetter = onDocumentCreated(
  {
    document: "chats/{chatId}/letters/{letterId}",
    region: "europe-west1",
  },
  async (event) => {
    if (!event.data) return;
    await pruneChatDrawings(event.params.chatId);
  },
);

// If the creator changes the configured limit, remove excess oldest drawings.
exports.enforceDrawingLimitOnChatUpdate = onDocumentUpdated(
  {
    document: "chats/{chatId}",
    region: "europe-west1",
  },
  async (event) => {
    const before = event.data?.before.data() || {};
    const after = event.data?.after.data() || {};
    if (before.maxLettersCount === after.maxLettersCount) return;
    if (!Number.isInteger(after.maxLettersCount) || after.maxLettersCount < 1) return;
    await pruneChatDrawings(event.params.chatId);
  },
);

// Process explicit cleanup requests made by chat members when they enter an over-limit chat.
exports.processChatCleanupRequest = onDocumentCreated(
  {
    document: "chats/{chatId}/cleanupRequests/{requestId}",
    region: "europe-west1",
  },
  async (event) => {
    if (!event.data) return;
    await pruneChatDrawings(event.params.chatId);
  },
);
