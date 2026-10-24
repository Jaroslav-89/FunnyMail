package com.jaroapps.funnymail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jaroapps.funnymail.core.di.appModule
import com.jaroapps.funnymail.core.firebase.FirebaseRuntimeStatus
import com.jaroapps.funnymail.core.navigation.AppDestination
import com.jaroapps.funnymail.features.auth.presentation.AuthScreen
import com.jaroapps.funnymail.features.drawing.presentation.DrawingScreen
import com.jaroapps.funnymail.features.home.presentation.HomeScreen
import com.jaroapps.funnymail.features.placeholder.presentation.PlaceholderScreen
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

@Composable
fun App() {
    val firebaseConfigured = remember { FirebaseRuntimeStatus.initialize() }
    var lastDraftJson by remember { mutableStateOf<String?>(null) }

    KoinApplication(
        configuration = koinConfiguration { modules(appModule) },
    ) {
        FunnyMailContent(
            firebaseConfigured = firebaseConfigured,
            hasDraft = lastDraftJson != null,
            onDraftPrepared = { json -> lastDraftJson = json },
        )
    }
}

@Composable
private fun FunnyMailContent(
    firebaseConfigured: Boolean,
    hasDraft: Boolean,
    onDraftPrepared: (String) -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Home) }

    MaterialTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
            when (destination) {
                AppDestination.Home -> HomeScreen(
                    onCreateLetter = { destination = AppDestination.Drawing },
                    onOpenLetters = { destination = AppDestination.Letters },
                    onOpenChats = { destination = AppDestination.Chats },
                    onOpenAccount = { destination = AppDestination.Auth },
                    hasDraft = hasDraft,
                )
                AppDestination.Auth -> AuthScreen(
                    firebaseConfigured = firebaseConfigured,
                    onBack = { destination = AppDestination.Home },
                )
                AppDestination.Drawing -> DrawingScreen(
                    onBack = { destination = AppDestination.Home },
                    onPrepared = { json ->
                        onDraftPrepared(json)
                        destination = AppDestination.Home
                    },
                )
                AppDestination.Letters -> PlaceholderScreen(
                    title = "Мои письма",
                    description = "Здесь будут храниться ваши письма.",
                    onBack = { destination = AppDestination.Home },
                )
                AppDestination.Chats -> PlaceholderScreen(
                    title = "Чаты",
                    description = "Здесь появятся ваши чаты и приглашения.",
                    onBack = { destination = AppDestination.Home },
                )
            }
        }
    }
}
