package app.yodo.messenger.domain.model

data class YodoUser(
    val uid: String,
    val displayName: String,
    val username: String? = null,
    val bio: String? = null,
    val email: String?,
    val phoneNumber: String?,
    val photoUrl: String?,
    val avatarBase64: String? = null,
    val aboutMe: String? = null,
    val birthDate: String? = null,
    val location: String? = null,
    val website: String? = null,
    val showBirthDate: Boolean = true,
    val showAboutMe: Boolean = true,
    val showLocation: Boolean = true,
    val showWebsite: Boolean = true,
    val showPhoneNumber: Boolean = false,
    val showEmail: Boolean = false
)
