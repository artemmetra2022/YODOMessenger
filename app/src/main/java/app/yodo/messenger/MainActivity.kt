package app.yodo.messenger

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import app.yodo.messenger.data.local.ThemePreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.navigation.Routes
import app.yodo.messenger.navigation.YodoNavGraph
import app.yodo.messenger.ui.theme.YodoMessengerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Внедряем напрямую, чтобы синхронно решить стартовый экран до первого кадра
    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var userSettingsPreferences: UserSettingsPreferences

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
            val fontSize by userSettingsPreferences.fontSize.collectAsState(
                initial = app.yodo.messenger.data.local.FontSize.MEDIUM
            )

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* Если отклонено — приложение продолжит работать, просто без push-уведомлений */ }

            // На Android 13+ показ уведомлений требует явного разрешения от пользователя
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            YodoMessengerTheme(darkTheme = isDarkTheme, fontScale = fontSize.scale) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    YodoNavGraph(navController = navController, startDestination = startDestination)
                }
            }
        }
    }
}
