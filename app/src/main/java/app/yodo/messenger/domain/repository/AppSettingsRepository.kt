package app.yodo.messenger.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Глобальные настройки приложения, хранящиеся в одном документе Firestore
 * (config/appSettings) и применяющиеся ко всем пользователям. Читать может
 * кто угодно (нужно до полноценного логина), менять — только два доверенных
 * email-адреса (см. ChatRepository.ADMIN_EMAILS / firestore.rules).
 */
interface AppSettingsRepository {

    /**
     * Требовать ли подтверждение почты при входе (существующая проверка
     * isEmailVerified в AuthRepositoryImpl.login). Если true — как и раньше,
     * пользователь с неподтверждённой почтой не пускается в приложение и
     * видит экран "подтвердите почту". Если false — эта проверка
     * пропускается, неподтверждённые email тоже проходят. На уже
     * подтверждённые email флаг никак не влияет.
     */
    fun observeRequireEmailVerification(): Flow<Boolean>

    /** Разовое чтение (используется в момент логина, до полноценной подписки). */
    suspend fun isEmailVerificationRequired(): Boolean

    /** Меняет флаг. Возвращает false, если у текущего пользователя нет прав (не админ). */
    suspend fun setRequireEmailVerification(enabled: Boolean): Boolean
}
