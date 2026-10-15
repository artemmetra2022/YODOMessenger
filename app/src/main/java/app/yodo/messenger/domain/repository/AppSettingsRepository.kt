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

    // ─────────────────────── Раздел «Школа»

    /**
     * Скрыт ли раздел «Школа» у всех пользователей (кнопка в Настройки →
     * Аккаунт). Админы видят кнопку всегда — иначе нечем вернуть раздел.
     * По умолчанию (документ не создан) — false, раздел виден всем.
     */
    fun observeSchoolSectionHidden(): Flow<Boolean>

    /** Скрыть/показать раздел у всех. False — нет прав (не админ). */
    suspend fun setSchoolSectionHidden(hidden: Boolean): Boolean

    /**
     * Пометка актуальности расписания уроков: админ отмечает, актуально ли
     * расписание (Google Drive из SchoolData.LESSONS_SCHEDULE_URL) и на какую
     * дату. actual=false — предупреждение «может быть неактуальным»,
     * untilDate — свободная строка («15.09.2026»), показывается как есть.
     * Null — админ ещё не ставил пометку (не показываем ничего).
     */
    fun observeSchoolScheduleStatus(): Flow<SchoolScheduleStatus?>

    /** Поставить пометку. False — нет прав (не админ). */
    suspend fun setSchoolScheduleStatus(status: SchoolScheduleStatus): Boolean

    /**
     * Дата ближайших каникул в ISO (yyyy-MM-dd), выставляется админом.
     * Пустая строка (или поле не задано) — админ не задавал дату, отсчёт
     * «До каникул» использует зашитую в SchoolData.HOLIDAY_DATE_ISO.
     */
    fun observeHolidayDate(): Flow<String>

    /** Задать дату каникул (ISO yyyy-MM-dd, "" — сбросить к зашитой). False — нет прав. */
    suspend fun setHolidayDate(isoDate: String): Boolean
}

/** Статус актуальности расписания уроков (config/appSettings). */
data class SchoolScheduleStatus(
    /** Актуально ли расписание на сейчас. */
    val actual: Boolean = false,
    /** Дата, на которую расписание актуально («15.09.2026»), свободная строка. */
    val untilDate: String = "",
    /** Когда админ ставил пометку (мс). */
    val updatedAt: Long = 0L
)
