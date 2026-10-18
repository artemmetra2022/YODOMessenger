package app.yodo.messenger.features.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import app.yodo.messenger.R
import app.yodo.messenger.features.weather.weatherEmoji

/** Виджет «Погода»: 1×1 — температура, 2×1 — с городом, 2×2 — крупно, 4×2 — с деталями, 4×4 — с почасовым прогнозом. */
class WeatherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = WidgetEntryPoint.get(context)
        val uid = ep.auth().currentUser?.uid
        val snapshot = WidgetStore.readWeather(context)
        provideContent {
            GlanceTheme(colors = YodoWidgetColors) {
                WeatherContent(context, uid, snapshot)
            }
        }
    }
}

@Composable
private fun WeatherContent(context: Context, uid: String?, snap: WeatherSnapshot?) {
    val layout = layoutFor(LocalSize.current)
    val open = WidgetIntents.open(context, WidgetIntents.TARGET_WEATHER, uid)
    WidgetRoot(open, padding = if (layout == WidgetLayout.TINY) 6.dp else 12.dp) {
        if (snap == null) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                WText("🌡️", if (layout == WidgetLayout.TINY) 22 else 30)
                if (layout != WidgetLayout.TINY) {
                    WText(context.getString(R.string.widget_weather_label), 12, secondary = true)
                    WText(context.getString(R.string.widget_no_data), 12, maxLines = 3)
                }
            }
            return@WidgetRoot
        }
        val emoji = weatherEmoji(snap.symbol)
        when (layout) {
            WidgetLayout.TINY -> Column(
                GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WText(emoji, 20)
                WText(formatTemp(snap.temp), 18, bold = true)
            }
            WidgetLayout.COMPACT -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                WText(emoji, 30)
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    WText(formatTemp(snap.temp), 22, bold = true)
                    WText(snap.city, 12, secondary = true)
                }
            }
            WidgetLayout.SQUARE -> Column(GlanceModifier.fillMaxSize()) {
                WText(snap.city, 12, secondary = true)
                Spacer(GlanceModifier.defaultWeight())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WText(emoji, 30)
                    Spacer(GlanceModifier.width(6.dp))
                    WText(formatTemp(snap.temp), 34, bold = true)
                }
                WText(snap.description, 13, maxLines = 2)
                WText(context.getString(R.string.widget_feels_like, formatTemp(snap.feelsLike)), 11, secondary = true)
            }
            WidgetLayout.WIDE -> Row(GlanceModifier.fillMaxSize()) {
                Column(GlanceModifier.width(112.dp).fillMaxHeight()) {
                    WText(snap.city, 12, secondary = true)
                    Spacer(GlanceModifier.defaultWeight())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WText(emoji, 28)
                        Spacer(GlanceModifier.width(6.dp))
                        WText(formatTemp(snap.temp), 32, bold = true)
                    }
                    WText(snap.description, 12, maxLines = 2)
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    WText("↑ ${formatTemp(snap.tempMax)}   ↓ ${formatTemp(snap.tempMin)}", 14, bold = true)
                    Spacer(GlanceModifier.height(4.dp))
                    WText(context.getString(R.string.widget_feels_like, formatTemp(snap.feelsLike)), 12)
                    WText(context.getString(R.string.widget_wind, snap.windSpeed.toString()), 12)
                    WText(context.getString(R.string.widget_humidity, snap.humidity), 12)
                    snap.precipProbability?.let { WText(context.getString(R.string.widget_precip, it), 12) }
                }
            }
            WidgetLayout.LARGE -> Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WText(snap.city, 15, bold = true, modifier = GlanceModifier.defaultWeight())
                    WText(formatClock(snap.updatedAt), 10, secondary = true)
                }
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WText(emoji, 44)
                    Spacer(GlanceModifier.width(8.dp))
                    WText(formatTemp(snap.temp), 46, bold = true)
                    Spacer(GlanceModifier.width(12.dp))
                    Column {
                        WText(snap.description, 14, bold = true, maxLines = 2)
                        WText("↑ ${formatTemp(snap.tempMax)}  ↓ ${formatTemp(snap.tempMin)}", 12, secondary = true)
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Column(GlanceModifier.defaultWeight()) {
                        WText(context.getString(R.string.widget_feels_like, formatTemp(snap.feelsLike)), 11, maxLines = 2)
                    }
                    Column(GlanceModifier.defaultWeight()) {
                        WText(context.getString(R.string.widget_wind, snap.windSpeed.toString()), 11, maxLines = 2)
                    }
                    Column(GlanceModifier.defaultWeight()) {
                        WText(context.getString(R.string.widget_humidity, snap.humidity), 11, maxLines = 2)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth()) {
                    snap.hourly.forEach { hour ->
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            WText(formatHour(hour.time), 10, secondary = true)
                            WText(weatherEmoji(hour.symbol), 18)
                            WText(formatTemp(hour.temp), 13, bold = true)
                        }
                    }
                }
            }
        }
    }
}
