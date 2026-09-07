package app.yodo.messenger.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
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
 * НОВОЕ (разделение настроек по категориям): «Уведомления» — mute all, звук,
 * вибрация, тихие часы, скрытие превью, пауза уведомлений, тестовое
 * уведомление. Логика и внешний вид перенесены из бывшего монолитного
 * SettingsScreen.kt без изменений поведения.
 *
 * ПРИМЕЧАНИЕ: пункт поиска "Пауза уведомлений" (notification_snooze) ведёт на
 * якорь ANCHOR_QUIET_HOURS — единственный реальный якорь в этой области; так
 * было и в исходном едином экране настроек (ANCHOR_NOTIFICATION_SNOOZE в
 * индексе поиска не был расставлен как отдельный якорь в UI и там же).
 */
@Composable
fun NotificationsSettingsScreen(
    onBackClick: () -> Unit,
    initialAnchorId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val muteAllNotifications by viewModel.muteAllNotifications.collectAsState()
    val notificationSound by viewModel.notificationSound.collectAsState()
    val notificationVibration by viewModel.notificationVibration.collectAsState()
    val quietHoursEnabled by viewModel.quietHoursEnabled.collectAsState()
    val quietHoursStart by viewModel.quietHoursStart.collectAsState()
    val quietHoursEnd by viewModel.quietHoursEnd.collectAsState()
    val hideNotificationPreview by viewModel.hideNotificationPreview.collectAsState()
    val notificationsSnoozedUntil by viewModel.notificationsSnoozedUntil.collectAsState()

    val colorTheme = LocalColorTheme.current
    val listState = rememberLazyListState()
    val anchorPositions = remember { mutableMapOf<String, Float>() }
    var highlightedAnchor by remember { mutableStateOf<String?>(null) }

    // ИЗМЕНЕНО: пункт поиска "Пауза уведомлений" ведёт на этот же якорь.
    val resolvedInitialAnchorId = if (initialAnchorId == SettingsSearchIndex.ANCHOR_NOTIFICATION_SNOOZE) {
        SettingsSearchIndex.ANCHOR_QUIET_HOURS
    } else initialAnchorId

    LaunchedEffect(resolvedInitialAnchorId) {
        if (resolvedInitialAnchorId != null) {
            kotlinx.coroutines.delay(250)
            highlightedAnchor = resolvedInitialAnchorId
            anchorPositions[resolvedInitialAnchorId]?.let { listState.animateScrollBy(it - 24f) }
            kotlinx.coroutines.delay(SETTINGS_HIGHLIGHT_DURATION_MS)
            if (highlightedAnchor == resolvedInitialAnchorId) highlightedAnchor = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Уведомления", style = MaterialTheme.typography.titleLarge) },
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
                    icon = Icons.Filled.VolumeUp,
                    title = stringResource(R.string.settings_section_notifications),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_NOTIFICATIONS, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.NotificationsOff,
                        title = stringResource(R.string.settings_mute_all),
                        subtitle = stringResource(R.string.settings_mute_all_subtitle),
                        checked = muteAllNotifications,
                        onCheckedChange = { viewModel.setMuteAllNotifications(it) },
                        colorTheme = colorTheme
                    )
                    AnimatedVisibility(
                        visible = !muteAllNotifications,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(200))
                    ) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsToggleRow(
                                icon = Icons.Filled.VolumeUp,
                                title = stringResource(R.string.settings_sound),
                                subtitle = stringResource(R.string.settings_sound_subtitle),
                                checked = notificationSound,
                                onCheckedChange = { viewModel.setNotificationSound(it) },
                                colorTheme = colorTheme
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsToggleRow(
                                icon = Icons.Filled.Vibration,
                                title = stringResource(R.string.settings_vibration),
                                subtitle = stringResource(R.string.settings_vibration_subtitle),
                                checked = notificationVibration,
                                onCheckedChange = { viewModel.setNotificationVibration(it) },
                                colorTheme = colorTheme
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_QUIET_HOURS, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsToggleRow(
                        icon = Icons.Filled.NotificationsOff,
                        title = "Тихие часы",
                        subtitle = "Не беспокоить в заданный ночной интервал",
                        checked = quietHoursEnabled,
                        onCheckedChange = { viewModel.setQuietHoursEnabled(it) },
                        colorTheme = colorTheme
                    )
                    AnimatedVisibility(visible = quietHoursEnabled) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsNavigateRow(
                                icon = Icons.Filled.Timer,
                                title = "Начало: %02d:00".format(quietHoursStart),
                                subtitle = "Нажмите, чтобы сдвинуть на +1 час",
                                colorTheme = colorTheme,
                                onClick = { viewModel.shiftQuietHoursStart(1) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsNavigateRow(
                                icon = Icons.Filled.Timer,
                                title = "Конец: %02d:00".format(quietHoursEnd),
                                subtitle = "Нажмите, чтобы сдвинуть на +1 час",
                                colorTheme = colorTheme,
                                onClick = { viewModel.shiftQuietHoursEnd(1) }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.RemoveRedEye,
                        title = "Скрывать текст в уведомлениях",
                        subtitle = "Показывать «Новое сообщение» без имени и текста",
                        checked = hideNotificationPreview,
                        onCheckedChange = { viewModel.setHideNotificationPreview(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.Timer,
                        title = if (notificationsSnoozedUntil > System.currentTimeMillis()) "Пауза активна — выключить" else "Пауза уведомлений на 1 час",
                        subtitle = if (notificationsSnoozedUntil > System.currentTimeMillis()) "Уведомления временно отключены" else "Тишина на ближайший час",
                        colorTheme = colorTheme,
                        onClick = {
                            if (notificationsSnoozedUntil > System.currentTimeMillis()) viewModel.clearNotificationSnooze()
                            else viewModel.snoozeNotifications(60L * 60L * 1000L)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.Timer,
                        title = "Пауза уведомлений на 8 часов",
                        subtitle = "Удобно на ночь или на встречу",
                        colorTheme = colorTheme,
                        onClick = { viewModel.snoozeNotifications(8L * 60L * 60L * 1000L) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.VolumeUp,
                        title = "Отправить тестовое уведомление",
                        subtitle = "Проверить, что уведомления работают",
                        colorTheme = colorTheme,
                        onClick = { viewModel.sendTestNotification() }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
