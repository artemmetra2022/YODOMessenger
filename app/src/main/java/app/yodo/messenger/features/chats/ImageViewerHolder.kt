package app.yodo.messenger.features.chats

/**
 * Singleton для передачи изображения в ImageViewerScreen
 * (избегаем передачи base64 через NavGraph — слишком длинная строка)
 */
object ImageViewerHolder {
    var imageBase64: String? = null
    var senderName: String? = null
    var timestamp: Long = 0L

    fun clear() {
        imageBase64 = null
        senderName = null
        timestamp = 0L
    }
}
