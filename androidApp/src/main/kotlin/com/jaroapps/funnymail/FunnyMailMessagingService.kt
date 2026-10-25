package com.jaroapps.funnymail

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FunnyMailMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title?.takeIf { it.isNotBlank() } ?: "FunnyMail"
        val chatName = message.data["chatTitle"]?.takeIf { it.isNotBlank() }
        val body = message.notification?.body?.takeIf { it.isNotBlank() }
            ?: chatName?.let { "Новое сообщение в чате $it" }
            ?: "Новое сообщение в чате"

        ensureChannel()
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            message.data["chatId"]?.let { putExtra("chatId", it) }
        }
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                this,
                message.data["chatId"]?.hashCode() ?: 0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val nextUnreadCount = (NotificationManagerCompat.from(this).activeNotifications.size + 1)
            .coerceAtLeast(1)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setNumber(nextUnreadCount)
            .setLargeIcon(applicationIconBitmap())
            .setColor(ContextCompat.getColor(this, R.color.funnymail_brand_yellow))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        NotificationManagerCompat.from(this).notify(
            (message.messageId ?: System.currentTimeMillis().toString()).hashCode(),
            notification,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Новые сообщения в чатах",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Уведомления о новых рисунках и сообщениях в чатах FunnyMail"
            },
        )
    }

    private fun applicationIconBitmap(): Bitmap? = runCatching {
        val drawable = packageManager.getApplicationIcon(packageName)
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
    }.getOrNull()

    private companion object {
        const val CHANNEL_ID = "new_chat_messages"
    }
}
