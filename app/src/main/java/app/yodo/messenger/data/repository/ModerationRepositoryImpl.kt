package app.yodo.messenger.data.repository

import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.domain.model.DeletedMessageRecord
import app.yodo.messenger.domain.model.ModerationMatchType
import app.yodo.messenger.domain.model.ModerationRule
import app.yodo.messenger.domain.model.ModerationDeleteReason
import app.yodo.messenger.domain.repository.ModerationActionResult
import app.yodo.messenger.domain.repository.ModerationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModerationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ModerationRepository {

    private val rulesRef get() = firestore.collection("moderationRules")
    private val historyRef get() = firestore.collection("moderationDeletedMessages")

    private fun isAdmin(): Boolean = firebaseAuth.currentUser?.email?.lowercase(Locale.ROOT) in
        app.yodo.messenger.domain.repository.ChatRepository.ADMIN_EMAILS.map { it.lowercase(Locale.ROOT) }

    override fun observeRules(): Flow<List<ModerationRule>> = callbackFlow {
        if (!isAdmin()) { trySend(emptyList()); close(); return@callbackFlow }
        val listener = rulesRef.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents.orEmpty().mapNotNull { d ->
                    runCatching { parseRule(d.id, d.data ?: emptyMap()) }.getOrNull()
                })
            }
        awaitClose { listener.remove() }
    }

    override suspend fun saveRule(rule: ModerationRule): ModerationActionResult {
        if (!isAdmin()) return ModerationActionResult.Error("Доступ только для администраторов")
        val pattern = rule.pattern.trim()
        if (pattern.isBlank()) return ModerationActionResult.Error("Укажите паттерн")
        if (rule.matchType == ModerationMatchType.REGEX) {
            try { Regex(pattern) } catch (_: Exception) { return ModerationActionResult.Error("Некорректное регулярное выражение") }
        }
        return try {
            val uid = firebaseAuth.currentUser?.uid.orEmpty()
            val now = System.currentTimeMillis()
            val ref = if (rule.id.isBlank()) rulesRef.document() else rulesRef.document(rule.id)
            val data = mapOf(
                "name" to rule.name.trim().take(100),
                "pattern" to pattern.take(500),
                "matchType" to rule.matchType.name,
                "reason" to rule.reason.name,
                "enabled" to rule.enabled,
                "createdAt" to if (rule.id.isBlank()) now else rule.createdAt,
                "updatedAt" to now,
                "createdBy" to if (rule.createdBy.isBlank()) uid else rule.createdBy
            )
            ref.set(data).await()
            ModerationActionResult.Success
        } catch (e: Exception) { ModerationActionResult.Error(e.toUserMessage("Не удалось сохранить правило")) }
    }

    override suspend fun deleteRule(ruleId: String): ModerationActionResult {
        if (!isAdmin()) return ModerationActionResult.Error("Доступ только для администраторов")
        return try { rulesRef.document(ruleId).delete().await(); ModerationActionResult.Success }
        catch (e: Exception) { ModerationActionResult.Error(e.toUserMessage("Не удалось удалить правило")) }
    }

    override fun observeDeletedMessages(): Flow<List<DeletedMessageRecord>> = callbackFlow {
        if (!isAdmin()) { trySend(emptyList()); close(); return@callbackFlow }
        val listener = historyRef.orderBy("deletedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val now = System.currentTimeMillis()
                val records = snapshot?.documents.orEmpty().mapNotNull { d ->
                    runCatching { parseHistory(d.id, d.data ?: emptyMap()) }.getOrNull()
                }.filter { it.restoreUntil > now }
                trySend(records)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun restoreDeletedMessage(record: DeletedMessageRecord): ModerationActionResult {
        if (!isAdmin()) return ModerationActionResult.Error("Доступ только для администраторов")
        if (!record.canRestore()) return ModerationActionResult.Error("Срок восстановления 30 дней истёк")
        return try {
            val messageRef = firestore.collection("chats").document(record.chatId)
                .collection("messages").document(record.messageId)
            messageRef.set(record.originalData).await()
            historyRef.document(record.id).delete().await()
            ModerationActionResult.Success
        } catch (e: Exception) { ModerationActionResult.Error(e.toUserMessage("Не удалось восстановить сообщение")) }
    }

    override suspend fun cleanupExpiredHistory() {
        if (!isAdmin()) return
        val now = System.currentTimeMillis()
        val docs = historyRef.whereLessThanOrEqualTo("restoreUntil", now).get().await().documents
        docs.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    override suspend fun findMatchingRule(text: String): ModerationRule? {
        if (text.isBlank()) return null
        return try {
            val rules = rulesRef.whereEqualTo("enabled", true).get().await().documents.mapNotNull { d ->
                runCatching { parseRule(d.id, d.data ?: emptyMap()) }.getOrNull()
            }
            rules.firstOrNull { rule -> matches(rule, text) }
        } catch (_: Exception) { null }
    }

    private fun matches(rule: ModerationRule, text: String): Boolean {
        return when (rule.matchType) {
            ModerationMatchType.CONTAINS -> text.contains(rule.pattern, ignoreCase = true)
            ModerationMatchType.REGEX -> runCatching { Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(text) }.getOrDefault(false)
            ModerationMatchType.URL_DOMAIN -> {
                val domain = rule.pattern.lowercase(Locale.ROOT).removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
                Regex("(?i)(?:https?://)?(?:www\\.)?" + Regex.escape(domain)).containsMatchIn(text)
            }
        }
    }

    private fun parseRule(id: String, d: Map<String, Any?>) = ModerationRule(
        id = id, name = d["name"] as? String ?: "Правило", pattern = d["pattern"] as? String ?: "",
        matchType = runCatching { ModerationMatchType.valueOf(d["matchType"] as? String ?: "CONTAINS") }.getOrDefault(ModerationMatchType.CONTAINS),
        reason = runCatching { ModerationDeleteReason.valueOf(d["reason"] as? String ?: "OTHER") }.getOrDefault(ModerationDeleteReason.OTHER),
        enabled = d["enabled"] as? Boolean ?: true, createdAt = (d["createdAt"] as? Number)?.toLong() ?: 0L,
        updatedAt = (d["updatedAt"] as? Number)?.toLong() ?: 0L, createdBy = d["createdBy"] as? String ?: ""
    )

    private fun parseHistory(id: String, d: Map<String, Any?>) = DeletedMessageRecord(
        id = id, chatId = d["chatId"] as? String ?: "", messageId = d["messageId"] as? String ?: "",
        senderId = d["senderId"] as? String ?: "", senderName = d["senderName"] as? String ?: "",
        preview = d["preview"] as? String ?: "", reason = runCatching { ModerationDeleteReason.valueOf(d["reason"] as? String ?: "OTHER") }.getOrDefault(ModerationDeleteReason.OTHER),
        deletedAt = (d["deletedAt"] as? Number)?.toLong() ?: 0L, restoreUntil = (d["restoreUntil"] as? Number)?.toLong() ?: 0L,
        deletedBy = d["deletedBy"] as? String ?: "", automatic = d["automatic"] as? Boolean ?: false,
        originalData = (d["originalData"] as? Map<*, *>)?.entries?.mapNotNull { (k,v) -> (k as? String)?.let { it to v } }?.toMap() ?: emptyMap()
    )
}
