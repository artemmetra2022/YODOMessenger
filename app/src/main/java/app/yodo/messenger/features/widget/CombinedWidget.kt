package app.yodo.messenger.features.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
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
import kotlinx.coroutines.flow.first

/** Комбинированный виджет: погода и непрочитанные. Размеры 1×1, 2×1, 2×2, 4×2, 4×4. */
class CombinedWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = WidgetEntryPoint.get(context)
        val uid = ep.auth().currentUser?.uid
        val settings = ep.settings()
        val showTitles = !settings.isPinSet.first() && !settings.hideChatListPreviews.first()
        val unread = if (uid == null) null else WidgetStore.readUnread(context)?.takeIf { it.uid == uid }
        val weather = WidgetStore.readWeather(context)
        provideContent {
            GlanceTheme(colors = YodoWidgetColors) {
                CombinedContent(context, uid, unread, weather, showTitles)
            }
        }
    }
}

@Composable
private fun CombinedContent(
    context: Context, uid: String?, unread: UnreadSnapshot?, weather: WeatherSnapshot?, showTitles: Boolean
) {
    val layout = layoutFor(LocalSize.current)
    val openChats = WidgetIntents.open(context, WidgetIntents.TARGET_CHATS, uid)
    val openWeather = actionStartActivity(WidgetIntents.open(context, WidgetIntents.TARGET_WEATHER, uid))
    val res = context.resources
    val total = unread?.total ?: 0
    val emoji = weather?.let { weatherEmoji(it.symbol) } ?: "🌡️"
    val temp = weather?.let { formatTemp(it.temp) } ?: "—"
    val city = weather?.city.orEmpty()
    val unreadLabel = context.getString(R.string.widget_unread_label)

    WidgetRoot(openChats, padding = if (layout == WidgetLayout.TINY) 6.dp else 12.dp) {
        when (layout) {
            WidgetLayout.TINY -> Column(
                GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WText(emoji, 16)
                WText(temp, 16, bold = true)
                if (total > 0) Badge(badgeText(total), small = true)
            }
            WidgetLayout.COMPACT -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Row(GlanceModifier.defaultWeight().clickable(openWeather), verticalAlignment = Alignment.CenterVertically) {
                    WText(emoji, 26)
                    Spacer(GlanceModifier.width(6.dp))
                    Column {
                        WText(temp, 20, bold = true)
                        if (city.isNotEmpty()) WText(city, 11, secondary = true)
                    }
                }
                if (uid != null && total > 0) Badge(badgeText(total))
            }
            WidgetLayout.SQUARE -> Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth().clickable(openWeather), verticalAlignment = Alignment.CenterVertically) {
                    WText(emoji, 26)
                    Spacer(GlanceModifier.width(6.dp))
                    WText(temp, 28, bold = true)
                }
                if (city.isNotEmpty()) WText(city, 11, secondary = true)
                Spacer(GlanceModifier.defaultWeight())
                WText(unreadLabel, 11, secondary = true)
                WText(
                    when {
                        uid == null -> context.getString(R.string.widget_sign_in)
                        total == 0 -> context.getString(R.string.widget_all_read)
                        else -> "${badgeText(total)} " + res.getQuantityString(R.plurals.widget_unread_messages, total)
                    }, 13, bold = true, maxLines = 2
                )
            }
            WidgetLayout.WIDE -> Row(GlanceModifier.fillMaxSize()) {
                Column(GlanceModifier.width(110.dp).fillMaxHeight().clickable(openWeather)) {
                    if (city.isNotEmpty()) WText(city, 12, secondary = true)
                    Spacer(GlanceModifier.defaultWeight())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WText(emoji, 26)
                        Spacer(GlanceModifier.width(6.dp))
                        WText(temp, 30, bold = true)
                    }
                    WText(weather?.description.orEmpty(), 12, maxLines = 2)
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    UnreadBlock(context, uid, unread, showTitles, limit = 3)
                }
            }
            WidgetLayout.LARGE -> Column(GlanceModifier.fillMaxSize()) {
                Column(GlanceModifier.fillMaxWidth().clickable(openWeather)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WText(emoji, 36)
                        Spacer(GlanceModifier.width(8.dp))
                        WText(temp, 38, bold = true)
                        Spacer(GlanceModifier.width(10.dp))
                        Column {
                            WText(city, 14, bold = true)
                            WText(weather?.description.orEmpty(), 12, secondary = true, maxLines = 2)
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        weather?.hourly?.take(5)?.forEach { h ->
                            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                WText(formatHour(h.time), 10, secondary = true)
                                WText(weatherEmoji(h.symbol), 16)
                                WText(formatTemp(h.temp), 12, bold = true)
                            }
                        }
                    }
                }
                Spacer(GlanceModifier.height(10.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BrandIcon(16)
                    Spacer(GlanceModifier.width(6.dp))
                    WText(unreadLabel, 14, bold = true, modifier = GlanceModifier.defaultWeight())
                    if (uid != null && total > 0) Badge(badgeText(total))
                }
                Spacer(GlanceModifier.height(4.dp))
                Column(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    UnreadBlock(context, uid, unread, showTitles, limit = 4)
                }
            }
        }
    }
}

@Composable
private fun UnreadBlock(context: Context, uid: String?, unread: UnreadSnapshot?, showTitles: Boolean, limit: Int) {
    when {
        uid == null -> WText(context.getString(R.string.widget_sign_in), 13, bold = true, maxLines = 2)
        unread == null -> WText(context.getString(R.string.widget_no_data), 12, maxLines = 3)
        else -> ChatList(context, uid, unread, showTitles, limit)
    }
}
