# Этап 6 — Push-уведомления: что нужно настроить

## Как это работает целиком

1. При входе приложение получает FCM-токен устройства и сохраняет его в Firestore (`users/{uid}.fcmToken`)
2. При отправке сообщения (Firestore: `chats/{chatId}/messages`) срабатывает **Cloud Function** `onNewMessage`
3. Функция находит токены всех участников чата (кроме отправителя) и рассылает им push через Firebase Cloud Messaging
4. Устройство получателя показывает системное уведомление (даже если приложение свёрнуто/закрыто)

## Шаг 1. Убедиться, что план Firebase — Blaze (Pay as you go)

Cloud Functions **требуют платного плана Blaze**. Это не значит, что придётся платить — у Firebase щедрый бесплатный лимит (2 млн вызовов функций в месяц), для тестового мессенджера ты в него не упрёшься. Но привязать карту для активации плана придётся.

**Firebase Console → в самом низу левого меню → Upgrade → Blaze**

## Шаг 2. Создать Service Account для деплоя из GitHub Actions

1. Firebase Console → ⚙️ (шестерёнка) → **Project settings → Service accounts**
2. Нажми **Generate new private key** → скачается `.json` файл
3. Открой этот файл текстовым просмотрщиком, скопируй **весь текст целиком** (это уже читаемый JSON, кодировать в base64 не нужно)

## Шаг 3. Добавить Secret в GitHub

1. В репозитории: **Settings → Secrets and variables → Actions → New repository secret**
2. Name: `FIREBASE_SERVICE_ACCOUNT`
3. Secret: вставь весь скопированный JSON
4. **Add secret**

## Шаг 4. Задеплоить функцию

1. Загрузи в репозиторий новые файлы: папку `functions/`, `firebase.json`, `.firebaserc`, `.github/workflows/deploy-functions.yml`
2. Вкладка **Actions → Deploy Cloud Functions → Run workflow**
3. Подожди пару минут, дождись зелёной галочки

## Шаг 5. Пересобрать APK

Загрузи обновлённые файлы приложения (список ниже) и запусти **Build Debug APK** заново, как обычно.

## Что нужно проверить при первом тесте

- При первом запуске приложение спросит разрешение на уведомления (Android 13+) — обязательно разреши
- Push работает **между двумя разными аккаунтами** — отправь сообщение с одного телефона/эмулятора, уведомление придёт на другой, где залогинен собеседник
- Если push не пришёл — проверь в Firebase Console → Functions → Logs, там будут видны ошибки (например, "no valid tokens found")

## Изменённые/новые файлы этого этапа

**Android-приложение:**
- `notifications/NotificationHelper.kt` — новый
- `res/drawable/ic_notification.xml` — новый
- `data/remote/fcm/YodoFirebaseMessagingService.kt` — переписан
- `features/chats/ChatListViewModel.kt` — добавлена синхронизация токена
- `MainActivity.kt` — запрос разрешения на уведомления
- `YodoApp.kt` — создание канала уведомлений

**Backend (Cloud Functions):**
- `functions/index.js`, `functions/package.json`
- `firebase.json`, `.firebaserc`
- `.github/workflows/deploy-functions.yml`

## Ограничения текущей версии

- Тап по уведомлению открывает приложение, но пока не переходит сразу в конкретный чат (нужен deep-link парсинг в NavGraph) — сделаю отдельным пунктом, если важно
- Нет отдельных уведомлений о звонках — это Этап звонков по твоему roadmap
