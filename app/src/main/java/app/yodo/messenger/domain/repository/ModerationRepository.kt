package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.DeletedMessageRecord
import app.yodo.messenger.domain.model.ModerationRule
import kotlinx.coroutines.flow.Flow

sealed class ModerationActionResult {
    data object Success : ModerationActionResult()
    data class Error(val message: String) : ModerationActionResult()
}

interface ModerationRepository {
    fun observeRules(): Flow<List<ModerationRule>>
    suspend fun saveRule(rule: ModerationRule): ModerationActionResult
    suspend fun deleteRule(ruleId: String): ModerationActionResult
    fun observeDeletedMessages(): Flow<List<DeletedMessageRecord>>
    suspend fun restoreDeletedMessage(record: DeletedMessageRecord): ModerationActionResult
    suspend fun cleanupExpiredHistory()
    suspend fun findMatchingRule(text: String): ModerationRule?
}
