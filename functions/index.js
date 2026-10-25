const admin = require("firebase-admin");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");

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
          notification: {
            title: "FunnyMail",
            body,
          },
          data: {
            type: "new_chat_message",
            chatId,
            chatTitle: title,
            letterId,
          },
          android: {
            priority: "high",
            notification: {
              channelId: "new_chat_messages",
              notificationCount: 1,
              color: "#F5A800",
              tag: `letter-${letterId}`,
            },
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
