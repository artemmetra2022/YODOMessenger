package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.SchoolIdea
import app.yodo.messenger.domain.model.SchoolNews
import app.yodo.messenger.domain.model.SchoolPoll
import app.yodo.messenger.domain.model.SchoolReview
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
}
