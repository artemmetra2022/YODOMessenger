package app.yodo.messenger.domain.model

/** Badge state is independent from public delivery/read receipts and their privacy setting. */
data class UnreadTail(val count: Int = 0, val lastMessageId: String? = null,
    val timestamp: Long = 0L, val senderId: String? = null)
data class UnreadBadgeSnapshot(val chat: UnreadTail, val topic: UnreadTail? = null)

fun normalizedUnreadCount(value: Any?): Int = (value as? Number)?.toLong()
    ?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0

fun isUnreadTailVisible(tail: UnreadTail, visible: List<Message>): Boolean {
    if (!tail.lastMessageId.isNullOrBlank()) return visible.any { it.id == tail.lastMessageId }
    // Compatibility with old chats/topics whose preview has no lastMessageId.
    return tail.timestamp > 0 && visible.any {
        it.timestamp == tail.timestamp && (tail.senderId == null || it.senderId == tail.senderId)
    }
}

fun remainingUnreadAfterTopicRead(chatCount: Int, topicCount: Int): Int =
    (chatCount.toLong() - topicCount.coerceAtLeast(0)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
