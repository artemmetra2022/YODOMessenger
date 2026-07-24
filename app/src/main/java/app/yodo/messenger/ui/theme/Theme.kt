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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalColorTheme = compositionLocalOf { BlueTheme }

@Composable
fun YodoMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = BlueTheme,
    dynamicColor: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = colorTheme.primary,
            secondary = colorTheme.secondary,
            tertiary = colorTheme.accent,
            background = colorTheme.backgroundDark,
            surface = colorTheme.surfaceDark,
            onBackground = colorTheme.onSurfaceDark,
            onSurface = colorTheme.onSurfaceDark,
            error = colorTheme.error
        )
        else -> lightColorScheme(
            primary = colorTheme.primary,
            secondary = colorTheme.secondary,
            tertiary = colorTheme.accent,
            background = colorTheme.backgroundLight,
            surface = colorTheme.surfaceLight,
            onBackground = colorTheme.onSurfaceLight,
            onSurface = colorTheme.onSurfaceLight,
            error = colorTheme.error
        )
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

    CompositionLocalProvider(LocalColorTheme provides colorTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

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
