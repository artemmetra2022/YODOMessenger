package app.yodo.messenger.notifications

internal data class ChatNotificationActions(val markRead: Boolean, val reply: Boolean, val mute: Boolean)
internal fun chatNotificationActions(type: String, member: Boolean, publisher: Boolean, hiddenPreview: Boolean): ChatNotificationActions {
    val known=type in setOf("PRIVATE","GROUP","CHANNEL","SUPPORT")
    val allowed=member&&known
    return ChatNotificationActions(allowed,allowed&&!hiddenPreview&&(type!="CHANNEL"||publisher),allowed)
}
internal fun notificationAlerts(sound: Boolean, vibration: Boolean, locallyMuted: Boolean): Pair<Boolean,Boolean> =
    (sound&&!locallyMuted) to (vibration&&!locallyMuted)
