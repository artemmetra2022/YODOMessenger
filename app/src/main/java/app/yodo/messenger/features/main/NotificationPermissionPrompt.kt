package app.yodo.messenger.features.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * НОВОЕ: собственный диалог с предложением выдать разрешение на уведомления.
 * Показывается один-единственный раз — сразу после первого входа пользователя,
 * до перехода в список чатов (перекрывает экран ChatList при первом появлении).
 *
 * По нажатию "Выдать" — вызывается системный запрос разрешения (Android 13+).
 * По нажатию "Отменить" — диалог просто закрывается без запроса.
 * В любом случае повторно этот экран больше не показывается.
 */
@Composable
fun NotificationPermissionPrompt(
    viewModel: NotificationPermissionViewModel = hiltViewModel()
) {
    val alreadyAsked by viewModel.alreadyAsked.collectAsState()
    var showDialog by remember(alreadyAsked) { mutableStateOf(!alreadyAsked) }

    val systemPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* результат системного диалога нам здесь не нужен обрабатывать отдельно */ }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                viewModel.markAsAsked()
            },
            icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
            title = { Text("Разрешить уведомления") },
            text = {
                Text("Чтобы оставаться в курсе новых сообщений, выдайте разрешение на уведомления.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    viewModel.markAsAsked()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        systemPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("Выдать")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    viewModel.markAsAsked()
                }) {
                    Text("Отменить")
                }
            }
        )
    }
}
