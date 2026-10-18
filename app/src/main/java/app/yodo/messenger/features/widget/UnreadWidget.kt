package app.yodo.messenger.features.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import app.yodo.messenger.R
import kotlinx.coroutines.flow.first

/** Виджет «Непрочитанные»: 1×1 — число, 2×1 — число и чаты, 2×2 — крупно, 4×2 и 4×4 — со списком чатов. */
class UnreadWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = WidgetEntryPoint.get(context)
        val uid = ep.auth().currentUser?.uid
        val settings = ep.settings()
        // Рабочий стол виден без разблокировки: при включённом PIN или скрытых превью
        // показываем только числа, без названий чатов.
        val showTitles = !settings.isPinSet.first() && !settings.hideChatListPreviews.first()
        val snapshot = if (uid == null) null else WidgetStore.readUnread(context)?.takeIf { it.uid == uid }
        provideContent {
            GlanceTheme(colors = YodoWidgetColors) {
                UnreadContent(context, uid, snapshot, showTitles)
            }
        }
    }
}

@Composable
private fun UnreadContent(context: Context, uid: String?, snap: UnreadSnapshot?, showTitles: Boolean) {
    val layout = layoutFor(LocalSize.current)
    val openApp = WidgetIntents.open(context, WidgetIntents.TARGET_CHATS, uid)
    WidgetRoot(openApp, padding = if (layout == WidgetLayout.TINY) 6.dp else 12.dp) {
        val res = context.resources
        val total = snap?.total ?: 0
        val label = context.getString(R.string.widget_unread_label)
        when {
            uid == null -> Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (layout != WidgetLayout.TINY) WText(label, 12, secondary = true)
                WText(context.getString(R.string.widget_sign_in), if (layout == WidgetLayout.TINY) 10 else 14, bold = true, maxLines = 2)
            }
            snap == null -> Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (layout != WidgetLayout.TINY) WText(label, 12, secondary = true)
                WText(context.getString(R.string.widget_no_data), if (layout == WidgetLayout.TINY) 10 else 13, maxLines = 3)
            }
            layout == WidgetLayout.TINY -> Column(
                GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandIcon(16)
                Spacer(GlanceModifier.height(2.dp))
                WText(if (total == 0) "✓" else badgeText(total), 20, bold = true)
            }
            layout == WidgetLayout.COMPACT -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    WText(label, 12, secondary = true)
                    WText(
                        if (total == 0) context.getString(R.string.widget_all_read)
                        else res.getQuantityString(R.plurals.widget_in_chats, snap.chatsWithUnread, snap.chatsWithUnread),
                        14, bold = true
                    )
                }
                if (total > 0) Badge(badgeText(total))
            }
            layout == WidgetLayout.SQUARE -> Column(GlanceModifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandIcon(16)
                    Spacer(GlanceModifier.width(6.dp))
                    WText(label, 12, secondary = true)
                }
                Spacer(GlanceModifier.defaultWeight())
                if (total == 0) {
                    WText("✓", 36, bold = true)
                    WText(context.getString(R.string.widget_all_read), 13, secondary = true)
                } else {
                    WText(badgeText(total), 38, bold = true)
                    WText(res.getQuantityString(R.plurals.widget_unread_messages, total), 12, secondary = true, maxLines = 2)
                    WText(res.getQuantityString(R.plurals.widget_in_chats, snap.chatsWithUnread, snap.chatsWithUnread), 12, secondary = true)
                }
            }
            layout == WidgetLayout.WIDE -> Row(GlanceModifier.fillMaxSize()) {
                Column(GlanceModifier.width(92.dp).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandIcon(16)
                        Spacer(GlanceModifier.width(6.dp))
                        WText(label, 12, secondary = true)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    WText(if (total == 0) "✓" else badgeText(total), 36, bold = true)
                    WText(
                        if (total == 0) context.getString(R.string.widget_all_read)
                        else res.getQuantityString(R.plurals.widget_unread_messages, total),
                        11, secondary = true, maxLines = 2
                    )
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    ChatList(context, uid, snap, showTitles, limit = 3)
                }
            }
            else -> Column(GlanceModifier.fillMaxSize()) { // LARGE
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BrandIcon(18)
                    Spacer(GlanceModifier.width(8.dp))
                    WText(label, 15, bold = true, modifier = GlanceModifier.defaultWeight())
                    if (total > 0) Badge(badgeText(total))
                }
                Spacer(GlanceModifier.height(8.dp))
                Column(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    ChatList(context, uid, snap, showTitles, limit = 6)
                }
                WText(context.getString(R.string.widget_updated, formatClock(snap.updatedAt)), 10, secondary = true)
            }
        }
    }
}

@Composable
internal fun ChatList(context: Context, uid: String?, snap: UnreadSnapshot, showTitles: Boolean, limit: Int) {
    when {
        snap.total == 0 -> WText(context.getString(R.string.widget_all_read), 14, bold = true)
        !showTitles -> WText(context.getString(R.string.widget_chats_hidden), 12, secondary = true, maxLines = 3)
        else -> snap.items.take(limit).forEach { item ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(
                    actionStartActivity(WidgetIntents.open(context, WidgetIntents.TARGET_CHAT, uid, item.chatId))
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WText(item.title, 14, modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                Badge(badgeText(item.unread), small = true)
            }
        }
    }
}

@Composable
internal fun BrandIcon(sizeDp: Int) {
    Image(
        provider = ImageProvider(R.drawable.ic_notification),
        contentDescription = null,
        modifier = GlanceModifier.size(sizeDp.dp),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
    )
}
