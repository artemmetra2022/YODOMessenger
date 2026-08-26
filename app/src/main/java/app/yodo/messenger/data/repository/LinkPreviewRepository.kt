package app.yodo.messenger.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?
)

/**
 * Получает превью ссылки (og:title/og:description/og:image) через Cloud Function
 * getLinkPreview — парсинг HTML идёт на сервере, не с клиента (см. functions/index.js),
 * чтобы не светить IP пользователя перед произвольным сайтом. Сервер сам кэширует
 * результат в Firestore на 24 часа; здесь дополнительно держим лёгкий in-memory кэш
 * на время жизни процесса, чтобы не дёргать функцию повторно при каждой перекомпозиции.
 */
@Singleton
class LinkPreviewRepository @Inject constructor(
    private val functions: FirebaseFunctions
) {
    private val memoryCache = ConcurrentHashMap<String, LinkPreview?>()

    suspend fun getPreview(url: String): LinkPreview? {
        memoryCache[url]?.let { return it }
        if (memoryCache.containsKey(url)) return null // ранее уже пытались, превью нет

        return try {
            val result = functions.getHttpsCallable("getLinkPreview")
                .call(mapOf("url" to url))
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?> ?: return null

            val preview = LinkPreview(
                url = data["url"] as? String ?: url,
                title = data["title"] as? String,
                description = data["description"] as? String,
                imageUrl = data["imageUrl"] as? String,
                siteName = data["siteName"] as? String
            )

            // Карточку показываем только если есть хоть заголовок или картинка —
            // "пустое" превью (сайт недоступен/не HTML) не рендерим.
            val result2 = if (preview.title != null || preview.imageUrl != null) preview else null
            memoryCache[url] = result2
            result2
        } catch (e: FirebaseFunctionsException) {
            memoryCache[url] = null
            null
        } catch (e: Exception) {
            memoryCache[url] = null
            null
        }
    }
}
