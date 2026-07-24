package app.yodo.messenger.domain.repository

import android.graphics.Bitmap
import android.net.Uri
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
    suspend fun updateUsername(username: String): ProfileUpdateResult
    suspend fun uploadAvatar(imageUri: Uri): ProfileUpdateResult
    // Загрузка аватара из уже скадрированного/смещённого пользователем битмапа
    // (результат экрана перемещения и масштабирования аватарки).
    suspend fun uploadAvatar(bitmap: Bitmap): ProfileUpdateResult
    suspend fun searchUsers(query: String): List<YodoUser>
    suspend fun getUserById(uid: String): YodoUser?
    suspend fun updateAboutMe(aboutMe: String): ProfileUpdateResult
    suspend fun updateBirthDate(birthDate: String): ProfileUpdateResult
    suspend fun updateLocation(location: String): ProfileUpdateResult
    suspend fun updateWebsite(website: String): ProfileUpdateResult
    suspend fun updatePrivacySettings(
        showBirthDate: Boolean, showAboutMe: Boolean, showLocation: Boolean,
        showWebsite: Boolean, showPhoneNumber: Boolean, showEmail: Boolean
    ): ProfileUpdateResult
    suspend fun blockUser(uid: String): ProfileUpdateResult
    suspend fun unblockUser(uid: String): ProfileUpdateResult
    suspend fun getBlockedUsers(): List<YodoUser>
    suspend fun isUserBlocked(uid: String): Boolean
}
