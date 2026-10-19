package app.yodo.messenger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * Полупрозрачная многослойная поверхность для экспериментального интерфейса.
 * Не использует blur(), потому что он размывает сам контент. Вместо этого сочетает
 * прозрачный градиент, световой кант и мягкую тень — текст и иконки остаются чёткими.
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
    val targetIntensity = LocalGlassIntensity.current.coerceIn(0, 100) / 100f
    val intensity by animateFloatAsState(
        targetValue = targetIntensity,
        animationSpec = tween(durationMillis = 260),
        label = "liquidGlassIntensity"
    )
    // 0%: почти бесцветное настоящее стекло. 100%: плотное матовое стекло,
    // визуально близкое к сильному backdrop blur, но без размытия текста и иконок.
    // Альфы подобраны мягче: спокойная поверхность без ярких бликов и резких кантов.
    val bodyAlpha = (if (dark) 0.06f else 0.045f) + intensity * (if (dark) 0.44f else 0.40f)
    val lowerAlpha = (if (dark) 0.04f else 0.028f) + intensity * (if (dark) 0.32f else 0.28f)
    val highlightAlpha = (if (dark) 0.04f else 0.07f) + intensity * (if (dark) 0.06f else 0.10f)
    val edgeAlpha = (if (dark) 0.09f else 0.15f) + intensity * (if (dark) 0.12f else 0.14f)
    val topHighlight = Color.White.copy(alpha = highlightAlpha.coerceIn(0f, 0.96f))
    val bodyTint = tint.copy(alpha = bodyAlpha.coerceIn(0f, 0.92f))
    val lowerTint = tint.copy(alpha = lowerAlpha.coerceIn(0f, 0.86f))
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = edgeAlpha.coerceIn(0f, 0.96f)),
            Color.White.copy(alpha = (0.03f + intensity * 0.12f).coerceIn(0f, 0.20f)),
            tint.copy(alpha = (0.06f + intensity * 0.28f).coerceIn(0f, 0.40f))
        )
    )
    return this
        .shadow(
            elevation = elevation.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.02f + intensity * (if (dark) 0.08f else 0.045f)),
            spotColor = Color.Black.copy(alpha = 0.025f + intensity * (if (dark) 0.10f else 0.05f))
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(topHighlight, bodyTint, lowerTint, bodyTint)
            )
        )
        // Верхний локальный блик имитирует отражение источника света на стекле —
        // ослаблен вдвое против прежнего, чтобы не «блестел» на глазах.
        .background(
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.03f + intensity * 0.09f).coerceAtMost(0.14f)),
                    Color.Transparent
                ),
                center = Offset(18f, 8f),
                radius = 520f
            )
        )
        .border(BorderStroke(0.6.dp, borderBrush), shape)
}

@Composable
fun glassTint(dark: Boolean): Color = MaterialTheme.colorScheme.surface

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
