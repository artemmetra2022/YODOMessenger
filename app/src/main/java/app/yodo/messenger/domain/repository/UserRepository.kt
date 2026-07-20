package app.yodo.messenger.domain.repository

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

    /** Проверяет уникальность и сохраняет @username (без символа @, только сам идентификатор). */
    suspend fun updateUsername(username: String): ProfileUpdateResult

    /** Загружает аватар в Firebase Storage и обновляет photoUrl в Auth-профиле и Firestore. */
    suspend fun uploadAvatar(imageUri: Uri): ProfileUpdateResult

    /** Поиск пользователей по началу имени или @username (без учёта регистра). Исключает текущего пользователя. */
    suspend fun searchUsers(query: String): List<YodoUser>

    /** Получить публичный профиль конкретного пользователя (для экрана "Профиль собеседника"). */
    suspend fun getUserById(uid: String): YodoUser?
}
