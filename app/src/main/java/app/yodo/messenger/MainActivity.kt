package app.yodo.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.rememberNavController
import app.yodo.messenger.data.local.PinRequirement
import app.yodo.messenger.data.local.ThemePreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.SessionRepository
import app.yodo.messenger.features.settings.PinLockScreen
import app.yodo.messenger.navigation.Routes
import app.yodo.messenger.navigation.YodoNavGraph
import app.yodo.messenger.ui.theme.getColorThemeByName
import app.yodo.messenger.ui.theme.YodoMessengerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var themePreferences: ThemePreferences
    @Inject lateinit var userSettingsPreferences: UserSettingsPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = if (authRepository.isLoggedIn()) {
            Routes.ChatList.route
        } else {
            Routes.Welcome.route
        }

        setContent {
            val isDarkTheme by themePreferences.isDarkTheme.collectAsState(initial = true)
            val colorThemeName by themePreferences.colorThemeName.collectAsState(initial = "BLUE")
            val fontSize by userSettingsPreferences.fontSize.collectAsState(
                initial = app.yodo.messenger.data.local.FontSize.MEDIUM
            )
            val colorTheme = getColorThemeByName(colorThemeName)

            // ПЕРЕНЕСЕНО: запрос разрешения на уведомления больше не выполняется автоматически
            // при каждом запуске приложения. Теперь это отдельный кастомный диалог
            // (см. NotificationPermissionPrompt), который показывается один раз —
            // сразу после первого входа пользователя, на экране списка чатов (MainScreen).

            // п.6: PIN-блокировка приложения. Требование берём из настроек:
            // ON_CLOSE — блокируем только при холодном старте процесса (isLocked = true изначально);
            // ON_BACKGROUND — блокируем каждый раз, когда приложение уходит в фон (ON_STOP)
            // и возвращаем экран блокировки при возврате (ON_START), пока не введён верный PIN.
            val pinRequirement by userSettingsPreferences.pinRequirement.collectAsState(initial = PinRequirement.NEVER)
            val isPinSet by userSettingsPreferences.isPinSet.collectAsState(initial = false)
            var isLocked by remember { mutableStateOf(false) }
            var didInitialLockCheck by remember { mutableStateOf(false) }

            // Как только известно, установлен ли PIN и какой режим выбран — решаем,
            // нужно ли блокировать экран сразу при старте процесса (холодный запуск).
            LaunchedEffect(isPinSet, pinRequirement, didInitialLockCheck) {
                if (!didInitialLockCheck) {
                    isLocked = isPinSet && pinRequirement != PinRequirement.NEVER
                    didInitialLockCheck = true
                }
            }

            DisposableEffect(pinRequirement, isPinSet) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP && isPinSet && pinRequirement == PinRequirement.ON_BACKGROUND) {
                        isLocked = true
                    }
                }
                ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
            }

            YodoMessengerTheme(
                darkTheme = isDarkTheme,
                colorTheme = colorTheme,
                fontScale = fontSize.scale
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isLocked && isPinSet && pinRequirement != PinRequirement.NEVER) {
                        PinLockScreen(onUnlocked = { isLocked = false })
                    } else {
                        val navController = rememberNavController()

                        // Завершение сеанса с другого устройства/экрана: слушаем документ
                        // ТЕКУЩЕЙ сессии, пока пользователь залогинен. Если документ исчез —
                        // значит сеанс завершили удалённо: делаем signOut и уходим на Welcome,
                        // полностью очищая back stack, чтобы нельзя было вернуться назад в чат.
                        LaunchedEffect(authRepository.isLoggedIn()) {
                            if (authRepository.isLoggedIn()) {
                                sessionRepository.observeCurrentSessionExists().collect { exists ->
                                    if (!exists) {
                                        authRepository.logout()
                                        navController.navigate(Routes.Welcome.route) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                            }
                        }

                        YodoNavGraph(navController = navController, startDestination = startDestination)
                    }
                }
            }
        }
    }
}
