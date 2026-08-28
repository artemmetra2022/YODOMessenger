package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.MessageStatus

/**
 * ИСПРАВЛЕНО (индикатор прочитано/доставлено "врёт"): раньше статус сообщения читался
 * напрямую из поля "status" документа Firestore без учёта того, подтверждена ли запись
 * сервером (hasPendingWrites из локального кэша). Из-за этого локально созданное
 * сообщение, которое ещё не долетело до сервера, могло на мгновение показать чужой
 * статус из кэша (например "READ" от предыдущей версии документа), либо наоборот —
 * реальный SENT показывался раньше, чем сервер его подтвердил.
 *
 * Эта функция — единственное место, где стоит решать "какую галочку показать". Она:
 * 1) если снапшот ещё не подтверждён сервером (hasPendingWrites=true) — всегда SENDING,
 *    независимо от того, что записано в поле status;
 * 2) иначе — доверяет статусу из документа, с безопасным fallback на SENT для
 *    null/незнакомых значений (защищает от крэша при рассинхроне схемы/старых данных).
 */
fun resolveMessageStatus(rawStatus: String?, hasPendingWrites: Boolean): MessageStatus {
    if (hasPendingWrites) return MessageStatus.SENDING
    return when (rawStatus) {
        MessageStatus.SENDING.name -> MessageStatus.SENDING
        MessageStatus.SENT.name -> MessageStatus.SENT
        MessageStatus.DELIVERED.name -> MessageStatus.DELIVERED
        MessageStatus.READ.name -> MessageStatus.READ
        MessageStatus.FAILED.name -> MessageStatus.FAILED
        else -> MessageStatus.SENT
    }
}
