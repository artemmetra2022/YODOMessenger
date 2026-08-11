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
    val createdAtMillis: Long = 0L,
    // НОВОЕ (VK-стиль): uid пользователей, поставивших лайк («нравится»).
    val likedBy: List<String> = emptyList(),
    // НОВОЕ (VK-стиль): число просмотров поста.
    val views: Int = 0,
    // НОВОЕ (AK): сколько комментариев под постом — чтобы показывать число на кнопке
    // без загрузки всей подколлекции комментариев.
    val commentCount: Int = 0
) {
    val likeCount: Int get() = likedBy.size
}

/**
 * НОВОЕ (AK): комментарий под постом в профиле.
 * Хранится в posts/{postId}/comments/{commentId}.
 */
data class PostComment(
    val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarBase64: String? = null,
    val authorPhotoUrl: String? = null,
    val text: String = "",
    val createdAtMillis: Long = 0L
)
