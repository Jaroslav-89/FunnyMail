package com.jaroapps.funnymail.features.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onOpenChats: () -> Unit,
    onOpenAccount: () -> Unit,
    hasDraft: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "FunnyMail",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Письма, нарисованные от руки",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Откройте чат, чтобы создать новое письмо или выбрать рисунок из локальной галереи.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (hasDraft) "Черновик подготовлен" else "Ваше пространство",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (hasDraft) {
                        "Черновик сохранён на этом устройстве. Откройте чат, чтобы отправить его."
                    } else {
                        "Рисунки можно создавать и выбирать прямо внутри чата."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        OutlinedButton(
            onClick = onOpenAccount,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Войти / профиль")
        }
        OutlinedButton(
            onClick = onOpenChats,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Чаты и приглашения")
        }
    }
}
