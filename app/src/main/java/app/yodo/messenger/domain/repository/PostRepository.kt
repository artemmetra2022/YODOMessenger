package app.yodo.messenger.domain.repository

import android.net.Uri
import app.yodo.messenger.domain.model.Post
import app.yodo.messenger.domain.model.PostComment
import kotlinx.coroutines.flow.Flow

sealed class PostResult {
    data object Success : PostResult()
    data class Error(val message: String) : PostResult()
}

// НОВОЕ: уже отрендеренное превью личной доски, готовое к прикреплению к посту.
// bitmapBase64 — сжатый JPEG/PNG в base64 (как и прочие картинки в приложении, без Storage).
data class AttachedBoardPreview(
    val title: String,
    val bitmapBase64: String,
    val objectCount: Int
)

interface PostRepository {
    // Живой поток постов конкретного пользователя (для стены в профиле), новые сверху.
    fun observePosts(userId: String): Flow<List<Post>>

    // imageUris — локальные Uri фото, выбранных пользователем (можно пусто, если только текст).
    // attachedBoard — НОВОЕ: превью прикреплённой личной доски (Canvas), если автор её выбрал
    // в редакторе поста. Сама доска никуда не выгружается — только готовый bitmap-снимок.
    suspend fun createPost(
        text: String,
        imageUris: List<Uri>,
        attachedBoard: AttachedBoardPreview? = null
    ): PostResult

    suspend fun deletePost(postId: String): PostResult

    // НОВОЕ (VK-стиль): поставить/снять лайк на посте.
    suspend fun toggleLike(postId: String): PostResult

    // НОВОЕ (VK-стиль): зарегистрировать просмотр поста (+1 к счётчику).
    suspend fun registerView(postId: String): PostResult

    // НОВОЕ (AK): живой поток комментариев под постом (старые сверху, как в ленте).
    fun observeComments(postId: String): Flow<List<PostComment>>

    // НОВОЕ (AK): добавить комментарий под постом.
    suspend fun addComment(postId: String, text: String): PostResult

    // НОВОЕ (AK): удалить свой комментарий (или любой — если это ваш пост).
    suspend fun deleteComment(postId: String, commentId: String): PostResult
}
