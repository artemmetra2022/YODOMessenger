package app.yodo.messenger.features.settings

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * НОВОЕ (разумные категории): категория «Приложение» — разделы, которые не
 * являются настройками мессенджера как таковыми, и информация о самом приложении.
 *
 * Выделена из «Аккаунта», где эти пункты лежали вперемешку с выходом и удалением
 * аккаунта: «Школа» и её настройки, «Фишки и инструменты», повторное обучение,
 * переключатель показа настроек в общем поиске и «О приложении» с проверкой
 * обновлений. Логика пунктов не менялась — перенос без изменений поведения.
 */
@Composable
fun AppSettingsScreen(
    onBackClick: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSchool: () -> Unit,
    onOpenSchoolSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenAbout: () -> Unit,
    initialAnchorId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val showSettingsInGlobalSearch by viewModel.showSettingsInGlobalSearch.collectAsState()
    // НОВОЕ (раздел «Школа»): пункт «Школа» скрывается переключателем или
    // глобально админом; админы видят его всегда, чтобы можно было вернуть.
    val schoolSectionEnabled by viewModel.schoolSectionEnabled.collectAsState()
    val schoolSectionGloballyHidden by viewModel.schoolSectionGloballyHidden.collectAsState()
    val showSchoolSection = schoolSectionEnabled &&
        (!schoolSectionGloballyHidden || viewModel.isAppAdmin)

    val colorTheme = LocalColorTheme.current
    val listState = rememberLazyListState()
    val anchorPositions = remember { mutableMapOf<String, Float>() }
    var highlightedAnchor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialAnchorId) {
        if (initialAnchorId != null) {
            kotlinx.coroutines.delay(250)
            highlightedAnchor = initialAnchorId
            anchorPositions[initialAnchorId]?.let { listState.animateScrollBy(it - 24f) }
            kotlinx.coroutines.delay(SETTINGS_HIGHLIGHT_DURATION_MS)
            if (highlightedAnchor == initialAnchorId) highlightedAnchor = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приложение", style = MaterialTheme.typography.titleLarge) },
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
                    icon = Icons.Filled.Settings,
                    title = "Разделы и приложение",
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_APP, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_TOOLS, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Star,
                        title = "Фишки и инструменты",
                        subtitle = "20 мини-функций: заметки, таймер, пароли, конвертеры и другое",
                        colorTheme = colorTheme,
                        onClick = onOpenTools
                    )
                }
            }
            // НОВОЕ (раздел «Школа»): школьный справочник гимназии №196.
            if (showSchoolSection) {
                item {
                    SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_SCHOOL, anchorPositions, highlightedAnchor, colorTheme)) {
                        SettingsNavigateRow(
                            icon = Icons.Filled.School,
                            title = "Школа",
                            subtitle = "Справочник гимназии №196: учителя, новости, расписание, опросы, викторина и игры",
                            colorTheme = colorTheme,
                            onClick = onOpenSchool
                        )
                    }
                }
            }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_SCHOOL_SETTINGS, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.School,
                        title = "Настройки раздела «Школа»",
                        subtitle = "Показывать раздел целиком и выбирать отдельные подразделы",
                        colorTheme = colorTheme,
                        onClick = onOpenSchoolSettings
                    )
                }
            }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_ONBOARDING, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.School,
                        title = "Пройти обучение",
                        subtitle = "Короткий тур по возможностям приложения",
                        colorTheme = colorTheme,
                        onClick = onOpenOnboarding
                    )
                }
            }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_SEARCH_IN_GLOBAL, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsToggleRow(
                        icon = Icons.Filled.Search,
                        title = "Показывать настройки в общем поиске",
                        subtitle = "Результаты поиска настроек будут видны на главном экране поиска",
                        checked = showSettingsInGlobalSearch,
                        onCheckedChange = { viewModel.setShowSettingsInGlobalSearch(it) },
                        colorTheme = colorTheme
                    )
                }
            }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_ABOUT, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Info,
                        title = "О приложении",
                        subtitle = "Версия приложения и проверка обновлений",
                        colorTheme = colorTheme,
                        onClick = onOpenAbout
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
