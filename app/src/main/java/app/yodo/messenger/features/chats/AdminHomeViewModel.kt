package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.AppSettingsRepository
import app.yodo.messenger.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (единая вкладка «Админка»): сводный экран для 2 доверенных email-аккаунтов
 * (ChatRepository.ADMIN_EMAILS) — единая точка входа во все админ-функции приложения:
 * обращения в поддержку, глобальные жалобы, поиск/блокировка пользователей,
 * официальный канал рассылок, переключатель обязательного подтверждения email.
 *
 * Ничего не дублирует по логике — каждый раздел здесь просто открывает уже
 * существующий экран (AdminPanelScreen, ReportInboxScreen, официальный чат) либо,
 * для настройки email-подтверждения, использует тот же AppSettingsRepository, что
 * и SecurityViewModel. Прежние точки входа (пункт в Настройках, FAB в списке чатов,
 * кнопка на профиле пользователя) остаются на месте — Админка добавляет к ним
 * параллельный путь, а не заменяет их.
 */
@HiltViewModel
class AdminHomeViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    firebaseAuth: FirebaseAuth
) : ViewModel() {

    val isAppAdmin: Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }

    val officialChannelId: String = ChatRepository.OFFICIAL_CHANNEL_ID

    val requireEmailVerification: Flow<Boolean> = appSettingsRepository.observeRequireEmailVerification()

    fun setRequireEmailVerification(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setRequireEmailVerification(enabled) }
    }
}
