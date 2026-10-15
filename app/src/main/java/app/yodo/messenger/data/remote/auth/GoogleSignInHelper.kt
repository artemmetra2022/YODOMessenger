package app.yodo.messenger.data.remote.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed class GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult()
    data class Error(val message: String) : GoogleSignInResult()
    data object Cancelled : GoogleSignInResult()
}

/**
 * Обёртка над Credential Manager — актуальным (не deprecated) способом входа через Google.
 * webClientId берётся из ресурса R.string.default_web_client_id, который google-services
 * plugin генерирует автоматически из google-services.json — но ТОЛЬКО если в консоли Firebase
 * включён провайдер Google (иначе в json нет нужного oauth_client и ресурс не появится).
 */
class GoogleSignInHelper(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(webClientId: String): GoogleSignInResult {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = credentialManager.getCredential(context, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            GoogleSignInResult.Success(googleIdTokenCredential.idToken)
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Error(e.message ?: "Не удалось войти через Google")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Error("Ошибка обработки данных Google-аккаунта")
        }
    }
}
