package app.yodo.messenger.domain.model

enum class ChatType { PRIVATE, GROUP, CHANNEL }

data class ChatPreview(
    val chatId: String,
    val title: String,
    val username: String? = null,
    val avatarUrl: String?,
    val avatarBase64: String? = null,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val lastMessageSenderId: String? = null,
    val lastMessageStatus: String? = null,
    val unreadCount: Int,
    val isOnline: Boolean,
    // п.30: верифицированный официальный канал — синяя галочка
    val isVerified: Boolean = false,
    // п.28: время последнего визита для "был(а) N мин назад"
    val lastSeenMillis: Long = 0,
    val type: ChatType,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val otherUserId: String? = null,
    // НОВОЕ (п.39): локальный черновик недописанного сообщения для этого чата
    // (хранится на устройстве в DraftsPreferences, не в Firestore).
    // Если не пустой — в списке чатов вместо lastMessage показываем "Черновик: ...".
    val draftText: String = "",
    // НОВОЕ (п.38): включены ли исчезающие сообщения в этом чате (для иконки-таймера в списке).
    val disappearingTtlSeconds: Long? = null,
    // НОВОЕ: количество подписчиков канала (актуально только для type == CHANNEL).
    val subscriberCount: Int = 0,
    // НОВОЕ (архивация чатов): чат скрыт из основного списка и показывается в "Архиве".
    val isArchived: Boolean = false,
    // НОВОЕ (админ-функции групп): число ожидающих заявок на вступление —
    // заполняется только для групп, которыми управляет текущий пользователь
    // (владелец/админ); для остальных чатов всегда 0.
    val pendingRequestsCount: Int = 0,
    // НОВОЕ (превью шифрованных чатов): открытый текст последнего сообщения
    // для E2EE-личных чатов (поле lastMessagePlain в документе чата). Сам
    // документ сообщения остаётся зашифрованным — открытым остаётся только
    // короткое превью для списка чатов. Для обычных чатов совпадает с lastMessage.
    val isEncryptedPreview: Boolean = false
)
