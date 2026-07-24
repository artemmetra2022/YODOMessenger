# Полный чек-лист настройки Firebase — по порядку

Ты уже сделал: создал Firestore Database. Дальше по шагам.

---

## 1. Authentication — включить способы входа

**Firebase Console → Authentication → Sign-in method**

- Включи **Email/Password**
- Включи **Phone**

Без этого регистрация/вход не заработают вообще (будет ошибка "operation not allowed").

---

## 2. Phone Auth — добавить SHA-ключи (нужно для входа по номеру)

**Firebase Console → ⚙️ Project settings → вкладка "General" → прокрути до своего Android-приложения → Add fingerprint**

Получить SHA-1 и SHA-256 без компьютера немного сложнее — я подскажу отдельно, если решишь настраивать вход по телефону сейчас. Если тестируешь пока только через email — этот пункт можно пропустить, вход по телефону просто не будет работать до этого шага.

---

## 3. Firestore — правила безопасности (Rules)

**Firestore Database → вкладка Rules**

Замени содержимое на:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    match /usernames/{username} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    match /chats/{chatId} {
      allow read, write: if request.auth != null && request.auth.uid in resource.data.participantIds;
      allow create: if request.auth != null;
      match /messages/{messageId} {
        allow read, create: if request.auth != null;
      }
    }
  }
}
```

Нажми **Publish**.

Без этого шага: если у тебя ещё активен test mode (первые 30 дней с момента создания базы) — приложение и так работает. Но лучше сделать сразу, чтобы не сломалось внезапно через месяц.

---

## 4. Firestore — индексы (создаются по мере необходимости)

Ничего создавать заранее не нужно. Просто пользуйся приложением — если какой-то функции (поиск, создание чата) не хватает индекса, в логах будет ошибка со **ссылкой**, перейди по ней, Firebase сам создаст индекс за клик. Подожди 1-2 минуты, пока он построится, и попробуй снова.

Как посмотреть логи без компьютера — через приложение **Logcat** не получится, но можно смотреть логи из Android Studio... раз ты без ПК, есть вариант: если что-то не работает, опиши мне точно что именно (например "не создаётся групповой чат") — я скажу, какой индекс почти наверняка нужен, и дам прямую ссылку на его создание в консоли.

---

## 5. Storage — для аватарок

**Firebase Console → Build → Storage → Get started**

Если ты ещё не создавал Storage (для загрузки фото профиля) — сделай это сейчас, иначе загрузка аватара будет виснуть с ошибкой.

Затем вкладка **Rules** для Storage, замени на:

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /avatars/{fileName} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
  }
}
```

**Publish**.

---

## 6. Push-уведомления (опционально, можно отложить)

Это отдельная, более сложная настройка (Service Account + GitHub Secret + workflow) — она не блокирует основной функционал приложения. Если хочешь настроить сейчас — скажи, вернёмся к файлу `STAGE9B_PUSH_WITHOUT_CARD.md` пошагово. Если нет — всё остальное (чаты, сообщения, профиль, поиск, группы) работает без этого шага.

---

## Что проверить в итоге (минимальный рабочий набор)

- [ ] Authentication → Email/Password включен
- [ ] Firestore Database создана (уже есть у тебя)
- [ ] Firestore Rules опубликованы
- [ ] Storage создан и Rules опубликованы

Это даст полностью рабочий мессенджер: регистрация, чаты, сообщения, профиль, поиск, группы. Phone-вход и push можно настроить позже отдельно.
