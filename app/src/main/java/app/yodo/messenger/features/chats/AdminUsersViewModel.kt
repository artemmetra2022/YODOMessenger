package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.AdminRole
import app.yodo.messenger.domain.model.AdminUserDetails
import app.yodo.messenger.domain.model.AdminUserModerationStats
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ProfileUpdateResult
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AdminUserStatus(val label: String) {
    NEW("Новичок"),
    ACTIVE("Активный"),
    INACTIVE("Неактивный 30+ дней"),
    BLOCKED("Заблокирован"),
    REVIEW("На проверке")
}

enum class AdminPresenceFilter(val label: String) {
    ALL("Онлайн/оффлайн"),
    ONLINE("Только онлайн"),
    OFFLINE("Только оффлайн")
}

enum class AdminDifficultyFilter(val label: String) {
    ALL("Все пользователи"),
    DIFFICULT("Только трудные")
}

data class AdminUserFilters(
    val status: AdminUserStatus? = null,
    val country: String = "",
    val classId: String = "",
    val registeredFrom: String = "",
    val registeredTo: String = "",
    val presence: AdminPresenceFilter = AdminPresenceFilter.ALL,
    val difficulty: AdminDifficultyFilter = AdminDifficultyFilter.ALL
)

sealed class AdminUsersUiState {
    data object Loading : AdminUsersUiState()
    data class Results(val users: List<YodoUser>) : AdminUsersUiState()
}

@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    firebaseAuth: FirebaseAuth
) : ViewModel() {
    val isAppAdmin = firebaseAuth.currentUser?.email?.lowercase() in
        app.yodo.messenger.domain.repository.ChatRepository.ADMIN_EMAILS.map { it.lowercase() }

    private val _allUsers = MutableStateFlow<List<YodoUser>>(emptyList())
    private val _moderationStats = MutableStateFlow<Map<String, AdminUserModerationStats>>(emptyMap())
    val moderationStats: StateFlow<Map<String, AdminUserModerationStats>> = _moderationStats

    private val _uiState = MutableStateFlow<AdminUsersUiState>(AdminUsersUiState.Loading)
    val uiState: StateFlow<AdminUsersUiState> = _uiState
    private val _filters = MutableStateFlow(AdminUserFilters())
    val filters: StateFlow<AdminUserFilters> = _filters
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected
    private val _details = MutableStateFlow<AdminUserDetails?>(null)
    val details: StateFlow<AdminUserDetails?> = _details
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val blockedCache = mutableMapOf<String, Boolean>()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = AdminUsersUiState.Loading
            val users = userRepository.getAdminUsers()
            _allUsers.value = users
            blockedCache.clear()
            users.forEach { u -> blockedCache[u.uid] = userRepository.getGlobalBlock(u.uid) != null }
            _moderationStats.value = userRepository.getAdminUserModerationStats()
            applyFilters()
        }
    }

    fun setFilters(filters: AdminUserFilters) {
        _filters.value = filters
        applyFilters()
    }

    fun difficultOf(uid: String): Boolean = _moderationStats.value[uid]?.difficult == true

    fun moderationStatsOf(uid: String): AdminUserModerationStats =
        _moderationStats.value[uid] ?: AdminUserModerationStats()

    fun isOnline(user: YodoUser): Boolean {
        val last = user.lastActiveAt
        return last > 0L && System.currentTimeMillis() - last <= 5L * 60L * 1000L
    }

    private fun applyFilters() {
        val f = _filters.value
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val from = parseDate(f.registeredFrom, false)
        val to = parseDate(f.registeredTo, true)
        val result = _allUsers.value.filter { u ->
            val status = statusOf(u, now, day)
            val online = isOnline(u)
            (f.status == null || status == f.status) &&
            (f.country.isBlank() || (u.country ?: "").contains(f.country, true)) &&
            (f.classId.isBlank() || (u.classId ?: "").contains(f.classId, true)) &&
            (from == null || u.createdAt >= from) &&
            (to == null || u.createdAt <= to) &&
            (f.presence == AdminPresenceFilter.ALL || (f.presence == AdminPresenceFilter.ONLINE && online) || (f.presence == AdminPresenceFilter.OFFLINE && !online)) &&
            (f.difficulty == AdminDifficultyFilter.ALL || difficultOf(u.uid))
        }
        _uiState.value = AdminUsersUiState.Results(result)
        _selected.value = _selected.value.intersect(result.map { it.uid }.toSet())
    }

    fun searchUsers(search: String): List<YodoUser> {
        val base = (_uiState.value as? AdminUsersUiState.Results)?.users ?: emptyList()
        val q = search.trim().removePrefix("@")
        if (q.isBlank()) return base
        return base.filter { user ->
            user.uid.contains(q, true) ||
            user.displayName.contains(q, true) ||
            (user.username ?: "").contains(q, true) ||
            (user.email ?: "").contains(q, true)
        }
    }

    fun availableClasses(): List<String> = _allUsers.value.mapNotNull { it.classId?.trim() }
        .filter(String::isNotBlank).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun statusOf(user: YodoUser, now: Long = System.currentTimeMillis(), day: Long = 86400000L): AdminUserStatus {
        val blocked = blockedCache[user.uid] ?: false
        if (blocked) return AdminUserStatus.BLOCKED
        val age = if (user.createdAt > 0) now - user.createdAt else Long.MAX_VALUE
        if (user.accountStatus?.equals("REVIEW", true) == true) return AdminUserStatus.REVIEW
        if (age < 7 * day) return AdminUserStatus.NEW
        if (user.lastActiveAt > 0 && now - user.lastActiveAt >= 30 * day) return AdminUserStatus.INACTIVE
        return AdminUserStatus.ACTIVE
    }

    fun refreshBlockedStatuses() {
        viewModelScope.launch {
            _allUsers.value.forEach { u ->
                blockedCache[u.uid] = userRepository.getGlobalBlock(u.uid) != null
            }
            _moderationStats.value = userRepository.getAdminUserModerationStats()
            applyFilters()
        }
    }

    fun toggleSelected(uid: String) {
        _selected.value = if (uid in _selected.value) _selected.value - uid else _selected.value + uid
    }

    fun selectAllVisible() {
        val users = (_uiState.value as? AdminUsersUiState.Results)?.users ?: return
        selectAllUsers(users.map { it.uid })
    }

    fun selectAllUsers(uids: List<String>) {
        val ids = uids.distinct()
        if (ids.isEmpty()) { _selected.value = emptySet(); return }
        val allSelected = ids.all { it in _selected.value }
        _selected.value = if (allSelected) _selected.value - ids.toSet() else _selected.value + ids
    }

    fun clearSelection() { _selected.value = emptySet() }

    fun blockSelected(reason: String, description: String) {
        val ids = _selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { uid ->
                when (val r = userRepository.setGlobalBlockWithHistory(uid, reason, description)) {
                    is ProfileUpdateResult.Error -> _error.value = r.message
                    else -> {}
                }
            }
            clearSelection(); refreshBlockedStatuses()
        }
    }

    fun unblockSelected() {
        val ids = _selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { uid ->
                when (val r = userRepository.removeGlobalBlockWithHistory(uid)) {
                    is ProfileUpdateResult.Error -> _error.value = r.message
                    else -> {}
                }
            }
            clearSelection(); refreshBlockedStatuses()
        }
    }

    fun updateSelectedClass(classId: String) {
        val ids = _selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            when (val result = userRepository.updateUsersClass(ids, classId.trim())) {
                is ProfileUpdateResult.Error -> _error.value = result.message
                else -> {
                    clearSelection()
                    reload()
                }
            }
        }
    }

    fun assignAdmin(uid: String, role: AdminRole) {
        viewModelScope.launch {
            when (val r = userRepository.assignAdmin(uid, role)) {
                is ProfileUpdateResult.Error -> _error.value = r.message
                else -> reload()
            }
        }
    }

    fun removeAdmin(uid: String) {
        viewModelScope.launch {
            when (val r = userRepository.removeAdmin(uid)) {
                is ProfileUpdateResult.Error -> _error.value = r.message
                else -> reload()
            }
        }
    }

    fun openDetails(uid: String) {
        viewModelScope.launch { _details.value = userRepository.getAdminUserDetails(uid) }
    }
    fun closeDetails() { _details.value = null }
    fun consumeError() { _error.value = null }

    private fun parseDate(value: String, endOfDay: Boolean): Long? {
        if (value.isBlank()) return null
        return runCatching {
            val p = value.trim().split("-").map { it.toInt() }
            java.util.Calendar.getInstance().apply {
                clear(); set(p[0], p[1] - 1, p[2], if (endOfDay) 23 else 0, if (endOfDay) 59 else 0, if (endOfDay) 59 else 0)
                set(java.util.Calendar.MILLISECOND, if (endOfDay) 999 else 0)
            }.timeInMillis
        }.getOrNull()
    }
}
