package app.yodo.messenger.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.data.local.FontSize
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.allColorThemes
import kotlin.math.roundToInt

/**
 * НОВОЕ (разделение настроек по категориям): «Внешний вид» — объединяет бывшие
 * секции «Оформление» и «Кастомизация» (тёмная тема, цветовая схема, размер
 * шрифта). Логика и внешний вид самих настроек не менялись — перенесены из
 * бывшего монолитного SettingsScreen.kt без изменений поведения.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsScreen(
    onBackClick: () -> Unit,
    initialAnchorId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val colorThemeName by viewModel.colorThemeName.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val colorTheme = LocalColorTheme.current

    val listState = rememberLazyListState()
    val anchorPositions = remember { mutableMapOf<String, Float>() }
    var highlightedAnchor by remember { mutableStateOf<String?>(null) }

    suspend fun scrollAndHighlight(anchorId: String) {
        highlightedAnchor = anchorId
        val targetY = anchorPositions[anchorId]
        if (targetY != null) {
            listState.animateScrollBy(targetY - 24f)
        }
        kotlinx.coroutines.delay(SETTINGS_HIGHLIGHT_DURATION_MS)
        if (highlightedAnchor == anchorId) highlightedAnchor = null
    }

    LaunchedEffect(initialAnchorId) {
        if (initialAnchorId != null) {
            kotlinx.coroutines.delay(250)
            scrollAndHighlight(initialAnchorId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Внешний вид", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.ColorLens,
                    title = stringResource(R.string.settings_section_appearance),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_APPEARANCE, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.Brightness6,
                        title = stringResource(R.string.settings_dark_theme),
                        subtitle = stringResource(R.string.settings_dark_theme_subtitle),
                        checked = isDarkTheme,
                        onCheckedChange = { viewModel.setDarkTheme(it) },
                        colorTheme = colorTheme
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.ColorLens,
                    title = stringResource(R.string.settings_section_customization),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_CUSTOMIZATION, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.ColorLens,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.settings_color_theme), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            allColorThemes.forEach { theme ->
                                val isSelected = colorThemeName == theme.name.name
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { viewModel.setColorTheme(theme.name.name) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(theme.primary)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    3.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    CircleShape
                                                ) else Modifier
                                            )
                                    )
                                    Text(
                                        theme.name.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 3.dp),
                                        color = if (isSelected) colorTheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.FormatSize,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        var sliderPosition by remember(fontSize) { mutableFloatStateOf(fontSize.ordinal.toFloat()) }
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = {
                                viewModel.setFontSize(FontSize.entries[sliderPosition.roundToInt()])
                            },
                            valueRange = 0f..4f,
                            steps = 3,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FontSize.entries.forEach { size ->
                                Text(
                                    text = size.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (fontSize == size) colorTheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
