package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.repository.AppSettingsRepository
import app.yodo.messenger.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AppSettingsRepository {

    companion object {
        private const val DOC_PATH = "config/appSettings"
        private const val FIELD = "requireEmailVerification"
        // По умолчанию (документ ещё не создан) сохраняем текущее поведение —
        // подтверждение почты требуется, как было до появления этого переключателя.
        private const val DEFAULT_VALUE = true
    }

    private fun doc() = firestore.document(DOC_PATH)

    override fun observeRequireEmailVerification(): Flow<Boolean> = callbackFlow {
        val listener = doc().addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.w("AppSettingsRepository", "Ошибка слежения: ${error.message}")
                return@addSnapshotListener
            }
            trySend((snapshot?.getBoolean(FIELD)) ?: DEFAULT_VALUE)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun isEmailVerificationRequired(): Boolean {
        return try {
            val snap = doc().get().await()
            snap.getBoolean(FIELD) ?: DEFAULT_VALUE
        } catch (e: Exception) {
            android.util.Log.w("AppSettingsRepository", "Не удалось прочитать настройку: ${e.message}")
            // При ошибке чтения безопаснее сохранить прежнее (строгое) поведение.
            DEFAULT_VALUE
        }
    }

    override suspend fun setRequireEmailVerification(enabled: Boolean): Boolean {
        val myEmail = firebaseAuth.currentUser?.email?.lowercase()
        if (myEmail == null || myEmail !in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }) {
            return false
        }
        return try {
            doc().set(mapOf(FIELD to enabled), com.google.firebase.firestore.SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            android.util.Log.w("AppSettingsRepository", "Не удалось изменить настройку: ${e.message}")
            false
        }
    }
}
