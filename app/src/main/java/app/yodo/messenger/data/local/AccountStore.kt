package app.yodo.messenger.data.local

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * НОВОЕ (Y): хранилище сохранённых аккаунтов для быстрой смены аккаунта.
 * Позволяет переключаться между аккаунтами без повторного ручного ввода данных.
 *
 * ИСПРАВЛЕНО (безопасность): пароли больше не хранятся в SharedPreferences
 * открытым текстом (раньше root/ADB-доступ свободно читал их из yodo_accounts).
 * Теперь весь JSON с аккаунтами шифруется AEAD AES-256-GCM (Google Tink), а ключ
 * keyset-а защищён мастер-ключом из Android Keystore и не покидает устройство —
 * тот же механизм, что в CryptoManager для E2EE-ключей. Старые незашифрованные
 * записи мигрируются (затираются) при первом же сохранении.
 */
@Singleton
class AccountStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class SavedAccount(
        val uid: String,
        val email: String,
        val password: String,
        val displayName: String
    )

    private val prefs = context.getSharedPreferences("yodo_accounts", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AccountStore"
        // Легаси-ключ с незашифрованным JSON (мигрируется и затирается при записи).
        private const val KEY_ACCOUNTS = "accounts"
        // base64(AEAD-шифртекст JSON) — актуальный формат хранения.
        private const val KEY_ACCOUNTS_ENC = "accounts_enc"
        private const val KEYSET_PREF_FILE = "yodo_account_store_keyset_prefs"
        private const val KEYSET_NAME = "yodo_account_store_keyset"
        private const val MASTER_KEY_URI = "android-keystore://yodo_account_store_master_key"
    }

    private val aead: Aead? by lazy {
        try {
            AeadConfig.register()
            AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_FILE)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "AEAD init failed; saved accounts can't be persisted", e)
            null
        }
    }

    fun saveAccount(account: SavedAccount) {
        val current = getAccounts().filterNot { it.email.equals(account.email, ignoreCase = true) }
        persist(current + account)
    }

    fun removeAccount(email: String) {
        persist(getAccounts().filterNot { it.email.equals(email, ignoreCase = true) })
    }

    fun getAccounts(): List<SavedAccount> {
        val encrypted = prefs.getString(KEY_ACCOUNTS_ENC, null)
        if (encrypted != null) {
            val json = decrypt(encrypted) ?: return emptyList()
            return parse(json)
        }
        // Легаси-данные, записанные до шифрования: читаем как есть один раз,
        // при первом же save/remove они будут перезаписаны в accounts_enc.
        val legacy = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return parse(legacy)
    }

    private fun parse(json: String): List<SavedAccount> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedAccount(
                    uid = o.optString("uid"),
                    email = o.optString("email"),
                    password = o.optString("password"),
                    displayName = o.optString("displayName")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decrypt(value: String): String? {
        val a = aead ?: return null
        return try {
            String(a.decrypt(Base64.decode(value, Base64.NO_WRAP), ByteArray(0)), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt saved accounts", e)
            null
        }
    }

    private fun persist(accounts: List<SavedAccount>) {
        val a = aead
        if (a == null) {
            // Keystore недоступен — отказываемся сохранять, но не пишем пароли
            // открытым текстом (список аккаунтов просто не сохранится).
            Log.e(TAG, "AEAD unavailable; refusing to persist accounts in plaintext")
            return
        }
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(
                JSONObject()
                    .put("uid", acc.uid)
                    .put("email", acc.email)
                    .put("password", acc.password)
                    .put("displayName", acc.displayName)
            )
        }
        try {
            val ciphertext = Base64.encodeToString(
                a.encrypt(arr.toString().toByteArray(Charsets.UTF_8), ByteArray(0)),
                Base64.NO_WRAP
            )
            prefs.edit()
                .putString(KEY_ACCOUNTS_ENC, ciphertext)
                .remove(KEY_ACCOUNTS)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt saved accounts", e)
        }
    }
}
