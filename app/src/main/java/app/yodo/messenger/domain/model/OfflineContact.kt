package app.yodo.messenger.domain.model

/**
 * НОВОЕ (офлайн обмен контактами по QR).
 *
 * Контакт, добавленный сканированием QR-кода. В отличие от обычного поиска, все данные
 * (включая публичный ключ для сквозного шифрования) приходят прямо из QR — то есть
 * идентичность и ключ собеседника можно получить полностью офлайн, без обращения к серверу.
 * Сам обмен сообщениями по-прежнему идёт через backend, но ключ шифрования уже на руках.
 */
data class OfflineContact(
    val uid: String,
    val displayName: String,
    val username: String? = null,
    val publicKey: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
