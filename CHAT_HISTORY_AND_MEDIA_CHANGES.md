# Пагинация истории, «Отметить как непрочитанное», «Медиа, файлы, ссылки»

Дата: 21 октября 2026.
Статус: код собран и выпущен в **1.22.2** (сборка GitHub Actions, run 39652755381,
APK прикреплён к релизу [v1.22.2](https://github.com/artemmetra2022/YODOMessenger/releases/tag/v1.22.2)).
Первая сборка падала на `ChatMediaTab` (internal-тип в публичном API ViewModel) —
исправлено коммитом `7cab6f9`. JVM-тесты в CI не запускались: workflow собирает
только `assembleRelease`, поэтому `ChatHistoryPagingTest` и остальной набор тестов
нужно прогнать отдельно.

---

## 1. Пагинация истории чата

### Что было
`MessageRepository.observeMessages(chatId, topicId)` подписывался на всю коллекцию
`chats/{chatId}/messages`. При каждом изменении Firestore присылал весь список: на
длинной переписке это лишние чтения, память и время на маппинг/расшифровку.

### Что стало
- `MessageRepository.observeMessages(chatId, topicId, limit: Int? = null)`.
  `limit == null` — прежнее поведение без ограничения (используется в статистике канала).
- `MessageRepository.getMessagesPage(chatId, topicId, beforeTimestamp, limit)` — одна
  страница вверх: `orderBy(timestamp, DESC)` + `whereLessThan(timestamp, beforeTimestamp)`,
  возвращает сообщения по возрастанию. Истёкшие исчезающие сообщения отфильтрованы.
- `ChatViewModel` подписывается на последние `CHAT_HISTORY_PAGE_SIZE = 50` сообщений и
  хранит уже подгруженные страницы в `olderHistory`. Итоговый список — `mergeHistoryPages`
  (склейка без дублей, живое окно важнее старой копии: в нём актуальные статусы и правки).
- `ChatUiState`: `hasMoreHistory`, `isLoadingOlder`, `historyPage`.
- `loadOlderMessages()` — подгрузка следующей страницы по прокрутке вверх.
- `loadHistoryUntil(messageId)` — догрузка истории до конкретного сообщения (переход по
  уведомлению/ссылке), не больше 10 страниц за вызов и 3 попыток из UI.

### Сохранение позиции списка
`ChatScreen` запоминает ключ первого видимого элемента LazyColumn (`HistoryScrollAnchor`)
до загрузки и после вставки старых сообщений делает `scrollToItem` к тому же ключу с тем
же смещением. Ключи повторяют схему списка: `date_<id>` для разделителя даты и `<id>`
для сообщения (`lazyItemIndexForMessage`).

### Скользящее окно
Когда приходит новое сообщение, из окна выпадает самое старое. Чтобы уже просмотренная
история не пропадала, выпавшие сообщения переносятся в `olderHistory`
(`ChatViewModel.observeMessages`). Удаление сообщений в проекте — soft-delete
(`isDeleted = true`, документ остаётся), поэтому «выпасть» по этой причине не может.

### Firestore-индекс
Для оконной выборки в темах форума (`whereEqualTo(topicId)` + `orderBy(timestamp, DESC)`)
добавлен индекс в `firestore.indexes.json`:

```
messages: topicId ASC, timestamp DESC
```

**Индекс нужно задеплоить до релиза**, иначе запросы истории в форумных темах будут
падать с ошибкой «The query requires an index». Обычные чаты (без `topicId`) работают
на встроенном индексе по `timestamp` и деплоя не требуют.

### Ограничения
- Пагинация по `timestamp` строгим `<`. Если много сообщений имеют одинаковый timestamp
  ровно на границе страницы, теоретически возможен пропуск — на практике timestamp
  в миллисекундах уникален.
- `hasMoreHistory` вычисляется по размеру окна (`window.size >= 50`). Если в последних
  50 документах есть истёкшие исчезающие сообщения, они отфильтровываются и окно
  оказывается короче — подгрузка вверх в таком чате может отключиться раньше времени.
- Ошибка сети при подгрузке не считается концом истории: в чате она показывается
  сообщением об ошибке, следующая прокрутка вверх повторяет запрос; в разделе медиа
  показывается «Повторить».
- Поиск по сообщениям теперь ищет только по загруженной истории (окно + подгруженные
  страницы), а не по всей переписке.

---

## 2. «Отметить как непрочитанное»

Серверный счётчик непрочитанных (`unreadCounts.{uid}`) умеет только уменьшаться при
чтении — «вернуть» его нельзя. Поэтому пометка локальная, как в Telegram.

- `UserSettingsPreferences`: ключ `manual_unread_chat_ids`, поток `manualUnreadChatIds`,
  метод `setChatManuallyUnread(chatId, unread)`.
- `ChatPreview.isManuallyUnread` — новый флаг.
- `ChatListViewModel.markChatUnread(chatId)`; флаг применяется в `observeChats`
  (поток `manualUnreadChatIds` добавлен в `combine` — объявлен **до** `init{}`, чтобы не
  повторить историю с NPE из-за порядка инициализации).
- UI: пункт «Отметить как непрочитанное» в меню строки чата (`SwipeableChatListItem`),
  точка-бейдж 8 dp вместо числа (если серверных непрочитанных нет), участие в фильтре
  «Непрочитанные», в счётчике таба и в сортировке «сначала непрочитанные».
- Пометка снимается при открытии чата: `ChatViewModel.init` проверяет набор и, если чат
  в нём, вызывает `setChatManuallyUnread(chatId, false)`.
- Пустой личный чат, помеченный вручную, не скрывается фильтром «чаты без сообщений».

---

## 3. «Медиа, файлы, ссылки»

Новый экран `ChatMediaScreen` и `ChatMediaViewModel`, маршрут
`chat_media/{chatId}?topicId={topicId}` (`Routes.ChatMedia`), пункт меню чата
«Медиа, файлы, ссылки» (`ChatMenuId.MEDIA`, иконка `PermMedia`).

### Как устроено
Схема сообщений не менялась — денормализованных флагов «это фото/файл/ссылка» у старых
сообщений нет. Поэтому раздел читает историю страницами через `getMessagesPage` и
раскладывает сообщения по вкладкам на клиенте:

| Вкладка | Что попадает |
|---|---|
| Медиа | `imageBase64`, `imagesBase64` (альбомы), `voiceBase64`; открытые одноразовые фото исключены |
| Файлы | `fileBase64` / `fileName` |
| Ссылки | первая http(s)-ссылка в тексте (`extractFirstUrl`) |

- У каждой вкладки свой курсор, переключение вкладок не сканирует историю заново.
- За один запрос сканируется до 4 страниц по 60 документов, пока не наберётся 24 вложения
  (`MAX_SCAN_PAGES`, `SCAN_PAGE_SIZE`, `ITEMS_PER_PAGE`).
- Дальше — по прокрутке. Если вложений нет, но история не кончилась, показывается кнопка
  «Показать ещё» (чтобы не читать весь чат автоматически).
- Медиа — сетка 3 колонки с превью (`ImageUtils.decodeChatImageBitmapCached`), тап
  открывает существующий `ImageViewerScreen`; счётчик фото на плитке альбома.
- Файл открывается через `FileUtils.base64ToTempFile` + `FileProvider` (как в чате),
  при отсутствии приложения — системный «Поделиться». Ссылка открывается в браузере.

### Ограничения
- Раздел доступен из меню чата, а не из профиля/информации о чате.
- Голосовые в «Медиа» показываются плиткой с иконкой и длительностью, без воспроизведения.
- Сканирование клиентское: на очень длинной истории первые вложения находятся не сразу.

---

## Изменённые и новые файлы

```
app/src/main/java/app/yodo/messenger/
  data/local/UserSettingsPreferences.kt            пометки непрочитанного
  data/repository/MessageRepositoryImpl.kt         limit в observeMessages, getMessagesPage
  domain/model/ChatPreview.kt                      isManuallyUnread
  domain/repository/MessageRepository.kt           новые сигнатуры
  features/chats/ChatHistoryPaging.kt              NEW: чистые хелперы + ChatMediaTab
  features/chats/ChatViewModel.kt                  пагинация, olderHistory, снятие пометки
  features/chats/ChatScreen.kt                     якорь прокрутки, меню «Медиа», deep-link
  features/chats/ChatListViewModel.kt              manualUnreadChatIds, markChatUnread
  features/chats/ChatListScreen.kt                 пункт меню, точка-бейдж
  features/chats/ArchivedChatsScreen.kt            пункт меню в архиве
  features/chats/ChatMenuPolicy.kt                 ChatMenuId.MEDIA
  features/chats/ChatMediaViewModel.kt             NEW
  features/chats/ChatMediaScreen.kt                NEW
  navigation/Routes.kt                             Routes.ChatMedia
  navigation/YodoNavGraph.kt                       composable + onOpenChatMedia

app/src/test/java/app/yodo/messenger/features/chats/ChatHistoryPagingTest.kt   NEW
firestore.indexes.json                             индекс messages(topicId ASC, timestamp DESC)
```

## Что проверить вручную после сборки

1. Длинный чат (>200 сообщений): открыть — видна последняя страница; прокрутить вверх —
   подгружается история, содержимое не «прыгает».
2. Принять новое сообщение, стоя в середине истории — ранее просмотренные сообщения не
   исчезают.
3. Переход по уведомлению к старому сообщению — история догружается, сообщение в центре.
4. Форумная тема: убедиться, что индекс задеплоен, история грузится.
5. «Отметить как непрочитанное» → точка в списке, чат в фильтре «Непрочитанные»; открыть
   чат → пометка исчезла.
6. «Медиа, файлы, ссылки»: все три вкладки, открытие фото/файла/ссылки, «Показать ещё».
7. Пустой чат и чат без вложений: корректные пустые состояния, без бесконечной загрузки.
