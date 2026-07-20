package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override val currentUser: YodoUser?
        get() = firebaseAuth.currentUser?.toYodoUser()

    override fun isLoggedIn(): Boolean = firebaseAuth.currentUser != null

    override suspend fun login(email: String, password: String): AuthResult {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return AuthResult.Error("Не удалось получить данные пользователя")
            AuthResult.Success(user.toYodoUser())
        } catch (e: Exception) {
            AuthResult.Error(e.mapToUserMessage())
        }
    }

    override suspend fun register(name: String, email: String, password: String): AuthResult {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = result.user ?: return AuthResult.Error("Не удалось создать пользователя")

            // Записываем отображаемое имя в профиль Firebase Auth
            firebaseUser.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
            ).await()

            // Создаём документ пользователя в Firestore — понадобится для списка чатов/поиска контактов.
            // Оборачиваем в таймаут: если Firestore Database ещё не создана в консоли или недоступна,
            // не блокируем экран навсегда — пользователь Auth уже создан, это главное.
            val firestoreWriteSucceeded = withTimeoutOrNull(8000) {
                firestore.collection("users").document(firebaseUser.uid).set(
                    mapOf(
                        "uid" to firebaseUser.uid,
                        "displayName" to name.trim(),
                        "displayNameLowercase" to name.trim().lowercase(),
                        "email" to email.trim(),
                        "createdAt" to System.currentTimeMillis()
                    )
                ).await()
                true
            }

            if (firestoreWriteSucceeded == null) {
                // Не считаем это фатальной ошибкой регистрации — просто предупреждаем в логах.
                android.util.Log.w(
                    "AuthRepository",
                    "Не удалось записать профиль в Firestore за 8 секунд. " +
                        "Проверь, что Firestore Database создана в консоли Firebase."
                )
            }

            AuthResult.Success(firebaseUser.toYodoUser(nameOverride = name.trim()))
        } catch (e: Exception) {
            AuthResult.Error(e.mapToUserMessage())
        }
    }

    override fun logout() {
        firebaseAuth.signOut()
    }

    override suspend fun loginWithGoogle(idToken: String): AuthResult {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user ?: return AuthResult.Error("Не удалось получить данные пользователя")

            // Создаём документ в Firestore при первом входе через Google (если его ещё нет).
            // Таймаут — та же защита от зависания, что и в email/phone-регистрации.
            withTimeoutOrNull(8000) {
                val userRef = firestore.collection("users").document(user.uid)
                val snapshot = userRef.get().await()
                if (!snapshot.exists()) {
                    userRef.set(
                        mapOf(
                            "uid" to user.uid,
                            "displayName" to (user.displayName ?: ""),
                            "displayNameLowercase" to (user.displayName ?: "").lowercase(),
                            "email" to user.email,
                            "avatarUrl" to user.photoUrl?.toString(),
                            "createdAt" to System.currentTimeMillis()
                        )
                    ).await()
                }
            } ?: android.util.Log.w(
                "AuthRepository",
                "Не удалось синхронизировать профиль Google-входа с Firestore за 8 секунд."
            )

            AuthResult.Success(user.toYodoUser())
        } catch (e: Exception) {
            AuthResult.Error(e.mapToUserMessage())
        }
    }

    private fun FirebaseUser.toYodoUser(nameOverride: String? = null) = YodoUser(
        uid = uid,
        displayName = nameOverride ?: displayName.orEmpty(),
        email = email,
        phoneNumber = phoneNumber,
        photoUrl = photoUrl?.toString()
    )

    /** Переводим типовые ошибки Firebase в понятные пользователю сообщения на русском. */
    private fun Exception.mapToUserMessage(): String = when {
        message?.contains("badly formatted", ignoreCase = true) == true -> "Некорректный email"
        message?.contains("password is invalid", ignoreCase = true) == true -> "Неверный пароль"
        message?.contains("no user record", ignoreCase = true) == true -> "Пользователь не найден"
        message?.contains("email address is already in use", ignoreCase = true) == true -> "Этот email уже зарегистрирован"
        message?.contains("network", ignoreCase = true) == true -> "Проблема с подключением к сети"
        else -> message ?: "Неизвестная ошибка авторизации"
    }
}
