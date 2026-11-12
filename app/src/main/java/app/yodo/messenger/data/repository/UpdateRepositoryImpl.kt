package app.yodo.messenger.data.repository

import android.content.Context
import app.yodo.messenger.BuildConfig
import app.yodo.messenger.domain.model.AppUpdate
import app.yodo.messenger.domain.model.UpdateCheckResult
import app.yodo.messenger.domain.repository.UpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
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
    val state: String = "uploaded",
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

    private val downloadMutex = Mutex()

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

            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.state == "uploaded" && it.size > 0 }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val headers = e.response()?.headers()
            val retry = if (e.code() == 403 || e.code() == 429) {
                headers?.get("Retry-After")?.toLongOrNull()?.times(1000L)
                    ?: headers?.get("X-RateLimit-Reset")?.toLongOrNull()?.let { it * 1000L - System.currentTimeMillis() }
                    ?: 60_000L
            } else null
            UpdateCheckResult.Failed("GitHub: HTTP ${e.code()}", retry?.coerceAtLeast(30_000L))
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: "Не удалось проверить обновления")
        }
    }

    override suspend fun downloadApk(
        url: String,
        versionName: String,
        onProgress: (Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            val dir = File(context.cacheDir, APK_DIR).apply { check(isDirectory || mkdirs()) { "Не удалось создать папку обновлений" } }
            val safeVersion = versionName.replace(Regex("[^0-9A-Za-z._-]"), "_")
            val target = File(dir, "yodo-$safeVersion.apk")
            val partial = File(dir, "yodo-$safeVersion.apk.part")
            try {
                val request = Request.Builder().url(url).header("User-Agent", "YodoMessenger-Android").build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Сервер вернул ${response.code}" }
                    val body = response.body ?: error("Пустой ответ сервера")
                    val total = body.contentLength()
                    var downloaded = 0L
                    onProgress(0L, total)
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded, total)
                            }
                        }
                    }
                    check(downloaded > 0L && (total < 0 || downloaded == total)) { "APK скачан не полностью" }
                }
                currentCoroutineContext().ensureActive()
                check(partial.renameTo(target)) { "Не удалось сохранить APK" }
                target
            } finally { partial.delete() }
        }
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
