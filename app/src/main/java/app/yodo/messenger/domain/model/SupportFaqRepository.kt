package app.yodo.messenger.domain.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Firestore-backed FAQ with the bundled FAQ as an offline-safe fallback. */
object SupportFaqRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val _sections = MutableStateFlow(SupportFaqData.sections)
    val sections: StateFlow<List<FaqSection>> = _sections

    suspend fun load() {
        try {
            val snap = firestore.collection("faqEntries").get().await()
            if (!snap.isEmpty) {
                val grouped = snap.documents.mapNotNull { d ->
                    val sectionId = d.getString("sectionId") ?: return@mapNotNull null
                    val sectionTitle = d.getString("sectionTitle") ?: sectionId
                    val emoji = d.getString("emoji") ?: "❓"
                    val q = FaqQuestion(
                        id = d.id,
                        question = d.getString("question") ?: return@mapNotNull null,
                        answer = d.getString("answer") ?: ""
                    )
                    Triple(sectionId, sectionTitle, emoji to q)
                }.groupBy { it.first }.map { (id, rows) ->
                    FaqSection(id, rows.first().second, rows.first().third.first, rows.map { it.third.second })
                }
                if (grouped.isNotEmpty()) _sections.value = grouped
            }
        } catch (_: Exception) { /* bundled FAQ remains available */ }
    }

    suspend fun saveSections(sections: List<FaqSection>, action: String = "update") {
        val batch = firestore.batch()
        val old = firestore.collection("faqEntries").get().await()
        old.documents.forEach { batch.delete(it.reference) }
        sections.forEach { section ->
            section.questions.forEach { q ->
                val ref = firestore.collection("faqEntries").document(q.id)
                batch.set(ref, mapOf(
                    "sectionId" to section.id,
                    "sectionTitle" to section.title,
                    "emoji" to section.emoji,
                    "question" to q.question,
                    "answer" to q.answer,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to (auth.currentUser?.email ?: auth.currentUser?.uid ?: "unknown")
                ))
            }
        }
        batch.commit().await()
        firestore.collection("faqVersions").add(mapOf(
            "action" to action,
            "author" to (auth.currentUser?.email ?: auth.currentUser?.uid ?: "unknown"),
            "createdAt" to FieldValue.serverTimestamp(),
            "questionCount" to sections.sumOf { it.questions.size },
            "snapshot" to sections.flatMap { s -> s.questions.map { q -> mapOf("id" to q.id, "sectionId" to s.id, "sectionTitle" to s.title, "emoji" to s.emoji, "question" to q.question, "answer" to q.answer) } }
        )).await()
        _sections.value = sections
    }

    suspend fun recordView(questionId: String) {
        try {
            firestore.collection("faqStats").document(questionId).set(
                mapOf("views" to FieldValue.increment(1), "lastViewedAt" to FieldValue.serverTimestamp()),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (_: Exception) { }
    }

    suspend fun recordFeedback(questionId: String, helpful: Boolean) {
        try {
            firestore.collection("faqStats").document(questionId).set(
                mapOf((if (helpful) "helpful" else "notHelpful") to FieldValue.increment(1)),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (_: Exception) { }
    }
}
