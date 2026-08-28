package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.JoinRequest
import app.yodo.messenger.domain.model.MemberPermissions
import app.yodo.messenger.domain.model.Permission
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import app.yodo.messenger.domain.repository.GroupInfo
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GroupInfoUiState {
    data object Loading : GroupInfoUiState()
    data class Content(val info: GroupInfo) : GroupInfoUiState()
    data object NotFound : GroupInfoUiState()
}

/**
 * НОВОЕ (админ-функции групп): состояние управления участниками группы —
 * права текущего пользователя, заявки на вступление, забаненные, подписи ролей.
 */
data class GroupAdminState(
    // Права текущего пользователя (владелец/роль) — определяют видимость админ-действий.
    val myPermissions: MemberPermissions? = null,
    val canManageMembers: Boolean = false,
    // Ожидающие заявки на вступление (для владельца/админа).
    val joinRequests: List<JoinRequest> = emptyList(),
    // Забаненные участники (для владельца/админа).
    val bannedMembers: List<YodoUser> = emptyList(),
    // НОВОЕ (бейджи ролей): uid -> название роли («Модератор», кастомная и т.д.) —
    // показывается под именем участника всем, как строка «Владелец».
    val memberRoles: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val myUid: String? get() = firebaseAuth.currentUser?.uid

    private val _uiState = MutableStateFlow<GroupInfoUiState>(GroupInfoUiState.Loading)
    val uiState: StateFlow<GroupInfoUiState> = _uiState

    private val _adminState = MutableStateFlow(GroupAdminState())
    val adminState: StateFlow<GroupAdminState> = _adminState

    private val _didLeave = MutableStateFlow(false)
    val didLeave: StateFlow<Boolean> = _didLeave

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val info = chatRepository.getGroupInfo(chatId)
            _uiState.value = if (info != null) GroupInfoUiState.Content(info) else GroupInfoUiState.NotFound
            if (info != null) loadAdminState(info.createdBy)
        }
    }

    private suspend fun loadAdminState(ownerId: String?) {
        val myUid = firebaseAuth.currentUser?.uid ?: return
        val permissions = chatRepository.getMemberPermissions(chatId, myUid)
        // Управление участниками (заявки, исключение, бан) — только владелец или
        // назначенный админ: это же ограничение стоит в репозитории (isChannelManager)
        // и в правилах Firestore (изменение participantIds/adminIds).
        val canManage = myUid == ownerId || permissions.has(Permission.ADD_ADMINS)
        // НОВОЕ (бейджи ролей): подписи ролей под именами участников — видны всем.
        val assigned = runCatching { chatRepository.getAssignedRoles(chatId) }.getOrDefault(emptyList())
        val customRoles = runCatching { chatRepository.getCustomRoles(chatId) }.getOrDefault(emptyList())
        val roleNames = assigned.associate { r ->
            r.userId to (r.builtIn?.displayName
                ?: customRoles.firstOrNull { it.id == r.customRoleId }?.name ?: "Роль")
        }
        _adminState.value = _adminState.value.copy(
            myPermissions = permissions,
            canManageMembers = canManage,
            memberRoles = roleNames
        )
        if (canManage) {
            val requests = chatRepository.getJoinRequests(chatId)
            val bannedIds = chatRepository.getBannedMemberIds(chatId)
            val banned = bannedIds.mapNotNull { userRepository.getUserById(it) }
            _adminState.value = _adminState.value.copy(joinRequests = requests, bannedMembers = banned)
        }
    }

    /** Перечитать данные группы и админ-состояние (после любого действия). */
    fun reload() {
        load()
    }

    fun leaveGroup() {
        viewModelScope.launch {
            chatRepository.leaveGroup(chatId)
            _didLeave.value = true
        }
    }

    // НОВОЕ (админ-функции групп): одобрение/отклонение заявок на вступление.

    fun approveRequest(userId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.approveJoinRequest(chatId, userId)) {
                is ChannelUpdateResult.Error -> _adminState.value = _adminState.value.copy(errorMessage = result.message)
                else -> reload()
            }
        }
    }

    fun rejectRequest(userId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.rejectJoinRequest(chatId, userId)) {
                is ChannelUpdateResult.Error -> _adminState.value = _adminState.value.copy(errorMessage = result.message)
                else -> reload()
            }
        }
    }

    // НОВОЕ (админ-функции групп): исключение/бан/разбан участника.

    fun kickMember(userId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.removeMember(chatId, userId)) {
                is ChannelUpdateResult.Error -> _adminState.value = _adminState.value.copy(errorMessage = result.message)
                else -> reload()
            }
        }
    }

    fun banMember(userId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.banMember(chatId, userId)) {
                is ChannelUpdateResult.Error -> _adminState.value = _adminState.value.copy(errorMessage = result.message)
                else -> reload()
            }
        }
    }

    fun unbanMember(userId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.unbanMember(chatId, userId)) {
                is ChannelUpdateResult.Error -> _adminState.value = _adminState.value.copy(errorMessage = result.message)
                else -> reload()
            }
        }
    }

    fun consumeErrorMessage() {
        _adminState.value = _adminState.value.copy(errorMessage = null)
    }
}
