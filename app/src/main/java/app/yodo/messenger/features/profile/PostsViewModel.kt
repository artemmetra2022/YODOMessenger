package app.yodo.messenger.features.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.Post
import app.yodo.messenger.domain.model.PostComment
import app.yodo.messenger.domain.repository.PostRepository
import app.yodo.messenger.domain.repository.PostResult
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Общая ViewModel для ленты постов на стене профиля — используется и в "своём"
 * профиле (ProfileScreen), и в чужом (UserProfileScreen). userId передаётся при
 * старте наблюдения, чтобы одну и ту же ViewModel можно было переиспользовать.
 */
@HiltViewModel
class PostsViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var observedUserId: String? = null

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    fun startObserving(userId: String) {
        if (observedUserId == userId) return
        observedUserId = userId
        viewModelScope.launch {
            postRepository.observePosts(userId).collect { _posts.value = it }
        }
    }

    fun createPost(text: String, imageUris: List<Uri>) {
        viewModelScope.launch {
            _isPosting.value = true
            when (val result = postRepository.createPost(text, imageUris)) {
                is PostResult.Success -> { /* лента обновится через observePosts */ }
                is PostResult.Error -> _errorMessage.value = result.message
            }
            _isPosting.value = false
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            postRepository.deletePost(postId)
        }
    }

    // НОВОЕ (VK-стиль): лайк/дизлайк поста.
    fun toggleLike(postId: String) {
        viewModelScope.launch { postRepository.toggleLike(postId) }
    }

    // НОВОЕ (VK-стиль): отметить просмотр поста (один раз за сессию на каждый пост).
    private val viewedPostIds = mutableSetOf<String>()
    fun registerView(postId: String) {
        if (!viewedPostIds.add(postId)) return
        viewModelScope.launch { postRepository.registerView(postId) }
    }

    // НОВОЕ (AK): комментарии под постом.
    // Комментарии грузятся только для открытого поста, чтобы не держать десятки
    // лишних подписок на всю стену сразу.
    private val _openCommentsPostId = MutableStateFlow<String?>(null)
    val openCommentsPostId: StateFlow<String?> = _openCommentsPostId

    private val _comments = MutableStateFlow<List<PostComment>>(emptyList())
    val comments: StateFlow<List<PostComment>> = _comments

    private val _isSendingComment = MutableStateFlow(false)
    val isSendingComment: StateFlow<Boolean> = _isSendingComment

    private var commentsJob: kotlinx.coroutines.Job? = null

    fun openComments(postId: String) {
        if (_openCommentsPostId.value == postId) return
        _openCommentsPostId.value = postId
        _comments.value = emptyList()
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            postRepository.observeComments(postId).collect { _comments.value = it }
        }
    }

    fun closeComments() {
        commentsJob?.cancel()
        commentsJob = null
        _openCommentsPostId.value = null
        _comments.value = emptyList()
    }

    fun sendComment(text: String) {
        val postId = _openCommentsPostId.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            _isSendingComment.value = true
            when (val result = postRepository.addComment(postId, text)) {
                is PostResult.Success -> { /* список обновится через observeComments */ }
                is PostResult.Error -> _errorMessage.value = result.message
            }
            _isSendingComment.value = false
        }
    }

    fun deleteComment(commentId: String) {
        val postId = _openCommentsPostId.value ?: return
        viewModelScope.launch {
            when (val result = postRepository.deleteComment(postId, commentId)) {
                is PostResult.Success -> {}
                is PostResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun consumeError() {
        _errorMessage.value = null
    }
}
