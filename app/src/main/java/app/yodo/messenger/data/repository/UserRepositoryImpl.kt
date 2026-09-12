package app.yodo.messenger.data.repository

import android.graphics.Bitmap
import android.net.Uri
import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.domain.model.GlobalAdminActionType
import app.yodo.messenger.domain.model.GlobalAdminLogEntry
import app.yodo.messenger.domain.model.GlobalBlock
import app.yodo.messenger.domain.model.AdminBlockHistoryEntry
import app.yodo.messenger.domain.model.AdminGroupChannelEntry
import app.yodo.messenger.domain.model.AdminLoginEntry
import app.yodo.messenger.domain.model.AdminUserDetails
import app.yodo.messenger.domain.model.AdminUserModerationStats
import app.yodo.messenger.domain.model.AdminTimelineEntry
import app.yodo.messenger.domain.model.AdminActivityPoint
import app.yodo.messenger.domain.model.AdminAssignment
import app.yodo.messenger.domain.model.AdminRole
import app.yodo.messenger.domain.model.PrivacyWho
import app.yodo.messenger.domain.model.ProfileHistoryEntry
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.ProfileUpdateResult
import app.yodo.messenger.domain.repository.UserRepository
import app.yodo.messenger.util.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
class UserRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: android.content.Context
) : UserRepository {

    override fun observeCurrentUser(): Flow<YodoUser?> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) { trySend(null); close(); return@callbackFlow }
        // Фиксируем открытие сессии для админ-карточки. Реальный IP Android-клиент
        // Firebase SDK не предоставляет, поэтому поле остаётся null до появления
        // серверного источника IP (например, Cloud Function).
        runCatching {
            val now = System.currentTimeMillis()
            firestore.collection("users").document(uid).update("lastActiveAt", now).await()
            firestore.collection("adminLoginHistory").document(uid).collection("entries").add(
                mapOf(
                    "timestamp" to now,
                    "provider" to (firebaseAuth.currentUser?.providerData?.lastOrNull()?.providerId ?: "unknown"),
                    "device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    "ipAddress" to null
                )
            ).await()
        }
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(YodoUser(
                        uid = uid,
                        displayName = firebaseAuth.currentUser?.displayName.orEmpty(),
                        email = firebaseAuth.currentUser?.email,
                        phoneNumber = firebaseAuth.currentUser?.phoneNumber,
                        photoUrl = firebaseAuth.currentUser?.photoUrl?.toString(),
                        isEmailVerified = firebaseAuth.currentUser?.isEmailVerified ?: false
                    ))
                    return@addSnapshotListener
                }
                trySend(snapshot.toYodoUser(uid))
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateDisplayName(name: String): ProfileUpdateResult {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return ProfileUpdateResult.Error("Имя не может быть пустым")
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            logProfileChange("displayName", "Имя", trimmed)
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(trimmed).build()).await()
            firestore.collection("users").document(user.uid)
                .update(mapOf("displayName" to trimmed, "displayNameLowercase" to trimmed.lowercase())).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось обновить имя")) }
    }

    override suspend fun updateEmojiStatus(emoji: String): ProfileUpdateResult {
        val trimmed = emoji.trim().take(8)
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid).update("emojiStatus", trimmed).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось обновить статус")) }
    }

    override suspend fun updateCustomStatus(status: String): ProfileUpdateResult {
        val trimmed = status.trim().take(100)
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid).update("customStatus", trimmed).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось обновить статус")) }
    }

    override suspend fun updateBio(bio: String): ProfileUpdateResult {
        val trimmed = bio.trim().take(150)
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            logProfileChange("bio", "Описание", trimmed)
            firestore.collection("users").document(user.uid).update("bio", trimmed).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось обновить описание")) }
    }

    override suspend fun updateUsername(username: String): ProfileUpdateResult {
        val normalized = username.trim().removePrefix("@").lowercase()
        if (normalized.isBlank()) return ProfileUpdateResult.Error("Введите username")
        if (!normalized.matches(Regex("^[a-z0-9_]{3,20}$")))
            return ProfileUpdateResult.Error("Username: 3-20 символов, только латиница, цифры и \"_\"")
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            logProfileChange("username", "Имя пользователя", normalized)
            firestore.runTransaction { transaction ->
                val usernameRef = firestore.collection("usernames").document(normalized)
                val usernameSnapshot = transaction.get(usernameRef)
                if (usernameSnapshot.exists() && usernameSnapshot.getString("uid") != user.uid)
                    throw IllegalStateException("USERNAME_TAKEN")
                val userRef = firestore.collection("users").document(user.uid)
                val userSnapshot = transaction.get(userRef)
                val oldUsername = userSnapshot.getString("usernameLowercase")
                if (oldUsername != null && oldUsername != normalized)
                    transaction.delete(firestore.collection("usernames").document(oldUsername))
                transaction.set(usernameRef, mapOf("uid" to user.uid))
                transaction.update(userRef, mapOf("username" to normalized, "usernameLowercase" to normalized))
            }.await()
            ProfileUpdateResult.Success
        } catch (e: Exception) {
            ProfileUpdateResult.Error(e.toUserMessage("Не удалось сохранить username"))
        }
    }

    override suspend fun uploadAvatar(imageUri: Uri): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            val base64 = withContext(Dispatchers.Default) {
                ImageUtils.compressAvatarToBase64(context, imageUri)
            } ?: return ProfileUpdateResult.Error("Не удалось обработать изображение")
            firestore.collection("users").document(user.uid)
                .update(mapOf("avatarBase64" to base64, "avatarUrl" to null)).await()
            logAvatarChange()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось загрузить фото")) }
    }

    override suspend fun uploadAvatar(bitmap: Bitmap): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            val base64 = withContext(Dispatchers.Default) {
                ImageUtils.compressAvatarToBase64(bitmap)
            } ?: return ProfileUpdateResult.Error("Не удалось обработать изображение")
            firestore.collection("users").document(user.uid)
                .update(mapOf("avatarBase64" to base64, "avatarUrl" to null)).await()
            logAvatarChange()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось загрузить фото")) }
    }

    override suspend fun searchUsers(query: String): List<YodoUser> {
        val normalized = query.trim().removePrefix("@").lowercase()
        if (normalized.isBlank()) return emptyList()
        val currentUid = firebaseAuth.currentUser?.uid
        val usersRef = firestore.collection("users")
        return try {
            val byName = usersRef.orderBy("displayNameLowercase")
                .startAt(normalized).endAt(normalized + "\uf8ff").limit(20).get().await()
            val byUsername = usersRef.orderBy("usernameLowercase")
                .startAt(normalized).endAt(normalized + "\uf8ff").limit(20).get().await()
            (byName.documents + byUsername.documents)
                .distinctBy { it.id }.filter { it.id != currentUid }
                .map { it.toYodoUser(it.id) }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getUsersByPhoneNumbers(phoneNumbers: List<String>): List<YodoUser> {
        val currentUid = firebaseAuth.currentUser?.uid
        val normalized = phoneNumbers.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return emptyList()
        val usersRef = firestore.collection("users")
        return try {
            // Firestore whereIn поддерживает максимум 30 значений за запрос — делим на пачки.
            normalized.chunked(30).flatMap { chunk ->
                usersRef.whereIn("phoneNumber", chunk).get().await().documents
            }.distinctBy { it.id }
                .filter { it.id != currentUid }
                .map { it.toYodoUser(it.id) }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getUserById(uid: String): YodoUser? {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) doc.toYodoUser(uid) else null
        } catch (e: Exception) { null }
    }

    // НОВОЕ (История изменений профиля): запись одного изменения в журнал.
    // Читает старое значение поля и если оно отличается — добавляет запись
    // в users/{uid}/profileHistory. Ошибки журналирования не ломают само обновление.
    private suspend fun logProfileChange(field: String, fieldLabel: String, newValue: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        runCatching {
            val userRef = firestore.collection("users").document(uid)
            val oldValue = userRef.get().await().getString(field) ?: ""
            if (oldValue == newValue) return
            userRef.collection("profileHistory").add(
                mapOf(
                    "field" to field,
                    "fieldLabel" to fieldLabel,
                    "oldValue" to oldValue,
                    "newValue" to newValue,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
        }
    }

    // НОВОЕ (История изменений профиля): отдельная запись про смену аватарки.
    private suspend fun logAvatarChange() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        runCatching {
            firestore.collection("users").document(uid).collection("profileHistory").add(
                mapOf(
                    "field" to "avatar",
                    "fieldLabel" to "Фото профиля",
                    "oldValue" to "",
                    "newValue" to "Обновлено",
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
        }
    }

    override suspend fun getProfileHistory(): List<ProfileHistoryEntry> {
        val uid = firebaseAuth.currentUser?.uid ?: return emptyList()
        return try {
            firestore.collection("users").document(uid).collection("profileHistory")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(200).get().await()
                .documents.map { d ->
                    ProfileHistoryEntry(
                        id = d.id,
                        field = d.getString("field") ?: "",
                        fieldLabel = d.getString("fieldLabel") ?: "",
                        oldValue = d.getString("oldValue") ?: "",
                        newValue = d.getString("newValue") ?: "",
                        timestamp = d.getLong("timestamp") ?: 0L
                    )
                }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun updateAboutMe(aboutMe: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            logProfileChange("aboutMe", "О себе", aboutMe.trim().take(300))
            firestore.collection("users").document(user.uid)
                .update("aboutMe", aboutMe.trim().take(300)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось обновить «О себе»")) }
    }

    override suspend fun updateBirthDate(birthDate: String): ProfileUpdateResult {
        val trimmed = birthDate.trim()
        // Пустая строка — это "очистить дату рождения", разрешаем без проверки формата.
        if (trimmed.isNotEmpty()) {
            val validationError = app.yodo.messenger.util.BirthDateValidator.validate(trimmed)
            if (validationError != null) return ProfileUpdateResult.Error(validationError)
        }
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            logProfileChange("birthDate", "Дата рождения", trimmed)
            firestore.collection("users").document(user.uid)
                .update("birthDate", trimmed).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось обновить дату рождения")) }
    }

    override suspend fun updateLocation(location: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            logProfileChange("location", "Местоположение", location.trim().take(100))
            firestore.collection("users").document(user.uid)
                .update("location", location.trim().take(100)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось обновить местоположение")) }
    }

    override suspend fun updateWebsite(website: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            logProfileChange("website", "Сайт", website.trim().take(200))
            firestore.collection("users").document(user.uid)
                .update("website", website.trim().take(200)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось обновить сайт")) }
    }

    override suspend fun updatePrivacySettings(
        showBirthDate: Boolean, showAboutMe: Boolean, showLocation: Boolean,
        showWebsite: Boolean, showPhoneNumber: Boolean, showEmail: Boolean
    ): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid).update(mapOf(
                "showBirthDate" to showBirthDate, "showAboutMe" to showAboutMe,
                "showLocation" to showLocation, "showWebsite" to showWebsite,
                "showPhoneNumber" to showPhoneNumber, "showEmail" to showEmail
            )).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось обновить настройки")) }
    }

    // НОВОЕ (п.15): настройки приватности «кто может …».
    override suspend fun updatePrivacyWho(
        whoCanInviteToGroups: PrivacyWho,
        whoCanMessageMe: PrivacyWho,
        whoCanSeeMyProfile: PrivacyWho
    ): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid).update(mapOf(
                "whoCanInviteToGroups" to whoCanInviteToGroups.name,
                "whoCanMessageMe" to whoCanMessageMe.name,
                "whoCanSeeMyProfile" to whoCanSeeMyProfile.name
            )).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось обновить настройки")) }
    }

    // НОВОЕ (п.15): серверный список контактов (contactIds) — наполнение при добавлении
    // контакта по QR. Нужен, чтобы режим «Только знакомые» проверялся на стороне
    // другого пользователя (у него нет доступа к вашему локальному телефону).
    override suspend fun addContactId(uid: String) {
        val me = firebaseAuth.currentUser?.uid ?: return
        if (uid == me) return
        try {
            firestore.collection("users").document(me)
                .update("contactIds", FieldValue.arrayUnion(uid)).await()
        } catch (e: Exception) {
            // Не критично: «Только знакомые» также пропускает тех, с кем уже есть личный чат
        }
    }

    // НОВОЕ (исключения из «Кто может мне писать»): управление списком
    // messagePrivacyExceptions — пользователей, для которых настройка
    // whoCanMessageMe не действует (могут писать всегда).
    override suspend fun addMessagePrivacyException(uid: String): ProfileUpdateResult {
        val me = firebaseAuth.currentUser?.uid ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        if (uid == me) return ProfileUpdateResult.Error("Нельзя добавить самого себя")
        return try {
            firestore.collection("users").document(me)
                .update("messagePrivacyExceptions", FieldValue.arrayUnion(uid)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось добавить исключение")) }
    }

    override suspend fun removeMessagePrivacyException(uid: String): ProfileUpdateResult {
        val me = firebaseAuth.currentUser?.uid ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(me)
                .update("messagePrivacyExceptions", FieldValue.arrayRemove(uid)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось удалить исключение")) }
    }

    override suspend fun getMessagePrivacyExceptions(): List<YodoUser> {
        val me = firebaseAuth.currentUser?.uid ?: return emptyList()
        return try {
            val myDoc = firestore.collection("users").document(me).get().await()
            val ids = (myDoc.get("messagePrivacyExceptions") as? List<*>)
                ?.filterIsInstance<String>() ?: return emptyList()
            ids.mapNotNull { uid ->
                runCatching {
                    val doc = firestore.collection("users").document(uid).get().await()
                    if (doc.exists()) doc.toYodoUser(uid) else null
                }.getOrNull()
            }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun blockUser(uid: String): ProfileUpdateResult {
        val me = firebaseAuth.currentUser?.uid ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(me)
                .update("blockedUsers", FieldValue.arrayUnion(uid)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось заблокировать")) }
    }

    override suspend fun unblockUser(uid: String): ProfileUpdateResult {
        val me = firebaseAuth.currentUser?.uid ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(me)
                .update("blockedUsers", FieldValue.arrayRemove(uid)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось разблокировать")) }
    }

    override suspend fun getBlockedUsers(): List<YodoUser> {
        val me = firebaseAuth.currentUser?.uid ?: return emptyList()
        return try {
            val myDoc = firestore.collection("users").document(me).get().await()
            val blockedIds = (myDoc.get("blockedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            blockedIds.mapNotNull { getUserById(it) }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun isUserBlocked(uid: String): Boolean {
        val me = firebaseAuth.currentUser?.uid ?: return false
        return try {
            val myDoc = firestore.collection("users").document(me).get().await()
            val blockedIds = (myDoc.get("blockedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            uid in blockedIds
        } catch (e: Exception) { false }
    }

    // НОВОЕ (реальная блокировка): читаем документ другого пользователя
    // и проверяем, есть ли я в его списке заблокированных (users читаются публично).
    override suspend fun isBlockedBy(uid: String): Boolean {
        val me = firebaseAuth.currentUser?.uid ?: return false
        return try {
            val theirDoc = firestore.collection("users").document(uid).get().await()
            val theirBlocked = (theirDoc.get("blockedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            me in theirBlocked
        } catch (e: Exception) { false }
    }

    // НОВОЕ (AD): глобальная блокировка аккаунта администратором приложения (2 почты).
    private fun globalBlocksRef() = firestore.collection("globalBlocks")

    private fun isRootAdmin(): Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }

    private suspend fun isAdminEmail(): Boolean {
        val email = firebaseAuth.currentUser?.email?.lowercase()
        if (email in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }) return true
        val uid = firebaseAuth.currentUser?.uid ?: return false
        return runCatching {
            val d = firestore.collection("admins").document(uid).get().await()
            d.exists() && d.getBoolean("enabled") != false
        }.getOrDefault(false)
    }

    private fun parseGlobalBlock(uid: String, data: Map<String, Any?>) = GlobalBlock(
        userId = uid,
        reason = data["reason"] as? String ?: "",
        blockedBy = data["blockedBy"] as? String ?: "",
        blockedByName = data["blockedByName"] as? String ?: "",
        blockedAt = (data["blockedAt"] as? Number)?.toLong() ?: 0L
    )

    override fun observeMyGlobalBlock(): Flow<GlobalBlock?> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) { trySend(null); close(); return@callbackFlow }
        val reg = globalBlocksRef().document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                @Suppress("UNCHECKED_CAST")
                val data = snapshot.data as? Map<String, Any?> ?: emptyMap()
                trySend(parseGlobalBlock(uid, data))
            } else {
                trySend(null)
            }
        }
        awaitClose { reg.remove() }
    }

    // НОВОЕ (глобальный аудит-лог + push о модерации): запись в корневую
    // коллекцию adminAuditLog — история действий Админки, не привязанных к
    // конкретному чату. Отдельно от chats/{chatId}/adminLog (ChatRepositoryImpl.
    // logAdminAction), который остаётся только для чатовых/групповых действий.
    private suspend fun logGlobalAdminAction(
        actionType: GlobalAdminActionType,
        details: String = "",
        targetUserId: String? = null,
        targetUserName: String? = null
    ) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val actorName = firestore.collection("users").document(uid).get().await()
                .getString("displayName") ?: "Админ"
            val entry = mapOf(
                "actorId" to uid,
                "actorName" to actorName,
                "actionType" to actionType.name,
                "details" to details,
                "targetUserId" to targetUserId,
                "targetUserName" to targetUserName,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("adminAuditLog").add(entry).await()
        } catch (e: Exception) { }
    }

    // НОВОЕ (push о модерации): кладёт запись в очередь moderationNotifications,
    // которую периодически вычитывает GitHub Actions (.github/scripts/send-push-notifications.js) (та же схема, что и
    // очередь notified==false в messages, только для событий модерации, а не
    // сообщений чата). Клиент получает push через YodoFirebaseMessagingService
    // с data.type == "moderation" и показывает его через
    // NotificationHelper.showModerationNotification — без диплинка в чат.
    private suspend fun queueModerationNotification(userId: String, title: String, body: String) {
        try {
            firestore.collection("moderationNotifications").add(
                mapOf(
                    "userId" to userId,
                    "title" to title,
                    "body" to body,
                    "notified" to false,
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) { }
    }

    override suspend fun setGlobalBlock(uid: String, reason: String): ProfileUpdateResult {
        if (!isAdminEmail()) return ProfileUpdateResult.Error("Нет прав администратора")
        val me = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            val myName = firestore.collection("users").document(me.uid).get().await()
                .getString("displayName") ?: (me.email ?: "Админ")
            globalBlocksRef().document(uid).set(mapOf(
                "reason" to reason.take(500),
                "blockedBy" to me.uid,
                "blockedByName" to myName,
                "blockedAt" to System.currentTimeMillis()
            )).await()
            val targetName = firestore.collection("users").document(uid).get().await()
                .getString("displayName")
            logGlobalAdminAction(
                GlobalAdminActionType.USER_GLOBALLY_BLOCKED,
                details = reason.take(500),
                targetUserId = uid,
                targetUserName = targetName
            )
            // НОВОЕ (push о модерации): уведомляем заблокированного пользователя.
            queueModerationNotification(
                userId = uid,
                title = "Аккаунт заблокирован",
                body = if (reason.isNotBlank()) "Причина: ${reason.take(200)}" else "Ваш аккаунт заблокирован администрацией"
            )
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось заблокировать аккаунт")) }
    }

    override suspend fun removeGlobalBlock(uid: String): ProfileUpdateResult {
        if (!isAdminEmail()) return ProfileUpdateResult.Error("Нет прав администратора")
        return try {
            globalBlocksRef().document(uid).delete().await()
            val targetName = firestore.collection("users").document(uid).get().await()
                .getString("displayName")
            logGlobalAdminAction(
                GlobalAdminActionType.USER_GLOBALLY_UNBLOCKED,
                targetUserId = uid,
                targetUserName = targetName
            )
            // НОВОЕ (push о модерации): уведомляем разблокированного пользователя.
            queueModerationNotification(
                userId = uid,
                title = "Блокировка снята",
                body = "Доступ к аккаунту восстановлен"
            )
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.toUserMessage("Не удалось снять блокировку")) }
    }

    override suspend fun getGlobalBlock(uid: String): GlobalBlock? {
        return try {
            val doc = globalBlocksRef().document(uid).get().await()
            if (!doc.exists()) return null
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return null
            parseGlobalBlock(uid, data)
        } catch (e: Exception) { null }
    }

    // НОВОЕ (глобальный аудит-лог): публичная обёртка над logGlobalAdminAction
    // для события изменения настройки "требовать подтверждение email" —
    // вызывается из AdminHomeViewModel сразу после AppSettingsRepository.
    override suspend fun getAdminUsers(): List<YodoUser> {
        if (!isAdminEmail()) return emptyList()
        return try {
            firestore.collection("users").get().await().documents.map { it.toYodoUser(it.id) }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getAdminUserModerationStats(): Map<String, AdminUserModerationStats> {
        if (!isAdminEmail()) return emptyMap()
        return try {
            val reportCounts = mutableMapOf<String, Int>()
            firestore.collectionGroup("reports").get().await().documents.forEach { d ->
                val uid = d.getString("targetUserId") ?: return@forEach
                reportCounts[uid] = (reportCounts[uid] ?: 0) + 1
            }
            val blockCounts = mutableMapOf<String, Int>()
            firestore.collectionGroup("blockHistory")
                .whereEqualTo("action", "BLOCK")
                .get().await().documents.forEach { d ->
                    val uid = d.reference.parent.parent?.id ?: return@forEach
                    blockCounts[uid] = (blockCounts[uid] ?: 0) + 1
                }
            (reportCounts.keys + blockCounts.keys).associateWith { uid ->
                val reports = reportCounts[uid] ?: 0
                val blocks = blockCounts[uid] ?: 0
                AdminUserModerationStats(reports, blocks, reports >= 3 || blocks >= 3)
            }
        } catch (_: Exception) { emptyMap() }
    }

    override suspend fun updateUsersClass(uids: List<String>, classId: String): ProfileUpdateResult {
        if (!isAdminEmail()) return ProfileUpdateResult.Error("Нет прав администратора")
        val ids = uids.map(String::trim).filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return ProfileUpdateResult.Error("Не выбраны пользователи")
        if (classId.length > 80) return ProfileUpdateResult.Error("Слишком длинное название класса")
        return try {
            val batch = firestore.batch()
            ids.forEach { uid ->
                batch.update(firestore.collection("users").document(uid), "classId", classId.ifBlank { null })
            }
            batch.commit().await()
            logGlobalAdminAction(
                GlobalAdminActionType.USER_CLASS_CHANGED,
                details = "Изменён класс у ${ids.size} пользователей: ${classId.ifBlank { "не указан" }}"
            )
            ProfileUpdateResult.Success
        } catch (e: Exception) {
            ProfileUpdateResult.Error(e.toUserMessage("Не удалось изменить класс пользователей"))
        }
    }

    override suspend fun getAdminUserDetails(uid: String): AdminUserDetails? {
        if (!isAdminEmail()) return null
        return try {
            val user = getUserById(uid) ?: return null
            val loginDocs = firestore.collection("adminLoginHistory").document(uid)
                .collection("entries").orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100).get().await().documents
            val logins = loginDocs.map { d ->
                AdminLoginEntry(
                    id = d.id,
                    timestamp = d.getLong("timestamp") ?: 0L,
                    provider = d.getString("provider") ?: "unknown",
                    device = d.getString("device") ?: "",
                    ipAddress = d.getString("ipAddress")
                )
            }

            val blockDocs = firestore.collection("users").document(uid).collection("blockHistory")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50).get().await().documents
            val blockHistory = blockDocs.map { d ->
                AdminBlockHistoryEntry(
                    id = d.id,
                    action = d.getString("action") ?: "",
                    reason = d.getString("reason") ?: "",
                    description = d.getString("description") ?: "",
                    byName = d.getString("byName") ?: "",
                    timestamp = d.getLong("timestamp") ?: 0L
                )
            }

            val chats = firestore.collection("chats")
                .whereArrayContains("participantIds", uid).get().await().documents
            val groups = chats.filter { it.getString("type") == "GROUP" || it.getString("type") == "CHANNEL" }
                .map { d ->
                    val admins = (d.get("adminIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val role = when {
                        d.getString("createdBy") == uid -> "Владелец"
                        uid in admins -> "Администратор"
                        else -> "Участник"
                    }
                    AdminGroupChannelEntry(
                        chatId = d.id,
                        title = d.getString("title") ?: "Без названия",
                        type = d.getString("type") ?: "GROUP",
                        role = role
                    )
                }

            val messagesSent = try {
                firestore.collectionGroup("messages").whereEqualTo("senderId", uid).get().await().size()
            } catch (_: Exception) { 0 }

            val reportsReceived = try {
                firestore.collectionGroup("reports").whereEqualTo("targetUserId", uid).get().await().size()
            } catch (_: Exception) { 0 }

            val ips = logins.mapNotNull { it.ipAddress }.filter { it.isNotBlank() && it != "unknown" }.distinct()
            val cutoff = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
            val activityDocs = loginDocs.filter { (it.getLong("timestamp") ?: 0L) >= cutoff }
            val activityMap = linkedMapOf<Long, Int>()
            activityDocs.forEach { d ->
                val ts = d.getLong("timestamp") ?: return@forEach
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
                val day = cal.timeInMillis
                activityMap[day] = (activityMap[day] ?: 0) + 1
            }
            val activity = (0..29).map { offset ->
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -offset)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
                val day = cal.timeInMillis
                AdminActivityPoint(dayStart = day, logins = activityMap[day] ?: 0, total = activityMap[day] ?: 0)
            }.reversed()
            val actionHistory = try {
                firestore.collection("adminAuditLog")
                    .whereEqualTo("targetUserId", uid)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await().documents.map { d ->
                        AdminTimelineEntry(
                            id = d.id, type = "ACTION", title = d.getString("actionType") ?: "Действие",
                            details = d.getString("details") ?: "", timestamp = d.getLong("timestamp") ?: 0L,
                            actorName = d.getString("actorName") ?: "Админ"
                        )
                    }
            } catch (_: Exception) { emptyList() }
            val reportHistory = try {
                firestore.collectionGroup("reports")
                    .whereEqualTo("targetUserId", uid)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await().documents.map { d ->
                        AdminTimelineEntry(
                            id = d.id, type = "REPORT", title = d.getString("reason") ?: "Жалоба",
                            details = d.getString("description") ?: d.getString("details") ?: "",
                            timestamp = d.getLong("createdAt") ?: 0L, status = d.getString("status") ?: ""
                        )
                    }
            } catch (_: Exception) { emptyList() }
            val messageHistory = try {
                firestore.collectionGroup("messages")
                    .whereEqualTo("senderId", uid)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await().documents.map { d ->
                        AdminTimelineEntry(
                            id = d.id, type = "MESSAGE", title = "Сообщение",
                            details = d.getString("text")?.take(240) ?: d.getString("content")?.take(240) ?: "[без текста]",
                            timestamp = d.getLong("timestamp") ?: 0L,
                            status = if (d.getBoolean("isDeleted") == true) "Удалено" else ""
                        )
                    }
            } catch (_: Exception) { emptyList() }
            val appealHistory = try {
                val result = mutableListOf<AdminTimelineEntry>()
                listOf("appeals", "supportRequests").forEach { collectionName ->
                    runCatching {
                        firestore.collection(collectionName).whereEqualTo("userId", uid)
                            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .limit(50).get().await().documents.forEach { d ->
                                result += AdminTimelineEntry(
                                    id = d.id, type = "APPEAL", title = d.getString("title") ?: "Обращение",
                                    details = d.getString("message") ?: d.getString("text") ?: "",
                                    timestamp = d.getLong("createdAt") ?: 0L, status = d.getString("status") ?: ""
                                )
                            }
                    }
                }
                result.sortedByDescending { it.timestamp }.take(50)
            } catch (_: Exception) { emptyList() }

            val assignment = getAdminAssignment(uid)
            AdminUserDetails(
                user = user, loginHistory = logins.take(5), messagesSent = messagesSent,
                reportsReceived = reportsReceived, groupsAndChannels = groups, ipAddresses = ips,
                blockHistory = blockHistory, activity = activity, adminAssignment = assignment,
                actionHistory = actionHistory, reportHistory = reportHistory, messageHistory = messageHistory,
                appealHistory = appealHistory
            )
        } catch (e: Exception) { null }
    }


    override suspend fun assignAdmin(uid: String, role: AdminRole): ProfileUpdateResult {
        if (!isRootAdmin()) return ProfileUpdateResult.Error("Только главный администратор может менять роли")
        if (uid.isBlank()) return ProfileUpdateResult.Error("Некорректный пользователь")
        if (role == AdminRole.SUPER_ADMIN && firebaseAuth.currentUser?.uid == uid) {
            return ProfileUpdateResult.Error("Нельзя менять собственную роль")
        }
        return try {
            val me = firebaseAuth.currentUser?.uid ?: return ProfileUpdateResult.Error("Нет активной сессии")
            val now = System.currentTimeMillis()
            firestore.collection("admins").document(uid).set(
                mapOf("uid" to uid, "role" to role.name, "assignedAt" to now, "assignedBy" to me, "enabled" to true)
            ).await()
            firestore.collection("users").document(uid).update("adminRole", role.name).await()
            logGlobalAdminAction(
                GlobalAdminActionType.ADMIN_ROLE_CHANGED,
                details = "Назначен ${role.label}",
                targetUserId = uid,
                targetUserName = firestore.collection("users").document(uid).get().await().getString("displayName")
            )
            ProfileUpdateResult.Success
        } catch (e: Exception) {
            ProfileUpdateResult.Error(e.toUserMessage())
        }
    }

    override suspend fun removeAdmin(uid: String): ProfileUpdateResult {
        if (!isRootAdmin()) return ProfileUpdateResult.Error("Только главный администратор может менять роли")
        if (uid == firebaseAuth.currentUser?.uid) return ProfileUpdateResult.Error("Нельзя снять администратора с себя")
        return try {
            firestore.collection("admins").document(uid).delete().await()
            firestore.collection("users").document(uid).update("adminRole", null).await()
            logGlobalAdminAction(GlobalAdminActionType.ADMIN_ROLE_CHANGED, details = "Администратор снят", targetUserId = uid)
            ProfileUpdateResult.Success
        } catch (e: Exception) {
            ProfileUpdateResult.Error(e.toUserMessage())
        }
    }

    override suspend fun getAdminAssignment(uid: String): AdminAssignment? {
        return try {
            val d = firestore.collection("admins").document(uid).get().await()
            if (!d.exists() || d.getBoolean("enabled") == false) return null
            val role = runCatching { AdminRole.valueOf(d.getString("role") ?: "ADMIN") }.getOrDefault(AdminRole.ADMIN)
            AdminAssignment(uid, role, d.getLong("assignedAt") ?: 0L, d.getString("assignedBy") ?: "", d.getBoolean("enabled") ?: true)
        } catch (_: Exception) { null }
    }

    override suspend fun getMyAdminAssignment(): AdminAssignment? =
        firebaseAuth.currentUser?.uid?.let { getAdminAssignment(it) }

    override suspend fun setGlobalBlockWithHistory(
        uid: String,
        reason: String,
        description: String
    ): ProfileUpdateResult {
        val result = setGlobalBlock(uid, if (description.isBlank()) reason else "$reason — $description")
        if (result is ProfileUpdateResult.Success) {
            writeBlockHistory(uid, "BLOCK", reason, description)
        }
        return result
    }

    override suspend fun removeGlobalBlockWithHistory(uid: String): ProfileUpdateResult {
        val result = removeGlobalBlock(uid)
        if (result is ProfileUpdateResult.Success) {
            writeBlockHistory(uid, "UNBLOCK", "", "")
        }
        return result
    }

    private suspend fun writeBlockHistory(
        uid: String,
        action: String,
        reason: String,
        description: String
    ) {
        if (!isAdminEmail()) return
        runCatching {
            val me = firebaseAuth.currentUser ?: return@runCatching
            val name = firestore.collection("users").document(me.uid).get().await()
                .getString("displayName") ?: me.email ?: "Администратор"
            firestore.collection("users").document(uid).collection("blockHistory").add(
                mapOf(
                    "action" to action,
                    "reason" to reason.take(200),
                    "description" to description.take(1000),
                    "byId" to me.uid,
                    "byName" to name,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
        }
    }

    override suspend fun logRequireEmailVerificationChanged(enabled: Boolean) {
        if (!isAdminEmail()) return
        logGlobalAdminAction(
            GlobalAdminActionType.REQUIRE_EMAIL_VERIFICATION_CHANGED,
            details = if (enabled) "Включено" else "Выключено"
        )
    }

    // НОВОЕ (глобальный аудит-лог): чтение журнала для AdminAuditLogScreen.
    // Доступно только двум главным админам — проверяется и здесь (защита UI),
    // и в firestore.rules (защита данных).
    override suspend fun getGlobalAuditLog(
        limit: Int,
        startAfterTimestamp: Long?
    ): List<GlobalAdminLogEntry> {
        if (!isAdminEmail()) return emptyList()
        return try {
            var query = firestore.collection("adminAuditLog")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
            if (startAfterTimestamp != null) {
                query = query.startAfter(startAfterTimestamp)
            }
            query.get().await().documents.mapNotNull { doc ->
                val actionTypeName = doc.getString("actionType") ?: return@mapNotNull null
                val actionType = runCatching { GlobalAdminActionType.valueOf(actionTypeName) }.getOrNull()
                    ?: return@mapNotNull null
                GlobalAdminLogEntry(
                    id = doc.id,
                    actorId = doc.getString("actorId") ?: "",
                    actorName = doc.getString("actorName") ?: "",
                    actionType = actionType,
                    details = doc.getString("details") ?: "",
                    targetUserId = doc.getString("targetUserId"),
                    targetUserName = doc.getString("targetUserName"),
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toYodoUser(uid: String) = YodoUser(
        uid = uid,
        displayName = getString("displayName") ?: "",
        username = getString("username"),
        bio = getString("bio"),
        email = getString("email"),
        phoneNumber = getString("phoneNumber"),
        photoUrl = getString("avatarUrl"),
        avatarBase64 = getString("avatarBase64"),
        aboutMe = getString("aboutMe"),
        birthDate = getString("birthDate"),
        location = getString("location"),
        website = getString("website"),
        showBirthDate = getBoolean("showBirthDate") ?: true,
        showAboutMe = getBoolean("showAboutMe") ?: true,
        showLocation = getBoolean("showLocation") ?: true,
        showWebsite = getBoolean("showWebsite") ?: true,
        showPhoneNumber = getBoolean("showPhoneNumber") ?: false,
        showEmail = getBoolean("showEmail") ?: false,
        publicKey = getString("publicKey"),
        publicId = getString("publicId"),
        emojiStatus = getString("emojiStatus"),
        customStatus = getString("customStatus"),
        isEmailVerified = getBoolean("isEmailVerified") ?: false,
        createdAt = getLong("createdAt") ?: 0L,
        lastActiveAt = getLong("lastActiveAt") ?: 0L,
        country = getString("country"),
        classId = getString("classId") ?: getString("class_id"),
        accountStatus = getString("accountStatus"),
        adminRole = getString("adminRole"),
        whoCanInviteToGroups = PrivacyWho.fromString(getString("whoCanInviteToGroups")),
        whoCanMessageMe = PrivacyWho.fromString(getString("whoCanMessageMe")),
        whoCanSeeMyProfile = PrivacyWho.fromString(getString("whoCanSeeMyProfile")),
        messagePrivacyExceptions = (get("messagePrivacyExceptions") as? List<*>)
            ?.filterIsInstance<String>() ?: emptyList()
    )

    override suspend fun getBehaviorMonitoringSnapshot(): app.yodo.messenger.domain.model.BehaviorMonitoringSnapshot {
        if (!isAdminEmail()) return app.yodo.messenger.domain.model.BehaviorMonitoringSnapshot()
        return try {
            val now = System.currentTimeMillis()
            val hourAgo = now - 60L * 60 * 1000
            val tenMinutesAgo = now - 10L * 60 * 1000

            val users = firestore.collection("users").get().await().documents
                .map { it.toYodoUser(it.id) }
            val byId = users.associateBy { it.uid }

            val reportCounts = mutableMapOf<String, Int>()
            val reportTotals = mutableMapOf<String, Int>()
            firestore.collectionGroup("reports").get().await().documents.forEach { d ->
                val uid = d.getString("targetUserId") ?: return@forEach
                reportTotals[uid] = (reportTotals[uid] ?: 0) + 1
                val createdAt = d.getLong("createdAt") ?: 0L
                if (createdAt >= hourAgo) reportCounts[uid] = (reportCounts[uid] ?: 0) + 1
            }

            val messageCounts = mutableMapOf<String, Int>()
            firestore.collectionGroup("messages")
                .whereGreaterThanOrEqualTo("timestamp", tenMinutesAgo)
                .get().await().documents.forEach { d ->
                    if (d.getBoolean("isDeleted") == true) return@forEach
                    val uid = d.getString("senderId") ?: return@forEach
                    messageCounts[uid] = (messageCounts[uid] ?: 0) + 1
                }

            val deleteCounts = mutableMapOf<String, Int>()
            firestore.collection("behaviorEvents")
                .whereEqualTo("type", "CHAT_DELETED")
                .whereGreaterThanOrEqualTo("createdAt", hourAgo)
                .get().await().documents.forEach { d ->
                    val uid = d.getString("userId") ?: return@forEach
                    deleteCounts[uid] = (deleteCounts[uid] ?: 0) + 1
                }

            val blockCounts = mutableMapOf<String, Int>()
            firestore.collectionGroup("blockHistory")
                .whereEqualTo("action", "BLOCK")
                .get().await().documents.forEach { d ->
                    val uid = d.reference.parent.parent?.id ?: return@forEach
                    blockCounts[uid] = (blockCounts[uid] ?: 0) + 1
                }

            val difficult = users.mapNotNull { user ->
                val reportTotal = reportTotals[user.uid] ?: 0
                val blocks = blockCounts[user.uid] ?: 0
                if (reportTotal >= 3 || blocks >= 3) {
                    app.yodo.messenger.domain.model.DifficultUser(user, reportTotal, blocks)
                } else null
            }.sortedByDescending { it.totalFlags }

            val alerts = mutableListOf<app.yodo.messenger.domain.model.BehaviorAlert>()
            reportCounts.filter { it.value >= 3 }.forEach { (uid, count) ->
                val u = byId[uid] ?: return@forEach
                alerts += app.yodo.messenger.domain.model.BehaviorAlert(
                    uid, u.displayName, "REPORTS_SPIKE",
                    "Много жалоб за короткое время",
                    "$count жалоб за последний час", count, 60, now
                )
            }
            messageCounts.filter { it.value >= 20 }.forEach { (uid, count) ->
                val u = byId[uid] ?: return@forEach
                alerts += app.yodo.messenger.domain.model.BehaviorAlert(
                    uid, u.displayName, "MASS_MESSAGING",
                    "Массовая отправка сообщений",
                    "$count сообщений за последние 10 минут", count, 10, now
                )
            }
            deleteCounts.filter { it.value >= 3 }.forEach { (uid, count) ->
                val u = byId[uid] ?: return@forEach
                alerts += app.yodo.messenger.domain.model.BehaviorAlert(
                    uid, u.displayName, "CHAT_DELETIONS",
                    "Частое удаление чатов",
                    "$count удалений чатов за последний час", count, 60, now
                )
            }

            app.yodo.messenger.domain.model.BehaviorMonitoringSnapshot(
                alerts = alerts.sortedByDescending { it.count },
                difficultUsers = difficult,
                newcomerPauseEnabled = isNewcomerMessagingPauseEnabled()
            )
        } catch (_: Exception) {
            app.yodo.messenger.domain.model.BehaviorMonitoringSnapshot(
                newcomerPauseEnabled = isNewcomerMessagingPauseEnabled()
            )
        }
    }

    override suspend fun setNewcomerMessagingPauseEnabled(enabled: Boolean): ProfileUpdateResult {
        if (!isAdminEmail()) return ProfileUpdateResult.Error("Нет прав администратора")
        return try {
            firestore.collection("config").document("behaviorMonitoring")
                .set(mapOf("newcomerPauseEnabled" to enabled), com.google.firebase.firestore.SetOptions.merge())
                .await()
            ProfileUpdateResult.Success
        } catch (e: Exception) {
            ProfileUpdateResult.Error(e.toUserMessage("Не удалось изменить настройку"))
        }
    }

    override suspend fun isNewcomerMessagingPauseEnabled(): Boolean {
        return try {
            firestore.collection("config").document("behaviorMonitoring").get().await()
                .getBoolean("newcomerPauseEnabled") ?: true
        } catch (_: Exception) { true }
    }

}
