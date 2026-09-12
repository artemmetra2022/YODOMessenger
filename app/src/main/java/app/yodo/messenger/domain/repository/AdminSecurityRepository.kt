package app.yodo.messenger.domain.repository

import kotlinx.coroutines.flow.Flow

data class AdminTotpState(val enabled: Boolean = false, val secret: String? = null)

interface AdminSecurityRepository {
    fun observeState(): Flow<AdminTotpState>
    suspend fun beginSetup(): Result<String>
    suspend fun verifyAndEnable(code: String): Result<Unit>
    suspend fun verify(code: String): Boolean
    suspend fun disable(code: String): Result<Unit>
}
