package app.yodo.messenger.domain.model

data class TeacherProfile(
    val userId: String = "",
    val displayName: String = "",
    val shortBio: String = "",
    val subjects: List<String> = emptyList(),
    val questionHours: String = "",
    val contactEmail: String = "",
    val photoUrl: String? = null,
    val rating: Double = 0.0,
    val showRating: Boolean = true,
    val vacationFromMillis: Long? = null,
    val vacationToMillis: Long? = null,
    val acceptingQuestions: Boolean = true,
    val notifyNewQuestions: Boolean = true,
    val notifyMessages: Boolean = true,
    val updatedAtMillis: Long = 0L
)
