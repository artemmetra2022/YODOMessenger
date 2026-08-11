package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.YodoUser

/**
 * Результат Auth-операций. Явный sealed-класс вместо exceptions,
 * чтобы ViewModel мог показывать понятные пользователю ошибки.
 */
sealed class AuthResult {
    data class Success(val user: YodoUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Результат отправки письма для сброса пароля. Отдельный от [AuthResult],
 * т.к. после отправки письма пользователь ещё не аутентифицирован и
 * объекта [YodoUser] попросту нет.
 */
sealed class ResetPasswordResult {
    data object Success : ResetPasswordResult()
    data class Error(val message: String) : ResetPasswordResult()
}

interface AuthRepository {

    val currentUser: YodoUser?

    /**
     * Вход по email ИЛИ username. [emailOrUsername] определяется автоматически:
     * если строка похожа на email (содержит "@" и домен) — логинимся через email,
     * иначе ищем пользователя по username в Firestore и логинимся по найденному email.
     */
    suspend fun login(emailOrUsername: String, password: String): AuthResult

    /** Username обязателен при регистрации: уникален, 3-20 символов, латиница/цифры/"_". */
    suspend fun register(name: String, username: String, email: String, password: String): AuthResult

    /** Вход/регистрация через Google — idToken получен из Credential Manager (GoogleSignInHelper). */
    suspend fun loginWithGoogle(idToken: String): AuthResult

    /**
     * Отправляет письмо для сброса пароля. [emailOrUsername] определяется автоматически
     * так же, как в [login]: email — напрямую, username — через поиск email в Firestore.
     */
    suspend fun resetPassword(emailOrUsername: String): ResetPasswordResult

    fun logout()

    fun isLoggedIn(): Boolean
}
