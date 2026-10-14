package app.yodo.messenger.data.repository

import android.util.Log
import app.yodo.messenger.domain.model.SchoolIdea
import app.yodo.messenger.domain.model.SchoolLessonFile
import app.yodo.messenger.domain.model.SchoolNews
import app.yodo.messenger.domain.model.SchoolPoll
import app.yodo.messenger.domain.model.SchoolReview
import app.yodo.messenger.domain.model.SchoolTeacherProfile
import app.yodo.messenger.domain.model.SchoolTeacherQuestion
import app.yodo.messenger.domain.repository.SchoolRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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
        private const val COL_TEACHERS = "schoolTeacherProfiles"
        private const val FIELD_PINNED = "pinned"
        private const val FIELD_PUB_DATE = "pubDate"
        private const val FIELD_CREATED_AT = "createdAt"

        /** Сколько записей истории файлов урока хранить/читать (показываются последние 3). */
        private const val LESSON_FILES_LIMIT = 10L
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
                    doc.toSchoolNews()
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
                FIELD_PINNED to false,
                // НОВОЕ (push о новостях): флаг очереди push-воркера — после
                // рассылки подписчикам воркер ставит true (Admin SDK, мимо rules).
                "notified" to false
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
                    doc.toSchoolPoll()
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
        // НОВОЕ (закрытие опросов): в закрытый опрос голосовать нельзя
        // (повторная защита поверх rules).
        if (snap.getBoolean("closed") == true) {
            throw IllegalStateException("Опрос завершён — голосование закрыто")
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
                FIELD_CREATED_AT to System.currentTimeMillis(),
                // НОВОЕ (push об опросах): флаг очереди push-воркера (как у новостей).
                "notified" to false
            )
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "addPoll: ${it.message}") }

    override suspend fun deletePoll(pollId: String): Result<Unit> = runCatching {
        firestore.collection(COL_POLLS).document(pollId).delete().await()
        Unit
    }.onFailure { Log.w(TAG, "deletePoll: ${it.message}") }

    // ─────────────────────────────────────────────── Push о новостях и опросах

    override fun observeSchoolPushEnabled(uid: String): Flow<Boolean> = callbackFlow {
        // До инициализации поле отсутствует — считаем подписанным (дефолт true):
        // push о новостях/опросах нужен всем по умолчанию, отключается тумблером.
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Ошибка чтения schoolPushEnabled: ${error.message}")
                    return@addSnapshotListener
                }
                val enabled = snapshot?.getBoolean("schoolPushEnabled") ?: true
                trySend(enabled)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun setSchoolPushEnabled(enabled: Boolean): Result<Unit> = runCatching {
        val uid = firebaseAuth.currentUser?.uid
            ?: throw IllegalStateException("Требуется вход в аккаунт")
        firestore.collection("users").document(uid)
            .set(mapOf("schoolPushEnabled" to enabled), SetOptions.merge()).await()
        Unit
    }.onFailure { Log.w(TAG, "setSchoolPushEnabled: ${it.message}") }

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
                    doc.toSchoolIdea()
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
                    doc.toSchoolReview()
                }.orEmpty()
                trySend(reviews)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getMyReview(uid: String): SchoolReview? = try {
        val snap = firestore.collection(COL_REVIEWS).document(uid).get().await()
        // Документа ещё нет — оценки нет. Прежний toObject и здесь возвращал не
        // null, а пустую модель, из-за чего ветка «оценки нет» была недостижима.
        if (snap.exists()) snap.toSchoolReview(uid) else null
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

    // ─────────────────────────────────────────────── Учительские страницы

    override fun observeTeacherProfile(teacherName: String): Flow<SchoolTeacherProfile?> = callbackFlow {
        val listener = teacherDoc(teacherName).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Ошибка чтения профиля учителя: ${error.message}")
                return@addSnapshotListener
            }
            val profile = if (snapshot != null && snapshot.exists()) {
                snapshot.toTeacherProfile()
            } else null
            trySend(profile)
        }
        awaitClose { listener.remove() }
    }

    override fun observeMyTeacherProfile(uid: String): Flow<SchoolTeacherProfile?> = callbackFlow {
        // У одного учителя — один профиль; слушаем по linkedUserId. Как только
        // админ привяжет аккаунт, документ появится в выдаче.
        val listener = firestore.collection(COL_TEACHERS)
            .whereEqualTo("linkedUserId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Ошибка чтения моего профиля учителя: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.firstOrNull()?.let { doc ->
                    doc.toTeacherProfile()
                })
            }
        awaitClose { listener.remove() }
    }

    override fun observeAllTeacherProfiles(): Flow<List<SchoolTeacherProfile>> = callbackFlow {
        val listener = firestore.collection(COL_TEACHERS)
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Ошибка чтения профилей учителей: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { doc ->
                    doc.toTeacherProfile()
                }.orEmpty())
            }
        awaitClose { listener.remove() }
    }

    override fun observeTeacherQuestions(teacherName: String): Flow<List<SchoolTeacherQuestion>> = callbackFlow {
        val listener = teacherDoc(teacherName).collection("questions")
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Ошибка чтения вопросов учителя: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { doc ->
                    doc.toTeacherQuestion()
                }.orEmpty())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun upsertTeacherProfile(profile: SchoolTeacherProfile): Result<Unit> = runCatching {
        if (profile.name.isBlank()) throw IllegalArgumentException("Имя учителя не задано")
        // id документа = имя учителя (стабильный ключ, как в боте).
        teacherDoc(profile.name).set(
            mapOf(
                "name" to profile.name,
                "subject" to profile.subject,
                "linkedUserId" to profile.linkedUserId,
                "linkedUserName" to profile.linkedUserName
            ),
            SetOptions.merge()
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "upsertTeacherProfile: ${it.message}") }

    override suspend fun linkTeacherProfile(teacherName: String, uid: String, userName: String): Result<Unit> =
        runCatching {
            // Профиль может быть уже привязан к другому аккаунту — перезапишем
            // (админ осознанно выбирает нового владельца). Плюс снимаем старую
            // привязку, если этот uid уже был привязан к другому учителю.
            val previous = firestore.collection(COL_TEACHERS)
                .whereEqualTo("linkedUserId", uid).get().await()
            previous.documents.forEach { doc ->
                doc.reference.set(
                    mapOf("linkedUserId" to "", "linkedUserName" to ""),
                    SetOptions.merge()
                ).await()
            }
            teacherDoc(teacherName).set(
                mapOf("linkedUserId" to uid, "linkedUserName" to userName),
                SetOptions.merge()
            ).await()
            Unit
        }.onFailure { Log.w(TAG, "linkTeacherProfile: ${it.message}") }

    override suspend fun unlinkTeacherProfile(teacherName: String): Result<Unit> = runCatching {
        teacherDoc(teacherName).set(
            mapOf("linkedUserId" to "", "linkedUserName" to ""),
            SetOptions.merge()
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "unlinkTeacherProfile: ${it.message}") }

    override suspend fun setTeacherFile(teacherName: String, fileUrl: String, fileNote: String): Result<Unit> =
        runCatching {
            val teacherRef = teacherDoc(teacherName)
            // Снимок ДО обновления: старая ссылка (дедупликация пуша) и подписчики.
            val before = teacherRef.get().await()
            val oldUrl = before.getString("fileUrl") ?: ""
            val subscribers = (before.get("subscribers") as? List<*>).orEmpty().filterIsInstance<String>()
            val now = System.currentTimeMillis()
            teacherRef.set(
                mapOf(
                    "fileUrl" to fileUrl,
                    "fileNote" to fileNote,
                    "fileUpdatedAt" to now
                ),
                SetOptions.merge()
            ).await()
            // НОВОЕ (история файлов урока): запись при каждой смене файла —
            // страница учителя показывает несколько последних. Правка описания
            // без смены ссылки историю не засоряет.
            if (fileUrl != oldUrl) {
                teacherRef.collection("lessonFiles").add(
                    mapOf(
                        "fileUrl" to fileUrl,
                        "fileNote" to fileNote,
                        "updatedAt" to now
                    )
                ).await()
            }
            // НОВОЕ (push подписчикам): событие для push-воркера — снимок
            // подписчиков на момент обновления. Отправляется, только когда
            // ссылка на файл реально изменилась (правка описания не спамит
            // подписчиков лишними уведомлениями).
            if (subscribers.isNotEmpty() && fileUrl != oldUrl) {
                teacherRef.collection("fileNotifications").add(
                    mapOf(
                        "subscribers" to subscribers,
                        "fileNote" to fileNote,
                        "createdAt" to now,
                        "notified" to false
                    )
                ).await()
            }
            Unit
        }.onFailure { Log.w(TAG, "setTeacherFile: ${it.message}") }

    override fun observeLessonFiles(teacherName: String): Flow<List<SchoolLessonFile>> = callbackFlow {
        val listener = teacherDoc(teacherName).collection("lessonFiles")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(LESSON_FILES_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Ошибка чтения истории файлов урока: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { doc ->
                    doc.toLessonFile()
                }.orEmpty())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun setTeacherSubscription(teacherName: String, uid: String, subscribed: Boolean): Result<Unit> =
        runCatching {
            val field = if (subscribed) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
            teacherDoc(teacherName).update("subscribers", field).await()
            Unit
        }.onFailure { Log.w(TAG, "setTeacherSubscription: ${it.message}") }

    override suspend fun askTeacherQuestion(teacherName: String, question: SchoolTeacherQuestion): Result<Unit> =
        runCatching {
            val uid = firebaseAuth.currentUser?.uid
                ?: throw IllegalStateException("Требуется вход в аккаунт")
            teacherDoc(teacherName).collection("questions").add(
                mapOf(
                    "fromUid" to uid,
                    "fromName" to question.fromName,
                    "text" to question.text,
                    "hidden" to false,
                    "answer" to "",
                    "answeredAt" to 0L,
                    FIELD_CREATED_AT to System.currentTimeMillis(),
                    // НОВОЕ (push учителю): воркер push-worker/index.js вычитывает
                    // вопросы через collectionGroup("questions") и отправляет
                    // учителю уведомление по флагу notified ниже.
                    "notified" to false
                )
            ).await()
            Unit
        }.onFailure { Log.w(TAG, "askTeacherQuestion: ${it.message}") }

    override suspend fun answerTeacherQuestion(teacherName: String, questionId: String, answer: String): Result<Unit> =
        runCatching {
            if (answer.isBlank()) throw IllegalArgumentException("Ответ не может быть пустым")
            teacherDoc(teacherName).collection("questions").document(questionId)
                .set(
                    mapOf(
                        "answer" to answer.trim(),
                        "answeredAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            Unit
        }.onFailure { Log.w(TAG, "answerTeacherQuestion: ${it.message}") }

    override suspend fun setTeacherQuestionHidden(
        teacherName: String,
        questionId: String,
        hidden: Boolean
    ): Result<Unit> = runCatching {
        teacherDoc(teacherName).collection("questions").document(questionId)
            .set(mapOf("hidden" to hidden), SetOptions.merge()).await()
        Unit
    }.onFailure { Log.w(TAG, "setTeacherQuestionHidden: ${it.message}") }

    private fun teacherDoc(teacherName: String) =
        firestore.collection(COL_TEACHERS).document(teacherName)
}

// ─────────────────────────────────────────── Чтение документов (без рефлексии)

/**
 * Раньше документы разбирались через DocumentSnapshot.toObject(...): Firestore
 * сопоставляет поля документа с полями класса по именам, то есть через
 * рефлексию. В release-сборке R8 переименовывает поля этих приватных классов,
 * сопоставление перестаёт находить совпадения, и toObject бросает
 * «No setter/field for … found on class …». Исключение возникало внутри
 * колбэка snapshot-listener'а, где его никто не ловит, — приложение вылетало
 * при открытии раздела «Школа». Поэтому поля читаются явно, как в остальных
 * репозиториях проекта.
 */
private fun DocumentSnapshot.stringField(field: String): String = getString(field) ?: ""

/** Firestore хранит целые как Long, но может отдать и Double — приводим к Long. */
private fun DocumentSnapshot.longField(field: String): Long = (get(field) as? Number)?.toLong() ?: 0L

private fun DocumentSnapshot.boolField(field: String): Boolean = getBoolean(field) ?: false

private fun DocumentSnapshot.stringListField(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

/** votes/voters: {индекс варианта или uid -> число голосов}. */
private fun DocumentSnapshot.longMapField(field: String): Map<String, Long> =
    (get(field) as? Map<*, *>)
        ?.mapNotNull { (key, value) ->
            val name = key as? String ?: return@mapNotNull null
            val number = (value as? Number)?.toLong() ?: return@mapNotNull null
            name to number
        }
        ?.toMap()
        ?: emptyMap()

private fun DocumentSnapshot.toSchoolNews() = SchoolNews(
    id = id,
    sender = stringField("sender"),
    text = stringField("text"),
    eventDate = stringField("eventDate"),
    pubDate = longField("pubDate"),
    pinned = boolField("pinned"),
    // Поле появилось позже: у старых новостей его нет — считаем опубликованной.
    published = if (contains("published")) boolField("published") else true
)

private fun DocumentSnapshot.toSchoolPoll() = SchoolPoll(
    id = id,
    question = stringField("question"),
    options = stringListField("options"),
    votes = longMapField("votes"),
    voters = longMapField("voters"),
    createdAt = longField("createdAt"),
    closed = boolField("closed")
)

private fun DocumentSnapshot.toSchoolIdea() = SchoolIdea(
    id = id,
    authorId = stringField("authorId"),
    authorName = stringField("authorName"),
    text = stringField("text"),
    createdAt = longField("createdAt")
)

/** Оценка лежит в документе с id = uid автора; id передаётся явно. */
private fun DocumentSnapshot.toSchoolReview(idOverride: String? = null) = SchoolReview(
    id = idOverride ?: id,
    authorId = stringField("authorId"),
    authorName = stringField("authorName"),
    stars = longField("stars").toInt(),
    liked = stringField("liked"),
    disliked = stringField("disliked"),
    updatedAt = longField("updatedAt")
)

private fun DocumentSnapshot.toTeacherProfile() = SchoolTeacherProfile(
    id = id,
    name = stringField("name"),
    subject = stringField("subject"),
    linkedUserId = stringField("linkedUserId"),
    linkedUserName = stringField("linkedUserName"),
    fileUrl = stringField("fileUrl"),
    fileNote = stringField("fileNote"),
    fileUpdatedAt = longField("fileUpdatedAt"),
    subscribers = stringListField("subscribers").associateWith { true }
)

private fun DocumentSnapshot.toTeacherQuestion() = SchoolTeacherQuestion(
    id = id,
    fromUid = stringField("fromUid"),
    fromName = stringField("fromName"),
    text = stringField("text"),
    hidden = boolField("hidden"),
    answer = stringField("answer"),
    answeredAt = longField("answeredAt"),
    createdAt = longField("createdAt")
)

private fun DocumentSnapshot.toLessonFile() = SchoolLessonFile(
    id = id,
    fileUrl = stringField("fileUrl"),
    fileNote = stringField("fileNote"),
    updatedAt = longField("updatedAt")
)
