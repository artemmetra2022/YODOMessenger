package app.yodo.messenger.features.teachers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import android.net.Uri

import app.yodo.messenger.domain.model.TeacherProfile

@HiltViewModel
class TeacherViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ViewModel() {
    private val _teachers = MutableStateFlow<List<TeacherProfile>>(emptyList())
    val teachers: StateFlow<List<TeacherProfile>> = _teachers.asStateFlow()
    private val _selected = MutableStateFlow<TeacherProfile?>(null)
    val selected: StateFlow<TeacherProfile?> = _selected.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var listener: ListenerRegistration? = null

    init { observeTeachers() }

    private fun observeTeachers() {
        listener = firestore.collection("teacherProfiles").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            _teachers.value = snap.documents.mapNotNull { doc -> fromDoc(doc.id, doc.data ?: emptyMap()) }
                .sortedBy { it.displayName.lowercase() }
        }
    }

    fun select(userId: String) { _selected.value = _teachers.value.firstOrNull { it.userId == userId } }

    fun saveTeacher(
        userId: String, displayName: String, bio: String, subjectsText: String, questionHours: String,
        email: String, rating: Double, showRating: Boolean, vacationFrom: Long?, vacationTo: Long?,
        notifyQuestions: Boolean, notifyMessages: Boolean, acceptingQuestions: Boolean
    ) {
        val normalizedSubjects = subjectsText.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (userId.isBlank() || displayName.isBlank()) { _message.value = "UID и имя обязательны"; return }
        viewModelScope.launch {
            try {
                firestore.collection("teacherProfiles").document(userId).set(mapOf(
                    "userId" to userId, "displayName" to displayName.trim(), "shortBio" to bio.trim(),
                    "subjects" to normalizedSubjects, "questionHours" to questionHours.trim(),
                    "contactEmail" to email.trim(), "rating" to rating.coerceIn(0.0, 5.0),
                    "showRating" to showRating, "vacationFromMillis" to vacationFrom, "vacationToMillis" to vacationTo,
                    "acceptingQuestions" to acceptingQuestions, "notifyNewQuestions" to notifyQuestions,
                    "notifyMessages" to notifyMessages, "updatedAtMillis" to System.currentTimeMillis()
                )).await()
                _message.value = "Карточка учителя сохранена"
                select(userId)
            } catch (e: Exception) { _message.value = e.message ?: "Не удалось сохранить" }
        }
    }

    fun uploadPhoto(userId: String, uri: Uri, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val ref = storage.reference.child("teacherProfiles/$userId/avatar.jpg")
                ref.putFile(uri).await()
                val url = ref.downloadUrl.await().toString()
                firestore.collection("teacherProfiles").document(userId).update("photoUrl", url, "updatedAtMillis", System.currentTimeMillis()).await()
                onDone(url)
            } catch (e: Exception) { _message.value = e.message ?: "Не удалось загрузить фото"; onDone(null) }
        }
    }

    fun setNotifications(userId: String, questions: Boolean, messages: Boolean) {
        firestore.collection("teacherProfiles").document(userId).update(
            "notifyNewQuestions", questions, "notifyMessages", messages, "updatedAtMillis", System.currentTimeMillis()
        )
    }

    fun clearMessage() { _message.value = null }

    override fun onCleared() { listener?.remove(); super.onCleared() }

    private fun fromDoc(id: String, d: Map<String, Any?>): TeacherProfile? = try {
        TeacherProfile(
            userId = d["userId"] as? String ?: id, displayName = d["displayName"] as? String ?: "",
            shortBio = d["shortBio"] as? String ?: "", subjects = (d["subjects"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            questionHours = d["questionHours"] as? String ?: "", contactEmail = d["contactEmail"] as? String ?: "",
            photoUrl = d["photoUrl"] as? String, rating = (d["rating"] as? Number)?.toDouble() ?: 0.0,
            showRating = d["showRating"] as? Boolean ?: true, vacationFromMillis = (d["vacationFromMillis"] as? Number)?.toLong(),
            vacationToMillis = (d["vacationToMillis"] as? Number)?.toLong(), acceptingQuestions = d["acceptingQuestions"] as? Boolean ?: true,
            notifyNewQuestions = d["notifyNewQuestions"] as? Boolean ?: true, notifyMessages = d["notifyMessages"] as? Boolean ?: true,
            updatedAtMillis = (d["updatedAtMillis"] as? Number)?.toLong() ?: 0L
        )
    } catch (_: Exception) { null }
}
