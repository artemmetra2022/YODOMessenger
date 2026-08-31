package app.yodo.messenger.features.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.yodo.messenger.R
import app.yodo.messenger.ui.theme.ColorTheme

/**
 * НОВОЕ (разделение настроек по категориям): общие переиспользуемые компоненты
 * и утилиты, вынесенные из бывшего монолитного SettingsScreen.kt — используются
 * и экраном списка категорий (SettingsCategoriesScreen), и каждым из 6 экранов
 * конкретной категории (AppearanceSettingsScreen, LanguageSettingsScreen,
 * ChatsSettingsScreen, PrivacySettingsScreen, NotificationsSettingsScreen,
 * AccountSettingsScreen). Логика и внешний вид компонентов не менялись —
 * это чистый перенос без изменений поведения.
 */

const val SETTINGS_HIGHLIGHT_DURATION_MS = 1400L

/**
 * Запоминает Y-координату (относительно окна) элемента, чтобы прокрутить список
 * к найденному пункту после тапа по результату поиска настроек.
 */
fun Modifier.onSettingsAnchor(
    anchorId: String,
    positions: MutableMap<String, Float>
): Modifier = this.then(
    Modifier.onGloballyPositioned { coordinates ->
        positions[anchorId] = coordinates.positionInWindow().y
    }
)

/**
 * Подсветка найденного пункта после перехода из результатов поиска. Пока
 * [anchorId] == [highlightedAnchor] — фон элемента плавно подсвечивается
 * акцентным цветом, а затем так же плавно гаснет. Совмещает [onSettingsAnchor]
 * (для скролла) с анимированной заливкой фона.
 */
@Composable
fun Modifier.settingsSearchAnchor(
    anchorId: String,
    positions: MutableMap<String, Float>,
    highlightedAnchor: String?,
    colorTheme: ColorTheme
): Modifier {
    val isHighlighted = anchorId == highlightedAnchor
    val highlightColor by animateColorAsState(
        targetValue = if (isHighlighted) colorTheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (isHighlighted) 220 else 500),
        label = "settingsAnchorHighlight"
    )
    return this
        .onSettingsAnchor(anchorId, positions)
        .background(highlightColor, RoundedCornerShape(16.dp))
}

@Composable
fun SettingsSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    colorTheme: ColorTheme
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.settings_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val keyboardController = LocalSoftwareKeyboardController.current
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(colorTheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { keyboardController?.hide() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChanged("") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * ИЗМЕНЕНО (разделение настроек по категориям): раньше тап по найденному пункту
 * скроллил к якорю на этом же (единственном) экране настроек. Теперь категории
 * разнесены по отдельным экранам, поэтому обработчик принимает найденный пункт
 * целиком (а не только anchorId) — вызывающий код сам решает, в какой экран
 * категории перейти (по item.categoryRoute) и что сделать с item.anchorId
 * внутри него (проскроллить/подсветить).
 */
@Composable
fun SettingsSearchResultsList(
    results: List<SettingsSearchItem>,
    colorTheme: ColorTheme,
    onResultClick: (SettingsSearchItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (results.isEmpty()) {
            Text(
                stringResource(R.string.settings_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
            )
        } else {
            Text(
                pluralStringResource(R.plurals.settings_search_results_count, results.size, results.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            SettingsCard {
                results.forEachIndexed { index, item ->
                    if (index > 0) androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onResultClick(item) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colorTheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "${item.sectionTitle} · ${item.subtitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
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
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    colorTheme: ColorTheme,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    Brush.verticalGradient(listOf(colorTheme.primary, colorTheme.accent)),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colorTheme.primary
        )
    }
}

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

@Composable
fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    colorTheme: ColorTheme
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colorTheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = colorTheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colorTheme: ColorTheme,
    enabled: Boolean = true,
    showIcon: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIcon) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorTheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colorTheme.primary
                else colorTheme.primary.copy(alpha = 0.4f)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.4f
                )
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colorTheme.primary)
        )
    }
}

@Composable
fun SettingsNavigateRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colorTheme: ColorTheme,
    onClick: () -> Unit,
    tintOverride: Color? = null
) {
    val iconTint = tintOverride ?: colorTheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = iconTint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}
