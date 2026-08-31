package app.yodo.messenger.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * НОВОЕ (разделение настроек по категориям): точка входа в раздел настроек.
 * Вместо одного длинного списка всех настроек — 6 категорий с кратким описанием,
 * каждая ведёт на свой отдельный экран. Поиск (как и раньше) ищет по всем
 * настройкам сразу, но теперь тап по найденному пункту сначала открывает нужную
 * категорию, а уже там прокручивает и подсвечивает конкретный пункт.
 */
private data class SettingsCategory(
    val route: String,
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
fun SettingsCategoriesScreen(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onOpenCategory: (categoryRoute: String) -> Unit,
    onOpenSearchResult: (categoryRoute: String, anchorId: String) -> Unit
) {
    val colorTheme = LocalColorTheme.current
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        if (query.isBlank()) emptyList() else SettingsSearchMatcher.search(query)
    }

    val categories = listOf(
        SettingsCategory(
            route = SettingsSearchIndex.ROUTE_APPEARANCE,
            icon = Icons.Filled.ColorLens,
            title = "Внешний вид",
            description = "Тёмная тема, цветовая схема, размер шрифта"
        ),
        SettingsCategory(
            route = SettingsSearchIndex.ROUTE_LANGUAGE,
            icon = Icons.Filled.Language,
            title = "Язык",
            description = "Язык интерфейса приложения"
        ),
        SettingsCategory(
            route = SettingsSearchIndex.ROUTE_CHATS,
            icon = Icons.Filled.Chat,
            title = "Чаты",
            description = "Отправка сообщений, клавиатура, фон чата, папки"
        ),
        SettingsCategory(
            route = SettingsSearchIndex.ROUTE_PRIVACY,
            icon = Icons.Filled.PrivacyTip,
            title = "Конфиденциальность",
            description = "Статус «в сети», PIN-код, блокировки, видимость профиля"
        ),
        SettingsCategory(
            route = SettingsSearchIndex.ROUTE_NOTIFICATIONS,
            icon = Icons.Filled.VolumeUp,
            title = "Уведомления",
            description = "Звук, вибрация, тихие часы, пауза уведомлений"
        ),
        SettingsCategory(
            route = SettingsSearchIndex.ROUTE_ACCOUNT,
            icon = Icons.Filled.Person,
            title = "Аккаунт",
            description = "Безопасность, смена аккаунта, выход, удаление аккаунта"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.settings_profile))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colorTheme.primary.copy(alpha = 0.10f), Color.Transparent),
                        endY = 400f
                    )
                )
                .padding(padding)
        ) {
            item {
                SettingsSearchBar(
                    query = query,
                    onQueryChanged = { query = it },
                    colorTheme = colorTheme
                )
            }
            if (query.isNotBlank()) {
                item {
                    SettingsSearchResultsList(
                        results = results,
                        colorTheme = colorTheme,
                        onResultClick = { onOpenSearchResult(it.categoryRoute, it.anchorId) }
                    )
                }
            } else {
                items(categories) { category ->
                    CategoryRow(category = category, onClick = { onOpenCategory(category.route) })
                    Spacer(modifier = Modifier.padding(4.dp))
                }
            }
            item { Spacer(modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun CategoryRow(category: SettingsCategory, onClick: () -> Unit) {
    val colorTheme = LocalColorTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorTheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(category.icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(category.title, style = MaterialTheme.typography.bodyLarge, color = colorTheme.primary)
            Text(
                category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}
