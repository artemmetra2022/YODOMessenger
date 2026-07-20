package app.yodo.messenger.domain.model

data class YodoUser(
    val uid: String,
    val displayName: String,
    val username: String? = null,
    val bio: String? = null,
    val email: String?,
    val phoneNumber: String?,
    val photoUrl: String?,
    val avatarBase64: String? = null
)
