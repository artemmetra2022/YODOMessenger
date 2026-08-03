package app.yodo.messenger.ui.locale

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.yodo.messenger.data.local.AppLanguage
import java.util.Locale

/**
 * Хранит исходный (Activity-based) контекст, существовавший до подмены LocalContext
 * в LocalizedApp. hiltViewModel() требует Activity в цепочке LocalContext.current,
 * а createConfigurationContext() отдаёт "голый" ContextImpl — поэтому места, вызывающие
 * hiltViewModel() (например LanguageSwitcher), должны на время восстановить
 * LocalContext provides LocalActivityContext.current.
 */
val LocalActivityContext = compositionLocalOf<Context?> { null }

/**
 * Оборачивает контент приложения, подменяя LocalContext на контекст с нужной локалью.
 *
 * Приложение не использует AppCompatActivity/AppCompatDelegate, поэтому смена языка
 * не требует пересоздания Activity: stringResource() внутри Compose разрешает строки
 * через LocalContext.current.resources, и достаточно предоставить контекст с изменённой
 * Configuration.locale через CompositionLocalProvider. Пересборка происходит автоматически,
 * так как languageCode передаётся как ключ remember() и как параметр — при его смене
 * весь контент под LocalizedApp перекомпонуется с новыми строками.
 *
 * ВАЖНО: createConfigurationContext() возвращает обычный ContextImpl, не Activity.
 * hiltViewModel() внутри дерева ищет Activity через LocalContext.current и падает с
 * IllegalStateException, если находит "голый" ContextImpl. Поэтому исходный контекст
 * дополнительно прокидывается через LocalActivityContext — компоненты, вызывающие
 * hiltViewModel() (см. LanguageSwitcher), временно восстанавливают его перед созданием
 * ViewModel, а stringResource()/ресурсы по-прежнему разрешаются через LocalContext
 * с локализованным контекстом.
 */
@Composable
fun LocalizedApp(
    languageCode: String,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current

    val localizedContext = remember(languageCode, baseContext, baseConfiguration) {
        // Безопасное разрешение локали: если код пустой или некорректный —
        // fallback на системную локаль, чтобы избежать краша.
        val locale = runCatching {
            resolveLocale(languageCode, baseContext)
        }.getOrElse {
            Locale.getDefault()
        }

        Locale.setDefault(locale)

        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        baseContext.createConfigurationContext(config)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalActivityContext provides baseContext
    ) {
        content()
    }
}

private fun resolveLocale(languageCode: String, context: Context): Locale {
    return when (val language = AppLanguage.fromCode(languageCode)) {
        AppLanguage.SYSTEM -> {
            // Системная локаль устройства на момент запуска/смены настройки.
            val systemLocales = context.resources.configuration.locales
            if (!systemLocales.isEmpty) systemLocales[0] else Locale.getDefault()
        }
        AppLanguage.RU -> Locale("ru")
        AppLanguage.EN -> Locale("en")
    }
}
