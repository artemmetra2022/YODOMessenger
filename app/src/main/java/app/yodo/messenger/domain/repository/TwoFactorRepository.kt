package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.TwoFactorState
import kotlinx.coroutines.flow.Flow

/**
 * Двухфакторная аутентификация по email-коду.
 * При включении единственный дополнительный шаг при входе — 6-значный
 * код, который приходит на почту, к которой привязан аккаунт.
 */
interface TwoFactorRepository {

    /** Текущее состояние (включена ли 2FA) в реальном времени. */
    fun observeState(): Flow<TwoFactorState>

    /** Разовая проверка, включена ли двухфакторная аутентификация у текущего пользователя. */
    suspend fun isEnabled(): Boolean

    /** Включает 2FA по email-коду. */
    suspend fun enable(): Boolean

    /**
     * Отключает 2FA. Требует подтверждения свежим email-кодом
     * (запрашивается через sendEmailCode перед вызовом).
     */
    suspend fun disable(emailCode: String): Boolean

    /**
     * Запрашивает отправку 6-значного кода на почту, к которой привязан аккаунт.
     * Используется и при входе (второй фактор), и при отключении 2FA в настройках.
     * Возвращает замаскированный адрес почты (например "п***н@gmail.com") для
     * показа в UI, либо ошибку.
     */
    suspend fun sendEmailCode(): TwoFactorEmailSendResult

    /** Проверяет введённый пользователем 6-значный код (сравнение хэша). */
    suspend fun verifyEmailCode(code: String): Boolean
}

/** Результат запроса на отправку email-кода. */
sealed class TwoFactorEmailSendResult {
    data class Success(val maskedEmail: String) : TwoFactorEmailSendResult()
    data class Error(val message: String) : TwoFactorEmailSendResult()
}
