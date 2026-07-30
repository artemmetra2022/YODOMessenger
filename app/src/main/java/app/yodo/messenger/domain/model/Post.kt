package app.yodo.messenger.domain.model

/**
 * Пост на стене профиля пользователя (аналог поста ВКонтакте) —
 * текст и/или до нескольких фото, видны всем пользователям приложения.
 */
data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarBase64: String? = null,
    val authorPhotoUrl: String? = null,
    val text: String = "",
    // Base64-кодированные фото (та же схема хранения, что и для фото в чате/аватаров —
    // без Firebase Storage, чтобы не требовать биллинг Blaze).
    val photosBase64: List<String> = emptyList(),
    val createdAtMillis: Long = 0L
)
