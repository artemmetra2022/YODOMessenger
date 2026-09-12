package app.yodo.messenger.features.chats

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class NewsAdminUserView(
    val uid: String, val displayName: String, val classId: String, val viewedAtMillis: Long, val reacted: String = ""
)

@HiltViewModel
class AdminNewsCampaignsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage
) : ViewModel() {
    private val _campaigns = MutableStateFlow<List<Map<String, Any?>>>(emptyList())
    val campaigns: StateFlow<List<Map<String, Any?>>> = _campaigns.asStateFlow()
    private val _templates = MutableStateFlow<List<Map<String, Any?>>>(emptyList())
    val templates: StateFlow<List<Map<String, Any?>>> = _templates.asStateFlow()
    private val _selectedViews = MutableStateFlow<List<NewsAdminUserView>>(emptyList())
    val selectedViews: StateFlow<List<NewsAdminUserView>> = _selectedViews.asStateFlow()
    private val _selectedComments = MutableStateFlow<List<Map<String, Any?>>>(emptyList())
    val selectedComments: StateFlow<List<Map<String, Any?>>> = _selectedComments.asStateFlow()
    private val _selectedUnread = MutableStateFlow<List<NewsAdminUserView>>(emptyList())
    val selectedUnread: StateFlow<List<NewsAdminUserView>> = _selectedUnread.asStateFlow()

    init { refresh() }

    fun refresh() {
        firestore.collection("newsCampaigns").orderBy("createdAtMillis", Query.Direction.DESCENDING).limit(100).get()
            .addOnSuccessListener { _campaigns.value = it.documents.map { d -> d.data.orEmpty() + ("id" to d.id) } }
        firestore.collection("newsTemplates").orderBy("updatedAtMillis", Query.Direction.DESCENDING).limit(100).get()
            .addOnSuccessListener { _templates.value = it.documents.map { d -> d.data.orEmpty() + ("id" to d.id) } }
    }

    fun saveCampaign(
        titleA: String, titleB: String, body: String, audienceType: String, classIds: List<String>,
        scheduledAt: Long?, windowStart: Int?, windowEnd: Int?, pushEnabled: Boolean, pushScheduledAt: Long?,
        pushWindowStart: Int?, pushWindowEnd: Int?, abEnabled: Boolean, commentsEnabled: Boolean,
        onCreated: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        val ref = firestore.collection("newsCampaigns").document()
        val data = hashMapOf<String, Any?>(
            "titleA" to titleA.trim(), "titleB" to titleB.trim(), "body" to body.trim(),
            "createdBy" to uid, "createdAtMillis" to System.currentTimeMillis(), "status" to "SCHEDULED",
            "scheduledAtMillis" to scheduledAt, "windowStartMinute" to windowStart, "windowEndMinute" to windowEnd,
            "audienceType" to audienceType, "audienceClassIds" to classIds, "pushEnabled" to pushEnabled,
            "pushScheduledAtMillis" to pushScheduledAt, "pushWindowStartMinute" to pushWindowStart,
            "pushWindowEndMinute" to pushWindowEnd, "abEnabled" to abEnabled, "commentsEnabled" to commentsEnabled,
            "sentCount" to 0L, "openedCount" to 0L, "viewCount" to 0L, "reactionCount" to 0L, "commentCount" to 0L,
            "clicksA" to 0L, "clicksB" to 0L, "sentA" to 0L, "sentB" to 0L, "pushSent" to false, "newsSent" to false,
            "attachments" to emptyList<Map<String, Any>>()
        )
        ref.set(data).addOnSuccessListener { onCreated(ref.id); refresh() }
    }

    fun uploadAttachments(campaignId: String, uris: List<Uri>, contentResolver: android.content.ContentResolver) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                val attachmentList = mutableListOf<Map<String, Any>>()
                val base = storage.reference.child("news_attachments/$campaignId")
                for (uri in uris) {
                    val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                    } ?: "document_${UUID.randomUUID()}"
                    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                    val ref = base.child("${UUID.randomUUID()}_$name")
                    ref.putFile(uri).await()
                    val url = ref.downloadUrl.await().toString()
                    attachmentList += mapOf("name" to name, "url" to url, "mimeType" to mime)
                }
                firestore.collection("newsCampaigns").document(campaignId).update("attachments", attachmentList).await()
                refresh()
            } catch (_: Exception) { }
        }
    }

    fun saveTemplate(name: String, title: String, body: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("newsTemplates").add(mapOf(
            "name" to name.trim(), "title" to title.trim(), "body" to body.trim(),
            "createdBy" to uid, "updatedAtMillis" to System.currentTimeMillis()
        )).addOnSuccessListener { refresh() }
    }

    fun loadViews(campaignId: String, audienceType: String = "ALL", audienceIds: List<String> = emptyList()) {
        val campaignRef = firestore.collection("newsCampaigns").document(campaignId)
        campaignRef.collection("views").orderBy("viewedAtMillis", Query.Direction.DESCENDING).limit(5000).get()
            .addOnSuccessListener { snap ->
                val readUsers = snap.documents.map { d ->
                    NewsAdminUserView(d.id, d.getString("displayName") ?: "Пользователь", d.getString("classId") ?: "—", d.getLong("viewedAtMillis") ?: 0L, d.getString("reaction") ?: "")
                }
                _selectedViews.value = readUsers
                firestore.collection("users").limit(5000).get().addOnSuccessListener { usersSnap ->
                    val audience = usersSnap.documents.filter { d ->
                        val data=d.data.orEmpty(); when(audienceType){
                            "ONLINE" -> data["online"] == true || data["isOnline"] == true
                            "CLASSES" -> (data["classId"] ?: data["class_id"]) in audienceIds
                            "GROUPS" -> (data["groupId"] ?: data["group_id"]) in audienceIds
                            else -> true
                        }
                    }.map { d -> NewsAdminUserView(d.id, d.getString("displayName") ?: "Пользователь", d.getString("classId") ?: d.getString("class_id") ?: "—", 0L, "") }
                    val readIds = readUsers.map { it.uid }.toSet()
                    _selectedUnread.value = audience.filterNot { it.uid in readIds }
                }
            }
    }

    fun loadComments(campaignId: String) {
        firestore.collection("newsCampaigns").document(campaignId).collection("comments")
            .orderBy("createdAtMillis", Query.Direction.DESCENDING).limit(300).get()
            .addOnSuccessListener { _selectedComments.value = it.documents.map { d -> d.data.orEmpty() + ("id" to d.id) } }
    }

    fun deleteComment(campaignId: String, commentId: String) {
        firestore.collection("newsCampaigns").document(campaignId).collection("comments").document(commentId).delete()
            .addOnSuccessListener { loadComments(campaignId) }
    }
}
