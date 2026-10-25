package com.jaroapps.funnymail

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.jaroapps.funnymail.core.auth.NativeSocialSignIn
import com.jaroapps.funnymail.core.storage.DrawingDraftStorage
import com.jaroapps.funnymail.core.storage.ThemePreferenceStorage
import com.jaroapps.funnymail.features.drawing.data.local.LocalDrawingDatabaseBuilder

class MainActivity : ComponentActivity() {
    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* permission can be changed later in system settings */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        createChatNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        NativeSocialSignIn.attachPlatformContext(this)
        DrawingDraftStorage.attachPlatformContext(this)
        ThemePreferenceStorage.attachPlatformContext(this)
        LocalDrawingDatabaseBuilder.attachPlatformContext(this)

        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        // Keep the red icon marker while any unread-message notification remains active.
        val hasActiveMessageNotifications =
            NotificationManagerCompat.from(this).activeNotifications.isNotEmpty()
        LauncherBadge.setUnread(this, unread = hasActiveMessageNotifications)
    }

    private fun createChatNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "new_chat_messages_v2",
                "Новые сообщения в чатах",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Уведомления о новых сообщениях в чатах FunnyMail"
                setShowBadge(false)
            },
        )
    }

    override fun onDestroy() {
        NativeSocialSignIn.attachPlatformContext(null)
        LocalDrawingDatabaseBuilder.attachPlatformContext(null)
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    LocalDrawingDatabaseBuilder.attachPlatformContext(LocalContext.current)
    App()
}
