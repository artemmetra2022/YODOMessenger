package app.yodo.messenger.domain.repository

import android.app.Activity

/** Колбэки шага отправки SMS-кода — форс-обёртка над callback-based Firebase PhoneAuthProvider API. */
interface PhoneVerificationCallbacks {
    fun onCodeSent(verificationId: String)
    fun onAutoVerified()
    fun onFailed(message: String)
}

interface PhoneAuthRepository {

    fun startPhoneVerification(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneVerificationCallbacks
    )

    fun resendCode(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneVerificationCallbacks
    )

    suspend fun verifyCode(verificationId: String, code: String): AuthResult

    /** Завершить вход, если onVerificationCompleted сработал автоматически (SMS Retriever API). */
    suspend fun completeAutoVerification(): AuthResult
}
