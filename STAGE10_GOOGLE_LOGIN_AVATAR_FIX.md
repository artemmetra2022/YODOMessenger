# Google-вход + аватарки без Storage — что нужно сделать

## 1. Вход через Google

### Обязательно: перекачай google-services.json

Ты включил Google как способ входа **после** того, как скачивал `google-services.json` в первый раз — в старом файле нет нужных данных (OAuth client ID), без них кнопка "Войти через Google" будет падать с ошибкой при сборке или в рантайме.

**Что сделать:**
1. Firebase Console → ⚙️ **Project settings** → вкладка **General** → раздел с твоим Android-приложением → **google-services.json** → скачай заново
2. Закодируй в base64 (как делали раньше, например через base64.guru) — **или** просто вставь сырой JSON, GitHub Actions теперь понимает оба формата
3. Обнови Secret `GOOGLE_SERVICES_JSON` в GitHub (Settings → Secrets and variables → Actions → найди его → Update)

### Что добавлено в код

- Кнопка **"Войти через Google"** на экране входа (под кнопкой "Войти по номеру телефона")
- Использует современный **Credential Manager** (не устаревший `GoogleSignInClient`)
- При первом входе через Google профиль автоматически создаётся в Firestore — имя и фото берутся из Google-аккаунта

### Проверка

После пересборки APK с обновлённым `google-services.json` — нажми "Войти через Google", появится системное окно выбора Google-аккаунта на телефоне.

**Если кнопка ничего не делает или падает** — почти наверняка причина в недообновлённом `google-services.json`. Пришли лог, если так и будет.

---

## 2. Аватарки без Storage — решение

**Проблема:** Firebase Storage с недавнего времени требует план Blaze даже для маленьких файлов — на новых проектах бесплатно уже не создать.

**Решение:** аватарки теперь хранятся не в Storage, а **прямо в Firestore**, сжатыми до маленького JPEG (макс. 400×400px, до ~700 КБ в Base64) в поле `avatarBase64` документа пользователя. Firestore-документы бесплатны на плане Spark, лимит 1 МБ на документ — фото после сжатия туда легко помещается.

### Что изменилось технически

- `util/ImageUtils.kt` — сжимает выбранное фото и кодирует в Base64 (или наоборот, декодирует для показа)
- `UserRepositoryImpl.uploadAvatar` — больше не трогает Storage, просто пишет сжатую строку в Firestore
- Новый переиспользуемый компонент `ui/components/UserAvatar.kt` — показывает фото по приоритету: **внешняя ссылка** (например, из Google-аккаунта) → **Base64 из Firestore** → заглушка-инициал

### Ограничения такого подхода (сознательно)

- Аватар ограничен маленьким размером (400×400px, среднее качество JPEG) — этого достаточно для круглой иконки профиля, но не подойдёт, если раньше планировался просмотр фото в полный экран/высоком разрешении
- Каждая загрузка профиля собеседника (например, при поиске) тянет за собой это фото в base64 — чуть увеличивает объём трафика/чтений Firestore по сравнению с обычной ссылкой на файл, но для мессенджера с некрупными аватарками это несущественно
- Google-аккаунты — фото по-прежнему грузится по внешней ссылке (не base64), это бесплатно и без ограничений, так как ссылку хостит сам Google

### Ничего в Firebase Console настраивать не нужно

Firestore Rules уже разрешают пользователю писать в свой документ (`users/{uid}`) — новое поле `avatarBase64` под них уже подпадает.

---

## Файлы, которые нужно загрузить в репозиторий

- `app/build.gradle.kts` — новые зависимости (Credential Manager)
- `app/src/main/java/app/yodo/messenger/data/remote/auth/GoogleSignInHelper.kt` — новый
- `app/src/main/java/app/yodo/messenger/domain/repository/AuthRepository.kt`
- `app/src/main/java/app/yodo/messenger/data/repository/AuthRepositoryImpl.kt`
- `app/src/main/java/app/yodo/messenger/features/auth/AuthViewModel.kt`
- `app/src/main/java/app/yodo/messenger/features/auth/LoginScreen.kt`
- `app/src/main/java/app/yodo/messenger/domain/model/YodoUser.kt`
- `app/src/main/java/app/yodo/messenger/util/ImageUtils.kt` — новый
- `app/src/main/java/app/yodo/messenger/data/repository/UserRepositoryImpl.kt`
- `app/src/main/java/app/yodo/messenger/ui/components/UserAvatar.kt` — новый
- `app/src/main/java/app/yodo/messenger/features/profile/ProfileScreen.kt`
- `app/src/main/java/app/yodo/messenger/features/profile/UserProfileScreen.kt`

Проще всего — загрузить архив целиком поверх текущих файлов.

**Не забудь пункт про обновление `google-services.json` — без него вход через Google работать не будет.**
