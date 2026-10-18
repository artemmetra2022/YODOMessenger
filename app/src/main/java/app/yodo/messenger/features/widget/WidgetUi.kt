package app.yodo.messenger.features.widget

import android.content.Intent
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Размеры, под которые Glance подбирает раскладку (в ячейках рабочего стола):
 * 1×1, 2×1, 2×2, 4×2, 4×4. Значения чуть ниже «идеальных» (40/110/250 dp на 1/2/4 ячейки),
 * потому что разные лаунчеры отдают размер виджета с небольшим запасом вниз.
 */
internal val WIDGET_SIZES = setOf(
    DpSize(50.dp, 50.dp),
    DpSize(100.dp, 50.dp),
    DpSize(100.dp, 100.dp),
    DpSize(200.dp, 100.dp),
    DpSize(200.dp, 200.dp)
)

internal enum class WidgetLayout { TINY, COMPACT, SQUARE, WIDE, LARGE }

internal fun layoutFor(size: DpSize): WidgetLayout = when {
    size.width >= 200.dp && size.height >= 200.dp -> WidgetLayout.LARGE
    size.width >= 200.dp && size.height >= 100.dp -> WidgetLayout.WIDE
    size.height >= 100.dp -> WidgetLayout.SQUARE
    size.width >= 100.dp -> WidgetLayout.COMPACT
    else -> WidgetLayout.TINY
}

/** Фирменная синяя палитра YODO для светлой и тёмной системной темы. */
internal val YodoWidgetColors = ColorProviders(
    light = lightColorScheme(
        primary = Color(0xFF3B82F6),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE9FE),
        onPrimaryContainer = Color(0xFF0B2A5B),
        background = Color(0xFFF7F9FC),
        onBackground = Color(0xFF111827),
        surface = Color.White,
        onSurface = Color(0xFF111827),
        onSurfaceVariant = Color(0xFF5B6472)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF6EA8FF),
        onPrimary = Color(0xFF062451),
        primaryContainer = Color(0xFF1D3B6E),
        onPrimaryContainer = Color(0xFFDCE9FE),
        background = Color(0xFF121826),
        onBackground = Color(0xFFE6EAF2),
        surface = Color(0xFF1A2233),
        onSurface = Color(0xFFE6EAF2),
        onSurfaceVariant = Color(0xFFA3ACBA)
    )
)

@Composable
internal fun WidgetRoot(onClick: Intent, padding: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.background)
            .cornerRadius(22.dp)
            .padding(padding)
            .clickable(actionStartActivity(onClick)),
        contentAlignment = Alignment.TopStart
    ) { content() }
}

@Composable
internal fun WText(
    text: String,
    size: Int,
    bold: Boolean = false,
    secondary: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = if (secondary) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    )
}

@Composable
internal fun Badge(text: String, small: Boolean = false) {
    Box(
        modifier = GlanceModifier
            .background(GlanceTheme.colors.primary)
            .cornerRadius(12.dp)
            .padding(horizontal = if (small) 6.dp else 9.dp, vertical = if (small) 1.dp else 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onPrimary,
                fontSize = (if (small) 11 else 13).sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

internal fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

internal fun formatHour(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
