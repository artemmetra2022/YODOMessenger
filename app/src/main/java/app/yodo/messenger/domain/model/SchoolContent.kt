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
