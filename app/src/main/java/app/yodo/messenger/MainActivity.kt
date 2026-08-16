package app.yodo.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.map
import app.yodo.messenger.data.local.LanguagePreferences
import app.yodo.messenger.data.local.PinRequirement
import app.yodo.messenger.data.local.ThemePreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.SessionRepository
import app.yodo.messenger.features.settings.PinLockScreen
import app.yodo.messenger.navigation.Routes
import app.yodo.messenger.navigation.YodoNavGraph
import app.yodo.messenger.ui.locale.LocalizedApp
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
    @Inject lateinit var languagePreferences: LanguagePreferences
    @Inject lateinit var twoFactorRepository: app.yodo.messenger.domain.repository.TwoFactorRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = if (authRepository.isLoggedIn()) {
            Routes.ChatList.route
        } else {
            Routes.Welcome.route
        }

        setContent {
            // Собираем все настройки из DataStore.
            // Пока любое из значений null (ещё не загружено) — показываем SplashScreen.
            val languageCode by languagePreferences.languageCode.collectAsState(initial = null)
            val isDarkTheme by themePreferences.isDarkTheme.collectAsState(initial = null)
            val colorThemeName by themePreferences.colorThemeName.collectAsState(initial = null)
            val fontSize by userSettingsPreferences.fontSize.collectAsState(initial = null)

            // PIN-блокировка
            val pinRequirement by userSettingsPreferences.pinRequirement.collectAsState(initial = PinRequirement.NEVER)
            val isPinSet by userSettingsPreferences.isPinSet.collectAsState(initial = false)
            // НОВОЕ (батч 7): 2FA по email-коду при входе и защита от скриншотов.
            val isTwoFactorSet by twoFactorRepository.observeState()
                .map { it.enabled }
                .collectAsState(initial = false)
            val screenshotProtection by userSettingsPreferences.screenshotProtection.collectAsState(initial = false)
            var twoFactorPassed by remember { mutableStateOf(false) }
            LaunchedEffect(screenshotProtection) {
                if (screenshotProtection) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            // НОВОЕ: задержка (в секундах) перед блокировкой при сворачивании (0 = сразу).
            val pinLockDelaySeconds by userSettingsPreferences.pinLockDelaySeconds.collectAsState(initial = 0)
            var isLocked by remember { mutableStateOf(false) }
            var didInitialLockCheck by remember { mutableStateOf(false) }
            // НОВОЕ: момент сворачивания приложения для расчёта задержки.
            var backgroundedAt by remember { mutableStateOf(0L) }

            // SplashScreen: пока настройки не загружены — крутим прогресс.
            if (languageCode == null || isDarkTheme == null || colorThemeName == null || fontSize == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@setContent
            }

            // Safe local copies — delegated properties can't be smart-cast
            val safeLanguageCode: String = languageCode!!
            val safeIsDarkTheme: Boolean = isDarkTheme!!
            val safeColorThemeName: String = colorThemeName!!
            val safeFontSize = fontSize!!
            val colorTheme = getColorThemeByName(safeColorThemeName)

            // Как только известно, установлен ли PIN и какой режим выбран — решаем,
            // нужно ли блокировать экран сразу при старте процесса (холодный запуск).
            LaunchedEffect(isPinSet, pinRequirement, didInitialLockCheck) {
                if (!didInitialLockCheck) {
                    isLocked = isPinSet && pinRequirement != PinRequirement.NEVER
                    didInitialLockCheck = true
                }
            }

            DisposableEffect(pinRequirement, isPinSet, pinLockDelaySeconds) {
                val observer = LifecycleEventObserver { _, event ->
                    if (isPinSet && pinRequirement == PinRequirement.ON_BACKGROUND) {
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                // При сворачивании: если задержка 0 — блокируем сразу,
                                // иначе запоминаем время и блокируем при возврате.
                                if (pinLockDelaySeconds <= 0) {
                                    isLocked = true
                                    backgroundedAt = 0L
                                } else {
                                    backgroundedAt = System.currentTimeMillis()
                                }
                            }
                            Lifecycle.Event.ON_START -> {
                                // При возврате: если прошло не меньше выбранной задержки — блокируем.
                                if (backgroundedAt > 0L &&
                                    System.currentTimeMillis() - backgroundedAt >= pinLockDelaySeconds * 1000L
                                ) {
                                    isLocked = true
                                }
                                backgroundedAt = 0L
                            }
                            else -> {}
                        }
                    }
                }
                ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
            }

            LocalizedApp(languageCode = safeLanguageCode) {
                YodoMessengerTheme(
                    darkTheme = safeIsDarkTheme,
                    colorTheme = colorTheme,
                    fontScale = safeFontSize.scale
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (isLocked && isPinSet && pinRequirement != PinRequirement.NEVER) {
                            PinLockScreen(onUnlocked = { isLocked = false })
                        } else if (isTwoFactorSet && !twoFactorPassed) {
                            // НОВОЕ (батч 7): второй пароль (двухфакторная аутентификация) при запуске.
                            app.yodo.messenger.features.security.AppTwoFactorGateScreen(
                                onUnlocked = { twoFactorPassed = true }
                            )
                        } else {
                            val navController = rememberNavController()

                            // Слушаем удалённый выход из сессии
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

                            YodoNavGraph(
                                navController = navController,
                                startDestination = startDestination
                            )
                        }
                    }
                }
            }
        }
    }
}
