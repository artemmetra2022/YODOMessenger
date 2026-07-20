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

interface AuthRepository {

    val currentUser: YodoUser?

    suspend fun login(email: String, password: String): AuthResult

    suspend fun register(name: String, email: String, password: String): AuthResult

    /** Вход/регистрация через Google — idToken получен из Credential Manager (GoogleSignInHelper). */
    suspend fun loginWithGoogle(idToken: String): AuthResult

    fun logout()

    fun isLoggedIn(): Boolean
}
