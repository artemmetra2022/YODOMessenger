package app.yodo.messenger.domain.model

data class ForumTopic(
    val id: String,
    val title: String,
    val createdBy: String,
    val createdAt: Long,
    val isClosed: Boolean = false,
    val isPinned: Boolean = false,
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    // НОВОЕ (бейдж непрочитанных по темам): количество непрочитанных сообщений
    // в этой теме для текущего пользователя (уже отфильтровано репозиторием из
    // карты unreadCounts.<uid> документа темы).
    val unreadCount: Int = 0
)
