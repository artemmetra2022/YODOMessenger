package app.yodo.messenger.domain.model

/**
 * НОВОЕ (История изменений профиля): одна запись журнала изменений профиля.
 * Хранится в Firestore: users/{uid}/profileHistory/{autoId}.
 *
 * @param id идентификатор документа
 * @param field техническое имя изменённого поля (displayName, username, aboutMe, ...)
 * @param fieldLabel человекочитаемое название поля для UI
 * @param oldValue предыдущее значение (может быть пустым)
 * @param newValue новое значение
 * @param timestamp момент изменения (millis)
 */
data class ProfileHistoryEntry(
    val id: String = "",
    val field: String = "",
    val fieldLabel: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val timestamp: Long = 0L
)
