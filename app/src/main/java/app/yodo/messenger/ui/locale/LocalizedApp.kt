package app.yodo.messenger.ui.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.yodo.messenger.data.local.AppLanguage
import java.util.Locale

/**
 * Обёртка над Activity-контекстом: getResources() отдаёт ресурсы с нужной локалью,
 * а всё остальное (включая цепочку baseContext для Hilt findActivity()) делегируется
 * к оригинальному Activity. Это критично: createConfigurationContext() возвращает
 * ContextImpl без Activity в цепочке, из-за чего hiltViewModel() падает.
 */
private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

@Composable
fun LocalizedApp(
    languageCode: String,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current

    val localizedContext = remember(languageCode, baseContext, baseConfiguration) {
        val locale = runCatching {
            resolveLocale(languageCode, baseContext)
        }.getOrElse { Locale.getDefault() }

        Locale.setDefault(locale)

        // Создаём конфигурацию с нужной локалью и получаем под неё ресурсы,
        // но сам контекст оставляем обёрткой над Activity.
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)

        val localizedResources = baseContext.createConfigurationContext(config).resources
        LocalizedContextWrapper(baseContext, localizedResources)
    }

    CompositionLocalProvider(LocalContext provides localizedContext) {
        content()
    }
}

private fun resolveLocale(languageCode: String, context: Context): Locale {
    return when (AppLanguage.fromCode(languageCode)) {
        AppLanguage.SYSTEM -> {
            val systemLocales = context.resources.configuration.locales
            if (!systemLocales.isEmpty) systemLocales[0] else Locale.getDefault()
        }
        AppLanguage.RU -> Locale("ru")
        AppLanguage.EN -> Locale("en")
    }
}