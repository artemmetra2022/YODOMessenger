package app.yodo.messenger.domain.model

data class BehaviorAlert(
    val userId: String = "",
    val userName: String = "",
    val type: String = "",
    val title: String = "",
    val description: String = "",
    val count: Int = 0,
    val windowMinutes: Int = 0,
    val createdAt: Long = 0L
)

data class DifficultUser(
    val user: YodoUser,
    val reports: Int = 0,
    val blocks: Int = 0
) {
    val totalFlags: Int get() = reports + blocks
}

data class BehaviorMonitoringSnapshot(
    val alerts: List<BehaviorAlert> = emptyList(),
    val difficultUsers: List<DifficultUser> = emptyList(),
    val newcomerPauseEnabled: Boolean = true
)
