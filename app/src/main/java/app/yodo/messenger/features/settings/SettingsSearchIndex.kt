package app.yodo.messenger.features.settings

/**
 * НОВОЕ (поиск по настройкам): статический индекс всех пунктов экрана настроек.
 *
 * Каждый пункт содержит заголовок, краткое описание и набор синонимов/ключевых
 * слов — это позволяет находить настройку не только по точному названию,
 * но и по смыслу (например "звук" находит "Уведомления", а "код" находит "PIN").
 *
 * [anchorId] — стабильный идентификатор секции на экране настроек, используется
 * для прокрутки/подсветки найденного пункта после перехода из результатов поиска.
 */
data class SettingsSearchItem(
    val id: String,
    val anchorId: String,
    val title: String,
    val subtitle: String,
    val keywords: List<String> = emptyList(),
    val sectionTitle: String
)

object SettingsSearchIndex {

    // Якоря секций — должны совпадать с anchorId, которые расставлены в SettingsScreen.
    const val ANCHOR_APPEARANCE = "appearance"
    const val ANCHOR_CUSTOMIZATION = "customization"
    const val ANCHOR_LANGUAGE = "language"
    const val ANCHOR_CHATS = "chats"
    const val ANCHOR_CHAT_BACKGROUND = "chat_background"
    const val ANCHOR_CHAT_FOLDERS = "chat_folders"
    const val ANCHOR_PRIVACY = "privacy"
    const val ANCHOR_AUTO_DELETE = "auto_delete"
    const val ANCHOR_PIN = "pin"
    const val ANCHOR_DECOY_PIN = "decoy_pin"
    const val ANCHOR_BLOCKED_USERS = "blocked_users"
    const val ANCHOR_PROFILE_VISIBILITY = "profile_visibility"
    const val ANCHOR_NOTIFICATIONS = "notifications"
    const val ANCHOR_QUIET_HOURS = "quiet_hours"
    const val ANCHOR_NOTIFICATION_SNOOZE = "notification_snooze"
    const val ANCHOR_ACCOUNT = "account"
    const val ANCHOR_TOOLS = "tools"
    const val ANCHOR_SECURITY_CENTER = "security_center"
    const val ANCHOR_ONBOARDING = "onboarding"
    const val ANCHOR_SWITCH_ACCOUNT = "switch_account"
    const val ANCHOR_LOGOUT = "logout"
    const val ANCHOR_DELETE_ACCOUNT = "delete_account"
    const val ANCHOR_SEARCH_IN_GLOBAL = "search_in_global"

    private const val SEC_APPEARANCE = "Оформление"
    private const val SEC_CUSTOMIZATION = "Кастомизация"
    private const val SEC_LANGUAGE = "Язык"
    private const val SEC_CHATS = "Чаты"
    private const val SEC_PRIVACY = "Конфиденциальность"
    private const val SEC_NOTIFICATIONS = "Уведомления"
    private const val SEC_ACCOUNT = "Аккаунт"
    private const val SEC_SEARCH = "Поиск"

    val items: List<SettingsSearchItem> = listOf(
        SettingsSearchItem(
            id = "dark_theme", anchorId = ANCHOR_APPEARANCE,
            title = "Тёмная тема", subtitle = "Переключить светлое/тёмное оформление",
            keywords = listOf("темная", "тёмная", "светлая", "ночная", "тема", "night", "dark", "оформление", "цвет фона"),
            sectionTitle = SEC_APPEARANCE
        ),
        SettingsSearchItem(
            id = "color_theme", anchorId = ANCHOR_CUSTOMIZATION,
            title = "Цветовая тема", subtitle = "Выбрать акцентный цвет приложения",
            keywords = listOf("цвет", "палитра", "акцент", "раскраска", "стиль", "color"),
            sectionTitle = SEC_CUSTOMIZATION
        ),
        SettingsSearchItem(
            id = "font_size", anchorId = ANCHOR_CUSTOMIZATION,
            title = "Размер шрифта", subtitle = "Настроить размер текста в приложении",
            keywords = listOf("шрифт", "текст", "размер текста", "буквы", "крупнее", "мельче", "font"),
            sectionTitle = SEC_CUSTOMIZATION
        ),
        SettingsSearchItem(
            id = "language", anchorId = ANCHOR_LANGUAGE,
            title = "Язык приложения", subtitle = "Сменить язык интерфейса",
            keywords = listOf("язык", "локализация", "русский", "английский", "language"),
            sectionTitle = SEC_LANGUAGE
        ),
        SettingsSearchItem(
            id = "send_on_enter", anchorId = ANCHOR_CHATS,
            title = "Отправка по Enter", subtitle = "Отправлять сообщение при нажатии Enter",
            keywords = listOf("энтер", "клавиша", "отправка", "ввод"),
            sectionTitle = SEC_CHATS
        ),
        SettingsSearchItem(
            id = "hide_keyboard", anchorId = ANCHOR_CHATS,
            title = "Скрывать клавиатуру", subtitle = "Скрывать клавиатуру после отправки сообщения",
            keywords = listOf("клавиатура", "скрыть", "закрыть клавиатуру"),
            sectionTitle = SEC_CHATS
        ),
        SettingsSearchItem(
            id = "auto_download", anchorId = ANCHOR_CHATS,
            title = "Автозагрузка изображений", subtitle = "Автоматически скачивать изображения в чатах",
            keywords = listOf("автозагрузка", "картинки", "фото", "загрузка", "трафик", "интернет"),
            sectionTitle = SEC_CHATS
        ),
        SettingsSearchItem(
            id = "advanced_polls", anchorId = ANCHOR_CHATS,
            title = "Расширенные опросы", subtitle = "Опросы с несколькими вариантами и викторины",
            keywords = listOf("опрос", "викторина", "голосование", "poll"),
            sectionTitle = SEC_CHATS
        ),
        SettingsSearchItem(
            id = "chat_background", anchorId = ANCHOR_CHAT_BACKGROUND,
            title = "Фон чата", subtitle = "Изменить фоновое изображение чатов",
            keywords = listOf("фон", "обои", "background", "картинка чата", "заднийфон"),
            sectionTitle = SEC_CHATS
        ),
        SettingsSearchItem(
            id = "chat_folders", anchorId = ANCHOR_CHAT_FOLDERS,
            title = "Папки чатов", subtitle = "Организовать чаты по папкам",
            keywords = listOf("папка", "категории", "организация чатов", "folder"),
            sectionTitle = SEC_CHATS
        ),
        SettingsSearchItem(
            id = "online_status", anchorId = ANCHOR_PRIVACY,
            title = "Статус «в сети»", subtitle = "Показывать другим, когда вы онлайн",
            keywords = listOf("онлайн", "в сети", "статус", "видимость", "был в сети"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "read_receipts", anchorId = ANCHOR_PRIVACY,
            title = "Отметки о прочтении", subtitle = "Показывать другим, что сообщение прочитано",
            keywords = listOf("прочитано", "галочки", "read receipts", "прочтение"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "auto_delete_account", anchorId = ANCHOR_AUTO_DELETE,
            title = "Автоудаление аккаунта", subtitle = "Удалить аккаунт после периода неактивности",
            keywords = listOf("автоудаление", "неактивность", "самоуничтожение", "удаление по таймеру"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "pin_code", anchorId = ANCHOR_PIN,
            title = "PIN-код", subtitle = "Защитить приложение кодом-паролем",
            keywords = listOf("пин", "код", "пароль", "блокировка приложения", "защита", "pin", "код доступа"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "decoy_pin", anchorId = ANCHOR_DECOY_PIN,
            title = "Ложный PIN", subtitle = "Скрыть выбранные чаты за отдельным кодом",
            keywords = listOf("ложный код", "скрытые чаты", "фейковый пин", "второй пин", "decoy"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "blocked_users", anchorId = ANCHOR_BLOCKED_USERS,
            title = "Заблокированные пользователи", subtitle = "Управлять списком блокировок",
            keywords = listOf("блокировка", "чёрный список", "заблокирован", "бан", "block"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "profile_about_me", anchorId = ANCHOR_PROFILE_VISIBILITY,
            title = "Видимость «О себе»", subtitle = "Показывать раздел «О себе» в профиле",
            keywords = listOf("о себе", "биография", "описание профиля"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "profile_birth_date", anchorId = ANCHOR_PROFILE_VISIBILITY,
            title = "Видимость даты рождения", subtitle = "Показывать дату рождения в профиле",
            keywords = listOf("день рождения", "дата рождения", "возраст"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "profile_location", anchorId = ANCHOR_PROFILE_VISIBILITY,
            title = "Видимость геолокации", subtitle = "Показывать местоположение в профиле",
            keywords = listOf("местоположение", "город", "гео", "локация"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "profile_website", anchorId = ANCHOR_PROFILE_VISIBILITY,
            title = "Видимость сайта", subtitle = "Показывать ссылку на сайт в профиле",
            keywords = listOf("сайт", "ссылка", "website"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "profile_phone", anchorId = ANCHOR_PROFILE_VISIBILITY,
            title = "Видимость номера телефона", subtitle = "Показывать номер телефона в профиле",
            keywords = listOf("телефон", "номер", "phone"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "profile_email", anchorId = ANCHOR_PROFILE_VISIBILITY,
            title = "Видимость почты", subtitle = "Показывать email в профиле",
            keywords = listOf("почта", "email", "электронная почта"),
            sectionTitle = SEC_PRIVACY
        ),
        SettingsSearchItem(
            id = "mute_all", anchorId = ANCHOR_NOTIFICATIONS,
            title = "Отключить все уведомления", subtitle = "Полностью выключить уведомления",
            keywords = listOf("выключить уведомления", "без звука", "тишина", "mute"),
            sectionTitle = SEC_NOTIFICATIONS
        ),
        SettingsSearchItem(
            id = "notification_sound", anchorId = ANCHOR_NOTIFICATIONS,
            title = "Звук уведомлений", subtitle = "Включить/выключить звук уведомлений",
            keywords = listOf("звук", "звонок", "мелодия", "sound"),
            sectionTitle = SEC_NOTIFICATIONS
        ),
        SettingsSearchItem(
            id = "notification_vibration", anchorId = ANCHOR_NOTIFICATIONS,
            title = "Вибрация", subtitle = "Включить/выключить вибрацию при уведомлениях",
            keywords = listOf("вибро", "вибрация", "vibration", "тряска"),
            sectionTitle = SEC_NOTIFICATIONS
        ),
        SettingsSearchItem(
            id = "quiet_hours", anchorId = ANCHOR_QUIET_HOURS,
            title = "Тихие часы", subtitle = "Не беспокоить в заданный ночной интервал",
            keywords = listOf("не беспокоить", "ночь", "расписание уведомлений", "quiet hours"),
            sectionTitle = SEC_NOTIFICATIONS
        ),
        SettingsSearchItem(
            id = "hide_notification_preview", anchorId = ANCHOR_NOTIFICATIONS,
            title = "Скрывать текст в уведомлениях", subtitle = "Показывать «Новое сообщение» без текста",
            keywords = listOf("превью", "скрыть текст", "конфиденциальность уведомлений", "preview"),
            sectionTitle = SEC_NOTIFICATIONS
        ),
        SettingsSearchItem(
            id = "notification_snooze", anchorId = ANCHOR_NOTIFICATION_SNOOZE,
            title = "Пауза уведомлений", subtitle = "Временно отключить уведомления на 1 или 8 часов",
            keywords = listOf("пауза", "снуз", "отложить уведомления", "snooze"),
            sectionTitle = SEC_NOTIFICATIONS
        ),
        SettingsSearchItem(
            id = "test_notification", anchorId = ANCHOR_NOTIFICATIONS,
            title = "Тестовое уведомление", subtitle = "Проверить, что уведомления работают",
            keywords = listOf("тест", "проверка уведомлений", "test"),
            sectionTitle = SEC_NOTIFICATIONS
        ),
        SettingsSearchItem(
            id = "tools", anchorId = ANCHOR_TOOLS,
            title = "Фишки и инструменты", subtitle = "Заметки, таймер, пароли, конвертеры и другое",
            keywords = listOf("инструменты", "функции", "утилиты", "мини-приложения", "tools"),
            sectionTitle = SEC_ACCOUNT
        ),
        SettingsSearchItem(
            id = "security_center", anchorId = ANCHOR_SECURITY_CENTER,
            title = "Центр безопасности", subtitle = "2FA, контрольные вопросы, защита от скриншотов",
            keywords = listOf("безопасность", "двухфакторная", "2fa", "скриншот", "security"),
            sectionTitle = SEC_ACCOUNT
        ),
        SettingsSearchItem(
            id = "onboarding", anchorId = ANCHOR_ONBOARDING,
            title = "Пройти обучение", subtitle = "Короткий тур по возможностям приложения",
            keywords = listOf("обучение", "тур", "гайд", "как пользоваться", "туториал"),
            sectionTitle = SEC_ACCOUNT
        ),
        SettingsSearchItem(
            id = "switch_account", anchorId = ANCHOR_SWITCH_ACCOUNT,
            title = "Сменить аккаунт", subtitle = "Переключиться между аккаунтами или добавить новый",
            keywords = listOf("сменить аккаунт", "другой аккаунт", "добавить аккаунт", "switch"),
            sectionTitle = SEC_ACCOUNT
        ),
        SettingsSearchItem(
            id = "logout", anchorId = ANCHOR_SWITCH_ACCOUNT,
            title = "Выйти из аккаунта", subtitle = "Выход из текущего аккаунта",
            keywords = listOf("выход", "разлогиниться", "logout", "sign out"),
            sectionTitle = SEC_ACCOUNT
        ),
        SettingsSearchItem(
            id = "delete_account", anchorId = ANCHOR_SWITCH_ACCOUNT,
            title = "Удалить аккаунт", subtitle = "Безвозвратное удаление аккаунта",
            keywords = listOf("удаление", "удалить профиль", "снести аккаунт", "delete"),
            sectionTitle = SEC_ACCOUNT
        ),
        SettingsSearchItem(
            id = "search_in_global", anchorId = ANCHOR_SEARCH_IN_GLOBAL,
            title = "Показывать настройки в общем поиске", subtitle = "Включить или выключить показ настроек на главном экране поиска",
            keywords = listOf("общий поиск", "глобальный поиск", "поиск настроек", "показывать настройки"),
            sectionTitle = SEC_SEARCH
        )
    )
}

/**
 * Нечёткий поиск по индексу настроек: учитывает опечатки (расстояние Левенштейна)
 * и совпадения по смыслу (ключевые слова/синонимы), а не только точную подстроку.
 */
object SettingsSearchMatcher {

    /** Возвращает найденные пункты, отсортированные по релевантности (лучшие — первыми). */
    fun search(query: String, items: List<SettingsSearchItem> = SettingsSearchIndex.items): List<SettingsSearchItem> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }

        return items
            .mapNotNull { item -> scoreItem(item, normalizedQuery, queryTokens)?.let { score -> item to score } }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun scoreItem(item: SettingsSearchItem, normalizedQuery: String, queryTokens: List<String>): Int? {
        val haystacks = buildList {
            add(normalize(item.title))
            add(normalize(item.subtitle))
            add(normalize(item.sectionTitle))
            addAll(item.keywords.map { normalize(it) })
        }

        var bestScore = 0
        var matched = false

        // Точное вхождение всей фразы — наивысший приоритет.
        haystacks.forEach { haystack ->
            if (haystack.contains(normalizedQuery)) {
                matched = true
                bestScore = maxOf(bestScore, if (haystack == normalizedQuery) 100 else 80)
            }
        }

        // Совпадение по отдельным словам запроса (в т.ч. с опечатками).
        for (token in queryTokens) {
            if (token.length < 2) continue
            var tokenMatched = false
            for (haystack in haystacks) {
                for (word in haystack.split(" ")) {
                    if (word.isBlank()) continue
                    if (word.contains(token) || token.contains(word)) {
                        tokenMatched = true
                        bestScore = maxOf(bestScore, 60)
                    } else {
                        val maxAllowedDistance = when {
                            token.length <= 3 -> 1
                            token.length <= 6 -> 2
                            else -> 3
                        }
                        val distance = levenshtein(token, word)
                        if (distance <= maxAllowedDistance) {
                            tokenMatched = true
                            bestScore = maxOf(bestScore, 40 - distance * 5)
                        }
                    }
                }
            }
            if (tokenMatched) matched = true
        }

        return if (matched) bestScore else null
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace('ё', 'е')
            .trim()
            .replace(Regex("[^a-zа-я0-9 ]"), " ")
            .replace(Regex(" +"), " ")
            .trim()

    /** Классическое расстояние Левенштейна — допускает опечатки при поиске. */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,      // вставка
                    prev[j] + 1,           // удаление
                    prev[j - 1] + cost     // замена
                )
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length]
    }
}
