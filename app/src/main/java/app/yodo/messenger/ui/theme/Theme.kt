package app.yodo.messenger.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.yodo.messenger.data.local.InterfaceStyle
import app.yodo.messenger.data.local.ScreenTransitionStyle

/**
 * НОВОЕ (AM): единые скругления для всего приложения.
 *
 * Material3 берёт форму выпадающих меню (DropdownMenu — меню «3 точки»),
 * диалогов (AlertDialog — окна создания чата/группы/канала) и карточек именно
 * отсюда. Раньше использовались значения по умолчанию (у меню — всего 4.dp), из-за
 * чего меню и окна выглядели угловатыми. Одно изменение здесь скругляет все подобные
 * поверхности сразу, без правки каждого экрана.
 */
private val YodoShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalColorTheme = compositionLocalOf { BlueTheme }
val LocalInterfaceStyle = compositionLocalOf { InterfaceStyle.CLASSIC }
val LocalGlassIntensity = compositionLocalOf { 55 }
val LocalScreenTransitionDuration = compositionLocalOf { 140 }
val LocalScreenTransitionStyle = compositionLocalOf { ScreenTransitionStyle.SLIDE }
val LocalScreenTransitionAmplitude = compositionLocalOf { 35 }

@Composable
fun YodoMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = BlueTheme,
    dynamicColor: Boolean = false,
    fontScale: Float = 1f,
    interfaceStyle: InterfaceStyle = InterfaceStyle.CLASSIC,
    glassIntensity: Int = 55,
    screenTransitionDurationMs: Int = 140,
    screenTransitionStyle: ScreenTransitionStyle = ScreenTransitionStyle.SLIDE,
    screenTransitionAmplitude: Int = 35,
    content: @Composable () -> Unit
) {
    fun tintOver(tint: Color, base: Color, alpha: Float): Color =
        tint.copy(alpha = alpha).compositeOver(base)
    val primaryOnColor = if (colorTheme.primary.luminance() > 0.58f) Color(0xFF111318) else Color.White
    val secondaryOnColor = if (colorTheme.secondary.luminance() > 0.58f) Color(0xFF111318) else Color.White

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = colorTheme.primary,
            onPrimary = primaryOnColor,
            primaryContainer = tintOver(colorTheme.primary, colorTheme.surfaceDark, 0.24f),
            onPrimaryContainer = colorTheme.onSurfaceDark,
            secondary = colorTheme.secondary,
            onSecondary = secondaryOnColor,
            secondaryContainer = tintOver(colorTheme.secondary, colorTheme.surfaceDark, 0.20f),
            onSecondaryContainer = colorTheme.onSurfaceDark,
            tertiary = colorTheme.accent,
            onTertiary = if (colorTheme.accent.luminance() > 0.58f) Color(0xFF111318) else Color.White,
            tertiaryContainer = tintOver(colorTheme.accent, colorTheme.surfaceDark, 0.18f),
            onTertiaryContainer = colorTheme.onSurfaceDark,
            background = colorTheme.backgroundDark,
            surface = colorTheme.surfaceDark,
            surfaceVariant = tintOver(colorTheme.primary, colorTheme.surfaceDark, 0.075f),
            onBackground = colorTheme.onSurfaceDark,
            onSurface = colorTheme.onSurfaceDark,
            onSurfaceVariant = tintOver(colorTheme.primary, colorTheme.onSurfaceDark, 0.10f),
            outline = colorTheme.onSurfaceDark.copy(alpha = 0.38f),
            outlineVariant = colorTheme.onSurfaceDark.copy(alpha = 0.18f),
            inverseSurface = colorTheme.onSurfaceDark,
            inverseOnSurface = colorTheme.backgroundDark,
            inversePrimary = colorTheme.accent,
            error = colorTheme.error
        )
        else -> lightColorScheme(
            primary = colorTheme.primary,
            onPrimary = primaryOnColor,
            primaryContainer = tintOver(colorTheme.primary, colorTheme.surfaceLight, 0.13f),
            onPrimaryContainer = colorTheme.onSurfaceLight,
            secondary = colorTheme.secondary,
            onSecondary = secondaryOnColor,
            secondaryContainer = tintOver(colorTheme.secondary, colorTheme.surfaceLight, 0.11f),
            onSecondaryContainer = colorTheme.onSurfaceLight,
            tertiary = colorTheme.accent,
            onTertiary = if (colorTheme.accent.luminance() > 0.58f) Color(0xFF111318) else Color.White,
            tertiaryContainer = tintOver(colorTheme.accent, colorTheme.surfaceLight, 0.10f),
            onTertiaryContainer = colorTheme.onSurfaceLight,
            background = colorTheme.backgroundLight,
            surface = colorTheme.surfaceLight,
            surfaceVariant = tintOver(colorTheme.primary, colorTheme.surfaceLight, 0.065f),
            onBackground = colorTheme.onSurfaceLight,
            onSurface = colorTheme.onSurfaceLight,
            onSurfaceVariant = tintOver(colorTheme.primary, colorTheme.onSurfaceLight, 0.08f),
            outline = colorTheme.onSurfaceLight.copy(alpha = 0.36f),
            outlineVariant = colorTheme.onSurfaceLight.copy(alpha = 0.16f),
            inverseSurface = colorTheme.onSurfaceLight,
            inverseOnSurface = colorTheme.backgroundLight,
            inversePrimary = colorTheme.secondary,
            error = colorTheme.error
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val typography = remember(fontScale) { scaledTypography(fontScale) }

    CompositionLocalProvider(
        LocalColorTheme provides colorTheme,
        LocalInterfaceStyle provides interfaceStyle,
        LocalGlassIntensity provides glassIntensity.coerceIn(0, 100),
        LocalScreenTransitionDuration provides screenTransitionDurationMs.coerceIn(0, 400),
        LocalScreenTransitionStyle provides screenTransitionStyle,
        LocalScreenTransitionAmplitude provides screenTransitionAmplitude.coerceIn(0, 100)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            // НОВОЕ (AM): единые скруглённые углы у меню и диалогов.
            shapes = YodoShapes,
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
