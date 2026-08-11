package app.yodo.messenger.domain.repository

import android.net.Uri
import app.yodo.messenger.domain.model.Post
import kotlinx.coroutines.flow.Flow

sealed class PostResult {
    data object Success : PostResult()
    data class Error(val message: String) : PostResult()
}

interface PostRepository {
    // Живой поток постов конкретного пользователя (для стены в профиле), новые сверху.
    fun observePosts(userId: String): Flow<List<Post>>

    // imageUris — локальные Uri фото, выбранных пользователем (можно пусто, если только текст).
    suspend fun createPost(text: String, imageUris: List<Uri>): PostResult

    suspend fun deletePost(postId: String): PostResult
}
