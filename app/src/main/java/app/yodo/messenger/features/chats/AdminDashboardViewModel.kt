package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    data class Stats(
        val users: Int = 0,
        val onlineUsers: Int = 0,
        val chats: Int = 0,
        val complaints: Int = 0,
        val news: Int = 0,
        val faq: Int = 0,
        val teachers: Int = 0,
        val auditEvents: Int = 0,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _stats = MutableStateFlow(Stats(isLoading = true))
    val stats: StateFlow<Stats> = _stats

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _stats.value = _stats.value.copy(isLoading = true, error = null)
            try {
                val refs = listOf(
                    "users", "chats", "newsCampaigns", "faqEntries",
                    "teacherProfiles", "adminAuditLog"
                )
                val totals = refs.map { name ->
                    async { firestore.collection(name).get().await().size() }
                }.awaitAll()

                val online = firestore.collection("users")
                    .whereEqualTo("isOnline", true)
                    .get().await().size()

                val complaints = firestore.collection("chats")
                    .document("yodo_official_channel")
                    .collection("reports")
                    .get().await().size()

                _stats.value = Stats(
                    users = totals[0],
                    onlineUsers = online,
                    chats = totals[1],
                    complaints = complaints,
                    news = totals[2],
                    faq = totals[3],
                    teachers = totals[4],
                    auditEvents = totals[5],
                    isLoading = false
                )
            } catch (e: Exception) {
                _stats.value = _stats.value.copy(
                    isLoading = false,
                    error = e.message ?: "Не удалось загрузить статистику"
                )
            }
        }
    }
}
