package app.yodo.messenger.features.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.yodo.messenger.features.chats.ChatListScreen

/**
 * ИЗМЕНЕНО (панель во всех основных окнах): нижняя панель навигации переехала
 * отсюда в [YodoBottomBar] и рисуется на уровне навигационного графа
 * (YodoNavGraph), чтобы оставаться видимой не только в списке чатов, но и на
 * «Рядом», «Офлайн», «Настройках» и «Админке».
 *
 * Здесь остался только сам список чатов и запрос разрешения на уведомления —
 * поведение экрана не изменилось.
 */
@Composable
fun MainScreen(
    onChatClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCreateGroupClick: () -> Unit = {},
    onCreateChannelClick: () -> Unit = {},
    onOpenContacts: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onWeatherClick: () -> Unit = {},
    onOpenAdminPanel: () -> Unit = {},
    // НОВОЕ (каталог/рекомендации каналов): открытие витрины каналов.
    onDiscoverChannels: () -> Unit = {},
    // НОВОЕ (админ-функции групп): переход к экрану группы по тапу на бейдж заявок.
    onOpenGroupInfo: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ChatListScreen(
            onChatClick = onChatClick,
            onProfileClick = onProfileClick,
            onSettingsClick = onSettingsClick,
            onSearchClick = onSearchClick,
            onCreateGroupClick = onCreateGroupClick,
            onCreateChannelClick = onCreateChannelClick,
            onOpenContacts = onOpenContacts,
            onOpenArchive = onOpenArchive,
            onWeatherClick = onWeatherClick,
            onOpenAdminPanel = onOpenAdminPanel,
            onDiscoverChannels = onDiscoverChannels,
            onOpenGroupInfo = onOpenGroupInfo
        )
    }

    NotificationPermissionPrompt()
}
