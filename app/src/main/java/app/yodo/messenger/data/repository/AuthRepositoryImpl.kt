package app.yodo.messenger.data.repository

import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.data.local.AccountStore
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.AppSettingsRepository
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.AuthResult
import app.yodo.messenger.domain.repository.ResetPasswordResult
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
    private val firestore: FirebaseFirestore,
    // НОВОЕ (Y): сохраняем аккаунты для быстрой смены аккаунта.
    private val accountStore: AccountStore,
    // НОВОЕ: глобальный переключатель "требовать подтверждение почты при входе"
    // (config/appSettings, меняют только 2 доверенных email — см. AppSettingsRepositoryImpl).
    private val appSettingsRepository: AppSettingsRepository
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

            // НОВОЕ: не пускаем в приложение, пока email не подтверждён — но только
            // если это требование включено (config/appSettings.requireEmailVerification,
            // переключают 2 доверенных email — см. AppSettingsRepositoryImpl). Если
            // выключено, неподтверждённый email проходит без изменений; на уже
            // подтверждённые email флаг никак не влияет. Аккаунт при этом уже
            // аутентифицирован в Firebase — просто на экране входа покажем экран
            // "подтвердите почту" вместо списка чатов.
            user.reload().await()
            if (!user.isEmailVerified && appSettingsRepository.isEmailVerificationRequired()) {
                return AuthResult.RequiresEmailVerification(email)
            }
            // НОВОЕ (email-статус): синхронизируем флаг в Firestore при обычном логине —
            // покрывает случай, когда почта была подтверждена по ссылке, минуя reloadUser().
            // Пишем РЕАЛЬНОЕ значение (user выше только что перезагружен reload()'ом, так
            // что isEmailVerified актуален): раньше сюда безусловно писалось true, и при
            // выключенном требовании верификации пользователь с неподтверждённой почтой
            // получал в профиле ложный статус «email подтверждён».
            try {
                firestore.collection("users").document(user.uid)
                    .update("isEmailVerified", user.isEmailVerified).await()
            } catch (_: Exception) { }

            warmUpFirestore(user.uid)
            // НОВОЕ (Y): запоминаем аккаунт для быстрой смены.
            accountStore.saveAccount(
                AccountStore.SavedAccount(user.uid, email, password, user.displayName.orEmpty())
            )
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

            // НОВОЕ (AE): генерируем случайный публичный ID пользователя (виден в профиле, для админ-банов).
            val publicId = generatePublicId()

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
                            "publicId" to publicId,
                            // НОВОЕ (email-статус): дублируем флаг в Firestore, т.к. Firebase Auth
                            // недоступен напрямую при просмотре ЧУЖОГО профиля.
                            "isEmailVerified" to firebaseUser.isEmailVerified,
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

            // НОВОЕ: отправляем письмо с подтверждением email. Само письмо и ссылку
            // формирует и отправляет Firebase — свой SMTP/сервер не нужен.
            // ИСПРАВЛЕНО: раньше ошибка здесь тихо проглатывалась (только Log.w),
            // и пользователь попадал на экран "подтвердите почту", даже если письмо
            // так и не ушло (например, Firebase вернул ERROR_TOO_MANY_REQUESTS из-за
            // дневного лимита на встроенный email-сервис) — снаружи это выглядело
            // как "письмо не приходит" без всякого объяснения. Теперь передаём
            // результат отправки дальше, чтобы UI мог показать понятную причину.
            var emailSendFailed = false
            var emailSendError: String? = null
            try {
                firebaseUser.sendEmailVerification().await()
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Не удалось отправить письмо подтверждения", e)
                emailSendFailed = true
                emailSendError = e.toUserMessage("Не удалось отправить письмо подтверждения")
            }

            warmUpFirestore(firebaseUser.uid)
            // НОВОЕ (Y): запоминаем аккаунт для быстрой смены.
            accountStore.saveAccount(
                AccountStore.SavedAccount(firebaseUser.uid, email.trim(), password, name.trim())
            )
            // НОВОЕ: сразу после регистрации отправляем на экран "подтвердите почту",
            // а не в чаты — данные пользователя и username уже созданы, только доступ
            // в основное приложение придержан до подтверждения.
            AuthResult.RequiresEmailVerification(
                email = email.trim(),
                emailSendFailed = emailSendFailed,
                emailSendError = emailSendError
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

    override suspend fun resetPassword(emailOrUsername: String): ResetPasswordResult {
        val input = emailOrUsername.trim()
        if (input.isBlank()) return ResetPasswordResult.Error("Введите email или username")

        return try {
            // Та же логика определения email, что и при входе: если строка похожа
            // на email — используем её напрямую, иначе резолвим username → email через Firestore.
            val email = if (isEmailLike(input)) {
                input
            } else {
                resolveEmailByUsername(input)
                    ?: return ResetPasswordResult.Error("Пользователь с таким username не найден")
            }

            firebaseAuth.sendPasswordResetEmail(email).await()
            ResetPasswordResult.Success
        } catch (e: Exception) {
            ResetPasswordResult.Error(e.toUserMessage("Не удалось отправить письмо для сброса пароля"))
        }
    }

    override fun logout() {
        firebaseAuth.signOut()
    }

    override fun isEmailVerified(): Boolean {
        val user = firebaseAuth.currentUser ?: return false
        // Если пользователь пришёл через Google — почта уже подтверждена Google,
        // отдельного подтверждения по ссылке для него не требуем.
        val isGoogleUser = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        return user.isEmailVerified || isGoogleUser
    }

    override suspend fun sendEmailVerification(): AuthResult {
        val user = firebaseAuth.currentUser
            ?: return AuthResult.Error("Вы не авторизованы")
        return try {
            user.sendEmailVerification().await()
            AuthResult.RequiresEmailVerification(user.email.orEmpty())
        } catch (e: Exception) {
            // ИСПРАВЛЕНО: раньше при повторной отправке письмо тоже могло не уйти
            // из-за лимита Firebase (FirebaseTooManyRequestsException), а пользователь
            // видел общий текст "Не удалось отправить письмо повторно" без объяснения
            // причины. Теперь используем toUserMessage(), который для этого случая
            // вернёт "Слишком много попыток. Попробуйте позже".
            AuthResult.Error(e.toUserMessage("Не удалось отправить письмо повторно"))
        }
    }

    override suspend fun reloadUser(): Boolean {
        val user = firebaseAuth.currentUser ?: return false
        return try {
            user.reload().await()
            // НОВОЕ (email-статус): как только Auth подтвердил почту — дублируем флаг
            // в Firestore, чтобы он стал виден в чужих профилях (UserProfileScreen).
            // Пишем только при переходе в true, чтобы не дёргать Firestore на каждый reload.
            if (user.isEmailVerified) {
                try {
                    firestore.collection("users").document(user.uid)
                        .update("isEmailVerified", true).await()
                } catch (_: Exception) {
                    // Не критично: если запись не прошла (например, оффлайн), статус
                    // подтянется при следующем успешном reload/логине.
                }
            }
            user.isEmailVerified
        } catch (e: Exception) {
            false
        }
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
                                // НОВОЕ (email-статус): Google-аккаунты считаем подтверждёнными сразу.
                                "isEmailVerified" to true,
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

    private fun FirebaseUser.toYodoUser(nameOverride: String? = null, usernameOverride: String? = null, publicIdOverride: String? = null) = YodoUser(
        uid = uid,
        displayName = nameOverride ?: displayName.orEmpty(),
        username = usernameOverride,
        email = email,
        phoneNumber = phoneNumber,
        photoUrl = photoUrl?.toString(),
        publicId = publicIdOverride,
        // НОВОЕ (email-статус): берём напрямую из Firebase Auth — здесь это всегда актуальное
        // значение (после user.reload() в login(), либо всегда true для Google-входа).
        isEmailVerified = isEmailVerified
    )

    companion object {
        // НОВОЕ (AE): алфавит без похожих символов (0/O, 1/I) для читаемого ID.
        private const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** Генерирует случайный публичный ID вида YODO-XXXX-XXXX. */
        fun generatePublicId(): String {
            val rnd = java.security.SecureRandom()
            val sb = StringBuilder("YODO-")
            for (i in 0 until 8) {
                if (i == 4) sb.append('-')
                sb.append(ID_ALPHABET[rnd.nextInt(ID_ALPHABET.length)])
            }
            return sb.toString()
        }
    }
}
