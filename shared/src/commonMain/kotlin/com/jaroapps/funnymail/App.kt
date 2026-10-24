package com.jaroapps.funnymail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jaroapps.funnymail.core.di.appModule
import com.jaroapps.funnymail.core.navigation.AppDestination
import com.jaroapps.funnymail.features.home.presentation.HomeScreen
import com.jaroapps.funnymail.features.placeholder.presentation.PlaceholderScreen
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

@Composable
fun App() {
    KoinApplication(
        configuration = koinConfiguration { modules(appModule) },
    ) {
        FunnyMailContent()
    }
}

@Composable
private fun FunnyMailContent() {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Home) }

    MaterialTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
            when (destination) {
                AppDestination.Home -> HomeScreen(
                    onCreateLetter = { destination = AppDestination.Drawing },
                    onOpenLetters = { destination = AppDestination.Letters },
                    onOpenChats = { destination = AppDestination.Chats },
                )
                AppDestination.Drawing -> PlaceholderScreen(
                    title = "Новое письмо",
                    description = "Здесь появится холст для рисования.",
                    onBack = { destination = AppDestination.Home },
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
