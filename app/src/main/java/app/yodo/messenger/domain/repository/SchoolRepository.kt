package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.SchoolIdea
import app.yodo.messenger.domain.model.SchoolNews
import app.yodo.messenger.domain.model.SchoolPoll
import app.yodo.messenger.domain.model.SchoolReview
import app.yodo.messenger.domain.model.SchoolTeacherProfile
import app.yodo.messenger.domain.model.SchoolTeacherQuestion
import kotlinx.coroutines.flow.Flow

/**
 * НОВОЕ (раздел «Школа»): динамический контент школьного раздела (Firestore).
 * Статический справочник (учителя/звонки/викторина/FAQ) в приложение вшит —
 * см. features/school/SchoolData.kt.
 */
interface SchoolRepository {

    fun observeNews(): Flow<List<SchoolNews>>

    fun observePolls(): Flow<List<SchoolPoll>>

    fun observeReviews(): Flow<List<SchoolReview>>

    suspend fun getMyReview(uid: String): SchoolReview?

    suspend fun submitReview(review: SchoolReview): Result<Unit>

    suspend fun vote(pollId: String, optionIndex: Int, uid: String): Result<Unit>

    suspend fun submitIdea(idea: SchoolIdea): Result<Unit>

    /** Только для админов приложения (см. ChatRepository.ADMIN_EMAILS). */
    fun observeIdeas(): Flow<List<SchoolIdea>>

    suspend fun addNews(news: SchoolNews): Result<Unit>

    suspend fun updateNewsText(newsId: String, text: String): Result<Unit>

    suspend fun setNewsPinned(newsId: String, pinned: Boolean): Result<Unit>

    suspend fun deleteNews(newsId: String): Result<Unit>

    suspend fun addPoll(question: String, options: List<String>): Result<Unit>

    suspend fun deletePoll(pollId: String): Result<Unit>

    // ─────────────────────── Учительские страницы (перенос из Telegram-бота)

    /** Профиль учителя по имени (страница в справочнике). Null — профиля нет. */
    fun observeTeacherProfile(teacherName: String): Flow<SchoolTeacherProfile?>

    /** Профиль, привязанный к текущему аккаунту (моя страница учителя). */
    fun observeMyTeacherProfile(uid: String): Flow<SchoolTeacherProfile?>

    /** Все профили (админка). */
    fun observeAllTeacherProfiles(): Flow<List<SchoolTeacherProfile>>

    /** Вопросы под страницей учителя (скрытые видны только владельцу/админам). */
    fun observeTeacherQuestions(teacherName: String): Flow<List<SchoolTeacherQuestion>>

    /** Создание/обновление профиля учителя — только админ. */
    suspend fun upsertTeacherProfile(profile: SchoolTeacherProfile): Result<Unit>

    /** Привязка/отвязка аккаунта к профилю учителя — только админ. */
    suspend fun linkTeacherProfile(teacherName: String, uid: String, userName: String): Result<Unit>

    suspend fun unlinkTeacherProfile(teacherName: String): Result<Unit>

    /** Файл урока — задаёт привязанный учитель (как /setlesson в боте). */
    suspend fun setTeacherFile(teacherName: String, fileUrl: String, fileNote: String): Result<Unit>

    /** Подписка/отписка от обновлений файла урока. */
    suspend fun setTeacherSubscription(teacherName: String, uid: String, subscribed: Boolean): Result<Unit>

    /** Задать вопрос учителю (как /ask в боте). */
    suspend fun askTeacherQuestion(teacherName: String, question: SchoolTeacherQuestion): Result<Unit>

    /** Ответить на вопрос / изменить ответ — привязанный учитель или админ. */
    suspend fun answerTeacherQuestion(teacherName: String, questionId: String, answer: String): Result<Unit>

    /** Скрыть/показать вопрос — владелец страницы или админ. */
    suspend fun setTeacherQuestionHidden(teacherName: String, questionId: String, hidden: Boolean): Result<Unit>
}
