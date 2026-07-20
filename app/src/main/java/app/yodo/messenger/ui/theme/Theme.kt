package app.yodo.messenger.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val YodoDarkColorScheme = darkColorScheme(
    primary = YodoPrimary,
    secondary = YodoSecondary,
    tertiary = YodoAccent,
    background = YodoBackground,
    surface = YodoSurface,
    onBackground = YodoOnSurface,
    onSurface = YodoOnSurface,
    error = YodoError
)

private val YodoLightColorScheme = lightColorScheme(
    primary = YodoPrimary,
    secondary = YodoSecondary,
    tertiary = YodoAccent,
    background = YodoBackgroundLight,
    surface = YodoSurfaceLight,
    onBackground = YodoOnSurfaceLight,
    onSurface = YodoOnSurfaceLight,
    error = YodoError
)

@Composable
fun YodoMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Динамические цвета Material You выключены по умолчанию,
    // чтобы сохранить фирменную палитру бренда на всех устройствах
    dynamicColor: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> YodoDarkColorScheme
        else -> YodoLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val typography = remember(fontScale) { scaledTypography(fontScale) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

/** Настройка "размер шрифта" из Settings — масштабирует всю типографику пропорционально. */
private fun scaledTypography(scale: Float): Typography {
    if (scale == 1f) return YodoTypography
    return YodoTypography.copy(
        displayLarge = YodoTypography.displayLarge.copy(fontSize = YodoTypography.displayLarge.fontSize * scale),
        headlineLarge = YodoTypography.headlineLarge.copy(fontSize = YodoTypography.headlineLarge.fontSize * scale),
        titleLarge = YodoTypography.titleLarge.copy(fontSize = YodoTypography.titleLarge.fontSize * scale),
        bodyLarge = YodoTypography.bodyLarge.copy(fontSize = YodoTypography.bodyLarge.fontSize * scale),
        bodyMedium = YodoTypography.bodyMedium.copy(fontSize = YodoTypography.bodyMedium.fontSize * scale),
        labelLarge = YodoTypography.labelLarge.copy(fontSize = YodoTypography.labelLarge.fontSize * scale),
        labelMedium = YodoTypography.labelMedium.copy(fontSize = YodoTypography.labelMedium.fontSize * scale)
    )
}
