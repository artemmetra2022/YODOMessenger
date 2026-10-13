package app.yodo.messenger.data.repository

import android.content.Context
import app.yodo.messenger.BuildConfig
import app.yodo.messenger.domain.model.AppUpdate
import app.yodo.messenger.domain.model.UpdateCheckResult
import app.yodo.messenger.domain.repository.UpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L
)

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GithubAsset> = emptyList()
)

private interface GithubApi {
    // GitHub требует User-Agent (его задаёт interceptor ниже) и валидный Accept.
    @GET("releases/latest")
    suspend fun latestRelease(): GithubRelease
}

@Singleton
class UpdateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UpdateRepository {

    companion object {
        private const val PREFS_NAME = "yodo_update_prefs"
        private const val KEY_LAST_CHECK = "last_check_at"
        private const val AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val APK_DIR = "updates"
    }

    override val currentVersionName: String = BuildConfig.VERSION_NAME

    override val currentVersionCode: Int = BuildConfig.VERSION_CODE

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "YodoMessenger-Android")
                        .build()
                )
            }
            .build()
    }

    private val api: GithubApi by lazy {
        Retrofit.Builder()
            // Репозиторий задаётся BuildConfig-полем (см. app/build.gradle.kts),
            // чтобы адрес релизов не хардкодить в нескольких местах.
            .baseUrl("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GithubApi::class.java)
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun checkForUpdate(force: Boolean): UpdateCheckResult {
        if (!force) {
            val last = prefs().getLong(KEY_LAST_CHECK, 0L)
            if (last > 0L && System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) {
                return UpdateCheckResult.UpToDate
            }
        }

        return try {
            val release = api.latestRelease()
            prefs().edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            val parsed = parseVersion(release.tagName)
            // Не смогли разобрать тег — сравнивать не с чем, не предлагаем обновление.
            if (parsed == null || parsed.second <= currentVersionCode) {
                return UpdateCheckResult.UpToDate
            }

            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            UpdateCheckResult.Available(
                AppUpdate(
                    versionName = parsed.first,
                    versionCode = parsed.second,
                    releaseNotes = release.body.trim(),
                    apkUrl = apk?.browserDownloadUrl?.takeIf { it.isNotBlank() },
                    releasePageUrl = release.htmlUrl.ifBlank {
                        "https://github.com/${BuildConfig.GITHUB_REPO}/releases"
                    }
                )
            )
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: "Не удалось проверить обновления")
        }
    }

    override suspend fun downloadApk(
        url: String,
        versionName: String,
        onProgress: (Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, APK_DIR).apply {
            if (exists()) listFiles()?.forEach { it.delete() } else mkdirs()
        }
        val safeVersion = versionName.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val target = File(dir, "yodo-$safeVersion.apk")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YodoMessenger-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Сервер вернул ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Пустой ответ сервера")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                        read = input.read(buffer)
                    }
                }
            }
        }
        target
    }

    /**
     * Разбирает тег релиза вида «v1.2.3» в пару (versionName, versionCode).
     * Формула versionCode совпадает с CI (.github/workflows/build-apk.yml), иначе
     * сравнение версий клиента и релиза разошлось бы.
     */
    private fun parseVersion(tag: String): Pair<String, Int>? {
        val match = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)$").find(tag.trim()) ?: return null
        val (major, minor, patch) = match.destructured
        val majorInt = major.toIntOrNull() ?: return null
        val minorInt = minor.toIntOrNull() ?: return null
        val patchInt = patch.toIntOrNull() ?: return null
        return "$majorInt.$minorInt.$patchInt" to (majorInt * 10000 + minorInt * 100 + patchInt)
    }
}
