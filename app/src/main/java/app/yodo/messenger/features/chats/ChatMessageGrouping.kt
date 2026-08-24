package app.yodo.messenger.features.chats

import app.yodo.messenger.domain.model.Message

enum class MessageGroupPosition {
    SINGLE,
    FIRST,
    MIDDLE,
    LAST
}

fun messageGroupPosition(messages: List<Message>, index: Int): MessageGroupPosition {
    require(index in messages.indices)
    val sameBefore = index > 0 && messages[index - 1].senderId == messages[index].senderId
    val sameAfter = index < messages.lastIndex && messages[index + 1].senderId == messages[index].senderId
    return when {
        !sameBefore && !sameAfter -> MessageGroupPosition.SINGLE
        !sameBefore -> MessageGroupPosition.FIRST
        sameAfter -> MessageGroupPosition.MIDDLE
        else -> MessageGroupPosition.LAST
    }
}

fun messageItemSpacing(messages: List<Message>, index: Int): Int {
    if (index !in messages.indices || index == 0) return 8
    return if (messages[index - 1].senderId == messages[index].senderId) 2 else 8
}
