package app.yodo.messenger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import app.yodo.messenger.ui.theme.LocalGlassIntensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
    val bodyAlpha = (if (dark) 0.06f else 0.035f) + intensity * (if (dark) 0.82f else 0.78f)
    val lowerAlpha = (if (dark) 0.035f else 0.02f) + intensity * (if (dark) 0.68f else 0.62f)
    val highlightAlpha = (if (dark) 0.10f else 0.24f) + intensity * (if (dark) 0.22f else 0.68f)
    val edgeAlpha = (if (dark) 0.18f else 0.40f) + intensity * (if (dark) 0.30f else 0.52f)
    val topHighlight = Color.White.copy(alpha = highlightAlpha.coerceIn(0f, 0.96f))
    val bodyTint = tint.copy(alpha = bodyAlpha.coerceIn(0f, 0.92f))
    val lowerTint = tint.copy(alpha = lowerAlpha.coerceIn(0f, 0.86f))
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = edgeAlpha.coerceIn(0f, 0.96f)),
            Color.White.copy(alpha = (0.04f + intensity * 0.20f).coerceIn(0f, 0.28f)),
            tint.copy(alpha = (0.10f + intensity * 0.40f).coerceIn(0f, 0.52f))
        )
    )
    return this
        .shadow(
            elevation = elevation.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.04f + intensity * (if (dark) 0.24f else 0.11f)),
            spotColor = Color.Black.copy(alpha = 0.05f + intensity * (if (dark) 0.28f else 0.13f))
        )
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(topHighlight, bodyTint, lowerTint)
            )
        )
        // Верхний локальный блик имитирует отражение источника света на стекле.
        .background(
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.06f + intensity * 0.18f).coerceAtMost(0.25f)),
                    Color.Transparent
                ),
                center = Offset(18f, 8f),
                radius = 520f
            )
        )
        .border(BorderStroke(0.8.dp, borderBrush), shape)
}

@Composable
fun glassTint(dark: Boolean): Color =
    if (dark) Color(0xFF17212B) else Color(0xFFEAF4FC)
