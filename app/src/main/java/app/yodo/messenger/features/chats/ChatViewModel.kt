package app.yodo.messenger.features.chats

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.data.local.NotificationMessageStore
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.UserPresence
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.domain.repository.ReplyContext
import app.yodo.messenger.domain.repository.SendMessageResult
import app.yodo.messenger.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ChatUiState(
    val chatTitle: String = "Чат",
    val chatType: String = "PRIVATE",
    val otherUserId: String? = null,
    val messages: List<Message> = emptyList(),
    val pinnedMessages: List<Message> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val otherUserPresence: UserPresence? = null,
    val isOtherUserTyping: Boolean = false,
    // НОВОЕ (индикатор набора текста в группах): имена участников группы, которые сейчас печатают
    // (в приватных чатах используется isOtherUserTyping, здесь — для GROUP/CHANNEL).
    val typingUserNames: List<String> = emptyList(),
    val replyingTo: Message? = null,
    val editingMessage: Message? = null,
    val initialDraft: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val otherUserPhotoUrl: String? = null,
    val otherUserAvatarBase64: String? = null,
    val isVerified: Boolean = false,
    // НОВОЕ (переработка каналов): аватарка канала для шапки чата
    // (у каналов аватар лежит в самом документе чата, а не в профиле пользователя).
    val channelAvatarBase64: String? = null,
    val isAdmin: Boolean = false,
    // НОВОЕ: подписка/подписчики для пользовательских каналов
    val subscriberCount: Int = 0,
    val isSubscribed: Boolean = false,
    // НОВОЕ: владелец канала не может отписаться (только удалить канал целиком).
    val isChannelOwner: Boolean = false,
    // НОВОЕ (п.38): текущий таймер исчезающих сообщений чата (в секундах), null = выключен
    val disappearingTtlSeconds: Long? = null,
    // НОВОЕ: список отложенных сообщений этого чата (ещё не опубликованных)
    val scheduledMessages: List<app.yodo.messenger.domain.model.ScheduledMessage> = emptyList(),
    // п.2: id только что пересланного в этот чат сообщения — показываем плашку
    // "Сообщение переслано" с окном отмены на 5 секунд. null = плашка скрыта.
    val justForwardedMessageId: String? = null,
    // НОВОЕ (п.1): кому переслали (для текста плашки и перехода в профиль по клику),
    // и обратный отсчёт секунд до автоскрытия плашки (5,4,3,2,1).
    val justForwardedTargetName: String? = null,
    val justForwardedTargetUserId: String? = null,
    val forwardUndoSecondsLeft: Int = 0
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val presenceRepository: PresenceRepository,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val draftsPreferences: DraftsPreferences,
    private val pendingForwardHolder: PendingForwardHolder,
    private val pendingForwardUndoHolder: PendingForwardUndoHolder,
    private val firebaseAuth: FirebaseAuth,
    private val notificationMessageStore: NotificationMessageStore,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    fun prepareForward(message: Message) { pendingForwardHolder.set(message) }

    private var forwardUndoTimerJob: Job? = null

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    val sendOnEnter: StateFlow<Boolean> = userSettingsPreferences.sendOnEnter.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val autoDownloadImages: StateFlow<Boolean> = userSettingsPreferences.autoDownloadImages.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val hideKeyboardOnSend: StateFlow<Boolean> = userSettingsPreferences.hideKeyboardOnSend.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    // НОВОЕ (расширенные опросы): управляет тем, показывать ли в диалоге создания опроса
    // доп. параметры (множественный выбор, дата авто-закрытия).
    val advancedPollsEnabled: StateFlow<Boolean> = userSettingsPreferences.advancedPollsEnabled.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    // Фон чата, выбранный в настройках
    val chatBackgroundType: StateFlow<app.yodo.messenger.data.local.ChatBackgroundType> =
        userSettingsPreferences.chatBackgroundType.stateIn(
            scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000),
            initialValue = app.yodo.messenger.data.local.ChatBackgroundType.DEFAULT
        )
    val chatBackgroundCustomPath: StateFlow<String> = userSettingsPreferences.chatBackgroundCustomPath.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = ""
    )

    private var typingResetJob: Job? = null
    private var isCurrentlyMarkedTyping = false

    init {
        loadChatInfo()
        observeMessages()
        observePinnedMessages()
        observeTyping()
        observeDisappearingTtl()
        observeScheduledMessages()
        markAsRead()
        loadDraft()
        cleanupExpiredMessagesPeriodically()
        publishDueScheduledMessagesPeriodically()
        checkPendingForwardUndo()
    }

    /**
     * п.2: если мы только что попали в этот чат в результате пересылки сообщения,
     * забираем это из holder'а и на 5 секунд показываем плашку "Сообщение переслано"
     * с возможностью отмены (удаления только что отправленного сообщения).
     */
    private fun checkPendingForwardUndo() {
        val pending = pendingForwardUndoHolder.takeIfForChat(chatId) ?: return
        // НОВОЕ (п.1): имя получателя — предпочитаем @username, иначе отображаемое имя.
        val displayTarget = pending.targetUsername?.let { "@$it" } ?: pending.targetName
        _uiState.value = _uiState.value.copy(
            justForwardedMessageId = pending.messageId,
            justForwardedTargetName = displayTarget,
            justForwardedTargetUserId = pending.targetUserId,
            forwardUndoSecondsLeft = 5
        )
        forwardUndoTimerJob?.cancel()
        forwardUndoTimerJob = viewModelScope.launch {
            // НОВОЕ (п.1): обратный отсчёт 5,4,3,2,1 рядом с кнопкой "Отменить".
            repeat(5) {
                delay(1000)
                val secondsLeft = (_uiState.value.forwardUndoSecondsLeft - 1).coerceAtLeast(0)
                _uiState.value = _uiState.value.copy(forwardUndoSecondsLeft = secondsLeft)
            }
            _uiState.value = _uiState.value.copy(
                justForwardedMessageId = null,
                justForwardedTargetName = null,
                justForwardedTargetUserId = null,
                forwardUndoSecondsLeft = 0
            )
        }
    }

    /** Пользователь нажал "Отменить" в плашке — удаляем пересланное сообщение и скрываем плашку. */
    fun undoForward() {
        forwardUndoTimerJob?.cancel()
        val messageId = _uiState.value.justForwardedMessageId ?: return
        _uiState.value = _uiState.value.copy(
            justForwardedMessageId = null,
            justForwardedTargetName = null,
            justForwardedTargetUserId = null,
            forwardUndoSecondsLeft = 0
        )
        viewModelScope.launch {
            messageRepository.deleteMessage(chatId, messageId)
        }
    }

    // НОВОЕ: следим за списком отложенных сообщений этого чата (для экрана "Отложенные").
    private fun observeScheduledMessages() {
        viewModelScope.launch {
            messageRepository.observeScheduledMessages(chatId).collect { list ->
                _uiState.value = _uiState.value.copy(scheduledMessages = list)
            }
        }
    }

    // НОВОЕ: как и с исчезающими сообщениями, без Cloud Functions/cron публикация
    // отложенных сообщений — best-effort на стороне клиента, пока открыт чат.
    // Ранний выход: если список отложенных сообщений пуст — пропускаем запрос к Firestore.
    private fun publishDueScheduledMessagesPeriodically() {
        viewModelScope.launch {
            while (true) {
                if (_uiState.value.scheduledMessages.isNotEmpty()) {
                    messageRepository.publishDueScheduledMessages(chatId)
                }
                delay(10_000L)
            }
        }
    }

    /** Планирует отправку текста (и/или фото из reply-контекста) на указанный момент времени. */
    fun scheduleMessage(text: String, scheduledForMillis: Long) {
        if (text.isBlank()) return
        clearTypingStatus()
        viewModelScope.launch { draftsPreferences.clearDraft(chatId) }
        val replying = _uiState.value.replyingTo
        val replyContext = replying?.let {
            ReplyContext(
                messageId = it.id,
                senderName = if (it.senderId == currentUserId) "Вы" else _uiState.value.chatTitle,
                text = it.previewText()
            )
        }
        _uiState.value = _uiState.value.copy(replyingTo = null)
        viewModelScope.launch {
            when (val result = messageRepository.scheduleMessage(chatId, text, scheduledForMillis, replyTo = replyContext)) {
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
                else -> {}
            }
        }
    }

    fun cancelScheduledMessage(scheduledMessageId: String) {
        viewModelScope.launch { messageRepository.cancelScheduledMessage(chatId, scheduledMessageId) }
    }

    // НОВОЕ (п.38): следим за текущим таймером исчезающих сообщений этого чата,
    // чтобы показывать актуальную иконку/подпись в шапке и в меню выбора времени.
    private fun observeDisappearingTtl() {
        viewModelScope.launch {
            chatRepository.observeDisappearingTtl(chatId).collect { ttl ->
                _uiState.value = _uiState.value.copy(disappearingTtlSeconds = ttl)
            }
        }
    }

    fun setDisappearingTtl(ttlSeconds: Long?) {
        viewModelScope.launch { chatRepository.setDisappearingTtl(chatId, ttlSeconds) }
    }

    // НОВОЕ (п.38): периодически (пока открыт чат) чистим уже истёкшие сообщения —
    // best-effort ��ез Cloud Functions/cron. Достаточно, чтобы в реальном использовании
    // сообщения пропадали вскоре после истечения таймера у любого из открывших чат.
    // Ранний выход: если в чате не включён TTL — не делаем запрос к Firestore вообще.
    private fun cleanupExpiredMessagesPeriodically() {
        viewModelScope.launch {
            while (true) {
                if (_uiState.value.disappearingTtlSeconds != null) {
                    messageRepository.deleteExpiredMessages(chatId)
                }
                delay(15_000L)
            }
        }
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val draft = draftsPreferences.getDraft(chatId)
            if (draft.isNotBlank()) _uiState.value = _uiState.value.copy(initialDraft = draft)
        }
    }

    fun saveDraft(text: String) {
        viewModelScope.launch { draftsPreferences.saveDraft(chatId, text) }
    }

    private fun loadChatInfo() {
        viewModelScope.launch {
            chatRepository.getChatInfo(chatId)?.let { info ->
                // НОВОЕ: право публиковать посты — владелец канала (создатель) или
                // назначенный им админ. Плюс сохраняем старую проверку по email для
                // единственного служебного официального канала (обратная совместимость).
                val myUid = currentUserId
                val myEmail = firebaseAuth.currentUser?.email?.lowercase()
                val isOfficialChannelAdmin = chatId == ChatRepository.OFFICIAL_CHANNEL_ID &&
                        myEmail != null && myEmail in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }
                val isOwnerOrAdmin = myUid != null &&
                        (myUid == info.channelOwnerId || myUid in info.channelAdminIds)
                val isAdmin = info.type == "CHANNEL" && (isOfficialChannelAdmin || isOwnerOrAdmin)
                _uiState.value = _uiState.value.copy(
                    chatTitle = info.title, chatType = info.type,
                    otherUserId = info.otherUserId,
                    otherUserPhotoUrl = info.otherUserPhotoUrl,
                    otherUserAvatarBase64 = info.otherUserAvatarBase64,
                    isVerified = info.isVerified,
                    // НОВОЕ (переработка каналов): аватарка канала для шапки чата
                    channelAvatarBase64 = info.avatarBase64,
                    isAdmin = isAdmin,
                    subscriberCount = info.subscriberCount,
                    isSubscribed = info.isSubscribed,
                    isChannelOwner = myUid != null && myUid == info.channelOwnerId
                )
                info.otherUserId?.let { observePresence(it) }
            }
        }
    }

    private fun observePresence(otherUserId: String) {
        viewModelScope.launch {
            presenceRepository.observePresence(otherUserId).collect { presence ->
                _uiState.value = _uiState.value.copy(otherUserPresence = presence)
            }
        }
    }

    // НОВОЕ (индикатор набора текста в группах): кэш uid -> displayName, чтобы не запрашивать
    // getGroupInfo целиком на каждое изменение typingUsers.
    private var groupMemberNamesCache: Map<String, String>? = null

    private fun observeTyping() {
        viewModelScope.launch {
            presenceRepository.observeTypingUsers(chatId).collect { typingUids ->
                val myUid = firebaseAuth.currentUser?.uid
                val othersTyping = typingUids.filter { it != myUid }
                val otherUserId = _uiState.value.otherUserId
                if (otherUserId != null) {
                    // Приватный чат: обновляем isOtherUserTyping и сбрасываем групповой список.
                    _uiState.value = _uiState.value.copy(
                        isOtherUserTyping = otherUserId in othersTyping,
                        typingUserNames = emptyList()
                    )
                } else if (_uiState.value.chatType == "GROUP" || _uiState.value.chatType == "CHANNEL") {
                    // Группа/канал: резолвим имена для всех печатающих (или очищаем список).
                    val names = if (othersTyping.isNotEmpty()) resolveMemberNames(othersTyping)
                                else emptyList()
                    _uiState.value = _uiState.value.copy(
                        typingUserNames = names,
                        isOtherUserTyping = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isOtherUserTyping = false,
                        typingUserNames = emptyList()
                    )
                }
            }
        }
    }

    private suspend fun resolveMemberNames(uids: List<String>): List<String> {
        val cache = groupMemberNamesCache ?: run {
            val info = chatRepository.getGroupInfo(chatId)
            val map = info?.members?.associate { it.uid to it.displayName } ?: emptyMap()
            groupMemberNamesCache = map
            map
        }
        return uids.mapNotNull { cache[it] }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            messageRepository.observeMessages(chatId).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
                // ФИКС (бейдж непрочитанных): markAsRead() раньше вызывался только при
                // открытии чата. Если сообщение (например одноразовое фото) приходило, пока
                // чат уже открыт, счётчик непрочитанных на сервере рос, а в главном меню бейдж
                // оставался. Теперь помечаем как прочитанное при каждом входящем сообщении,
                // пока экран чата открыт.
                val myUid = firebaseAuth.currentUser?.uid
                val lastFromOther = messages.lastOrNull()?.senderId?.let { it != myUid } ?: false
                if (lastFromOther) markAsRead()
            }
        }
    }

    private fun observePinnedMessages() {
        viewModelScope.launch {
            messageRepository.observePinnedMessages(chatId).collect { pinned ->
                _uiState.value = _uiState.value.copy(pinnedMessages = pinned)
            }
        }
    }

    private fun markAsRead() {
        viewModelScope.launch { messageRepository.markChatAsRead(chatId) }
        // Пользователь открыл чат — накопленная для rich-уведомления история больше не нужна,
        // и само уведомление (если ещё висит в шторке) можно убрать.
        viewModelScope.launch { notificationMessageStore.clear(chatId) }
        NotificationHelper.cancelNotification(appContext, chatId)
    }

    fun onInputTextChanged(text: String) {
        saveDraft(text)
        typingResetJob?.cancel()
        if (text.isNotBlank()) {
            if (!isCurrentlyMarkedTyping) {
                isCurrentlyMarkedTyping = true
                viewModelScope.launch { presenceRepository.setTyping(chatId, true) }
            }
            typingResetJob = viewModelScope.launch {
                delay(3000)
                isCurrentlyMarkedTyping = false
                presenceRepository.setTyping(chatId, false)
            }
        } else {
            clearTypingStatus()
        }
    }

    private fun clearTypingStatus() {
        typingResetJob?.cancel()
        if (isCurrentlyMarkedTyping) {
            isCurrentlyMarkedTyping = false
            viewModelScope.launch { presenceRepository.setTyping(chatId, false) }
        }
    }

    fun setReplyingTo(message: Message?) {
        _uiState.value = _uiState.value.copy(replyingTo = message, editingMessage = null)
    }

    fun setEditingMessage(message: Message?) {
        _uiState.value = _uiState.value.copy(editingMessage = message, replyingTo = null)
    }

    // НОВОЕ (переработка исчезающих сообщений, per-message как в Telegram):
    // - hasExplicitTtl = false -> пользователь не трогал иконку часов для этого сообщения,
    //   используется TTL чата по умолчанию (disappearingTtlSeconds из настроек чата).
    // - hasExplicitTtl = true, explicitTtlSeconds = null -> пользователь явно ВЫКЛЮЧИЛ
    //   исчезание для этого конкретного сообщения (даже если в чате TTL по умолчанию включён).
    // - hasExplicitTtl = true, explicitTtlSeconds = N -> пользователь явно выбрал таймер N сек.
    fun sendMessage(text: String, explicitTtlSeconds: Long? = null, hasExplicitTtl: Boolean = false, silent: Boolean = false) {
        if (text.isBlank()) return
        clearTypingStatus()
        viewModelScope.launch { draftsPreferences.clearDraft(chatId) }
        val editing = _uiState.value.editingMessage
        if (editing != null) {
            _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null, editingMessage = null)
            viewModelScope.launch {
                when (val result = messageRepository.editMessage(chatId, editing.id, text)) {
                    is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                    is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
                }
            }
            return
        }
        val replying = _uiState.value.replyingTo
        val replyContext = replying?.let {
            ReplyContext(
                messageId = it.id,
                senderName = if (it.senderId == currentUserId) "Вы" else _uiState.value.chatTitle,
                text = it.previewText()
            )
        }
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null, replyingTo = null)
        viewModelScope.launch {
            when (val result = messageRepository.sendMessage(
                chatId, text, replyContext,
                hasTtlOverride = hasExplicitTtl, ttlOverrideSeconds = explicitTtlSeconds,
                silent = silent
            )) {
                is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
            }
        }
    }

    // НОВОЕ (картинки из буфера + подпись): передаём необязательную подпись к фото.
    fun sendImage(base64: String, caption: String = "", isViewOnce: Boolean = false) {
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = messageRepository.sendImageMessage(chatId, base64, caption = caption, isViewOnce = isViewOnce)) {
                is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
            }
        }
    }

    // НОВОЕ (одноразовые ��едиа): вызывается экраном сразу после полноэкранного показа
    // view-once фото — стирает imageBase64 на сервере, чтобы повторно открыть было нельзя.
    fun markViewOnceImageOpened(messageId: String) {
        viewModelScope.launch {
            messageRepository.markViewOnceImageOpened(chatId, messageId)
        }
    }

    // НОВОЕ (детектор скриншотов): если во время просмотра view-once фото обнаружен
    // скриншот (см. ScreenshotDetector — сработает, только если получатель как-то обошёл
    // FLAG_SECURE), отправляем в чат обычное текстовое сообщение-уведомление от лица
    // того, кто сделал скриншот — так его увидит автор фото (и остальные участники чата).
    fun notifyScreenshotTaken() {
        viewModelScope.launch {
            messageRepository.sendMessage(chatId, "📸 Сделал(а) скриншот фото «на один просмотр»")
        }
    }

    // НОВОЕ (п.37): отправка записанного голосового сообщения.
    fun sendVoice(base64: String, durationMs: Long) {
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = messageRepository.sendVoiceMessage(chatId, base64, durationMs)) {
                is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
            }
        }
    }

    // НОВОЕ: отправка файлового вложения — base64 + метаданные уже подготовлены
    // на уровне UI (см. FileUtils.prepareFileForSending), здесь только пересылка в репозиторий.
    fun sendFile(base64: String, fileName: String, mimeType: String, sizeBytes: Long) {
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = messageRepository.sendFileMessage(chatId, base64, fileName, mimeType, sizeBytes)) {
                is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
            }
        }
    }

    // НОВОЕ: отправка геолокации — точки на карте.
    fun sendLocation(lat: Double, lng: Double) {
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = messageRepository.sendLocationMessage(chatId, lat, lng)) {
                is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
            }
        }
    }

    // НОВОЕ (расширенные опросы): создание опроса из диалога PollCreationDialog.
    // closesAtMillis приходит только если у пользователя включена настройка
    // "Расширенные опросы" (userSettingsPreferences.advancedPollsEnabled) — обычный
    // опрос всегда бессрочный, пока его не закроют вручную.
    fun sendPoll(
        question: String,
        options: List<String>,
        isAnonymous: Boolean,
        allowMultipleAnswers: Boolean,
        closesAtMillis: Long? = null,
        isQuiz: Boolean = false,
        correctOptionIndex: Int? = null,
        explanation: String? = null
    ) {
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = messageRepository.sendPollMessage(
                chatId, question, options, isAnonymous, allowMultipleAnswers, closesAtMillis,
                isQuiz, correctOptionIndex, explanation
            )) {
                is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
            }
        }
    }

    // НОВОЕ (расширенные опросы): голос/переголосование по варианту ответа.
    fun voteOnPoll(messageId: String, optionIndex: Int) {
        viewModelScope.launch {
            when (val result = messageRepository.voteOnPoll(chatId, messageId, optionIndex)) {
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
                else -> {}
            }
        }
    }

    // НОВОЕ (расширенные опросы): досрочное закрытие опроса.
    fun closePoll(messageId: String) {
        viewModelScope.launch {
            when (val result = messageRepository.closePoll(chatId, messageId)) {
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
                else -> {}
            }
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            when (val result = messageRepository.deleteMessage(chatId, message.id)) {
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
                else -> {}
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch { messageRepository.toggleReaction(chatId, messageId, emoji) }
    }

    // НОВОЕ (F3 статистика постов): отмечаем просмотр поста канала при показе на экране.
    // Уникальность по uid гарантируется в репозитории (viewedBy).
    fun registerPostView(messageId: String) {
        if (_uiState.value.chatType != "CHANNEL") return
        viewModelScope.launch { messageRepository.registerPostView(chatId, messageId) }
    }

    fun togglePinMessage(messageId: String) {
        viewModelScope.launch { messageRepository.togglePinMessage(chatId, messageId) }
    }

    fun saveToFavorite(message: Message) {
        viewModelScope.launch {
            try {
                val savedChatId = chatRepository.getOrCreateSavedChat()
                val myName = firebaseAuth.currentUser?.displayName
                    ?.takeIf { it.isNotBlank() } ?: "Вы"
                val myUid = currentUserId ?: return@launch
                messageRepository.forwardMessage(savedChatId, message, myName, myUid)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Не удалось сохранить в Избранное")
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            try { chatRepository.clearChatHistory(chatId) }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(errorMessage = e.toUserMessage("Не удалось очистить")) }
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            try { chatRepository.deleteChat(chatId) }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(errorMessage = e.toUserMessage("Не удалось удалить")) }
        }
    }

    fun exportChat(context: Context) {
        viewModelScope.launch {
            val text = messageRepository.exportChatHistory(chatId)
            val file = File(context.cacheDir, "yodo_chat_export.txt")
            file.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Экспорт чата"))
        }
    }

    fun toggleSearch() {
        _uiState.value = _uiState.value.copy(isSearchActive = !_uiState.value.isSearchActive, searchQuery = "")
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    // НОВОЕ: подписка/отписка от канала. Оптимистично обновляем UI сразу,
    // список чатов на экране ChatList и так обновится через основной listener.
    // Владелец канала не может отписаться от собственного канала — иначе он
    // потеряет к нему доступ; для владельца доступно только удаление канала целиком.
    fun toggleChannelSubscription() {
        if (_uiState.value.isChannelOwner) return
        val subscribed = _uiState.value.isSubscribed
        _uiState.value = _uiState.value.copy(
            isSubscribed = !subscribed,
            subscriberCount = (_uiState.value.subscriberCount + if (subscribed) -1 else 1).coerceAtLeast(0)
        )
        viewModelScope.launch {
            if (subscribed) chatRepository.unsubscribeFromChannel(chatId)
            else chatRepository.subscribeToChannel(chatId)
        }
    }

    // НОВОЕ: владелец удаляет канал полностью (для всех подписчиков), не просто выходит из него.
    fun deleteChannel() {
        if (!_uiState.value.isChannelOwner) return
        viewModelScope.launch {
            chatRepository.deleteChannel(chatId)
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        if (isCurrentlyMarkedTyping) {
            viewModelScope.launch { presenceRepository.setTyping(chatId, false) }
        }
    }
}