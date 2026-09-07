package app.yodo.messenger.domain.model

/**
 * НОВОЕ (раздел «Школа»): модели динамического контента школьного раздела.
 * Статический справочник (учителя, звонки, викторина, FAQ) — см.
 * features/school/SchoolData.kt, здесь только то, что живёт в Firestore.
 */

/** Новость гимназии (коллекция schoolNews). */
data class SchoolNews(
    val id: String = "",
    val sender: String = "",
    val text: String = "",
    val eventDate: String = "",
    val pubDate: Long = 0L,
    val pinned: Boolean = false
)

/** Опрос (коллекция schoolPolls). votes: индекс варианта -> число голосов. */
data class SchoolPoll(
    val id: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val votes: Map<String, Long> = emptyMap(),
    val voters: Map<String, Long> = emptyMap(),
    val createdAt: Long = 0L
) {
    val totalVotes: Long get() = votes.values.sum()
    fun myVote(uid: String?): Int? = uid?.let { voters[it]?.toInt() }
}

/** Идея пользователя (коллекция schoolIdeas, читают только админы). */
data class SchoolIdea(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Long = 0L
)

/** Оценка раздела (коллекция schoolReviews, документ = uid автора). */
data class SchoolReview(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val stars: Int = 0,
    val liked: String = "",
    val disliked: String = "",
    val updatedAt: Long = 0L
)

/**
 * НОВОЕ (учительские страницы): профиль учителя — «второй профиль» поверх
 * обычного аккаунта мессенджера. Создаёт и привязывает к аккаунту админ
 * (SchoolAdminScreen); учитель управляет файлом урока и вопросами.
 * Документ schoolTeacherProfiles/{имя учителя}.
 */
data class SchoolTeacherProfile(
    val id: String = "",
    val name: String = "",
    val subject: String = "",
    /** uid аккаунта мессенджера, привязанного к учителю ("" = не привязан). */
    val linkedUserId: String = "",
    /** Отображаемое имя привязанного аккаунта (для админки, пишется при привязке). */
    val linkedUserName: String = "",
    /** Ссылка на файл урока (как /setlesson в Telegram-боте). */
    val fileUrl: String = "",
    /** Название/описание файла урока. */
    val fileNote: String = "",
    val fileUpdatedAt: Long = 0L,
    /** Подписчики обновлений файла: uid -> true. */
    val subscribers: Map<String, Boolean> = emptyMap()
)

/** Вопрос ученика на странице учителя (как /ask в Telegram-боте). */
data class SchoolTeacherQuestion(
    val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val text: String = "",
    val hidden: Boolean = false,
    val createdAt: Long = 0L
)
