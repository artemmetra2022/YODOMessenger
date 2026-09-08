package app.yodo.messenger.features.school

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.SchoolPreferences
import app.yodo.messenger.domain.model.SchoolLessonFile
import app.yodo.messenger.domain.model.SchoolTeacherProfile
import app.yodo.messenger.domain.model.SchoolTeacherQuestion
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.SchoolRepository
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (учительские страницы): ViewModel страницы учителя — и для ученика
 * (посмотреть файл урока, задать вопрос, подписаться), и для владельца
 * (обновить файл, скрыть вопросы). Перенос /lesson, /ask, /setlesson,
 * /subscribe и скрытия вопросов из Telegram-бота.
 */
@HiltViewModel
class SchoolTeacherPageViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // NavGraph передаёт URL-энкоженное имя учителя (в нём бывают пробелы и точки)
    // и режим страницы: "page" (из справочника) или "my" (моя страница учителя).
    val teacherName: String = runCatching {
        java.net.URLDecoder.decode(savedStateHandle.get<String>("teacherName") ?: "", "UTF-8")
    }.getOrDefault(savedStateHandle.get<String>("teacherName") ?: "")
    private val isMyPage: Boolean = savedStateHandle.get<String>("mode") == "my"

    val myUid: String? get() = firebaseAuth.currentUser?.uid
    val myDisplayName: String
        get() = firebaseAuth.currentUser?.email?.substringBefore("@") ?: "Пользователь"

    val isAppAdmin: Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS

    /** Владелец ли я этой страницы (привязан ли мой аккаунт к учителю). */
    val isOwner: Boolean
        get() = myUid != null && (profile.value?.linkedUserId == myUid || isMyPage)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val profile: StateFlow<SchoolTeacherProfile?> =
        if (isMyPage && myUid != null) {
            schoolRepository.observeMyTeacherProfile(myUid!!)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        } else {
            schoolRepository.observeTeacherProfile(teacherName)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        }

    /**
     * Вопросы: в режиме «моя страница» профиль подгружается асинхронно, поэтому
     * подписка на вопросы строится реактивно от имени профиля через flatMapLatest.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val questions: StateFlow<List<SchoolTeacherQuestion>> =
        if (isMyPage) {
            profile
                .flatMapLatest { p ->
                    if (p == null) flowOf(emptyList())
                    else schoolRepository.observeTeacherQuestions(p.name)
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        } else {
            schoolRepository.observeTeacherQuestions(teacherName)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    /** НОВОЕ (история файлов урока): последние обновления файла (новые сверху). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lessonFiles: StateFlow<List<SchoolLessonFile>> =
        if (isMyPage) {
            profile
                .flatMapLatest { p ->
                    if (p == null) flowOf(emptyList())
                    else schoolRepository.observeLessonFiles(p.name)
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        } else {
            schoolRepository.observeLessonFiles(teacherName)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    val amSubscribed: Boolean
        get() = myUid != null && profile.value?.subscribers?.get(myUid) == true

    /** Сколько вопросов ещё ждут ответа (видит владелец страницы). */
    val unansweredCount: Int
        get() = if (isOwner) questions.value.count { !it.answered && !it.hidden } else 0

    /**
     * НОВОЕ (время ответа учителя): среднее время от вопроса до ответа по
     * отвеченным вопросам. Null — ответов ещё не было (показывать нечего).
     */
    val avgResponseTimeMs: Long?
        get() {
            val answered = questions.value.filter {
                it.answer.isNotBlank() && it.answeredAt > it.createdAt && it.createdAt > 0
            }
            if (answered.isEmpty()) return null
            return answered.sumOf { it.answeredAt - it.createdAt } / answered.size
        }

    /** НОВОЕ (время ответа учителя): человекочитаемая строка среднего времени. */
    val avgResponseTimeLabel: String?
        get() {
            val ms = avgResponseTimeMs ?: return null
            val hours = ms / 3_600_000
            val minutes = (ms % 3_600_000) / 60_000
            return when {
                hours >= 24 -> {
                    val days = hours / 24
                    if (days % 10 == 1 && days % 100 != 11) "~$days день"
                    else if (days % 10 in 2..4 && days % 100 !in 12..14) "~$days дня"
                    else "~$days дней"
                }
                hours >= 1 -> "~$hours ч"
                minutes >= 1 -> "~$minutes мин"
                else -> "меньше минуты"
            }
        }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() { _message.value = null }

    fun setLessonFile(fileUrl: String, fileNote: String) {
        val name = profile.value?.name ?: return
        viewModelScope.launch {
            schoolRepository.setTeacherFile(name, fileUrl.trim(), fileNote.trim())
                .onSuccess { _message.value = "✅ Файл урока обновлён" }
                .onFailure { _message.value = it.message ?: "Не удалось обновить файл" }
        }
    }

    fun toggleSubscription() {
        val uid = myUid ?: run {
            _message.value = "Требуется вход в аккаунт"
            return
        }
        val name = profile.value?.name ?: return
        viewModelScope.launch {
            schoolRepository.setTeacherSubscription(name, uid, !amSubscribed)
                .onSuccess {
                    _message.value = if (amSubscribed) "Вы отписались от обновлений"
                    else "✅ Вы подписаны на обновления файла"
                }
                .onFailure { _message.value = it.message ?: "Не удалось изменить подписку" }
        }
    }

    fun askQuestion(text: String) {
        if (text.isBlank()) return
        val name = profile.value?.name ?: return
        viewModelScope.launch {
            schoolRepository.askTeacherQuestion(
                name,
                SchoolTeacherQuestion(fromName = myDisplayName, text = text.trim())
            ).onSuccess { _message.value = "✅ Вопрос отправлен учителю" }
                .onFailure { _message.value = it.message ?: "Не удалось отправить вопрос" }
        }
    }

    fun setHidden(questionId: String, hidden: Boolean) {
        val name = profile.value?.name ?: return
        viewModelScope.launch {
            schoolRepository.setTeacherQuestionHidden(name, questionId, hidden)
                .onSuccess {
                    _message.value = if (hidden) "Вопрос скрыт со страницы" else "Вопрос снова виден"
                }
                .onFailure { _message.value = it.message ?: "Не удалось изменить вопрос" }
        }
    }

    fun answerQuestion(questionId: String, answer: String) {
        if (answer.isBlank()) return
        val name = profile.value?.name ?: return
        viewModelScope.launch {
            schoolRepository.answerTeacherQuestion(name, questionId, answer.trim())
                .onSuccess { _message.value = "✅ Ответ опубликован" }
                .onFailure { _message.value = it.message ?: "Не удалось опубликовать ответ" }
        }
    }
}

/**
 * НОВОЕ (учительские страницы): админ-управление профилями учителей —
 * создание/редактирование профилей и привязка аккаунта мессенджера к учителю
 * (поиск пользователя как в AdminUsersViewModel).
 */
@HiltViewModel
class SchoolTeacherAdminViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val isAppAdmin: Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS

    val profiles: StateFlow<List<SchoolTeacherProfile>> = schoolRepository.observeAllTeacherProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() { _message.value = null }

    // ── Поиск пользователей для привязки (как в AdminUsersViewModel)

    private val _searchResults = MutableStateFlow<List<YodoUser>>(emptyList())
    val searchResults: StateFlow<List<YodoUser>> = _searchResults
    private var searchJob: Job? = null

    fun onSearchQuery(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            runCatching { userRepository.searchUsers(query) }
                .onSuccess { _searchResults.value = it }
                .onFailure { _searchResults.value = emptyList() }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    // ── Действия админа

    fun createProfile(name: String, subject: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            schoolRepository.upsertTeacherProfile(
                SchoolTeacherProfile(name = name.trim(), subject = subject.trim())
            ).onSuccess { _message.value = "✅ Профиль учителя создан: ${name.trim()}" }
                .onFailure { _message.value = it.message ?: "Не удалось создать профиль" }
        }
    }

    fun linkUser(teacherName: String, user: YodoUser) {
        viewModelScope.launch {
            schoolRepository.linkTeacherProfile(teacherName, user.uid, user.displayName)
                .onSuccess {
                    _message.value = "✅ ${user.displayName} привязан к «${teacherName}»"
                    clearSearch()
                }
                .onFailure { _message.value = it.message ?: "Не удалось привязать" }
        }
    }

    fun unlinkUser(teacherName: String) {
        viewModelScope.launch {
            schoolRepository.unlinkTeacherProfile(teacherName)
                .onSuccess { _message.value = "Привязка снята с «${teacherName}»" }
                .onFailure { _message.value = it.message ?: "Не удалось отвязать" }
        }
    }
}

/**
 * НОВОЕ (учительские страницы): ViewModel настроек — режим учителя
 * (показ «Моей страницы» в разделе «Школа»).
 */
@HiltViewModel
class SchoolTeacherModeViewModel @Inject constructor(
    private val schoolPreferences: SchoolPreferences
) : ViewModel() {

    val teacherModeEnabled: StateFlow<Boolean> = schoolPreferences.teacherModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTeacherModeEnabled(enabled: Boolean) {
        viewModelScope.launch { schoolPreferences.setTeacherModeEnabled(enabled) }
    }
}
