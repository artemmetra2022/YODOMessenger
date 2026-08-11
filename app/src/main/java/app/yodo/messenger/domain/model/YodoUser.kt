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
    val showEmail: Boolean = false,
    // НОВОЕ (сквозное шифрование): публичный ключ пользователя (base64 keyset), публикуется в Firestore.
    val publicKey: String? = null,
    // НОВОЕ (AE): публичный ID пользователя (выдаётся при регистрации, случайный).
    // Показывается в профиле, НЕ используется в поиске — только для админ-банов.
    val publicId: String? = null,
    // НОВОЕ (батч 7): эмодзи-статус и текстовый статус, видимые другим пользователям.
    val emojiStatus: String? = null,
    val customStatus: String? = null
)
