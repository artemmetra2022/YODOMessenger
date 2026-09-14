package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.schoolDataStore by preferencesDataStore(name = "yodo_school_settings")

/**
 * НОВОЕ (раздел «Школа»): локальные настройки отображения школьного раздела
 * и локальный прогресс (счёт игры и викторины), перенесённые из Telegram-бота.
 *
 * Всё хранится в DataStore по образцу UserSettingsPreferences: Flow на чтение,
 * suspend-сеттер на запись. Подразделы, выключенные в настройках, скрываются
 * с главного экрана «Школы» (см. SchoolScreen).
 */
@Singleton
class SchoolPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sectionEnabledKey = booleanPreferencesKey("school_section_enabled")
    private val visibleSectionsKey = stringSetPreferencesKey("school_visible_sections")
    private val teacherModeEnabledKey = booleanPreferencesKey("school_teacher_mode_enabled")
    private val gameWinsKey = intPreferencesKey("school_game_wins")
    private val gameLossesKey = intPreferencesKey("school_game_losses")
    private val quizCorrectKey = intPreferencesKey("school_quiz_correct")
    private val quizTotalKey = intPreferencesKey("school_quiz_total")
    private val myStarsKey = intPreferencesKey("school_my_stars")
    private val myReviewIdKey = stringPreferencesKey("school_my_review_id")

    /** Идентификаторы подразделов — стабильные строки, см. SchoolSectionId. */
    object SectionIds {
        const val NEWS = "news"
        const val TEACHERS = "teachers"
        const val SCHEDULE = "schedule"
        const val POLLS = "polls"
        const val QUIZ = "quiz"
        const val GAME = "game"
        const val REVIEW = "review"
        const val FAQ = "faq"
        const val PARENTS = "parents"
        val ALL = listOf(NEWS, TEACHERS, SCHEDULE, POLLS, QUIZ, GAME, REVIEW, FAQ, PARENTS)
    }

    /** Показывать ли раздел «Школа» вообще (пункт в настройках). По умолчанию включён. */
    val sectionEnabled: Flow<Boolean> =
        context.schoolDataStore.data.map { it[sectionEnabledKey] ?: true }

    /** Какие подразделы видеть на главном экране «Школы» (по умолчанию все). */
    val visibleSections: Flow<Set<String>> =
        context.schoolDataStore.data.map { it[visibleSectionsKey] ?: SectionIds.ALL.toSet() }

    /**
     * НОВОЕ (учительские страницы): включён ли «режим учителя» — показывать
     * пункт «Моя страница учителя» на главном экране «Школы». Сама привязка
     * делается админом на сервере; переключатель лишь прячет/показывает вход.
     */
    val teacherModeEnabled: Flow<Boolean> =
        context.schoolDataStore.data.map { it[teacherModeEnabledKey] ?: false }

    val gameWins: Flow<Int> = context.schoolDataStore.data.map { it[gameWinsKey] ?: 0 }
    val gameLosses: Flow<Int> = context.schoolDataStore.data.map { it[gameLossesKey] ?: 0 }
    val quizCorrect: Flow<Int> = context.schoolDataStore.data.map { it[quizCorrectKey] ?: 0 }
    val quizTotal: Flow<Int> = context.schoolDataStore.data.map { it[quizTotalKey] ?: 0 }

    /** Локальный дубликат моей оценки (id документа в schoolReviews), чтобы не ждать сеть. */
    val myStars: Flow<Int> = context.schoolDataStore.data.map { it[myStarsKey] ?: 0 }
    val myReviewId: Flow<String> = context.schoolDataStore.data.map { it[myReviewIdKey] ?: "" }

    suspend fun setSectionEnabled(enabled: Boolean) {
        context.schoolDataStore.edit { it[sectionEnabledKey] = enabled }
    }

    suspend fun setTeacherModeEnabled(enabled: Boolean) {
        context.schoolDataStore.edit { it[teacherModeEnabledKey] = enabled }
    }

    suspend fun setSectionVisible(sectionId: String, visible: Boolean) {
        context.schoolDataStore.edit { prefs ->
            val current = prefs[visibleSectionsKey] ?: SectionIds.ALL.toSet()
            val currentList = if (current.isEmpty()) SectionIds.ALL else current.toList()
            val all = SectionIds.ALL
            // Если выключили последний подраздел — считаем, что пользователь
            // не хотел полностью ломать раздел: оставляем набор как есть.
            val updated = if (visible) (currentList + sectionId).distinct()
            else (currentList - sectionId)
            val finalSet = if (updated.isEmpty()) currentList.toSet() else updated.intersect(all.toSet()).let { if (it.isEmpty()) currentList.toSet() else it }
            prefs[visibleSectionsKey] = finalSet.toSet()
        }
    }

    suspend fun recordGameRound(won: Boolean) {
        context.schoolDataStore.edit { prefs ->
            if (won) prefs[gameWinsKey] = (prefs[gameWinsKey] ?: 0) + 1
            else prefs[gameLossesKey] = (prefs[gameLossesKey] ?: 0) + 1
        }
    }

    suspend fun recordQuizAnswer(correct: Boolean) {
        context.schoolDataStore.edit { prefs ->
            prefs[quizTotalKey] = (prefs[quizTotalKey] ?: 0) + 1
            if (correct) prefs[quizCorrectKey] = (prefs[quizCorrectKey] ?: 0) + 1
        }
    }

    suspend fun resetQuizScore() {
        context.schoolDataStore.edit {
            it[quizCorrectKey] = 0
            it[quizTotalKey] = 0
        }
    }

    suspend fun setMyReview(reviewId: String, stars: Int) {
        context.schoolDataStore.edit {
            it[myReviewIdKey] = reviewId
            it[myStarsKey] = stars
        }
    }
}
