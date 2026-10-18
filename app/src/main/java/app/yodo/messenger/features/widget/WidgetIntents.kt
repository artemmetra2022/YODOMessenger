package app.yodo.messenger.features.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.yodo.messenger.MainActivity

/** Намерения для нажатий на виджет. MainActivity разбирает их в extractWidgetTarget(). */
object WidgetIntents {
    const val ACTION_OPEN = "app.yodo.messenger.action.OPEN_FROM_WIDGET"
    const val EXTRA_TARGET = "widgetTarget"
    const val EXTRA_UID = "widgetUid"
    const val EXTRA_CHAT_ID = "widgetChatId"

    const val TARGET_CHATS = "chats"
    const val TARGET_CHAT = "chat"
    const val TARGET_WEATHER = "weather"

    fun open(context: Context, target: String, uid: String?, chatId: String? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            // Разные data — чтобы PendingIntent'ы разных строк/виджетов не склеивались.
            data = Uri.Builder().scheme("yodo-widget").authority(target)
                .appendPath(chatId ?: "-").build()
            putExtra(EXTRA_TARGET, target)
            putExtra(EXTRA_UID, uid.orEmpty())
            if (chatId != null) putExtra(EXTRA_CHAT_ID, chatId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
