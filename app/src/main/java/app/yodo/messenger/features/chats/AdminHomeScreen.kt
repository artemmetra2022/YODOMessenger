package app.yodo.messenger.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * НОВОЕ (единая вкладка «Админка»): сводный экран для 2 доверенных аккаунтов —
 * все админ-функции приложения в одном месте вместо того, чтобы искать их по
 * разным экранам. Сам по себе не содержит бизнес-логики — каждый пункт просто
 * открывает уже существующий экран/поток. Прежние точки входа (пункт в
 * Настройках, FAB в списке чатов, кнопка на профиле пользователя) не убраны —
 * этот экран лишь даёт параллельный, собранный в одном месте путь к ним же.
 *
 * Видимость самой вкладки в нижней навигации управляется полем isAppAdmin —
 * обычные пользователи её вообще не видят.
 */
@Composable
fun AdminHomeScreen(
    onOpenSupportInbox: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenOfficialChannel: (chatId: String) -> Unit,
    // НОВОЕ (глобальный аудит-лог): переход к журналу действий Админки.
    onOpenAuditLog: () -> Unit,
    viewModel: AdminHomeViewModel = hiltViewModel()
) {
    val requireEmailVerification by viewModel.requireEmailVerification.collectAsState(initial = true)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Админка") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AdminSectionRow(
                icon = Icons.Filled.SupportAgent,
                title = "Обращения в поддержку",
                subtitle = "Переписка с пользователями по support-чатам",
                onClick = onOpenSupportInbox
            )
            AdminSectionRow(
                icon = Icons.Filled.Flag,
                title = "Жалобы",
                subtitle = "Все жалобы и обжалования по всем чатам",
                onClick = onOpenReports
            )
            AdminSectionRow(
                icon = Icons.Filled.People,
                title = "Пользователи",
                subtitle = "Поиск аккаунта и глобальная блокировка",
                onClick = onOpenUsers
            )
            AdminSectionRow(
                icon = Icons.Filled.Campaign,
                title = "Официальный канал",
                subtitle = "Публикация постов и рассылок",
                onClick = { onOpenOfficialChannel(viewModel.officialChannelId) }
            )
            AdminSectionRow(
                icon = Icons.Filled.History,
                title = "Журнал действий",
                subtitle = "История глобальных блокировок и изменений настроек",
                onClick = onOpenAuditLog
            )

            // Настройка обязательного подтверждения email — та же логика и то же
            // хранилище (AppSettingsRepository), что и в Центре безопасности.
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.MarkEmailRead, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Требовать подтверждение email", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Действует для всех пользователей при входе",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = requireEmailVerification,
                        onCheckedChange = { viewModel.setRequireEmailVerification(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminSectionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
