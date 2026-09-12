package app.yodo.messenger.domain.model

/** Данные расширенной карточки пользователя для главного администратора. */
data class AdminLoginEntry(
    val id: String = "",
    val timestamp: Long = 0L,
    val provider: String = "unknown",
    val device: String = "",
    val ipAddress: String? = null
)

data class AdminBlockHistoryEntry(
    val id: String = "",
    val action: String = "",
    val reason: String = "",
    val description: String = "",
    val byName: String = "",
    val timestamp: Long = 0L
)

data class AdminGroupChannelEntry(
    val chatId: String = "",
    val title: String = "",
    val type: String = "",
    val role: String = "Участник"
)

data class AdminUserDetails(
    val user: YodoUser,
    val loginHistory: List<AdminLoginEntry> = emptyList(),
    val messagesSent: Int = 0,
    val reportsReceived: Int = 0,
    val groupsAndChannels: List<AdminGroupChannelEntry> = emptyList(),
    val ipAddresses: List<String> = emptyList(),
    val blockHistory: List<AdminBlockHistoryEntry> = emptyList(),
    val activity: List<AdminActivityPoint> = emptyList(),
    val adminAssignment: AdminAssignment? = null,
    val actionHistory: List<AdminTimelineEntry> = emptyList(),
    val reportHistory: List<AdminTimelineEntry> = emptyList(),
    val messageHistory: List<AdminTimelineEntry> = emptyList(),
    val appealHistory: List<AdminTimelineEntry> = emptyList()
)


data class AdminUserModerationStats(
    val reports: Int = 0,
    val blocks: Int = 0,
    val difficult: Boolean = false
)

data class AdminTimelineEntry(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val details: String = "",
    val timestamp: Long = 0L,
    val actorName: String = "",
    val status: String = ""
)
