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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.yodo.messenger.ui.theme.YodoError

/**
 * НОВОЕ (разделение настроек по категориям): «Аккаунт» — раздел «Жалобы»
 * (только для 2 главных админов), «Фишки и инструменты», Центр безопасности,
 * повторный онбординг, показ настроек в общем поиске, скрытие статус-бара,
 * смена аккаунта, выход и удаление аккаунта. Логика и внешний вид перенесены
 * из бывшего монолитного SettingsScreen.kt без изменений поведения —
 * включая обработку ошибок (errorMessage/errorMessageResId) и события
 * accountDeleted, которые на едином экране обрабатывались глобально, а
 * фактически относятся к действиям именно этого раздела (выход, удаление).
 */
@Composable
fun AccountSettingsScreen(
    onBackClick: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onSwitchAccount: () -> Unit,
    onLoggedOut: () -> Unit = {},
    initialAnchorId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val accountDeleted by viewModel.accountDeleted.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val errorMessageResId by viewModel.errorMessageResId.collectAsState()
    val showSettingsInGlobalSearch by viewModel.showSettingsInGlobalSearch.collectAsState()
    val hideStatusBarOnChatList by viewModel.hideStatusBarOnChatList.collectAsState()

    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
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

    val notAuthorizedText = stringResource(R.string.settings_not_authorized)
    LaunchedEffect(accountDeleted) { if (accountDeleted) onLoggedOut() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.consumeError() }
    }
    LaunchedEffect(errorMessageResId) {
        errorMessageResId?.let { resId ->
            val text = if (resId == R.string.settings_not_authorized) notAuthorizedText else ""
            if (text.isNotEmpty()) snackbarHostState.showSnackbar(text)
            viewModel.consumeError()
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text(stringResource(R.string.settings_delete_account_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_account_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { showDeleteAccountDialog = false; viewModel.deleteAccount() }) {
                    Text(stringResource(R.string.settings_delete_account), color = YodoError)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Аккаунт", style = MaterialTheme.typography.titleLarge) },
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
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.settings_section_account),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_ACCOUNT, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            // НОВОЕ (AC): раздел «Жалобы» виден только главным админам (2 почты).
            if (viewModel.isAppAdmin) {
                item {
                    SettingsCard {
                        SettingsNavigateRow(
                            icon = Icons.Filled.Flag,
                            title = "Жалобы",
                            subtitle = "Жалобы и обжалования пользователей на рассмотрение",
                            colorTheme = colorTheme,
                            onClick = onOpenReports
                        )
                    }
                }
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
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_SECURITY_CENTER, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Security,
                        title = "Центр безопасности",
                        subtitle = "Двухфакторная аутентификация, сброс через вопросы, защита от скриншотов, статусы",
                        colorTheme = colorTheme,
                        onClick = onOpenSecurity
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
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_HIDE_STATUS_BAR_CHAT_LIST, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsToggleRow(
                        icon = Icons.Filled.Fullscreen,
                        title = "Скрывать статус-бар в списке чатов",
                        subtitle = "Системная панель времени и батареи будет полностью скрыта на главном экране",
                        checked = hideStatusBarOnChatList,
                        onCheckedChange = { viewModel.setHideStatusBarOnChatList(it) },
                        colorTheme = colorTheme
                    )
                }
            }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_SWITCH_ACCOUNT, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.SwapHoriz,
                        title = "Сменить аккаунт",
                        subtitle = "Переключиться между аккаунтами или добавить новый",
                        colorTheme = colorTheme,
                        onClick = onSwitchAccount
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.ExitToApp,
                        title = stringResource(R.string.settings_logout),
                        subtitle = stringResource(R.string.settings_logout_subtitle),
                        colorTheme = colorTheme,
                        tintOverride = YodoError,
                        onClick = { viewModel.logout(); onLoggedOut() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.Delete,
                        title = stringResource(R.string.settings_delete_account),
                        subtitle = stringResource(R.string.settings_delete_account_subtitle),
                        colorTheme = colorTheme,
                        tintOverride = YodoError,
                        onClick = { showDeleteAccountDialog = true }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
