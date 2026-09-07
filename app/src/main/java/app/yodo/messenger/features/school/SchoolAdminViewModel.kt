package app.yodo.messenger.features.school

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.SchoolIdea
import app.yodo.messenger.domain.model.SchoolNews
import app.yodo.messenger.domain.model.SchoolPoll
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.SchoolRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (раздел «Школа»): школьная админ-панель — управление новостями,
 * опросами и просмотр идей/оценок. Доступна только двум доверенным аккаунтам
 * приложения (isAppAdmin, тот же список, что и для вкладки «Админка»).
 * Это перенос админских функций Telegram-бота (/event, /admin_polls, идеи).
 */
@HiltViewModel
class SchoolAdminViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val isAppAdmin: Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS

    val news: StateFlow<List<SchoolNews>> = schoolRepository.observeNews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val polls: StateFlow<List<SchoolPoll>> = schoolRepository.observePolls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ideas: StateFlow<List<SchoolIdea>> = schoolRepository.observeIdeas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reviews = schoolRepository.observeReviews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() { _message.value = null }

    fun addNews(sender: String, text: String, eventDate: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            schoolRepository.addNews(
                SchoolNews(sender = sender.ifBlank { "Администрация гимназии" },
                    text = text.trim(),
                    eventDate = eventDate.ifBlank { "" })
            ).onSuccess { _message.value = "✅ Новость опубликована!" }
                .onFailure { _message.value = it.message ?: "Не удалось опубликовать новость" }
        }
    }

    fun editNewsText(newsId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            schoolRepository.updateNewsText(newsId, text.trim())
                .onSuccess { _message.value = "✅ Текст новости обновлён" }
                .onFailure { _message.value = it.message ?: "Не удалось обновить новость" }
        }
    }

    fun setNewsPinned(newsId: String, pinned: Boolean) {
        viewModelScope.launch {
            schoolRepository.setNewsPinned(newsId, pinned)
                .onSuccess {
                    _message.value = if (pinned) "📌 Новость закреплена" else "✅ Новость откреплена"
                }
                .onFailure { _message.value = it.message ?: "Не удалось изменить закрепление" }
        }
    }

    fun deleteNews(newsId: String) {
        viewModelScope.launch {
            schoolRepository.deleteNews(newsId)
                .onSuccess { _message.value = "✅ Новость удалена" }
                .onFailure { _message.value = it.message ?: "Не удалось удалить новость" }
        }
    }

    fun createPoll(question: String, options: List<String>) {
        if (question.isBlank() || options.count { it.isNotBlank() } < 2) {
            _message.value = "Нужен вопрос и минимум два варианта ответа"
            return
        }
        viewModelScope.launch {
            schoolRepository.addPoll(question.trim(), options.filter { it.isNotBlank() }.map { it.trim() })
                .onSuccess { _message.value = "✅ Опрос создан!" }
                .onFailure { _message.value = it.message ?: "Не удалось создать опрос" }
        }
    }

    fun deletePoll(pollId: String) {
        viewModelScope.launch {
            schoolRepository.deletePoll(pollId)
                .onSuccess { _message.value = "🗑 Опрос удалён" }
                .onFailure { _message.value = it.message ?: "Не удалось удалить опрос" }
        }
    }
}
