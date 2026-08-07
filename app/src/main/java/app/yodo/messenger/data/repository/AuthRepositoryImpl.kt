package app.yodo.messenger.data.repository

import app.yodo.messenger.core.util.toUserMessage
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

    override suspend fun login(emailOrUsername: String, password: String): AuthResult {
        val input = emailOrUsername.trim()
        if (input.isBlank()) return AuthResult.Error("Введите email или username")

        return try {
            // Если строка похожа на email — логинимся напрямую через FirebaseAuth.
            // Иначе трактуем ввод как username: ищем соответствующий email в Firestore.
            val email = if (isEmailLike(input)) {
                input
            } else {
                resolveEmailByUsername(input)
                    ?: return AuthResult.Error("Пользователь с таким username не найден")
            }

            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return AuthResult.Error("Не удалось получить данные пользователя")
            warmUpFirestore(user.uid)
            AuthResult.Success(user.toYodoUser())
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage("Неизвестная ошибка авторизации"))
        }
    }

    override suspend fun register(name: String, username: String, email: String, password: String): AuthResult {
        val normalizedUsername = username.trim().removePrefix("@").lowercase()
        if (normalizedUsername.isBlank()) {
            return AuthResult.Error("Введите username")
        }
        if (!normalizedUsername.matches(Regex("^[a-z0-9_]{3,20}$"))) {
            return AuthResult.Error("Username: 3-20 символов, только латиница, цифры и \"_\"")
        }

        return try {
            // Резервируем username ДО создания пользователя в FirebaseAuth: если он уже занят,
            // не создаём аккаунт вообще, чтобы не оставлять "осиротевших" Auth-пользователей без username.
            val usernameRef = firestore.collection("usernames").document(normalizedUsername)
            val alreadyTaken = withTimeoutOrNull(8000) {
                usernameRef.get().await().exists()
            } ?: return AuthResult.Error("Не удалось проверить username, попробуйте ещё раз")

            if (alreadyTaken) {
                return AuthResult.Error("Этот username уже занят")
            }

            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = result.user ?: return AuthResult.Error("Не удалось создать пользователя")

            // Записываем отображаемое имя в профиль Firebase Auth
            firebaseUser.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
            ).await()

            // Создаём документ пользователя и резервируем username атомарно в одной транзакции.
            // Оборачиваем в таймаут: если Firestore Database ещё не создана в консоли или недоступна,
            // не блокируем экран навсегда — пользователь Auth уже создан, это главное.
            val firestoreWriteSucceeded = withTimeoutOrNull(8000) {
                firestore.runTransaction { transaction ->
                    val usernameSnapshot = transaction.get(usernameRef)
                    if (usernameSnapshot.exists()) {
                        throw IllegalStateException("USERNAME_TAKEN")
                    }
                    transaction.set(usernameRef, mapOf("uid" to firebaseUser.uid))
                    transaction.set(
                        firestore.collection("users").document(firebaseUser.uid),
                        mapOf(
                            "uid" to firebaseUser.uid,
                            "displayName" to name.trim(),
                            "displayNameLowercase" to name.trim().lowercase(),
                            "username" to normalizedUsername,
                            "usernameLowercase" to normalizedUsername,
                            "email" to email.trim(),
                            "createdAt" to System.currentTimeMillis()
                        )
                    )
                }.await()
                true
            }

            if (firestoreWriteSucceeded == null) {
                // Firestore недоступна — удаляем только что созданный Auth-аккаунт,
                // чтобы не оставлять «осиротевшего» пользователя без профиля.
                // Пользователь сможет попробовать зарегистрироваться снова.
                android.util.Log.w(
                    "AuthRepository",
                    "Не удалось записать профиль в Firestore за 8 секунд — откатываем Auth-аккаунт."
                )
                try { firebaseUser.delete().await() } catch (_: Exception) {}
                return AuthResult.Error(
                    "Не удалось сохранить профиль. Проверьте соединение и попробуйте ещё раз."
                )
            }

            warmUpFirestore(firebaseUser.uid)
            AuthResult.Success(
                firebaseUser.toYodoUser(nameOverride = name.trim(), usernameOverride = normalizedUsername)
            )
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage("Не удалось зарегистрироваться"))
        }
    }

    /** Проверка «похоже на email»: содержит ровно одну «@», перед ней ≥1 символ,
     *  после — хотя бы один «.» с символами по обе стороны. */
    private fun isEmailLike(input: String): Boolean {
        val parts = input.split("@")
        if (parts.size != 2) return false
        val local = parts[0]
        val domain = parts[1]
        if (local.isBlank()) return false
        val domainParts = domain.split(".")
        return domainParts.size >= 2 && domainParts.all { it.isNotBlank() }
    }

    /** Ищет email пользователя по username (без учёта регистра и необязательного "@"). */
    private suspend fun resolveEmailByUsername(username: String): String? {
        val normalized = username.trim().removePrefix("@").lowercase()
        return withTimeoutOrNull(8000) {
            val usernameDoc = firestore.collection("usernames").document(normalized).get().await()
            val uid = usernameDoc.getString("uid") ?: return@withTimeoutOrNull null
            val userDoc = firestore.collection("users").document(uid).get().await()
            userDoc.getString("email")
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
            // username для Google-аккаунтов генерируем автоматически из uid, чтобы
            // поиск по username и авторизация через username работали корректно.
            withTimeoutOrNull(8000) {
                val userRef = firestore.collection("users").document(user.uid)
                val snapshot = userRef.get().await()
                if (!snapshot.exists()) {
                    // Генерируем уникальный username на основе uid (первые 16 символов)
                    val autoUsername = "user_${user.uid.take(16).lowercase()}"
                    val usernameRef = firestore.collection("usernames").document(autoUsername)
                    firestore.runTransaction { tx ->
                        if (!tx.get(usernameRef).exists()) {
                            tx.set(usernameRef, mapOf("uid" to user.uid))
                        }
                        tx.set(
                            userRef,
                            mapOf(
                                "uid" to user.uid,
                                "displayName" to (user.displayName ?: ""),
                                "displayNameLowercase" to (user.displayName ?: "").lowercase(),
                                "username" to autoUsername,
                                "usernameLowercase" to autoUsername,
                                "email" to user.email,
                                "avatarUrl" to user.photoUrl?.toString(),
                                "createdAt" to System.currentTimeMillis()
                            )
                        )
                    }.await()
                }
            } ?: android.util.Log.w(
                "AuthRepository",
                "Не удалось синхронизировать профиль Google-входа с Firestore за 8 секунд."
            )

            warmUpFirestore(user.uid)
            AuthResult.Success(user.toYodoUser())
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage("Не удалось войти через Google"))
        }
    }

    /**
     * "Прогревает" grpc-соединение Firestore сразу после успешного входа, ещё до перехода
     * на экран со списком чатов. Без этого первый запрос к Firestore (уже на новом экране)
     * сам устанавливает TLS/grpc-канал и проверяет свежий Auth-токен — это добавляет
     * несколько секунд именно к ПЕРВОЙ загрузке списка чатов после входа. При повторном
     * заходе в приложение канал уже готов, поэтому там всё быстро.
     * Не блокирует и не влияет на результат входа — запрос "выстрелил и забыт",
     * а любая ошибка (например, оффлайн) тихо игнорируется.
     */
    private fun warmUpFirestore(uid: String) {
        firestore.collection("chats")
            .whereArrayContains("participantIds", uid)
            .limit(1)
            .get()
    }

    private fun FirebaseUser.toYodoUser(nameOverride: String? = null, usernameOverride: String? = null) = YodoUser(
        uid = uid,
        displayName = nameOverride ?: displayName.orEmpty(),
        username = usernameOverride,
        email = email,
        phoneNumber = phoneNumber,
        photoUrl = photoUrl?.toString()
    )
}
