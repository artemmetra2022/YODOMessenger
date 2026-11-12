package app.yodo.messenger.data.outbox

import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus

/** Stable IDs reconcile a durable request and its Firestore snapshot into exactly one bubble. */
fun mergeOutgoingMessages(remote: List<Message>, pending: List<Message>): List<Message> {
    val acknowledgedIds = remote.filter { it.status != MessageStatus.SENDING }.map { it.id }.toSet()
    val local = pending.filter { it.id !in acknowledgedIds }
    val localIds = local.map { it.id }.toSet()
    return (remote.filterNot { it.id in localIds } + local)
        .distinctBy { it.id }.sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.id })
}

/** LazyColumn includes date rows, so message count alone is never its final item index. */
fun lastMessageItemIndex(messages: List<Message>, dateLabel: (Long) -> String): Int {
    var index = messages.size - 1
    var previousDate: String? = null
    messages.forEach { message ->
        val label = dateLabel(message.timestamp)
        if (label != previousDate) { index++; previousDate = label }
    }
    return index
}
