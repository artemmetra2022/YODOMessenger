package app.yodo.messenger.features.main

import app.yodo.messenger.ui.components.NavigationBarItem
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import app.yodo.messenger.data.local.InterfaceStyle
import app.yodo.messenger.ui.components.glassTint
import app.yodo.messenger.ui.components.liquidGlass
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.LocalInterfaceStyle

/**
 * НОВОЕ (панель во всех основных окнах): нижняя панель навигации вынесена из
 * MainScreen на уровень навигационного графа и теперь рисуется поверх всей
 * навигации (см. YodoNavGraph).
 *
 * Раньше панель существовала только внутри экрана списка чатов, поэтому переход
 * на «Рядом», «Офлайн», «Настройки» или «Админку» уводил на экран без панели —
 * вернуться к чатам можно было только кнопкой «Назад». Теперь панель остаётся
 * на всех основных экранах, и переключаться между разделами можно откуда угодно.
 *
 * Внешний вид и поведение пунктов не изменились: та же тема, тот же
 * экспериментальный «жидкое стекло»-стиль, вкладка «Админка» — только для двух
 * доверенных аккаунтов.
 */
@Composable
fun YodoBottomBar(
    selectedTab: Int,
    showAdminTab: Boolean,
    onChatsClick: () -> Unit,
    onNearbyClick: () -> Unit,
    onOfflineClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAdminClick: () -> Unit
) {
    val experimentalInterface = LocalInterfaceStyle.current == InterfaceStyle.EXPERIMENTAL
    val colorTheme = LocalColorTheme.current
    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colorTheme.primary,
        selectedTextColor = colorTheme.primary,
        indicatorColor = colorTheme.primary.copy(alpha = if (experimentalInterface) 0.13f else 0.16f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    )

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    NavigationBar(
        modifier = Modifier
            .then(if (experimentalInterface) Modifier.padding(horizontal = 10.dp, vertical = 6.dp) else Modifier)
            .liquidGlass(
                enabled = experimentalInterface,
                shape = RoundedCornerShape(28.dp),
                tint = glassTint(isDark),
                dark = isDark,
                elevation = 12
            ),
        containerColor = if (experimentalInterface) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (experimentalInterface) 0.dp else 3.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == TAB_CHATS,
            onClick = onChatsClick,
            icon = { Icon(Icons.Filled.Chat, contentDescription = stringResource(R.string.nav_chats_cd)) },
            label = { Text(stringResource(R.string.nav_chats)) },
            colors = navItemColors
        )
        NavigationBarItem(
            selected = selectedTab == TAB_NEARBY,
            onClick = onNearbyClick,
            icon = { Icon(Icons.Filled.NearMe, contentDescription = stringResource(R.string.nav_nearby_cd)) },
            label = { Text(stringResource(R.string.nav_nearby)) },
            colors = navItemColors
        )
        NavigationBarItem(
            selected = selectedTab == TAB_OFFLINE,
            onClick = onOfflineClick,
            icon = { Icon(Icons.Filled.WifiOff, contentDescription = stringResource(R.string.nav_offline_cd)) },
            label = { Text(stringResource(R.string.nav_offline)) },
            colors = navItemColors
        )
        NavigationBarItem(
            selected = selectedTab == TAB_SETTINGS,
            onClick = onSettingsClick,
            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title)) },
            label = { Text(stringResource(R.string.settings_title)) },
            colors = navItemColors
        )
        // НОВОЕ (единая вкладка «Админка»): видна только двум доверенным
        // аккаунтам — у остальных пользователей нижняя навигация из 4 пунктов.
        if (showAdminTab) {
            NavigationBarItem(
                selected = selectedTab == TAB_ADMIN,
                onClick = onAdminClick,
                icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Админка") },
                label = { Text("Админка") },
                colors = navItemColors
            )
        }
    }
}

/** Индексы вкладок нижней панели — общие для панели и навигационного графа. */
const val TAB_CHATS = 0
const val TAB_NEARBY = 1
const val TAB_OFFLINE = 2
const val TAB_SETTINGS = 3
const val TAB_ADMIN = 4
