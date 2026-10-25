package com.jaroapps.funnymail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import com.jaroapps.funnymail.core.auth.NativeSocialSignIn
import com.jaroapps.funnymail.core.storage.DrawingDraftStorage
import com.jaroapps.funnymail.features.drawing.data.local.LocalDrawingDatabaseBuilder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NativeSocialSignIn.attachPlatformContext(this)
        DrawingDraftStorage.attachPlatformContext(this)
        LocalDrawingDatabaseBuilder.attachPlatformContext(this)

        setContent {
            App()
        }
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
