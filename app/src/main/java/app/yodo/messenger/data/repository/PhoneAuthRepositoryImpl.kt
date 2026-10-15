package app.yodo.messenger.data.repository

import android.app.Activity
import app.yodo.messenger.domain.repository.AuthResult
import app.yodo.messenger.domain.repository.PhoneAuthRepository
import app.yodo.messenger.domain.repository.PhoneVerificationCallbacks
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneAuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : PhoneAuthRepository {

    // Храним креденшл автоверификации, чтобы завершить вход без ручного ввода кода,
    // если устройство само подтвердило номер (SMS Retriever API).
    private var autoVerifiedCredential: PhoneAuthCredential? = null

    override fun startPhoneVerification(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneVerificationCallbacks
    ) {
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(buildCallbacks(callbacks))
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    override fun resendCode(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneVerificationCallbacks
    ) {
        // Повторная отправка — Firebase сам применит anti-spam лимиты
        startPhoneVerification(phoneNumber, activity, callbacks)
    }

    override suspend fun verifyCode(verificationId: String, code: String): AuthResult {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            signIn(credential)
        } catch (e: Exception) {
            AuthResult.Error(e.mapPhoneErrorToUserMessage())
        }
    }

    private suspend fun signIn(credential: PhoneAuthCredential): AuthResult {
        return try {
            val result = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = result.user ?: return AuthResult.Error("Не удалось получить данные пользователя")

            // Создаём документ пользователя в Firestore, если его ещё нет (новый пользователь по телефону).
            // Таймаут — чтобы недоступная/ещё не созданная Firestore Database не вешала вход навсегда.
            withTimeoutOrNull(8000) {
                val userDoc = firestore.collection("users").document(firebaseUser.uid)
                val snapshot = userDoc.get().await()
                if (!snapshot.exists()) {
                    userDoc.set(
                        mapOf(
                            "uid" to firebaseUser.uid,
                            "displayName" to (firebaseUser.displayName ?: firebaseUser.phoneNumber.orEmpty()),
                            "displayNameLowercase" to (firebaseUser.displayName ?: firebaseUser.phoneNumber.orEmpty()).lowercase(),
                            "phoneNumber" to firebaseUser.phoneNumber,
                            "createdAt" to System.currentTimeMillis()
                        )
                    ).await()
                }
            } ?: android.util.Log.w(
                "PhoneAuthRepository",
                "Не удалось синхронизировать профиль с Firestore за 8 секунд. " +
                    "Проверь, что Firestore Database создана в консоли Firebase."
            )

            AuthResult.Success(
                app.yodo.messenger.domain.model.YodoUser(
                    uid = firebaseUser.uid,
                    displayName = firebaseUser.displayName.orEmpty(),
                    email = firebaseUser.email,
                    phoneNumber = firebaseUser.phoneNumber,
                    photoUrl = firebaseUser.photoUrl?.toString()
                )
            )
        } catch (e: Exception) {
            AuthResult.Error(e.mapPhoneErrorToUserMessage())
        }
    }

    private fun buildCallbacks(
        callbacks: PhoneVerificationCallbacks
    ) = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Автоматическая верификация без ввода кода (SMS Retriever API)
            autoVerifiedCredential = credential
            callbacks.onAutoVerified()
        }

        override fun onVerificationFailed(e: FirebaseException) {
            callbacks.onFailed(e.mapPhoneErrorToUserMessage())
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            callbacks.onCodeSent(verificationId)
        }
    }

    /** Завершить вход, если onVerificationCompleted сработал автоматически. */
    override suspend fun completeAutoVerification(): AuthResult {
        val credential = autoVerifiedCredential
            ?: return AuthResult.Error("Автоматическая верификация недоступна, введите код вручную")
        return signIn(credential)
    }

    private fun Throwable.mapPhoneErrorToUserMessage(): String = when {
        message?.contains("invalid", ignoreCase = true) == true &&
            message?.contains("code", ignoreCase = true) == true -> "Неверный код подтверждения"
        message?.contains("TOO_MANY_REQUESTS", ignoreCase = true) == true -> "Слишком много попыток, попробуйте позже"
        message?.contains("invalid phone", ignoreCase = true) == true -> "Некорректный номер телефона"
        message?.contains("network", ignoreCase = true) == true -> "Проблема с подключением к сети"
        else -> message ?: "Ошибка верификации номера телефона"
    }
}
