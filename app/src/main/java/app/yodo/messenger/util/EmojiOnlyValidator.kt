package app.yodo.messenger.util

/**
 * НОВОЕ: эмодзи-статус профиля должен содержать только один смайлик — без текста.
 * Отсекает обычные буквы/цифры и оставляет один эмодзи (учитывая суррогатные пары
 * и модификаторы вроде ZWJ/variation selector/skin tone, из которых может состоять
 * один составной эмодзи, например 👨‍👩‍👧 или ❤️).
 */
object EmojiOnlyValidator {

    /** Возвращает true, если символ похож на часть эмодзи (а не на обычный текст). */
    private fun isEmojiPart(codePoint: Int): Boolean {
        return when (codePoint) {
            0x200D -> true // ZWJ — склеивает составные эмодзи
            0xFE0F, 0xFE0E -> true // variation selector
            in 0x1F3FB..0x1F3FF -> true // тон кожи
            in 0x1F600..0x1F64F -> true // Emoticons
            in 0x1F300..0x1F5FF -> true // Misc Symbols and Pictographs
            in 0x1F680..0x1F6FF -> true // Transport and Map
            in 0x1F900..0x1F9FF -> true // Supplemental Symbols and Pictographs
            in 0x1FA70..0x1FAFF -> true // Symbols and Pictographs Extended-A
            in 0x2600..0x26FF -> true // Misc symbols
            in 0x2700..0x27BF -> true // Dingbats
            in 0x2300..0x23FF -> true // Misc Technical (⌚ и т.п.)
            in 0x1F1E6..0x1F1FF -> true // Regional indicators (флаги)
            else -> false
        }
    }

    /**
     * Оставляет только первый эмодзи из введённой строки (включая составные эмодзи
     * из нескольких кодовых точек), отбрасывая любой обычный текст. Если эмодзи
     * не найден, возвращает пустую строку.
     */
    fun sanitize(input: String): String {
        if (input.isEmpty()) return ""
        val result = StringBuilder()
        var index = 0
        var started = false
        while (index < input.length) {
            val codePoint = input.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            if (isEmojiPart(codePoint)) {
                result.appendCodePoint(codePoint)
                started = true
            } else if (started) {
                // Обычный символ после уже собранного эмодзи — статус окончен.
                break
            }
            index += charCount
        }
        return result.toString()
    }
}
