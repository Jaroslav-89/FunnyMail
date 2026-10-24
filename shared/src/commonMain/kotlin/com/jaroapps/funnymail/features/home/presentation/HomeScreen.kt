package com.jaroapps.funnymail.features.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
    onCreateLetter: () -> Unit,
    onOpenLetters: () -> Unit,
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
            text = "Создавайте рисунки и отправляйте их друзьям. Ваши письма будут доступны в чате.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onCreateLetter,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Нарисовать письмо")
        }

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
                        "Рисунок проверен и сохранён на этом устройстве. Он ещё не отправлен — " +
                            "для отправки писем нужно подключить чаты и Firestore."
                    } else {
                        "Пока здесь нет писем. Начните с нового рисунка — список появится после подключения хранилища."
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
            onClick = onOpenLetters,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Мои письма")
        }
        OutlinedButton(
            onClick = onOpenChats,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Чаты и приглашения")
        }
    }
}
