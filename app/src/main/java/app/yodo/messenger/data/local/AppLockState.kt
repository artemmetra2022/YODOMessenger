package app.yodo.messenger.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * НОВОЕ (скрытые чаты): глобальное runtime-состояние блокировки приложения.
 *
 * decoyMode = true означает, что пользователь разблокировал приложение "ложным"
 * (decoy) PIN-кодом. В этом режиме чаты, помеченные как скрытые, не показываются
 * в списке чатов — как будто их не существует. Состояние хранится только в памяти
 * процесса и сбрасывается при вводе основного PIN-кода.
 */
@Singleton
class AppLockState @Inject constructor() {
    private val _decoyMode = MutableStateFlow(false)
    val decoyMode: StateFlow<Boolean> = _decoyMode.asStateFlow()

    fun setDecoyMode(active: Boolean) {
        _decoyMode.value = active
    }
}
