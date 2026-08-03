package app.yodo.messenger.data.repository

import android.content.Context
import android.os.Build
import app.yodo.messenger.domain.model.DeviceSession
import app.yodo.messenger.domain.repository.SessionRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : SessionRepository {

    companion object {
        private const val PREFS_NAME = "yodo_session_prefs"
        private const val KEY_SESSION_ID = "session_id"
    }

    /** UUID текущего устройства — создаётся один раз и хранится в SharedPreferences. */
    override fun getCurrentSessionId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_SESSION_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_SESSION_ID, id).apply()
        }
        return id
    }

    override suspend fun updateCurrentSession() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val sessionId = getCurrentSessionId()

        val data = mapOf(
            "sessionId" to sessionId,
            "deviceName" to getDeviceName(),
            "platform" to "Android ${Build.VERSION.RELEASE}",
            "appVersion" to getAppVersion(),
            "lastActiveAt" to System.currentTimeMillis()
        )

        val ref = firestore
            .collection("users")
            .document(uid)
            .collection("sessions")
            .document(sessionId)

        try {
            val snap = ref.get().await()
            if (snap.exists()) {
                // Документ есть — обновляем только lastActiveAt и appVersion
                ref.update(
                    mapOf(
                        "lastActiveAt" to System.currentTimeMillis(),
                        "appVersion" to getAppVersion()
                    )
                ).await()
            } else {
                // Первый вход с этого устройства — создаём полный документ
                ref.set(data + mapOf("createdAt" to System.currentTimeMillis())).await()
            }
        } catch (e: Exception) {
            android.util.Log.w("SessionRepository", "Не удалось обновить сессию: ${e.message}")
        }
    }

    override fun observeSessions(): Flow<List<DeviceSession>> {
        val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())
        val currentId = getCurrentSessionId()

        return callbackFlow {
            val listener = firestore
                .collection("users")
                .document(uid)
                .collection("sessions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("SessionRepository", "Ошибка подписки: ${error.message}")
                        return@addSnapshotListener
                    }
                    val sessions = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            DeviceSession(
                                sessionId = doc.getString("sessionId") ?: doc.id,
                                deviceName = doc.getString("deviceName") ?: "Неизвестное устройство",
                                platform = doc.getString("platform") ?: "Android",
                                appVersion = doc.getString("appVersion") ?: "",
                                lastActiveAt = doc.getLong("lastActiveAt") ?: 0L,
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                isCurrent = doc.id == currentId
                            )
                        } catch (e: Exception) { null }
                    }?.sortedByDescending { it.lastActiveAt } ?: emptyList()

                    trySend(sessions)
                }
            awaitClose { listener.remove() }
        }
    }

    override fun observeCurrentSessionExists(): Flow<Boolean> {
        val uid = firebaseAuth.currentUser?.uid ?: return flowOf(false)
        val currentId = getCurrentSessionId()

        return callbackFlow {
            // На старте документ сессии может ещё не существовать: updateCurrentSession()
            // создаёт его асинхронно (см. YodoApp.onCreate), и снапшот-листенер вполне может
            // сработать раньше. Поэтому "документа нет" трактуем как реальное завершение сеанса
            // только ПОСЛЕ того, как хотя бы раз увидели, что документ существует — иначе
            // пользователя разлогинит собственный первый вход.
            var sawExisting = false

            val listener = firestore
                .collection("users")
                .document(uid)
                .collection("sessions")
                .document(currentId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("SessionRepository", "Ошибка подписки на текущую сессию: ${error.message}")
                        // Ошибку подписки не трактуем как "сессию завершили" — иначе разлогиним
                        // пользователя просто из-за временной проблемы с сетью/правами.
                        return@addSnapshotListener
                    }
                    val exists = snapshot?.exists() == true
                    if (exists) {
                        sawExisting = true
                        trySend(true)
                    } else if (sawExisting) {
                        // Документ реально существовал и пропал — вот это уже сигнал разлогина.
                        trySend(false)
                    }
                    // else: документа ещё нет (создаётся) — ничего не эмитим, ждём.
                }
            awaitClose { listener.remove() }
        }
    }

    override suspend fun terminateSession(sessionId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (sessionId == getCurrentSessionId()) return  // нельзя удалить текущий сеанс
        try {
            firestore
                .collection("users")
                .document(uid)
                .collection("sessions")
                .document(sessionId)
                .delete()
                .await()
        } catch (e: Exception) {
            android.util.Log.w("SessionRepository", "Не удалось завершить сессию: ${e.message}")
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Вспомогательные функции
    // ────────────────────────────────────────────────────────────────────────────

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
            .lowercase()
            .replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model
        else "$manufacturer $model"
    }

    private fun getAppVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    } catch (e: Exception) { "—" }
}
