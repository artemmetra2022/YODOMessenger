package app.yodo.messenger.features.reminders

import java.security.MessageDigest

internal fun reminderKey(uid: String, chatId: String, messageId: String): String {
    val bytes = "$uid\u0000$chatId\u0000$messageId".toByteArray(Charsets.UTF_8)
    return "message-reminder-" + MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
