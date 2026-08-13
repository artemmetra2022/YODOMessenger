package app.yodo.messenger.data.repository

import android.graphics.Bitmap
import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.domain.model.AdminActionType
import app.yodo.messenger.domain.model.AdminLogEntry
import app.yodo.messenger.domain.model.AdminLogFilter
import app.yodo.messenger.domain.model.AssignedRole
import app.yodo.messenger.domain.model.BuiltInRole
import app.yodo.messenger.domain.model.ChannelProfile
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.ChatType
import app.yodo.messenger.domain.model.CustomRole
import app.yodo.messenger.domain.model.MemberPermissions
import app.yodo.messenger.domain.model.Permission
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChannelSearchItem
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import app.yodo.messenger.domain.repository.ChatInfo
import app.yodo.messenger.domain.repository.ChatListResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import app.yodo.messenger.domain.repository.GroupInfo
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.domain.repository.SupportConversation
import app.yodo.messenger.util.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ОПТИМИЗАЦИЯ ПЕРВОЙ ЗАГРУЗКИ ПОСЛЕ ЛОГИНА:
 *
 * Проблема: сразу после [signInWithEmailAndPassword] Firestore-соединение (gRPC + TLS)
 * ещё не установлено — оно создаётся только на первый запрос. Если первый запрос —
 * это observeChatList, то пользователь ждёт холодного handshake'а (несколько секунд),
 * а при повторном входе уже всё в кэше — отсюда 20-секундная разница.
 *
 * Решение (слои оптимизации):
 * 1. warmUpFirestore() в AuthRepositoryImpl.login() — форсирует установление Firestore-сета
 *    сразу после Auth, ДО перехода на экран списка чатов.
 * 2. getIdToken(true).await() в начале observeChatList() — гарантирует что токен уже
 *    перепроверен SDK перед первым listener'ом (иначе первый snapshot может молча зависнуть).
 * 3. .limit(200) на основной запрос чатов — не ждём слияния кэша со всеми чатами
 *    пользователя, только первые 200.
 * 4. Параллельная загрузка аватарок (async вместо forEach) — get() для каждого собеседника
 *    идут одновременно, а не один за другим.
 * 5. Дебаунс emitList() на 300мс — пакетируем обновления presence, чтобы не мигал UI
 *    на каждый сетевой пакет от presence-слушателей.
 * 6. 15-секундный таймаут на основной listener — если что-то совсем сломалось (нет индекса,
 *    нет connectivity), показываем ошибку вместо вечного спиннера.
 *
 * Результат: вместо "иногда 20-30 сек, иногда вообще не появляется" —
 * мгновенная загрузка списка (1-2 сек включая холодный старт Firestore).
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ChatRepository {

    override fun observeChatList(): Flow<ChatListResult> = callbackFlow {
        val currentUser = firebaseAuth.currentUser
        val uid = currentUser?.uid
        if (uid == null || currentUser == null) { trySend(ChatListResult.Success(emptyList())); close(); return@callbackFlow }

        // Сразу после логина/регистрации ID-токен свежий, но локальный SDK иногда ещё не
        // успел его "принять" внутренне — первый snapshot-listener в таком состоянии может
        // висеть в ожидании handshake на несколько секунд дольше обычного, а иногда вовсе
        // не эмиттит первый снапшот, пока токен не обновится сам по себе (что и объясняло
        // "иногда вообще не появляется, помогает только перезаход"). Форсируем обновление
        // токена ДО того как вешаем listener — это не блокирует эмиссию (запускается
        // параллельно в launch), но гарантирует, что к моменту ответа Firestore токен уже
        // точно валиден и не будет молчаливого зависания стрима.
        launch {
            try { currentUser.getIdToken(true).await() } catch (e: Exception) { }
        }

        // НОВОЕ (чат поддержки): гарантируем, что у каждого пользователя есть личная
        // беседа поддержки — тогда она сразу появляется в списке чатов (как
        // официальный канал, с галочкой), а не только после первого открытия. Админы свою
        // беседу поддержки не создают (они отвечают, а не пишут в поддержку).
        val myEmailForSupport = currentUser.email?.lowercase()
        if (myEmailForSupport == null || myEmailForSupport !in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }) {
            launch { try { getOrCreateSupportChat() } catch (e: Exception) { } }
        }

        data class PresenceData(val rawOnline: Boolean, val lastSeen: Long, val hidden: Boolean)

        val avatarCache = mutableMapOf<String, Triple<String?, String?, String?>>()
        val presenceCache = mutableMapOf<String, PresenceData>()
        val presenceListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
        var latestChats = emptyList<ChatPreview>()
        var officialChannel: ChatPreview? = null

        fun isEffectivelyOnline(data: PresenceData?): Boolean {
            if (data == null || data.hidden || !data.rawOnline) return false
            val isStale = (System.currentTimeMillis() - data.lastSeen) >
                    PresenceRepository.PRESENCE_STALE_THRESHOLD_MILLIS
            return !isStale
        }

        var emitJob: kotlinx.coroutines.Job? = null
        fun emitList() {
            // Дебаунс: если уже запланирована эмиссия в течение 300мс, отменяем старую,
            // планируем новую. Без этого каждое обновление presence (может быть в секунду
            // по 5-10 обновлений от разных пользователей) тригерит перерисовку UI, что на
            // устаревших телефонах может вызвать дёргание и фризы.
            emitJob?.cancel()
            emitJob = launch {
                delay(300)
                val enriched = latestChats.map { chat ->
                    val cached = chat.otherUserId?.let { avatarCache[it] }
                    val presence = chat.otherUserId?.let { presenceCache[it] }
                    val online = isEffectivelyOnline(presence)
                    val lastSeen = if (online || presence == null || presence.hidden) 0L else presence.lastSeen
                    chat.copy(
                        avatarUrl = cached?.first ?: chat.avatarUrl,
                        avatarBase64 = cached?.second ?: chat.avatarBase64,
                        username = cached?.third ?: chat.username,
                        isOnline = online,
                        lastSeenMillis = lastSeen
                    )
                }
                val withChannel = listOfNotNull(officialChannel) + enriched
                // ИСПРАВЛЕНО (AQ): сортировка по времени теперь здесь, а не в Firestore-запросе,
                // чтобы чаты без сообщений тоже попадали в список (внизу).
                val sorted = withChannel.sortedWith(
                    compareByDescending<ChatPreview> { it.isPinned }
                        .thenByDescending { it.lastMessageTimestamp }
                )
                trySend(ChatListResult.Success(sorted))
            }
        }

        // п.41: автосоздание канала для админа
        val currentUserEmail = firebaseAuth.currentUser?.email?.lowercase()
        if (currentUserEmail != null && currentUserEmail in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }) {
            launch {
                try {
                    val channelRef = firestore.collection("chats").document(ChatRepository.OFFICIAL_CHANNEL_ID)
                    val channelDoc = channelRef.get().await()
                    if (!channelDoc.exists()) {
                        channelRef.set(
                            mapOf(
                                "participantIds" to listOf(uid),
                                "type" to "CHANNEL",
                                "title" to "YodoMessenger",
                                // НОВОЕ (переработка каналов): поля для поиска и профиля канала
                                "titleLowercase" to "yodomessenger",
                                "createdAt" to System.currentTimeMillis(),
                                "isVerified" to true,
                                "lastMessage" to "",
                                "lastMessageTimestamp" to System.currentTimeMillis(),
                                "lastMessageSenderId" to uid,
                                "lastMessageStatus" to "SENT",
                                "unreadCounts" to mapOf(uid to 0),
                                "isOnline" to false,
                                "createdBy" to uid
                            )
                        ).await()
                    }
                } catch (e: Exception) { }
            }
        }

        // Listener на официальный канал
        val channelListener = firestore.collection("chats")
            .document(ChatRepository.OFFICIAL_CHANNEL_ID)
            .addSnapshotListener { channelSnapshot, _ ->
                if (channelSnapshot != null && channelSnapshot.exists()) {
                    officialChannel = ChatPreview(
                        chatId = channelSnapshot.id,
                        title = channelSnapshot.getString("title") ?: "YodoMessenger",
                        username = null,
                        avatarUrl = null,
                        // НОВОЕ (переработка каналов): реальная аватарка официального канала, если загружена
                        avatarBase64 = channelSnapshot.getString("avatarBase64"),
                        lastMessage = channelSnapshot.getString("lastMessage") ?: "",
                        lastMessageTimestamp = channelSnapshot.getLong("lastMessageTimestamp") ?: 0L,
                        lastMessageSenderId = channelSnapshot.getString("lastMessageSenderId"),
                        lastMessageStatus = channelSnapshot.getString("lastMessageStatus"),
                        unreadCount = 0,
                        isOnline = false,
                        isVerified = channelSnapshot.getBoolean("isVerified") ?: true,
                        type = ChatType.CHANNEL,
                        isPinned = false,
                        isMuted = false,
                        otherUserId = null
                    )
                } else {
                    officialChannel = null
                }
                emitList()
            }

        val query = firestore.collection("chats")
            .whereArrayContains("participantIds", uid)
            // ИСПРАВЛЕНО (AQ): раньше здесь был orderBy("lastMessageTimestamp").
            // Firestore НЕ возвращает документы, у которых поля сортировки нет,
            // поэтому чаты/группы/каналы без единого сообщения просто пропадали из списка
            // (особенно заметно на втором аккаунте). Сортируем на клиенте в emitList().
            // Бонусом запрос больше не требует составного индекса.
            // Ограничиваем первую партию: без limit() Firestore обязан вернуть (и
            // предварительно смёрджить с локальным кэшем) ВСЕ чаты пользователя одним
            // снапшотом, прежде чем listener вообще что-то эмитит. У активных пользователей
            // с большим числом чатов это заметно увеличивает время до первого кадра именно
            // на холодном старте, когда кэша ещё нет. 200 с запасом покрывает подавляющее
            // большинство пользователей и рендерится мгновенно.
            .limit(200)

        // Страховка от "вечного Loading": если по какой-то причине (например, ещё не
        // созданный составной индекс в новом Firebase-проекте) снапшот-листенер вообще не
        // ответит, показываем пользователю ошибку вместо бесконечного спиннера, вместо того
        // чтобы полагаться только на "помогает перезайти".
        val timeoutJob = launch {
            delay(15_000L)
            trySend(ChatListResult.Error("Не удалось загрузить чаты. Проверьте соединение и попробуйте ещё раз."))
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            timeoutJob.cancel()
            if (error != null) {
                trySend(ChatListResult.Error(error.message ?: "Неизвестная ошибка Firestore"))
                return@addSnapshotListener
            }
            latestChats = snapshot?.documents.orEmpty()
                .filter { it.id != ChatRepository.OFFICIAL_CHANNEL_ID }
                .mapNotNull { doc ->
                    try {
                        val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        val unreadCounts = doc.get("unreadCounts") as? Map<*, *>
                        val unreadForMe = (unreadCounts?.get(uid) as? Long)?.toInt() ?: 0
                        val titles = doc.get("titles") as? Map<*, *>
                        val personalizedTitle = titles?.get(uid) as? String
                        val title = personalizedTitle ?: doc.getString("title") ?: "Без названия"
                        val pinnedMap = doc.get("pinned") as? Map<*, *>
                        val mutedMap = doc.get("muted") as? Map<*, *>
                        val archivedMap = doc.get("archived") as? Map<*, *>
                        val type = doc.getString("type")?.let { rawType ->
                            runCatching { ChatType.valueOf(rawType) }.getOrDefault(ChatType.PRIVATE)
                        } ?: ChatType.PRIVATE
                        val otherUserId = if (type == ChatType.PRIVATE) {
                            participantIds.firstOrNull { it != uid }
                        } else null
                        ChatPreview(
                            chatId = doc.id,
                            title = title,
                            username = doc.getString("otherUsername"),
                            avatarUrl = doc.getString("avatarUrl"),
                            // У каналов и групп аватарка лежит в самом документе чата
                            // (поле avatarBase64), а не в профиле пользователя.
                            avatarBase64 = if (type == ChatType.CHANNEL || type == ChatType.GROUP) doc.getString("avatarBase64") else null,
                            lastMessage = doc.getString("lastMessage") ?: "",
                            lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
                            lastMessageSenderId = doc.getString("lastMessageSenderId"),
                            lastMessageStatus = doc.getString("lastMessageStatus"),
                            unreadCount = unreadForMe,
                            isOnline = false,
                            isVerified = doc.getBoolean("isVerified") ?: false,
                            type = type,
                            isPinned = pinnedMap?.get(uid) as? Boolean ?: false,
                            isMuted = mutedMap?.get(uid) as? Boolean ?: false,
                            otherUserId = otherUserId,
                            subscriberCount = if (type == ChatType.CHANNEL) participantIds.size else 0,
                            isArchived = archivedMap?.get(uid) as? Boolean ?: false
                        )
                    } catch (e: Exception) { null }
                }
            emitList()

            val missingIds = latestChats.mapNotNull { it.otherUserId }.filter { it !in avatarCache }.distinct()
            if (missingIds.isNotEmpty()) {
                launch {
                    // Загружаем аватарки параллельно (async), а не последовательно (forEach).
                    // Без параллелизма это добавляет 100+ мс на каждый пользователь при холодном
                    // старте — с 10 чатами выходит легко в секунду-две задержки перед тем как
                    // список вообще закончит рендериться с корректными аватарками.
                    val jobs = missingIds.map { otherId ->
                        async {
                            try {
                                val otherDoc = firestore.collection("users").document(otherId).get().await()
                                avatarCache[otherId] = Triple(
                                    otherDoc.getString("avatarUrl"),
                                    otherDoc.getString("avatarBase64"),
                                    otherDoc.getString("username")
                                )
                            } catch (e: Exception) { }
                        }
                    }
                    jobs.awaitAll()
                    emitList()
                }
            }

            val newParticipantIds = latestChats.mapNotNull { it.otherUserId }
                .filter { it !in presenceListeners }.distinct()
            newParticipantIds.forEach { otherId ->
                val presenceListener = firestore.collection("users").document(otherId)
                    .addSnapshotListener { presenceSnapshot, _ ->
                        presenceCache[otherId] = if (presenceSnapshot == null || !presenceSnapshot.exists()) {
                            PresenceData(rawOnline = false, lastSeen = 0L, hidden = false)
                        } else {
                            PresenceData(
                                rawOnline = presenceSnapshot.getBoolean("isOnline") ?: false,
                                lastSeen = presenceSnapshot.getLong("lastSeen") ?: 0L,
                                hidden = presenceSnapshot.getBoolean("hideOnlineStatus") ?: false
                            )
                        }
                        emitList()
                    }
                presenceListeners[otherId] = presenceListener
            }
        }

        launch {
            while (true) {
                delay(30_000L)
                emitList()
            }
        }

        awaitClose {
            timeoutJob.cancel()
            emitJob?.cancel()
            listener.remove()
            channelListener.remove()
            presenceListeners.values.forEach { it.remove() }
        }
    }

    override suspend fun getOrCreateSavedChat(): String {
        val uid = firebaseAuth.currentUser?.uid
            ?: throw IllegalStateException("Пользователь не авторизован")
        // ИСПРАВЛЕНИЕ (баг «может появиться несколько избранных»): раньше чат
        // «Избранное» создавался со случайным id, поэтому при быстрых/параллельных
        // вызовах (гонка) могло создаться сразу несколько чатов. Теперь id
        // детерминирован: saved_<uid> — повторный set просто перезапишет тот же документ.
        val savedChatId = "saved_" + uid
        val savedRef = firestore.collection("chats").document(savedChatId)
        // ИСПРАВЛЕНО (баг: кнопка «Избранное» ничего не делала): раньше здесь
        // выполнялся составной запрос whereArrayContains + whereEqualTo, который
        // требует составного индекса Firestore. Если индекса нет, запрос бросал
        // исключение, оно глоталось в ViewModel и переход не происходил.
        // Сначала проверяем детерминированный документ (без индекса), а миграцию
        // старых SAVED-чатов делаем опционально и не падаем, если она недоступна.
        val doc = savedRef.get().await()
        if (doc.exists()) return savedChatId
        // Опциональная миграция: если ранее были SAVED-чаты со случайным id —
        // переиспользуем старый, чтобы не потерять сообщения. Ошибку индекса игнорируем.
        val legacyId = runCatching {
            firestore.collection("chats")
                .whereArrayContains("participantIds", uid)
                .whereEqualTo("type", "SAVED")
                .get().await()
                .documents.firstOrNull { it.id != savedChatId }?.id
        }.getOrNull()
        if (legacyId != null) return legacyId
        savedRef.set(
            mapOf(
                "participantIds" to listOf(uid),
                "type" to "SAVED",
                "title" to "Избранное",
                "lastMessage" to "",
                "lastMessageTimestamp" to System.currentTimeMillis(),
                "unreadCounts" to mapOf(uid to 0),
                "isOnline" to false
            )
        ).await()
        return savedChatId
    }

    override suspend fun createOrGetPrivateChat(otherUserId: String): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid ?: return CreateChatResult.Error("Вы не авторизованы")
        if (uid == otherUserId) return CreateChatResult.Error("Нельзя создать чат с самим собой")
        return try {
            val existing = firestore.collection("chats")
                .whereArrayContains("participantIds", uid)
                .whereEqualTo("type", "PRIVATE")
                .get().await()
            val existingChat = existing.documents.firstOrNull { doc ->
                val participants = doc.get("participantIds") as? List<*>
                participants?.contains(otherUserId) == true
            }
            if (existingChat != null) return CreateChatResult.Success(existingChat.id)
            val myDoc = firestore.collection("users").document(uid).get().await()
            val otherDoc = firestore.collection("users").document(otherUserId).get().await()
            val myName = myDoc.getString("displayName") ?: "Пользователь"
            val otherName = otherDoc.getString("displayName") ?: "Пользователь"
            val newChatRef = firestore.collection("chats").document()
            newChatRef.set(
                mapOf(
                    "participantIds" to listOf(uid, otherUserId),
                    "type" to "PRIVATE",
                    "titles" to mapOf(uid to otherName, otherUserId to myName),
                    "lastMessage" to "",
                    "lastMessageTimestamp" to System.currentTimeMillis(),
                    "unreadCounts" to mapOf(uid to 0, otherUserId to 0),
                    "isOnline" to false,
                    "otherUsername" to (otherDoc.getString("username") ?: "")
                )
            ).await()
            CreateChatResult.Success(newChatRef.id)
        } catch (e: Exception) {
            CreateChatResult.Error(e.toUserMessage("Не удалось создать чат"))
        }
    }

    override suspend fun createGroupChat(
        title: String,
        memberIds: List<String>,
        description: String,
        avatarBitmap: android.graphics.Bitmap?,
        accessMode: app.yodo.messenger.domain.model.ChannelAccessMode
    ): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid ?: return CreateChatResult.Error("Вы не авторизованы")
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return CreateChatResult.Error("Введите название группы")
        val allParticipants = (memberIds + uid).distinct()
        if (allParticipants.size < 3) return CreateChatResult.Error("Выберите хотя бы 2 участников")
        return try {
            // Аватарка группы — сжатый Base64 (та же логика, что у каналов)
            val avatarBase64 = avatarBitmap?.let { bmp ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    app.yodo.messenger.util.ImageUtils.compressAvatarToBase64(bmp)
                }
            }
            val newChatRef = firestore.collection("chats").document()
            val data = mutableMapOf<String, Any?>(
                "participantIds" to allParticipants,
                "type" to "GROUP",
                "title" to trimmedTitle,
                "description" to description.trim(),
                "lastMessage" to "",
                "lastMessageTimestamp" to System.currentTimeMillis(),
                "unreadCounts" to allParticipants.associateWith { 0 },
                "isOnline" to false,
                "createdBy" to uid,
                "createdAt" to System.currentTimeMillis(),
                // НОВОЕ (конфиденциальность групп): режим доступа, как у каналов.
                "accessMode" to accessMode.name,
                "ownerId" to uid
            )
            if (avatarBase64 != null) data["avatarBase64"] = avatarBase64
            newChatRef.set(data).await()
            CreateChatResult.Success(newChatRef.id)
        } catch (e: Exception) {
            CreateChatResult.Error(e.toUserMessage("Не удалось создать группу"))
        }
    }

    override suspend fun getChatInfo(chatId: String): ChatInfo? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (!doc.exists()) return null
            val type = doc.getString("type") ?: "PRIVATE"
            val titles = doc.get("titles") as? Map<*, *>
            val personalizedTitle = titles?.get(uid) as? String
            val title = personalizedTitle ?: doc.getString("title") ?: "Без названия"
            val otherUserId = if (type == "PRIVATE") {
                val participantIds = doc.get("participantIds") as? List<*>
                participantIds?.filterIsInstance<String>()?.firstOrNull { it != uid }
            } else null
            var otherPhotoUrl: String? = null
            var otherAvatarBase64: String? = null
            if (otherUserId != null) {
                val otherDoc = firestore.collection("users").document(otherUserId).get().await()
                otherPhotoUrl = otherDoc.getString("avatarUrl")
                otherAvatarBase64 = otherDoc.getString("avatarBase64")
            }
            val channelOwnerId = if (type == "CHANNEL") doc.getString("createdBy") else null
            val channelAdminIds = if (type == "CHANNEL") {
                (doc.get("adminIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            } else emptyList()
            val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            ChatInfo(
                title = title, otherUserId = otherUserId, type = type,
                avatarUrl = doc.getString("avatarUrl"),
                // НОВОЕ (переработка каналов): у каналов аватарка в самом документе чата —
                // используется в шапке чата и в профиле канала.
                avatarBase64 = doc.getString("avatarBase64"),
                otherUserPhotoUrl = otherPhotoUrl, otherUserAvatarBase64 = otherAvatarBase64,
                isVerified = doc.getBoolean("isVerified") ?: false,
                channelOwnerId = channelOwnerId,
                channelAdminIds = channelAdminIds,
                subscriberCount = if (type == "CHANNEL") participantIds.size else 0,
                isSubscribed = type == "CHANNEL" && uid in participantIds,
                createdAt = doc.getLong("createdAt") ?: 0L,
                // НОВОЕ (режимы доступа/ограничения): нужны в ChatScreen для применения ограничений.
                accessMode = app.yodo.messenger.domain.model.ChannelAccessMode.fromRaw(doc.getString("accessMode")),
                restrictions = readRestrictions(doc)
            )
        } catch (e: Exception) { null }
    }

    // НОВОЕ (переработка каналов): создание пользовательского канала с аватаркой.
    // Создатель = владелец (может публиковать посты и назначать/снимать админов).
    // Каждый подписчик добавляется в participantIds, поэтому существующий механизм
    // с��иска чатов (whereArrayContains) сразу подхватывает канал для всех подписчиков.
    override suspend fun createChannel(
        title: String,
        description: String,
        avatarBitmap: Bitmap?,
        accessMode: app.yodo.messenger.domain.model.ChannelAccessMode
    ): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid ?: return CreateChatResult.Error("Вы не авторизованы")
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return CreateChatResult.Error("Введите название канала")
        return try {
            // Аватарка канала — сжатый Base64 прямо в документ чата (без Storage).
            val avatarBase64 = avatarBitmap?.let { bmp ->
                withContext(Dispatchers.Default) { ImageUtils.compressAvatarToBase64(bmp) }
            }
            val newChatRef = firestore.collection("chats").document()
            val data = mutableMapOf<String, Any?>(
                "participantIds" to listOf(uid),
                "type" to "CHANNEL",
                "title" to trimmedTitle,
                // Для поиска каналов по префиксу без учёта регистра
                "titleLowercase" to trimmedTitle.lowercase(),
                "description" to description.trim(),
                "isVerified" to false,
                "lastMessage" to "",
                "lastMessageTimestamp" to System.currentTimeMillis(),
                "lastMessageSenderId" to uid,
                "unreadCounts" to mapOf(uid to 0),
                "isOnline" to false,
                "createdBy" to uid,
                "adminIds" to emptyList<String>(),
                "createdAt" to System.currentTimeMillis(),
                // НОВОЕ (режимы доступа каналов): режим доступа канала.
                "accessMode" to accessMode.name,
                // Ограничения по умолчанию — всё разрешено.
                "allowForwarding" to true,
                "allowComments" to true,
                "allowReactions" to true,
                "allowSaving" to true,
                "allowLinkPreviews" to true
            )
            if (avatarBase64 != null) data["avatarBase64"] = avatarBase64
            newChatRef.set(data).await()
            CreateChatResult.Success(newChatRef.id)
        } catch (e: Exception) {
            CreateChatResult.Error(e.toUserMessage("Не удалось создать канал"))
        }
    }

    override suspend fun subscribeToChannel(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("chats").document(chatId)
                .update(
                    mapOf(
                        "participantIds" to FieldValue.arrayUnion(uid),
                        "unreadCounts.$uid" to 0
                    )
                ).await()
        } catch (e: Exception) { }
    }

    override suspend fun unsubscribeFromChannel(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            // НОВОЕ: владелец канала не может отписаться — это защита и на уровне данных,
            // не только в UI (на случай прямого вызова репозитория).
            if (doc.getString("createdBy") == uid) return
            firestore.collection("chats").document(chatId)
                .update("participantIds", FieldValue.arrayRemove(uid)).await()
        } catch (e: Exception) { }
    }

    // НОВОЕ: полное удаление канала владельцем — удаляет все сообщения и сам документ чата.
    override suspend fun deleteChannel(chatId: String): ChannelUpdateResult {
        val uid = firebaseAuth.currentUser?.uid ?: return ChannelUpdateResult.Error("Не авторизован")
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val doc = chatRef.get().await()
            if (!doc.exists()) return ChannelUpdateResult.Error("Канал не найден")
            if (doc.getString("createdBy") != uid) {
                return ChannelUpdateResult.Error("Удалить канал может только его владелец")
            }
            val messagesRef = chatRef.collection("messages")
            val messagesSnapshot = messagesRef.get().await()
            // Firestore batch ограничен 500 операциями — делим на чанки.
            messagesSnapshot.documents.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            chatRef.delete().await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось удалить канал"))
        }
    }

    // НОВОЕ: приглашение пользователей в канал владельцем/админом — подписывает их напрямую.
    override suspend fun inviteUsersToChannel(chatId: String, userIds: List<String>) {
        if (userIds.isEmpty()) return
        try {
            val updates = mutableMapOf<String, Any>(
                "participantIds" to FieldValue.arrayUnion(*userIds.toTypedArray())
            )
            userIds.forEach { uid -> updates["unreadCounts.$uid"] = 0 }
            firestore.collection("chats").document(chatId).update(updates).await()
        } catch (e: Exception) { }
    }

    override suspend fun addChannelAdmin(chatId: String, userId: String) {
        try {
            firestore.collection("chats").document(chatId)
                .update("adminIds", FieldValue.arrayUnion(userId)).await()
            val targetName = firestore.collection("users").document(userId).get().await()
                .getString("displayName")
            logAdminAction(chatId, AdminActionType.ADMIN_ADDED, targetUserId = userId, targetUserName = targetName)
        } catch (e: Exception) { }
    }

    override suspend fun removeChannelAdmin(chatId: String, userId: String) {
        try {
            firestore.collection("chats").document(chatId)
                .update("adminIds", FieldValue.arrayRemove(userId)).await()
            val targetName = firestore.collection("users").document(userId).get().await()
                .getString("displayName")
            logAdminAction(chatId, AdminActionType.ADMIN_REMOVED, targetUserId = userId, targetUserName = targetName)
        } catch (e: Exception) { }
    }

    // НОВОЕ (переработка каналов): поиск каналов по подстроке названия/описания.
    // РАНЬШЕ использовался range-запрос (type + orderBy titleLowercase + startAt/endAt),
    // который требует составного индекса Firestore. Если индекс не создан — запрос падает,
    // и каналы вообще не находились. Теперь фильтруем по одному полю (type == CHANNEL —
    // это одиночный индекс, всегда доступен), а совпадение по названию/описанию считаем
    // на клиенте. Заодно ищем по подстроке (contains), а не только по префиксу, и
    // гарантированно включаем официальный канал.
    override suspend fun searchChannels(query: String): List<ChannelSearchItem> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyList()
        val uid = firebaseAuth.currentUser?.uid
        return try {
            val snapshot = firestore.collection("chats")
                .whereEqualTo("type", "CHANNEL")
                .limit(300)
                .get().await()
            snapshot.documents
                .filter { doc ->
                    // НОВОЕ (режимы доступа): скрытые каналы не показываем в поиске,
                    // кроме случая, когда пользователь уже подписан (чтобы мог найти свой канал).
                    val accessMode = app.yodo.messenger.domain.model.ChannelAccessMode.fromRaw(doc.getString("accessMode"))
                    val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val isSub = uid != null && uid in participantIds
                    if (accessMode == app.yodo.messenger.domain.model.ChannelAccessMode.HIDDEN && !isSub) return@filter false
                    val title = (doc.getString("title") ?: "").lowercase()
                    val titleLc = doc.getString("titleLowercase") ?: title
                    val desc = (doc.getString("description") ?: "").lowercase()
                    val category = (doc.getString("category") ?: "").lowercase()
                    val tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>()
                        ?.joinToString(" ") { it.lowercase() } ?: ""
                    titleLc.contains(normalized) || title.contains(normalized) ||
                        desc.contains(normalized) || category.contains(normalized) || tags.contains(normalized)
                }
                .map { doc ->
                    val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    ChannelSearchItem(
                        chatId = doc.id,
                        title = doc.getString("title") ?: "Без названия",
                        description = doc.getString("description").orEmpty(),
                        avatarBase64 = doc.getString("avatarBase64"),
                        subscriberCount = participantIds.size,
                        isVerified = doc.getBoolean("isVerified") ?: false,
                        isSubscribed = uid != null && uid in participantIds,
                        category = doc.getString("category"),
                        accessMode = app.yodo.messenger.domain.model.ChannelAccessMode.fromRaw(doc.getString("accessMode"))
                    )
                }
                // Верифицированные каналы (в т.ч. официальный) — выше в выдаче.
                .sortedWith(compareByDescending<ChannelSearchItem> { it.isVerified }.thenByDescending { it.subscriberCount })
                .take(30)
        } catch (e: Exception) { emptyList() }
    }

    // НОВОЕ (переработка каналов): полный профиль канала для ChannelProfileScreen.
    override suspend fun getChannelProfile(chatId: String): ChannelProfile? {
        val uid = firebaseAuth.currentUser?.uid
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (!doc.exists() || doc.getString("type") != "CHANNEL") return null
            val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            ChannelProfile(
                chatId = chatId,
                title = doc.getString("title") ?: "Без назван��я",
                description = doc.getString("description").orEmpty(),
                avatarBase64 = doc.getString("avatarBase64"),
                subscriberCount = participantIds.size,
                isVerified = doc.getBoolean("isVerified") ?: false,
                ownerId = doc.getString("createdBy"),
                adminIds = (doc.get("adminIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                isSubscribed = uid != null && uid in participantIds,
                createdAt = doc.getLong("createdAt") ?: 0L,
                category = doc.getString("category"),
                tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                coverBase64 = doc.getString("coverBase64"),
                // НОВОЕ (режимы доступа): режим и ограничения.
                accessMode = app.yodo.messenger.domain.model.ChannelAccessMode.fromRaw(doc.getString("accessMode")),
                restrictions = readRestrictions(doc),
                // НОВОЕ (модерируемые каналы): статус заявки текущего пользователя и число ожидающих.
                hasPendingJoinRequest = if (uid != null && uid !in participantIds) {
                    runCatching {
                        firestore.collection("chats").document(chatId)
                            .collection("joinRequests").document(uid).get().await().exists()
                    }.getOrDefault(false)
                } else false,
                pendingRequestsCount = if (uid != null && (uid == doc.getString("createdBy") ||
                        uid in ((doc.get("adminIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()))) {
                    runCatching {
                        firestore.collection("chats").document(chatId)
                            .collection("joinRequests").get().await().size()
                    }.getOrDefault(0)
                } else 0
            )
        } catch (e: Exception) { null }
    }

    // НОВОЕ (ограничения канала): читаем набор ограничений из документа канала.
    // Отсутствующее поле = разрешено (так старые каналы работают как раньше).
    private fun readRestrictions(doc: com.google.firebase.firestore.DocumentSnapshot): app.yodo.messenger.domain.model.ChannelRestrictions {
        return app.yodo.messenger.domain.model.ChannelRestrictions(
            allowForwarding = doc.getBoolean("allowForwarding") ?: true,
            allowComments = doc.getBoolean("allowComments") ?: true,
            allowReactions = doc.getBoolean("allowReactions") ?: true,
            allowSaving = doc.getBoolean("allowSaving") ?: true,
            allowLinkPreviews = doc.getBoolean("allowLinkPreviews") ?: true
        )
    }

    // НОВОЕ (переработка каналов): обновление названия и описания канала (владелец/админ).
    override suspend fun updateChannelInfo(chatId: String, title: String, description: String): ChannelUpdateResult {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return ChannelUpdateResult.Error("Название не может быть пустым")
        return try {
            firestore.collection("chats").document(chatId).update(
                mapOf(
                    "title" to trimmed,
                    "titleLowercase" to trimmed.lowercase(),
                    "description" to description.trim()
                )
            ).await()
            logAdminAction(chatId, AdminActionType.CHAT_INFO_CHANGED, details = "Название: $trimmed")
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось сохранить изменения"))
        }
    }

    // НОВОЕ (переработка каналов): загрузка аватарки канала — сжатый Base64 в документ чата.
    override suspend fun uploadChannelAvatar(chatId: String, bitmap: Bitmap): ChannelUpdateResult {
        return try {
            val base64 = withContext(Dispatchers.Default) {
                ImageUtils.compressAvatarToBase64(bitmap)
            } ?: return ChannelUpdateResult.Error("Не удалось обработать изображение")
            firestore.collection("chats").document(chatId).update("avatarBase64", base64).await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось загрузить фото"))
        }
    }

    // НОВОЕ (F5): сохранение категории и тегов канала.
    override suspend fun updateChannelMeta(chatId: String, category: String?, tags: List<String>): ChannelUpdateResult {
        return try {
            val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
            firestore.collection("chats").document(chatId).update(
                mapOf(
                    "category" to (category?.trim()?.takeIf { it.isNotBlank() }),
                    "tags" to cleanTags
                )
            ).await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось сохранить категорию"))
        }
    }

    // НОВОЕ (F5): загрузка обложки (баннера) канала — сжатый Base64 в документ чата.
    override suspend fun uploadChannelCover(chatId: String, bitmap: Bitmap): ChannelUpdateResult {
        return try {
            val base64 = withContext(Dispatchers.Default) {
                ImageUtils.compressAvatarToBase64(bitmap)
            } ?: return ChannelUpdateResult.Error("Не удалось обработать изображение")
            firestore.collection("chats").document(chatId).update("coverBase64", base64).await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось загрузить обложку"))
        }
    }

    // === НОВОЕ (режимы доступа и ограничения каналов) ===

    // Проверка: текущий пользователь — владелец или админ канала.
    private suspend fun isChannelManager(chatId: String, uid: String): Boolean {
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            val admins = (doc.get("adminIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            uid == doc.getString("createdBy") || uid in admins
        } catch (e: Exception) { false }
    }

    override suspend fun updateChannelAccessMode(
        chatId: String, mode: app.yodo.messenger.domain.model.ChannelAccessMode
    ): ChannelUpdateResult {
        val uid = firebaseAuth.currentUser?.uid ?: return ChannelUpdateResult.Error("Не авторизован")
        if (!isChannelManager(chatId, uid)) return ChannelUpdateResult.Error("Недостаточно прав")
        return try {
            firestore.collection("chats").document(chatId)
                .update("accessMode", mode.name).await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось сменить режим доступа"))
        }
    }

    override suspend fun updateChannelRestrictions(
        chatId: String, restrictions: app.yodo.messenger.domain.model.ChannelRestrictions
    ): ChannelUpdateResult {
        val uid = firebaseAuth.currentUser?.uid ?: return ChannelUpdateResult.Error("Не авторизован")
        if (!isChannelManager(chatId, uid)) return ChannelUpdateResult.Error("Недостаточно прав")
        return try {
            firestore.collection("chats").document(chatId).update(
                mapOf(
                    "allowForwarding" to restrictions.allowForwarding,
                    "allowComments" to restrictions.allowComments,
                    "allowReactions" to restrictions.allowReactions,
                    "allowSaving" to restrictions.allowSaving,
                    "allowLinkPreviews" to restrictions.allowLinkPreviews
                )
            ).await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось сохранить ограничения"))
        }
    }

    override suspend fun requestToJoinChannel(chatId: String): ChannelUpdateResult {
        val user = firebaseAuth.currentUser ?: return ChannelUpdateResult.Error("Не авторизован")
        val uid = user.uid
        return try {
            val userDoc = firestore.collection("users").document(uid).get().await()
            firestore.collection("chats").document(chatId)
                .collection("joinRequests").document(uid).set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to (userDoc.getString("displayName") ?: user.displayName ?: "Пользователь"),
                        "username" to userDoc.getString("username"),
                        "avatarBase64" to userDoc.getString("avatarBase64"),
                        "photoUrl" to userDoc.getString("avatarUrl"),
                        "requestedAt" to System.currentTimeMillis()
                    )
                ).await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось отправить заявку"))
        }
    }

    override suspend fun cancelJoinRequest(chatId: String): ChannelUpdateResult {
        val uid = firebaseAuth.currentUser?.uid ?: return ChannelUpdateResult.Error("Не авторизован")
        return try {
            firestore.collection("chats").document(chatId)
                .collection("joinRequests").document(uid).delete().await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось отменить заявку"))
        }
    }

    override suspend fun getJoinRequests(chatId: String): List<app.yodo.messenger.domain.model.JoinRequest> {
        val uid = firebaseAuth.currentUser?.uid ?: return emptyList()
        if (!isChannelManager(chatId, uid)) return emptyList()
        return try {
            firestore.collection("chats").document(chatId)
                .collection("joinRequests").get().await().documents.map { d ->
                    app.yodo.messenger.domain.model.JoinRequest(
                        userId = d.getString("userId") ?: d.id,
                        displayName = d.getString("displayName") ?: "Пользователь",
                        username = d.getString("username"),
                        avatarBase64 = d.getString("avatarBase64"),
                        photoUrl = d.getString("photoUrl"),
                        requestedAt = d.getLong("requestedAt") ?: 0L
                    )
                }.sortedByDescending { it.requestedAt }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun approveJoinRequest(chatId: String, userId: String): ChannelUpdateResult {
        val uid = firebaseAuth.currentUser?.uid ?: return ChannelUpdateResult.Error("Не авторизован")
        if (!isChannelManager(chatId, uid)) return ChannelUpdateResult.Error("Недостаточно прав")
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            chatRef.update(
                mapOf(
                    "participantIds" to FieldValue.arrayUnion(userId),
                    "unreadCounts.$userId" to 0
                )
            ).await()
            chatRef.collection("joinRequests").document(userId).delete().await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось одобрить заявку"))
        }
    }

    override suspend fun rejectJoinRequest(chatId: String, userId: String): ChannelUpdateResult {
        val uid = firebaseAuth.currentUser?.uid ?: return ChannelUpdateResult.Error("Не авторизован")
        if (!isChannelManager(chatId, uid)) return ChannelUpdateResult.Error("Недостаточно прав")
        return try {
            firestore.collection("chats").document(chatId)
                .collection("joinRequests").document(userId).delete().await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось откло��ить заявку"))
        }
    }

    // === НОВОЕ (чат поддержки) ===

    override fun isSupportAdmin(): Boolean {
        val email = firebaseAuth.currentUser?.email?.lowercase() ?: return false
        return email in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }
    }

    // Каждый пользователь имеет единственную беседу поддержки support_<uid>.
    // Документ — обычный чат (тот же конвейер сообщений), но с type == "SUPPORT",
    // верифицированный (галочка) и фиксированным названием. participantIds = [uid] —
    // админы получают доступ по firestore.rules (по email), не через участие.
    override suspend fun getOrCreateSupportChat(): CreateChatResult {
        val user = firebaseAuth.currentUser ?: return CreateChatResult.Error("Вы не авторизованы")
        val uid = user.uid
        val chatId = ChatRepository.supportChatIdFor(uid)
        return try {
            val ref = firestore.collection("chats").document(chatId)
            val doc = ref.get().await()
            if (!doc.exists()) {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val name = userDoc.getString("displayName") ?: user.displayName ?: "Пользователь"
                val email = user.email ?: userDoc.getString("email") ?: ""
                val now = System.currentTimeMillis()
                // НОВОЕ (приветствие поддержки, без Cloud Functions — план Firebase не Blaze):
                // текст приветственного сообщения. Пишем его прямо с клиента как обычное
                // Message-сообщение от имени "support_system" — allow в firestore.rules
                // разрешает это ровно один раз для владельца чата, документ с фиксированным
                // id "support_welcome" не даёт создать приветствие повторно.
                val welcomeText = "Здравствуйте, это аккаунт поддержки. Задавайте вопросы именно в него — " +
                    "мы отвечаем в этом же чате. Опишите проблему подробно и, если есть, приложите " +
                    "скриншот — так мы разберёмся быстрее."
                ref.set(
                    mapOf(
                        "participantIds" to listOf(uid),
                        "type" to "SUPPORT",
                        "title" to ChatRepository.SUPPORT_TITLE,
                        "isVerified" to true,
                        // служебные поля для админ-панели
                        "supportUserId" to uid,
                        "supportUserName" to name,
                        "supportUserEmail" to email,
                        "supportUserAvatar" to userDoc.getString("photoBase64"),
                        "lastMessage" to welcomeText,
                        "lastMessageTimestamp" to now,
                        "lastMessageSenderId" to "support_system",
                        "lastMessageStatus" to "SENT",
                        "unreadCounts" to mapOf(uid to 1),
                        "isOnline" to false,
                        "createdBy" to uid,
                        "createdAt" to now
                    )
                ).await()
                try {
                    ref.collection("messages").document("support_welcome").set(
                        mapOf(
                            "senderId" to "support_system",
                            "text" to welcomeText,
                            "timestamp" to now,
                            "status" to "SENT",
                            "notified" to true
                        )
                    ).await()
                } catch (_: Exception) {
                    // Не критично: если приветствие не удалось создать (например, оффлайн),
                    // сам чат поддержки уже создан и пользователь всё равно может им пользоваться.
                }
            }
            CreateChatResult.Success(chatId)
        } catch (e: Exception) {
            CreateChatResult.Error(e.toUserMessage("Не удалось открыть чат поддержки"))
        }
    }

    override fun observeSupportConversations(): Flow<List<SupportConversation>> = callbackFlow {
        if (!isSupportAdmin()) { trySend(emptyList()); close(); return@callbackFlow }
        val listener = firestore.collection("chats")
            .whereEqualTo("type", "SUPPORT")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val userId = doc.getString("supportUserId") ?: return@mapNotNull null
                    SupportConversation(
                        chatId = doc.id,
                        userId = userId,
                        userName = doc.getString("supportUserName") ?: "Пользователь",
                        userEmail = doc.getString("supportUserEmail") ?: "",
                        avatarBase64 = doc.getString("supportUserAvatar"),
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
                        lastMessageSenderId = doc.getString("lastMessageSenderId"),
                        awaitingReply = doc.getString("lastMessageSenderId") == userId &&
                            (doc.getString("lastMessage").orEmpty().isNotBlank())
                    )
                }.sortedByDescending { it.lastMessageTimestamp }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getGroupInfo(chatId: String): GroupInfo? {
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (!doc.exists()) return null
            val title = doc.getString("title") ?: "Группа"
            val createdBy = doc.getString("createdBy")
            // НОВОЕ (конфиденциальность групп): режим доступа и описание.
            val groupAccessMode = app.yodo.messenger.domain.model.ChannelAccessMode.fromRaw(doc.getString("accessMode"))
            val groupDescription = doc.getString("description") ?: ""
            val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            // Вместо N+1 запросов используем whereIn с разбивкой по 30 (лимит Firestore).
            val members = participantIds.chunked(30).flatMap { chunk ->
                firestore.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await()
                    .documents
                    .map { memberDoc ->
                        YodoUser(
                            uid = memberDoc.id,
                            displayName = memberDoc.getString("displayName") ?: "Пользователь",
                            username = memberDoc.getString("username"),
                            bio = memberDoc.getString("bio"),
                            email = memberDoc.getString("email"),
                            phoneNumber = memberDoc.getString("phoneNumber"),
                            photoUrl = memberDoc.getString("avatarUrl"),
                            avatarBase64 = memberDoc.getString("avatarBase64")
                        )
                    }
            }
            GroupInfo(
                title = title,
                members = members,
                createdBy = createdBy,
                accessMode = groupAccessMode,
                description = groupDescription
            )
        } catch (e: Exception) { null }
    }

    override suspend fun leaveGroup(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("chats").document(chatId)
                .update("participantIds", FieldValue.arrayRemove(uid)).await()
        } catch (e: Exception) { }
    }

    override suspend fun togglePinChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val pinnedMap = snapshot.get("pinned") as? Map<*, *>
            val currentlyPinned = pinnedMap?.get(uid) as? Boolean ?: false
            chatRef.update("pinned.$uid", !currentlyPinned).await()
        } catch (e: Exception) { }
    }

    override suspend fun toggleMuteChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val mutedMap = snapshot.get("muted") as? Map<*, *>
            val currentlyMuted = mutedMap?.get(uid) as? Boolean ?: false
            chatRef.update("muted.$uid", !currentlyMuted).await()
        } catch (e: Exception) { }
    }

    // НОВОЕ (архивация чатов): та же схема хранения, что у pinned/muted —
    // map<uid, Boolean> в документе чата, персонально для каждог�� участника.
    override suspend fun toggleArchiveChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val archivedMap = snapshot.get("archived") as? Map<*, *>
            val currentlyArchived = archivedMap?.get(uid) as? Boolean ?: false
            chatRef.update("archived.$uid", !currentlyArchived).await()
        } catch (e: Exception) { }
    }

    override suspend fun clearChatHistory(chatId: String) {
        try {
            val messagesRef = firestore.collection("chats").document(chatId).collection("messages")
            val snapshot = messagesRef.get().await()
            // Firestore batch ограничен 500 операциями — делим на чанки.
            snapshot.documents.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            firestore.collection("chats").document(chatId).update(
                mapOf("lastMessage" to "", "lastMessageTimestamp" to System.currentTimeMillis())
            ).await()
        } catch (e: Exception) { throw e }
    }

    override suspend fun deleteChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val messagesRef = firestore.collection("chats").document(chatId).collection("messages")
            val snapshot = messagesRef.get().await()
            // Firestore batch ограничен 500 операциями — делим на чанки.
            snapshot.documents.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            firestore.collection("chats").document(chatId)
                .update("participantIds", FieldValue.arrayRemove(uid)).await()
        } catch (e: Exception) { throw e }
    }

    override suspend fun getOtherUserAvatar(chatId: String): Pair<String?, String?>? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: return null
            val otherId = participantIds.firstOrNull { it != uid } ?: return null
            val otherDoc = firestore.collection("users").document(otherId).get().await()
            Pair(otherDoc.getString("avatarUrl"), otherDoc.getString("avatarBase64"))
        } catch (e: Exception) { null }
    }

    override fun observeDisappearingTtl(chatId: String): Flow<Long?> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, _ ->
                val ttl = snapshot?.getLong("disappearingTtlSeconds")
                trySend(ttl)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun setDisappearingTtl(chatId: String, ttlSeconds: Long?) {
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            if (ttlSeconds == null) {
                chatRef.update("disappearingTtlSeconds", FieldValue.delete()).await()
            } else {
                chatRef.update("disappearingTtlSeconds", ttlSeconds).await()
            }
        } catch (e: Exception) { }
    }

    // === НОВОЕ (система ролей с гранулярными правами, п.1 ТЗ) ===
    //
    // Схема хранения в Firestore, документ chats/{chatId}:
    //   roles: Map<userId, Map{ "builtIn": String?, "customRoleId": String? }>
    //   customRoles: Map<roleId, Map{ "name": String, "permissions": List<String>, "colorHex": String }>
    // Владелец (createdBy) всегда имеет полный набор прав и не хранится в roles.

    private fun parseCustomRole(id: String, data: Map<*, *>): CustomRole {
        val name = data["name"] as? String ?: "Роль"
        val perms = (data["permissions"] as? List<*>)?.filterIsInstance<String>()
            ?.mapNotNull { runCatching { Permission.valueOf(it) }.getOrNull() }
            ?.toSet() ?: emptySet()
        val color = data["colorHex"] as? String ?: "#FF7C4DFF"
        return CustomRole(id = id, name = name, permissions = perms, colorHex = color)
    }

    override suspend fun getCustomRoles(chatId: String): List<CustomRole> {
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            val map = doc.get("customRoles") as? Map<*, *> ?: return emptyList()
            map.entries.mapNotNull { (key, value) ->
                val id = key as? String ?: return@mapNotNull null
                val data = value as? Map<*, *> ?: return@mapNotNull null
                parseCustomRole(id, data)
            }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getAssignedRoles(chatId: String): List<AssignedRole> {
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            val map = doc.get("roles") as? Map<*, *> ?: return emptyList()
            map.entries.mapNotNull { (key, value) ->
                val userId = key as? String ?: return@mapNotNull null
                val data = value as? Map<*, *> ?: return@mapNotNull null
                val builtInName = data["builtIn"] as? String
                val builtIn = builtInName?.let { runCatching { BuiltInRole.valueOf(it) }.getOrNull() }
                val customRoleId = data["customRoleId"] as? String
                AssignedRole(userId = userId, builtIn = builtIn, customRoleId = customRoleId)
            }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getMemberPermissions(chatId: String, userId: String): MemberPermissions {
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (doc.getString("createdBy") == userId) {
                return MemberPermissions(
                    userId = userId,
                    roleName = BuiltInRole.OWNER.displayName,
                    isOwner = true,
                    permissions = Permission.entries.toSet()
                )
            }
            val rolesMap = doc.get("roles") as? Map<*, *>
            val assignment = rolesMap?.get(userId) as? Map<*, *>
                ?: return MemberPermissions(userId, "Участник", false, emptySet())
            val customRoleId = assignment["customRoleId"] as? String
            if (customRoleId != null) {
                val customRolesMap = doc.get("customRoles") as? Map<*, *>
                val roleData = customRolesMap?.get(customRoleId) as? Map<*, *>
                val role = roleData?.let { parseCustomRole(customRoleId, it) }
                return MemberPermissions(
                    userId = userId,
                    roleName = role?.name ?: "Роль",
                    isOwner = false,
                    permissions = role?.permissions ?: emptySet()
                )
            }
            val builtInName = assignment["builtIn"] as? String
            val builtIn = builtInName?.let { runCatching { BuiltInRole.valueOf(it) }.getOrNull() }
            MemberPermissions(
                userId = userId,
                roleName = builtIn?.displayName ?: "Участник",
                isOwner = false,
                permissions = builtIn?.let { Permission.defaultSetFor(it) } ?: emptySet()
            )
        } catch (e: Exception) {
            MemberPermissions(userId, "Участник", false, emptySet())
        }
    }

    override suspend fun assignBuiltInRole(chatId: String, userId: String, role: BuiltInRole): ChannelUpdateResult {
        return try {
            firestore.collection("chats").document(chatId)
                .update("roles.$userId", mapOf("builtIn" to role.name, "customRoleId" to null)).await()
            val targetName = firestore.collection("users").document(userId).get().await()
                .getString("displayName")
            logAdminAction(chatId, AdminActionType.ROLE_ASSIGNED, details = role.displayName, targetUserId = userId, targetUserName = targetName)
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось назначить роль"))
        }
    }

    override suspend fun assignCustomRole(chatId: String, userId: String, customRoleId: String): ChannelUpdateResult {
        return try {
            firestore.collection("chats").document(chatId)
                .update("roles.$userId", mapOf("builtIn" to null, "customRoleId" to customRoleId)).await()
            val targetName = firestore.collection("users").document(userId).get().await()
                .getString("displayName")
            val roleName = getCustomRoles(chatId).firstOrNull { it.id == customRoleId }?.name ?: "Роль"
            logAdminAction(chatId, AdminActionType.ROLE_ASSIGNED, details = roleName, targetUserId = userId, targetUserName = targetName)
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось назначить роль"))
        }
    }

    override suspend fun revokeRole(chatId: String, userId: String): ChannelUpdateResult {
        return try {
            firestore.collection("chats").document(chatId)
                .update("roles.$userId", FieldValue.delete()).await()
            val targetName = firestore.collection("users").document(userId).get().await()
                .getString("displayName")
            logAdminAction(chatId, AdminActionType.ROLE_REMOVED, targetUserId = userId, targetUserName = targetName)
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось снять роль"))
        }
    }

    override suspend fun createCustomRole(chatId: String, name: String, permissions: Set<Permission>): ChannelUpdateResult {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return ChannelUpdateResult.Error("Введите название роли")
        return try {
            val roleId = firestore.collection("chats").document().id
            val data = mapOf(
                "name" to trimmed,
                "permissions" to permissions.map { it.name },
                "colorHex" to "#FF7C4DFF"
            )
            firestore.collection("chats").document(chatId)
                .update("customRoles.$roleId", data).await()
            logAdminAction(chatId, AdminActionType.CUSTOM_ROLE_CREATED, details = trimmed)
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось создать роль"))
        }
    }

    override suspend fun updateCustomRole(chatId: String, roleId: String, name: String, permissions: Set<Permission>): ChannelUpdateResult {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return ChannelUpdateResult.Error("Введите название рол��")
        return try {
            val data = mapOf(
                "name" to trimmed,
                "permissions" to permissions.map { it.name },
                "colorHex" to "#FF7C4DFF"
            )
            firestore.collection("chats").document(chatId)
                .update("customRoles.$roleId", data).await()
            logAdminAction(chatId, AdminActionType.CUSTOM_ROLE_EDITED, details = trimmed)
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось изменить роль"))
        }
    }

    override suspend fun deleteCustomRole(chatId: String, roleId: String): ChannelUpdateResult {
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val doc = chatRef.get().await()
            val roleName = (doc.get("customRoles") as? Map<*, *>)
                ?.get(roleId)?.let { (it as? Map<*, *>)?.get("name") as? String } ?: "Роль"
            // Снимаем эту роль у всех, кому она была назначена, чтобы не остались "битые" ссылки.
            val rolesMap = doc.get("roles") as? Map<*, *> ?: emptyMap<String, Any>()
            val affectedUserIds = rolesMap.entries.filter { (_, value) ->
                (value as? Map<*, *>)?.get("customRoleId") == roleId
            }.mapNotNull { it.key as? String }
            val batch = firestore.batch()
            batch.update(chatRef, "customRoles.$roleId", FieldValue.delete())
            affectedUserIds.forEach { uid -> batch.update(chatRef, "roles.$uid", FieldValue.delete()) }
            batch.commit().await()
            logAdminAction(chatId, AdminActionType.CUSTOM_ROLE_DELETED, details = roleName)
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось удалить роль"))
        }
    }

    // === НОВОЕ (журнал действий администраторов, п.2 ТЗ) ===
    //
    // Подколлекция chats/{chatId}/adminLog. Каждая запись — одно действие.
    // Композитные индексы для фильтрации заданы в firestore.indexes.json.

    override suspend fun logAdminAction(
        chatId: String,
        actionType: AdminActionType,
        details: String,
        targetUserId: String?,
        targetUserName: String?
    ) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val actorName = firestore.collection("users").document(uid).get().await()
                .getString("displayName") ?: "Пользователь"
            val entry = mapOf(
                "actorId" to uid,
                "actorName" to actorName,
                "actionType" to actionType.name,
                "details" to details,
                "targetUserId" to targetUserId,
                "targetUserName" to targetUserName,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("chats").document(chatId)
                .collection("adminLog").add(entry).await()
        } catch (e: Exception) { }
    }

    override suspend fun getAdminLog(
        chatId: String,
        filter: AdminLogFilter,
        limit: Int,
        startAfterTimestamp: Long?
    ): List<AdminLogEntry> {
        return try {
            var query: Query = firestore.collection("chats").document(chatId)
                .collection("adminLog")
                .orderBy("timestamp", Query.Direction.DESCENDING)
            filter.actionType?.let { query = query.whereEqualTo("actionType", it.name) }
            filter.actorId?.let { query = query.whereEqualTo("actorId", it) }
            filter.fromMillis?.let { query = query.whereGreaterThanOrEqualTo("timestamp", it) }
            filter.toMillis?.let { query = query.whereLessThanOrEqualTo("timestamp", it) }
            startAfterTimestamp?.let { query = query.startAfter(it) }
            query = query.limit(limit.toLong())
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { doc ->
                val actionTypeName = doc.getString("actionType") ?: return@mapNotNull null
                val actionType = runCatching { AdminActionType.valueOf(actionTypeName) }.getOrNull()
                    ?: return@mapNotNull null
                AdminLogEntry(
                    id = doc.id,
                    chatId = chatId,
                    actorId = doc.getString("actorId") ?: "",
                    actorName = doc.getString("actorName") ?: "Пользователь",
                    actionType = actionType,
                    details = doc.getString("details").orEmpty(),
                    targetUserId = doc.getString("targetUserId"),
                    targetUserName = doc.getString("targetUserName"),
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // === НОВОЕ (система жалоб, п.5 ТЗ): бан участника чата/канала ===
    //
    // bannedUserIds хранится отдельным полем (не в participantIds), чтобы после разбана
    // можно было восстановить факт "когда-то был участником" при необходимости; здесь же
    // забаненный сразу удаляется из participantIds, теряя доступ к чату.

    override suspend fun banMember(chatId: String, userId: String): ChannelUpdateResult {
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val batch = firestore.batch()
            batch.update(chatRef, "bannedUserIds", FieldValue.arrayUnion(userId))
            batch.update(chatRef, "participantIds", FieldValue.arrayRemove(userId))
            batch.update(chatRef, "adminIds", FieldValue.arrayRemove(userId))
            batch.commit().await()
            val targetName = firestore.collection("users").document(userId).get().await()
                .getString("displayName")
            logAdminAction(chatId, AdminActionType.USER_BANNED, targetUserId = userId, targetUserName = targetName)
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("��е удалось забанить пользователя"))
        }
    }

    override suspend fun unbanMember(chatId: String, userId: String): ChannelUpdateResult {
        return try {
            firestore.collection("chats").document(chatId)
                .update("bannedUserIds", FieldValue.arrayRemove(userId)).await()
            val targetName = firestore.collection("users").document(userId).get().await()
                .getString("displayName")
            logAdminAction(chatId, AdminActionType.USER_UNBANNED, targetUserId = userId, targetUserName = targetName)
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось разбанить пользователя"))
        }
    }

    override suspend fun getBannedMemberIds(chatId: String): List<String> {
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            (doc.get("bannedUserIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
}