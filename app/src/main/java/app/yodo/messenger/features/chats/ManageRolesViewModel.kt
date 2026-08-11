package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.AssignedRole
import app.yodo.messenger.domain.model.BuiltInRole
import app.yodo.messenger.domain.model.CustomRole
import app.yodo.messenger.domain.model.Permission
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Строка списка участников на экране "Роли и права". */
data class RoleMemberRow(
    val user: YodoUser,
    val isOwner: Boolean,
    val roleLabel: String, // "Владелец" / название встроенной роли / название кастомной роли / "Участник"
    val assignedRole: AssignedRole?
)

data class ManageRolesUiState(
    val isLoading: Boolean = true,
    val ownerId: String? = null,
    val members: List<RoleMemberRow> = emptyList(),
    val customRoles: List<CustomRole> = emptyList(),
    val canManage: Boolean = false
)

@HiltViewModel
class ManageRolesViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(ManageRolesUiState())
    val uiState: StateFlow<ManageRolesUiState> = _uiState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val myUid = firebaseAuth.currentUser?.uid
            val chatInfo = chatRepository.getChatInfo(chatId)
            val groupInfo = if (chatInfo?.type == "GROUP") chatRepository.getGroupInfo(chatId) else null
            val ownerId = chatInfo?.channelOwnerId ?: groupInfo?.createdBy
            val participantUsers: List<YodoUser> = groupInfo?.members
                ?: chatInfo?.let { info ->
                    // Для каналов список участников не хранится в ChatInfo напрямую —
                    // используем адрес владельца+админов как минимум для управления ролями админов.
                    (listOfNotNull(info.channelOwnerId) + info.channelAdminIds)
                        .distinct()
                        .mapNotNull { userRepository.getUserById(it) }
                } ?: emptyList()

            val assignedRoles = chatRepository.getAssignedRoles(chatId).associateBy { it.userId }
            val customRoles = chatRepository.getCustomRoles(chatId)
            val customRolesById = customRoles.associateBy { it.id }

            val rows = participantUsers.map { user ->
                val isOwner = user.uid == ownerId
                val assigned = assignedRoles[user.uid]
                val label = when {
                    isOwner -> BuiltInRole.OWNER.displayName
                    assigned?.customRoleId != null -> customRolesById[assigned.customRoleId]?.name ?: "Роль"
                    assigned?.builtIn != null -> assigned.builtIn.displayName
                    else -> "Участник"
                }
                RoleMemberRow(user = user, isOwner = isOwner, roleLabel = label, assignedRole = assigned)
            }.sortedByDescending { it.isOwner }

            val canManage = myUid != null && (myUid == ownerId || assignedRoles[myUid]?.builtIn != null &&
                run {
                    val perms = assignedRoles[myUid]?.builtIn?.let { Permission.defaultSetFor(it) } ?: emptySet()
                    Permission.ADD_ADMINS in perms
                })

            _uiState.value = ManageRolesUiState(
                isLoading = false,
                ownerId = ownerId,
                members = rows,
                customRoles = customRoles,
                canManage = canManage
            )
        }
    }

    fun assignBuiltInRole(userId: String, role: BuiltInRole) {
        viewModelScope.launch {
            when (val result = chatRepository.assignBuiltInRole(chatId, userId, role)) {
                is ChannelUpdateResult.Success -> refresh()
                is ChannelUpdateResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun assignCustomRole(userId: String, customRoleId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.assignCustomRole(chatId, userId, customRoleId)) {
                is ChannelUpdateResult.Success -> refresh()
                is ChannelUpdateResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun revokeRole(userId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.revokeRole(chatId, userId)) {
                is ChannelUpdateResult.Success -> refresh()
                is ChannelUpdateResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun createCustomRole(name: String, permissions: Set<Permission>) {
        viewModelScope.launch {
            when (val result = chatRepository.createCustomRole(chatId, name, permissions)) {
                is ChannelUpdateResult.Success -> refresh()
                is ChannelUpdateResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun updateCustomRole(roleId: String, name: String, permissions: Set<Permission>) {
        viewModelScope.launch {
            when (val result = chatRepository.updateCustomRole(chatId, roleId, name, permissions)) {
                is ChannelUpdateResult.Success -> refresh()
                is ChannelUpdateResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun deleteCustomRole(roleId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.deleteCustomRole(chatId, roleId)) {
                is ChannelUpdateResult.Success -> refresh()
                is ChannelUpdateResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun consumeErrorMessage() { _errorMessage.value = null }
}
