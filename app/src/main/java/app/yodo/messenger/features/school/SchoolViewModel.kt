package app.yodo.messenger.features.school

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.SchoolPreferences
import app.yodo.messenger.domain.model.SchoolIdea
import app.yodo.messenger.domain.model.SchoolNews
import app.yodo.messenger.domain.model.SchoolPoll
import app.yodo.messenger.domain.model.SchoolReview
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
 * НОВОЕ (раздел «Школа»): пользовательская часть школьного раздела —
 * новости, опросы, оценка раздела, идеи, игра и викторина (прогресс в
 * SchoolPreferences). Перенос пользовательской логики Telegram-бота.
 */
@HiltViewModel
class SchoolViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val schoolPreferences: SchoolPreferences,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val myUid: String? get() = firebaseAuth.currentUser?.uid
    val myDisplayName: String
        get() = firebaseAuth.currentUser?.email?.substringBefore("@") ?: "Пользователь"

    val news: StateFlow<List<SchoolNews>> = schoolRepository.observeNews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val polls: StateFlow<List<SchoolPoll>> = schoolRepository.observePolls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reviews: StateFlow<List<SchoolReview>> = schoolRepository.observeReviews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleSections: StateFlow<Set<String>> = schoolPreferences.visibleSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            SchoolPreferences.SectionIds.ALL.toSet())

    // Локальный прогресс игры/викторины (из бота: счёт сессии + победы в профиле).
    val gameWins: StateFlow<Int> = schoolPreferences.gameWins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val gameLosses: StateFlow<Int> = schoolPreferences.gameLosses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val quizCorrect: StateFlow<Int> = schoolPreferences.quizCorrect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val quizTotal: StateFlow<Int> = schoolPreferences.quizTotal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val myStars: StateFlow<Int> = schoolPreferences.myStars
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() { _message.value = null }

    fun vote(pollId: String, optionIndex: Int) {
        val uid = myUid ?: run {
            _message.value = "Требуется вход в аккаунт"
            return
        }
        viewModelScope.launch {
            schoolRepository.vote(pollId, optionIndex, uid)
                .onSuccess { _message.value = "✅ Голос принят!" }
                .onFailure { _message.value = it.message ?: "Не удалось проголосовать" }
        }
    }

    fun submitReview(stars: Int, liked: String, disliked: String) {
        val uid = myUid ?: run {
            _message.value = "Требуется вход в аккаунт"
            return
        }
        viewModelScope.launch {
            val review = SchoolReview(
                authorId = uid,
                authorName = myDisplayName,
                stars = stars,
                liked = liked.ifBlank { "-" },
                disliked = disliked.ifBlank { "-" }
            )
            schoolRepository.submitReview(review)
                .onSuccess {
                    schoolPreferences.setMyReview(uid, stars)
                    _message.value = "✅ Спасибо за оценку!"
                }
                .onFailure { _message.value = it.message ?: "Не удалось отправить оценку" }
        }
    }

    fun submitIdea(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            schoolRepository.submitIdea(
                SchoolIdea(authorName = myDisplayName, text = text.trim())
            ).onSuccess { _message.value = "✅ Ваша идея отправлена разработчикам!" }
                .onFailure { _message.value = it.message ?: "Не удалось отправить идею" }
        }
    }

    fun recordGameRound(won: Boolean) {
        viewModelScope.launch { schoolPreferences.recordGameRound(won) }
    }

    fun recordQuizAnswer(correct: Boolean) {
        viewModelScope.launch { schoolPreferences.recordQuizAnswer(correct) }
    }

    fun resetQuizScore() {
        viewModelScope.launch { schoolPreferences.resetQuizScore() }
    }
}

/**
 * НОВОЕ (раздел «Школа»): настройки отображения школьного раздела —
 * какие подразделы показывать на главном экране «Школы».
 */
@HiltViewModel
class SchoolSettingsViewModel @Inject constructor(
    private val schoolPreferences: SchoolPreferences
) : ViewModel() {

    val sectionEnabled: StateFlow<Boolean> = schoolPreferences.sectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val visibleSections: StateFlow<Set<String>> = schoolPreferences.visibleSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            SchoolPreferences.SectionIds.ALL.toSet())

    fun setSectionEnabled(enabled: Boolean) {
        viewModelScope.launch { schoolPreferences.setSectionEnabled(enabled) }
    }

    fun setSectionVisible(sectionId: String, visible: Boolean) {
        viewModelScope.launch { schoolPreferences.setSectionVisible(sectionId, visible) }
    }
}
