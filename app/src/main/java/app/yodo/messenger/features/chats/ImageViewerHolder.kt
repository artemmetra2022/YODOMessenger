package app.yodo.messenger.features.chats

/**
 * Singleton для передачи изображения в ImageViewerScreen
 * (избегаем передачи base64 через NavGraph — слишком длинная строка)
 */
object ImageViewerHolder {
    var imageBase64: String? = null
    var senderName: String? = null
    var timestamp: Long = 0L

    // НОВОЕ (V): альбом из нескольких фото — можно листать свайпом.
    var images: List<String> = emptyList()
    var initialIndex: Int = 0

    /** Список для просмотра: либо альбом, либо одиночное фото. */
    fun effectiveImages(): List<String> =
        if (images.isNotEmpty()) images else listOfNotNull(imageBase64)

    fun clear() {
        imageBase64 = null
        senderName = null
        timestamp = 0L
        images = emptyList()
        initialIndex = 0
    }
}
