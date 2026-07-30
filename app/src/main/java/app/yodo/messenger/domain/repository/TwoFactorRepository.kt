package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.TwoFactorState
import kotlinx.coroutines.flow.Flow

/**
 * Облачный пароль (двухэтапная аутентификация), аналог "Cloud Password" в Telegram.
 * Хранится в Firestore в виде соли + хэша (PBKDF2) — сам пароль никогда не сохраняется
 * и не передаётся в открытом виде.
 */
interface TwoFactorRepository {

    /** Текущее состояние (включён ли пароль, задана ли подсказка) в реальном времени. */
    fun observeState(): Flow<TwoFactorState>

    /** Разовая проверка, включена ли двухэтапная аутентификация у текущего пользователя. */
    suspend fun isEnabled(): Boolean

    /** Включает облачный пароль (или меняет существующий пароль на новый). */
    suspend fun setPassword(newPassword: String, hint: String?): Boolean

    /**
     * Проверяет, совпадает ли введённый пароль с сохранённым.
     * Возвращает true, если пароль верен.
     */
    suspend fun verifyPassword(password: String): Boolean

    /** Отключает облачный пароль. Требует текущий пароль для подтверждения. */
    suspend fun disable(currentPassword: String): Boolean
}
