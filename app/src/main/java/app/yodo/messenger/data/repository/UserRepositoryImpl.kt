package app.yodo.messenger.data.repository

import android.graphics.Bitmap
import android.net.Uri
import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.domain.model.GlobalAdminActionType
import app.yodo.messenger.domain.model.GlobalAdminLogEntry
import app.yodo.messenger.domain.model.GlobalBlock
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

    private fun isAdminEmail(): Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS

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
    // которую периодически вычитывает push-worker/index.js (та же схема, что и
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
        whoCanInviteToGroups = PrivacyWho.fromString(getString("whoCanInviteToGroups")),
        whoCanMessageMe = PrivacyWho.fromString(getString("whoCanMessageMe")),
        whoCanSeeMyProfile = PrivacyWho.fromString(getString("whoCanSeeMyProfile")),
        messagePrivacyExceptions = (get("messagePrivacyExceptions") as? List<*>)
            ?.filterIsInstance<String>() ?: emptyList()
    )
}
