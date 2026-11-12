package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.MessageStatus

/** Metadata changes to a receipt/reaction must never turn an acknowledged message into sending. */
fun resolveMessageStatus(rawStatus: String?, hasPendingWrites: Boolean,
    isOwnMessage: Boolean = true, serverAcknowledged: Boolean = false): MessageStatus {
    val stored = MessageStatus.entries.firstOrNull { it.name == rawStatus } ?: MessageStatus.SENT
    return if (hasPendingWrites && isOwnMessage && !serverAcknowledged && stored == MessageStatus.SENT)
        MessageStatus.SENDING else stored
}

fun aggregateReceiptStatus(recipients: List<String>, delivered: List<String>, read: List<String>): MessageStatus = when {
    recipients.isNotEmpty() && read.containsAll(recipients) -> MessageStatus.READ
    recipients.isNotEmpty() && (delivered + read).containsAll(recipients) -> MessageStatus.DELIVERED
    else -> MessageStatus.SENT
}
