package app.yodo.messenger.data.repository

import android.net.Uri
import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.domain.model.Post
import app.yodo.messenger.domain.model.PostComment
import app.yodo.messenger.domain.repository.PostRepository
import app.yodo.messenger.domain.repository.PostResult
import app.yodo.messenger.util.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: android.content.Context
) : PostRepository {

    // До 4 фото в одном посте — иначе документ Firestore (лимит 1 МБ) не вместит base64.
    private val maxPhotosPerPost = 4

    override fun observePosts(userId: String): Flow<List<Post>> = callbackFlow {
        val listener = firestore.collection("posts")
            .whereEqualTo("authorId", userId)
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot.documents.map { it.toPost() })
            }
        awaitClose { listener.remove() }
    }

    override suspend fun createPost(text: String, imageUris: List<Uri>): PostResult {
        val user = firebaseAuth.currentUser ?: return PostResult.Error("Вы не авторизованы")
        val trimmedText = text.trim().take(2000)
        if (trimmedText.isBlank() && imageUris.isEmpty())
            return PostResult.Error("Добавьте текст или фото")

        return try {
            // Свежие данные автора (имя/аватар) — берём из документа пользователя,
            // чтобы пост отображал актуальное имя, даже если профиль поменяется позже.
            val userDoc = firestore.collection("users").document(user.uid).get().await()
            val authorName = userDoc.getString("displayName")
                ?: user.displayName.orEmpty().ifBlank { "Пользователь" }
            val authorAvatarBase64 = userDoc.getString("avatarBase64")
            val authorPhotoUrl = userDoc.getString("avatarUrl") ?: user.photoUrl?.toString()

            val photosBase64 = withContext(Dispatchers.Default) {
                imageUris.take(maxPhotosPerPost).mapNotNull { uri ->
                    ImageUtils.compressPostImageToBase64(context, uri)
                }
            }
            if (imageUris.isNotEmpty() && photosBase64.isEmpty())
                return PostResult.Error("Не удалось обработать фото")

            val postRef = firestore.collection("posts").document()
            val data = hashMapOf(
                "authorId" to user.uid,
                "authorName" to authorName,
                "authorAvatarBase64" to authorAvatarBase64,
                "authorPhotoUrl" to authorPhotoUrl,
                "text" to trimmedText,
                "photosBase64" to photosBase64,
                "createdAtMillis" to System.currentTimeMillis()
            )
            postRef.set(data).await()
            PostResult.Success
        } catch (e: Exception) {
            PostResult.Error(e.toUserMessage("Не удалось опубликовать пост"))
        }
    }

    override suspend fun deletePost(postId: String): PostResult {
        val uid = firebaseAuth.currentUser?.uid ?: return PostResult.Error("Вы не авторизованы")
        return try {
            val doc = firestore.collection("posts").document(postId).get().await()
            if (!doc.exists()) return PostResult.Success
            if (doc.getString("authorId") != uid)
                return PostResult.Error("Нельзя удалить чужой пост")
            firestore.collection("posts").document(postId).delete().await()
            PostResult.Success
        } catch (e: Exception) {
            PostResult.Error(e.toUserMessage("Не удалось удалить пост"))
        }
    }

    // НОВОЕ (VK-стиль): лайк поста (arrayUnion/arrayRemove по uid).
    override suspend fun toggleLike(postId: String): PostResult {
        val uid = firebaseAuth.currentUser?.uid ?: return PostResult.Error("Вы не авторизованы")
        return try {
            val ref = firestore.collection("posts").document(postId)
            val doc = ref.get().await()
            val liked = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (uid in liked) {
                ref.update("likedBy", FieldValue.arrayRemove(uid)).await()
            } else {
                ref.update("likedBy", FieldValue.arrayUnion(uid)).await()
            }
            PostResult.Success
        } catch (e: Exception) {
            PostResult.Error(e.toUserMessage("Не удалось поставить лайк"))
        }
    }

    // НОВОЕ (VK-стиль): +1 к просмотрам. Не считаем просмотры автора.
    override suspend fun registerView(postId: String): PostResult {
        val uid = firebaseAuth.currentUser?.uid ?: return PostResult.Error("Вы не авторизованы")
        return try {
            val ref = firestore.collection("posts").document(postId)
            val doc = ref.get().await()
            if (doc.getString("authorId") == uid) return PostResult.Success
            ref.update("views", FieldValue.increment(1)).await()
            PostResult.Success
        } catch (e: Exception) {
            PostResult.Error(e.toUserMessage("Не удалось учесть просмотр"))
        }
    }

    // НОВОЕ (AK): комментарии лежат в подколлекции posts/{postId}/comments.
    private fun commentsRef(postId: String) =
        firestore.collection("posts").document(postId).collection("comments")

    override fun observeComments(postId: String): Flow<List<PostComment>> = callbackFlow {
        val listener = commentsRef(postId)
            .orderBy("createdAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snapshot.documents.map { doc ->
                        PostComment(
                            id = doc.id,
                            postId = postId,
                            authorId = doc.getString("authorId") ?: "",
                            authorName = doc.getString("authorName") ?: "",
                            authorAvatarBase64 = doc.getString("authorAvatarBase64"),
                            authorPhotoUrl = doc.getString("authorPhotoUrl"),
                            text = doc.getString("text") ?: "",
                            createdAtMillis = doc.getLong("createdAtMillis") ?: 0L
                        )
                    }
                )
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addComment(postId: String, text: String): PostResult = withContext(Dispatchers.IO) {
        val user = firebaseAuth.currentUser ?: return@withContext PostResult.Error("Вы не авторизованы")
        val trimmed = text.trim().take(1000)
        if (trimmed.isBlank()) return@withContext PostResult.Error("Комментарий пустой")
        try {
            // Имя и аватарка берутся из профиля, чтобы комментарий рисовался одним
            // чтением, без дополнительного запроса за каждым автором.
            val profile = runCatching {
                firestore.collection("users").document(user.uid).get().await()
            }.getOrNull()
            val commentRef = commentsRef(postId).document()
            commentRef.set(
                mapOf(
                    "authorId" to user.uid,
                    "authorName" to (profile?.getString("displayName")
                        ?: user.displayName ?: "Пользователь"),
                    "authorAvatarBase64" to profile?.getString("avatarBase64"),
                    "authorPhotoUrl" to (profile?.getString("photoUrl") ?: user.photoUrl?.toString()),
                    "text" to trimmed,
                    "createdAtMillis" to System.currentTimeMillis()
                )
            ).await()
            // Счётчик на самом посте — чтобы число было видно сразу на кнопке.
            runCatching {
                firestore.collection("posts").document(postId)
                    .update("commentCount", FieldValue.increment(1)).await()
            }
            PostResult.Success
        } catch (e: Exception) {
            PostResult.Error(e.toUserMessage("Не удалось отправить комментарий"))
        }
    }

    override suspend fun deleteComment(postId: String, commentId: String): PostResult = withContext(Dispatchers.IO) {
        firebaseAuth.currentUser ?: return@withContext PostResult.Error("Вы не авторизованы")
        try {
            commentsRef(postId).document(commentId).delete().await()
            runCatching {
                firestore.collection("posts").document(postId)
                    .update("commentCount", FieldValue.increment(-1)).await()
            }
            PostResult.Success
        } catch (e: Exception) {
            PostResult.Error(e.toUserMessage("Не удалось удалить комментарий"))
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toPost(): Post = Post(
        id = id,
        authorId = getString("authorId") ?: "",
        authorName = getString("authorName") ?: "",
        authorAvatarBase64 = getString("authorAvatarBase64"),
        authorPhotoUrl = getString("authorPhotoUrl"),
        text = getString("text") ?: "",
        photosBase64 = (get("photosBase64") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        createdAtMillis = getLong("createdAtMillis") ?: 0L,
        likedBy = (get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        views = (getLong("views") ?: 0L).toInt(),
        // НОВОЕ (AK): число комментариев для кнопки под постом.
        commentCount = (getLong("commentCount") ?: 0L).toInt().coerceAtLeast(0)
    )
}
