package app.yodo.messenger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import app.yodo.messenger.ui.theme.LocalGlassIntensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * Полупрозрачная многослойная поверхность для экспериментального интерфейса.
 * Не использует blur(), потому что он размывает сам контент. Вместо этого сочетает
 * прозрачный градиент, световой кант и мягкую тень — текст и иконки остаются чёткими.
 *
 * Если выше по дереву есть [LocalGlassBackdrop] (Android 13+), под панелью дополнительно
 * рисуется настоящее стекло: размытый и преломлённый у краёв фон (см. LiquidGlassBackdrop.kt),
 * а градиентные слои становятся тоньше, чтобы не перекрывать эффект.
 */
@Composable
fun Modifier.liquidGlass(
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color,
    dark: Boolean,
    elevation: Int = 8
): Modifier {
    if (!enabled) return this
    val backdrop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LocalGlassBackdrop.current else null
    val refracting = backdrop != null
    val targetIntensity = LocalGlassIntensity.current.coerceIn(0, 100) / 100f
    val intensity by animateFloatAsState(
        targetValue = targetIntensity,
        animationSpec = tween(durationMillis = 260),
        label = "liquidGlassIntensity"
    )
    // 0%: почти бесцветное настоящее стекло. 100%: плотное матовое стекло,
    // визуально близкое к сильному backdrop blur, но без размытия текста и иконок.
    // Альфы подобраны мягче: спокойная поверхность без ярких бликов и резких кантов.
    // При настоящем стекле тонировку делает шейдер — градиентное тело делаем втрое прозрачнее.
    val bodyScale = if (refracting) 0.35f else 1f
    val bodyAlpha = ((if (dark) 0.06f else 0.045f) + intensity * (if (dark) 0.44f else 0.40f)) * bodyScale
    val lowerAlpha = ((if (dark) 0.04f else 0.028f) + intensity * (if (dark) 0.32f else 0.28f)) * bodyScale
    // Белый «свет» на плитках почти убран: верхняя засветка удалена, кант и блик по кромке
    // приглушены, а единственный блик — крошечное еле заметное пятно строго посередине.
    val edgeAlpha = (if (dark) 0.04f else 0.06f) + intensity * (if (dark) 0.04f else 0.05f)
    val bodyTint = tint.copy(alpha = bodyAlpha.coerceIn(0f, 0.92f))
    val lowerTint = tint.copy(alpha = lowerAlpha.coerceIn(0f, 0.86f))
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = edgeAlpha.coerceIn(0f, 0.96f)),
            Color.White.copy(alpha = (0.03f + intensity * 0.12f).coerceIn(0f, 0.20f)),
            tint.copy(alpha = (0.06f + intensity * 0.28f).coerceIn(0f, 0.40f))
        )
    )
    val refraction = if (backdrop != null) {
        Modifier.glassRefraction(backdrop, shape, tint, intensity, dark)
    } else {
        Modifier
    }
    return this
        .shadow(
            elevation = elevation.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.02f + intensity * (if (dark) 0.08f else 0.045f)),
            spotColor = Color.Black.copy(alpha = 0.025f + intensity * (if (dark) 0.10f else 0.05f))
        )
        .clip(shape)
        .then(refraction)
        .background(
            Brush.verticalGradient(
                colors = listOf(bodyTint, bodyTint, lowerTint, bodyTint)
            )
        )
        // Крошечный едва заметный блик посередине панели.
        .drawBehind {
            val spotAlpha = (0.010f + intensity * 0.022f).coerceAtMost(0.035f)
            val spotRadius = (minOf(size.width, size.height) * 0.42f).coerceAtLeast(1f)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = spotAlpha), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = spotRadius
                )
            )
        }
        .border(BorderStroke(0.6.dp, borderBrush), shape)
}

@Composable
fun glassTint(dark: Boolean): Color = MaterialTheme.colorScheme.surface

/**
 * Размытая «подложка» без формы и преломления — для полосы под панелью ввода (зазор между
 * панелью и краем экрана). Сверху плавно растворяется, чтобы не было резкой границы.
 * Ставить на отдельный Box (matchParentSize) ПОЗАДИ панели, а не оборачивать ею саму панель.
 */
@Composable
fun Modifier.glassBottomBlur(tint: Color, dark: Boolean): Modifier {
    val backdrop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LocalGlassBackdrop.current else null
    val intensity = LocalGlassIntensity.current.coerceIn(0, 100) / 100f
    val body = if (backdrop != null) {
        Modifier.glassRefraction(backdrop, RectangleShape, tint, intensity, dark, flat = true)
    } else {
        Modifier.background(tint.copy(alpha = (0.35f + 0.5f * intensity).coerceIn(0f, 0.9f)))
    }
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Black,
                    1f to Color.Black
                ),
                blendMode = BlendMode.DstIn
            )
        }
        .then(body)
}

/** Мягкий фон без резких полос: базовый цвет темы и два больших рассеянных пятна. */
@Composable
fun Modifier.softMessengerBackdrop(
    enabled: Boolean,
    primary: Color,
    accent: Color,
    dark: Boolean
): Modifier {
    val base = MaterialTheme.colorScheme.background
    if (!enabled) return this.background(base)
    return this.drawWithCache {
        val radius = size.maxDimension * 1.05f
        val firstGlow = Brush.radialGradient(
            colors = listOf(
                primary.copy(alpha = if (dark) 0.115f else 0.075f),
                primary.copy(alpha = if (dark) 0.035f else 0.018f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.04f, size.height * 0.02f),
            radius = radius
        )
        val secondGlow = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = if (dark) 0.075f else 0.045f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.96f, size.height * 0.88f),
            radius = radius * 0.92f
        )
        onDrawBehind {
            drawRect(base)
            drawRect(firstGlow)
            drawRect(secondGlow)
        }
    }
}
