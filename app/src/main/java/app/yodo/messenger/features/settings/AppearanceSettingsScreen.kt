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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
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
import app.yodo.messenger.data.local.InterfaceStyle
import app.yodo.messenger.data.local.ScreenTransitionStyle
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
    val interfaceStyle by viewModel.interfaceStyle.collectAsState()
    val glassIntensity by viewModel.glassIntensity.collectAsState()
    val screenTransitionDurationMs by viewModel.screenTransitionDurationMs.collectAsState()
    val screenTransitionStyle by viewModel.screenTransitionStyle.collectAsState()
    val screenTransitionAmplitude by viewModel.screenTransitionAmplitude.collectAsState()
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
                    icon = Icons.Filled.DashboardCustomize,
                    title = "Интерфейс",
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    Column {
                        InterfaceStyle.entries.forEach { style ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setInterfaceStyle(style) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = interfaceStyle == style,
                                    onClick = { viewModel.setInterfaceStyle(style) }
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(
                                        text = style.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = style.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (interfaceStyle == InterfaceStyle.EXPERIMENTAL) {
                            var glassSlider by remember(glassIntensity) {
                                mutableFloatStateOf(glassIntensity.toFloat())
                            }
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Матовость стекла",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${glassSlider.roundToInt()}%",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorTheme.primary
                                    )
                                }
                                Text(
                                    text = "0% — максимально прозрачное, 100% — почти полностью матовое",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Slider(
                                    value = glassSlider,
                                    onValueChange = {
                                        glassSlider = it
                                        viewModel.setGlassIntensity(it.roundToInt())
                                    },
                                    onValueChangeFinished = {
                                        viewModel.setGlassIntensity(glassSlider.roundToInt())
                                    },
                                    valueRange = 0f..100f,
                                    steps = 19,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Прозрачное", style = MaterialTheme.typography.labelSmall)
                                    Text("Матовое", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "Кристалл" to 0,
                                        "Чистое" to 25,
                                        "Баланс" to 55,
                                        "Иней" to 80,
                                        "Мат" to 100
                                    ).forEach { (label, value) ->
                                        val selected = glassSlider.roundToInt() == value
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (selected) colorTheme.primary.copy(alpha = 0.16f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                )
                                                .border(
                                                    width = if (selected) 1.dp else 0.5.dp,
                                                    color = if (selected) colorTheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                    glassSlider = value.toFloat()
                                                    viewModel.setGlassIntensity(value)
                                                }
                                                .padding(horizontal = 11.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                "$label $value%",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (selected) colorTheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.Speed,
                    title = "Переходы между экранами",
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    var transitionSlider by remember(screenTransitionDurationMs) {
                        mutableFloatStateOf(screenTransitionDurationMs.toFloat())
                    }
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = "Стиль перехода",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ScreenTransitionStyle.entries.forEach { style ->
                                val selected = screenTransitionStyle == style
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (selected) colorTheme.primary.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        )
                                        .border(
                                            if (selected) 1.dp else 0.5.dp,
                                            if (selected) colorTheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { viewModel.setScreenTransitionStyle(style) }
                                        .padding(horizontal = 11.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        style.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) colorTheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Продолжительность",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (transitionSlider.roundToInt() == 0) "Выкл."
                                else "${transitionSlider.roundToInt()} мс",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colorTheme.primary
                            )
                        }
                        Text(
                            text = "Меньше — быстрее. 0 мс полностью отключает анимацию перехода.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Slider(
                            value = transitionSlider,
                            onValueChange = { transitionSlider = it },
                            onValueChangeFinished = {
                                val snapped = (transitionSlider / 20f).roundToInt() * 20
                                transitionSlider = snapped.toFloat()
                                viewModel.setScreenTransitionDurationMs(snapped)
                            },
                            valueRange = 0f..400f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Без анимации" to 0,
                                "Очень быстро" to 80,
                                "Быстро" to 140,
                                "Плавно" to 240,
                                "Медленно" to 400
                            ).forEach { (label, value) ->
                                val selected = screenTransitionDurationMs == value
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (selected) colorTheme.primary.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        )
                                        .border(
                                            if (selected) 1.dp else 0.5.dp,
                                            if (selected) colorTheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            transitionSlider = value.toFloat()
                                            viewModel.setScreenTransitionDurationMs(value)
                                        }
                                        .padding(horizontal = 11.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) colorTheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (screenTransitionStyle != ScreenTransitionStyle.FADE &&
                            screenTransitionStyle != ScreenTransitionStyle.NONE
                        ) {
                            var amplitudeSlider by remember(screenTransitionAmplitude) {
                                mutableFloatStateOf(screenTransitionAmplitude.toFloat())
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Амплитуда движения",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${amplitudeSlider.roundToInt()}%",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorTheme.primary
                                )
                            }
                            Slider(
                                value = amplitudeSlider,
                                onValueChange = { amplitudeSlider = it },
                                onValueChangeFinished = {
                                    val snapped = (amplitudeSlider / 5f).roundToInt() * 5
                                    amplitudeSlider = snapped.toFloat()
                                    viewModel.setScreenTransitionAmplitude(snapped)
                                },
                                valueRange = 0f..100f,
                                steps = 19,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "На Android 13+ системный жест «Назад» интерактивно следует за пальцем.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
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
