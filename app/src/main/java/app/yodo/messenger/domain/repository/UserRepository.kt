package app.yodo.messenger.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import app.yodo.messenger.domain.model.GlobalBlock
import app.yodo.messenger.domain.model.PrivacyWho
import app.yodo.messenger.domain.model.ProfileHistoryEntry
import app.yodo.messenger.domain.model.YodoUser
import kotlinx.coroutines.flow.Flow

sealed class ProfileUpdateResult {
    data object Success : ProfileUpdateResult()
    data class Error(val message: String) : ProfileUpdateResult()
}

interface UserRepository {
    fun observeCurrentUser(): Flow<YodoUser?>
    suspend fun updateDisplayName(name: String): ProfileUpdateResult
    suspend fun updateBio(bio: String): ProfileUpdateResult
    suspend fun updateEmojiStatus(emoji: String): ProfileUpdateResult
    suspend fun updateCustomStatus(status: String): ProfileUpdateResult
    suspend fun updateUsername(username: String): ProfileUpdateResult
    suspend fun uploadAvatar(imageUri: Uri): ProfileUpdateResult
    // Загрузка аватара из уже скадрированного/смещённого пользователем битмапа
    // (результат экрана перемещения и масштабирования аватарки).
    suspend fun uploadAvatar(bitmap: Bitmap): ProfileUpdateResult
    suspend fun searchUsers(query: String): List<YodoUser>
    // НОВОЕ: пакетный поиск зарегистрированных пользователей по номерам телефонов
    // (для экрана "Контакты" — сопоставление телефонной книги с аккаунтами Yodo).
    // phoneNumbers ожидаются в нормализованном виде (например, +79991234567).
    suspend fun getUsersByPhoneNumbers(phoneNumbers: List<String>): List<YodoUser>
    suspend fun getUserById(uid: String): YodoUser?
    suspend fun updateAboutMe(aboutMe: String): ProfileUpdateResult
    suspend fun updateBirthDate(birthDate: String): ProfileUpdateResult
    suspend fun updateLocation(location: String): ProfileUpdateResult
    suspend fun updateWebsite(website: String): ProfileUpdateResult
    suspend fun updatePrivacySettings(
        showBirthDate: Boolean, showAboutMe: Boolean, showLocation: Boolean,
        showWebsite: Boolean, showPhoneNumber: Boolean, showEmail: Boolean
    ): ProfileUpdateResult
    // НОВОЕ (п.15): настройки приватности «кто может …» — приглашать в группы,
    // писать в личку, смотреть профиль. Хранятся в документе пользователя в Firestore.
    suspend fun updatePrivacyWho(
        whoCanInviteToGroups: PrivacyWho,
        whoCanMessageMe: PrivacyWho,
        whoCanSeeMyProfile: PrivacyWho
    ): ProfileUpdateResult
    // НОВОЕ (п.15): добавить пользователя в мой серверный список контактов
    // (users/{uid}.contactIds) — по нему работает режим «Только знакомые».
    suspend fun addContactId(uid: String)
    // НОВОЕ (исключения из «Кто может мне писать»): пользователи из этого списка
    // могут писать мне всегда, даже если whoCanMessageMe == NOBODY/CONTACTS.
    suspend fun addMessagePrivacyException(uid: String): ProfileUpdateResult
    suspend fun removeMessagePrivacyException(uid: String): ProfileUpdateResult
    suspend fun getMessagePrivacyExceptions(): List<YodoUser>
    suspend fun blockUser(uid: String): ProfileUpdateResult
    suspend fun unblockUser(uid: String): ProfileUpdateResult
    suspend fun getBlockedUsers(): List<YodoUser>
    suspend fun isUserBlocked(uid: String): Boolean
    // НОВОЕ (реальная блокировка): заблокировал ли данный пользователь меня.
    suspend fun isBlockedBy(uid: String): Boolean
    // НОВОЕ (История изменений профиля): журнал изменений (от новых к старым).
    suspend fun getProfileHistory(): List<ProfileHistoryEntry>

    // НОВОЕ (AD): глобальная блокировка аккаунта администратором приложения.
    /** Наблюдаем глобальную блокировку текущего пользователя (null — не заблокирован). */
    fun observeMyGlobalBlock(): Flow<GlobalBlock?>
    /** Заблокировать аккаунт в приложении (только 2 почты-админа). */
    suspend fun setGlobalBlock(uid: String, reason: String): ProfileUpdateResult
    /** Снять глобальную блокировку. */
    suspend fun removeGlobalBlock(uid: String): ProfileUpdateResult
    /** Глобальная блокировка конкретного пользователя (для админ-UI). */
    suspend fun getGlobalBlock(uid: String): GlobalBlock?

    // НОВОЕ (глобальный аудит-лог): чтение журнала для AdminAuditLogScreen.
    // Доступно только двум главным админам — проверяется и здесь (защита UI),
    // и в firestore.rules (защита данных).
    suspend fun getGlobalAuditLog(
        limit: Int = 50,
        startAfterTimestamp: Long? = null
    ): List<app.yodo.messenger.domain.model.GlobalAdminLogEntry>

    // НОВОЕ (глобальный аудит-лог): запись события "изменение обязательного
    // подтверждения email" — вызывается из AdminHomeViewModel рядом с
    // AppSettingsRepository.setRequireEmailVerification, чтобы это изменение
    // тоже попадало в общий журнал действий Админки.
    suspend fun logRequireEmailVerificationChanged(enabled: Boolean)
}
