package app.yodo.messenger.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * НОВОЕ (Y): хранилище сохранённых аккаунтов для быстрой смены аккаунта.
 * Позволяет переключаться между аккаунтами без повторного ручного ввода данных.
 *
 * Примечание: данные хранятся локально на устройстве в SharedPreferences.
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

    fun saveAccount(account: SavedAccount) {
        val current = getAccounts().filterNot { it.email.equals(account.email, ignoreCase = true) }
        val updated = current + account
        persist(updated)
    }

    fun removeAccount(email: String) {
        persist(getAccounts().filterNot { it.email.equals(email, ignoreCase = true) })
    }

    fun getAccounts(): List<SavedAccount> {
        val raw = prefs.getString("accounts", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
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

    private fun persist(accounts: List<SavedAccount>) {
        val arr = JSONArray()
        accounts.forEach { a ->
            arr.put(
                JSONObject()
                    .put("uid", a.uid)
                    .put("email", a.email)
                    .put("password", a.password)
                    .put("displayName", a.displayName)
            )
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }
}
