package app.yodo.messenger.data.repository

import android.util.Log
import app.yodo.messenger.domain.model.SchoolIdea
import app.yodo.messenger.domain.model.SchoolNews
import app.yodo.messenger.domain.model.SchoolPoll
import app.yodo.messenger.domain.model.SchoolReview
import app.yodo.messenger.domain.repository.SchoolRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * НОВОЕ (раздел «Школа»): Firestore-хранилище динамического контента
 * школьного раздела, перенесённого из Telegram-бота.
 *
 * Коллекции (права — см. firestore.rules):
 *  - schoolNews   — новости; читают все авторизованные, пишут/удаляют только админы;
 *  - schoolPolls  — опросы; голосовать (voters/votes) может любой авторизованный,
 *                   создавать/удалять — только админы;
 *  - schoolIdeas  — идеи «предложить идею»; создать может любой, читают только админы;
 *  - schoolReviews— оценки раздела (1-5 звёзд); документ на пользователя:
 *                   создавать/обновлять можно только свой, читают все авторизованные.
 *
 * Образец работы с Firestore — AppSettingsRepositoryImpl (callbackFlow +
 * addSnapshotListener, await + SetOptions.merge).
 */
@Singleton
class SchoolRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : SchoolRepository {

    companion object {
        private const val TAG = "SchoolRepository"
        private const val COL_NEWS = "schoolNews"
        private const val COL_POLLS = "schoolPolls"
        private const val COL_IDEAS = "schoolIdeas"
        private const val COL_REVIEWS = "schoolReviews"
        private const val FIELD_PINNED = "pinned"
        private const val FIELD_PUB_DATE = "pubDate"
        private const val FIELD_CREATED_AT = "createdAt"
    }

    // ─────────────────────────────────────────────── Новости

    override fun observeNews(): Flow<List<SchoolNews>> = callbackFlow {
        val listener = firestore.collection(COL_NEWS)
            .orderBy(FIELD_PINNED, Query.Direction.DESCENDING)
            .orderBy(FIELD_PUB_DATE, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Ошибка чтения новостей: ${error.message}")
                    return@addSnapshotListener
                }
                val news = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(SchoolNewsFirestore::class.java)?.toDomain(doc.id)
                }.orEmpty()
                trySend(news)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addNews(news: SchoolNews): Result<Unit> = runCatching {
        firestore.collection(COL_NEWS).add(
            mapOf(
                "sender" to news.sender,
                "text" to news.text,
                "eventDate" to news.eventDate,
                "pubDate" to System.currentTimeMillis(),
                FIELD_PINNED to false
            )
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "addNews: ${it.message}") }

    override suspend fun updateNewsText(newsId: String, text: String): Result<Unit> = runCatching {
        firestore.collection(COL_NEWS).document(newsId)
            .set(mapOf("text" to text), SetOptions.merge()).await()
        Unit
    }.onFailure { Log.w(TAG, "updateNewsText: ${it.message}") }

    override suspend fun setNewsPinned(newsId: String, pinned: Boolean): Result<Unit> = runCatching {
        // Закреплённой может быть только одна новость — сначала снимаем закрепление
        // со всех (это делает только админ, права в rules), затем закрепляем новую.
        if (pinned) {
            firestore.collection(COL_NEWS)
                .whereEqualTo(FIELD_PINNED, true).get().await()
                .documents.forEach { doc ->
                    if (doc.id != newsId) {
                        doc.reference.set(mapOf(FIELD_PINNED to false), SetOptions.merge()).await()
                    }
                }
        }
        firestore.collection(COL_NEWS).document(newsId)
            .set(mapOf(FIELD_PINNED to pinned), SetOptions.merge()).await()
        Unit
    }.onFailure { Log.w(TAG, "setNewsPinned: ${it.message}") }

    override suspend fun deleteNews(newsId: String): Result<Unit> = runCatching {
        firestore.collection(COL_NEWS).document(newsId).delete().await()
        Unit
    }.onFailure { Log.w(TAG, "deleteNews: ${it.message}") }

    // ─────────────────────────────────────────────── Опросы

    override fun observePolls(): Flow<List<SchoolPoll>> = callbackFlow {
        val listener = firestore.collection(COL_POLLS)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Ошибка чтения опросов: ${error.message}")
                    return@addSnapshotListener
                }
                val polls = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(SchoolPollFirestore::class.java)?.toDomain(doc.id)
                }.orEmpty()
                trySend(polls)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun vote(pollId: String, optionIndex: Int, uid: String): Result<Unit> = runCatching {
        val ref = firestore.collection(COL_POLLS).document(pollId)
        // Проверяем, что ещё не голосовали (повторная защита поверх rules).
        val snap = ref.get().await()
        val voters = (snap.get("voters") as? Map<*, *>) ?: emptyMap<Any, Any>()
        if (uid in voters.keys) {
            throw IllegalStateException("Вы уже проголосовали в этом опросе")
        }
        ref.update(
            mapOf(
                "votes.$optionIndex" to FieldValue.increment(1),
                "voters.$uid" to optionIndex
            )
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "vote: ${it.message}") }

    override suspend fun addPoll(question: String, options: List<String>): Result<Unit> = runCatching {
        if (options.size < 2) throw IllegalArgumentException("Нужно минимум два варианта ответа")
        val votes = options.indices.associate { it.toString() to 0L }
        firestore.collection(COL_POLLS).add(
            mapOf(
                "question" to question,
                "options" to options,
                "votes" to votes,
                "voters" to emptyMap<String, Long>(),
                FIELD_CREATED_AT to System.currentTimeMillis()
            )
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "addPoll: ${it.message}") }

    override suspend fun deletePoll(pollId: String): Result<Unit> = runCatching {
        firestore.collection(COL_POLLS).document(pollId).delete().await()
        Unit
    }.onFailure { Log.w(TAG, "deletePoll: ${it.message}") }

    // ─────────────────────────────────────────────── Идеи

    override fun observeIdeas(): Flow<List<SchoolIdea>> = callbackFlow {
        val listener = firestore.collection(COL_IDEAS)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Ошибка чтения идей: ${error.message}")
                    return@addSnapshotListener
                }
                val ideas = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(SchoolIdeaFirestore::class.java)?.toDomain(doc.id)
                }.orEmpty()
                trySend(ideas)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun submitIdea(idea: SchoolIdea): Result<Unit> = runCatching {
        val uid = firebaseAuth.currentUser?.uid
            ?: throw IllegalStateException("Требуется вход в аккаунт")
        firestore.collection(COL_IDEAS).add(
            mapOf(
                "authorId" to uid,
                "authorName" to idea.authorName,
                "text" to idea.text,
                FIELD_CREATED_AT to System.currentTimeMillis()
            )
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "submitIdea: ${it.message}") }

    // ─────────────────────────────────────────────── Оценки

    override fun observeReviews(): Flow<List<SchoolReview>> = callbackFlow {
        val listener = firestore.collection(COL_REVIEWS)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Ошибка чтения оценок: ${error.message}")
                    return@addSnapshotListener
                }
                val reviews = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(SchoolReviewFirestore::class.java)?.toDomain(doc.id)
                }.orEmpty()
                trySend(reviews)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getMyReview(uid: String): SchoolReview? = try {
        val snap = firestore.collection(COL_REVIEWS).document(uid).get().await()
        snap.toObject(SchoolReviewFirestore::class.java)?.toDomain(uid)
    } catch (e: Exception) {
        Log.w(TAG, "getMyReview: ${e.message}")
        null
    }

    override suspend fun submitReview(review: SchoolReview): Result<Unit> = runCatching {
        val uid = firebaseAuth.currentUser?.uid
            ?: throw IllegalStateException("Требуется вход в аккаунт")
        firestore.collection(COL_REVIEWS).document(uid).set(
            mapOf(
                "authorId" to uid,
                "authorName" to review.authorName,
                "stars" to review.stars,
                "liked" to review.liked,
                "disliked" to review.disliked,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "submitReview: ${it.message}") }
}

// ─────────────────────────────────────────────────────── POJO для toObject

/** POJO-обёртки: Firestore не умеет напрямую в data class с вычисляемыми полями. */
private data class SchoolNewsFirestore(
    val sender: String = "",
    val text: String = "",
    val eventDate: String = "",
    val pubDate: Long = 0L,
    val pinned: Boolean = false
) {
    fun toDomain(id: String) = SchoolNews(
        id = id, sender = sender, text = text, eventDate = eventDate,
        pubDate = pubDate, pinned = pinned
    )
}

private data class SchoolPollFirestore(
    val question: String = "",
    val options: List<String> = emptyList(),
    val votes: Map<String, Long> = emptyMap(),
    val voters: Map<String, Long> = emptyMap(),
    val createdAt: Long = 0L
) {
    fun toDomain(id: String) = SchoolPoll(
        id = id, question = question, options = options, votes = votes,
        voters = voters, createdAt = createdAt
    )
}

private data class SchoolIdeaFirestore(
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Long = 0L
) {
    fun toDomain(id: String) = SchoolIdea(
        id = id, authorId = authorId, authorName = authorName, text = text, createdAt = createdAt
    )
}

private data class SchoolReviewFirestore(
    val authorId: String = "",
    val authorName: String = "",
    val stars: Int = 0,
    val liked: String = "",
    val disliked: String = "",
    val updatedAt: Long = 0L
) {
    fun toDomain(id: String) = SchoolReview(
        id = id, authorId = authorId, authorName = authorName, stars = stars,
        liked = liked, disliked = disliked, updatedAt = updatedAt
    )
}
