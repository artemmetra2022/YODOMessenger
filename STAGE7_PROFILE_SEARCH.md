# Этап 7 — Расширенный профиль + поиск: что нужно знать

## Что добавлено

**Профиль:**
- `username` (уникальный @handle, 3-20 символов, латиница/цифры/подчёркивание) — проверка уникальности через транзакцию Firestore и отдельную коллекцию `usernames`
- `bio` — описание "о себе", до 150 символов
- Все поля редактируются отдельно, каждое со своей кнопкой "Сохранить"

**Поиск (иконка 🔍 в списке чатов):**
- Поиск по началу имени **или** @username, без учёта регистра
- Debounce 350мс — не долбит Firestore на каждое нажатие
- Тап по найденному человеку — создаёт (или открывает существующий) приватный чат и сразу переходит в него

## Важно: новый составной индекс в Firestore

Поиск чата при создании использует `whereArrayContains("participantIds", uid)` + `whereEqualTo("type", "PRIVATE")` — это **новая комбинация полей**, для которой Firestore потребует ещё один composite index (аналогично тому, что было на Этапе 3).

**Как получить:** просто открой приложение, попробуй написать кому-то сообщение через поиск — если индекса не будет, в Logcat/логах Firebase появится ошибка со ссылкой. Перейди по ней — Firebase Console сам создаст нужный индекс за один клик.

## Важно: обнови правила безопасности Firestore

Раньше (test mode) всё было разрешено всем. Теперь, когда поиск читает **чужие** документы пользователей, стоит явно прописать правила — иначе если test mode уже истёк (он живёт 30 дней), поиск перестанет работать.

Firestore Console → **Rules**, минимальный вариант для текущего этапа разработки:

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

Это рабочий, но упрощённый вариант для разработки — не идеальный с точки зрения безопасности (например, `usernames` можно ужесточить так, чтобы писать мог только владелец), но защищает от полностью открытого доступа. Можно усилить позже перед публикацией.

## Изменённые/новые файлы

- `domain/model/YodoUser.kt` — добавлены `username`, `bio`
- `domain/repository/UserRepository.kt` + `data/repository/UserRepositoryImpl.kt` — методы `updateUsername`, `updateBio`, `searchUsers`
- `domain/repository/ChatRepository.kt` + `data/repository/ChatRepositoryImpl.kt` — метод `createOrGetPrivateChat`, персонализированные названия чатов (`titles`)
- `features/search/SearchViewModel.kt`, `SearchScreen.kt` — новые
- `features/profile/ProfileViewModel.kt`, `ProfileScreen.kt` — поля username/bio
- `features/chats/ChatListScreen.kt` — кнопка поиска теперь рабочая
- `navigation/Routes.kt`, `YodoNavGraph.kt` — маршрут Search

## Что можно сделать дальше (не реализовано сейчас)

- В профиле собеседника (при просмотре чужого профиля из поиска/чата) пока негде посмотреть его bio — это отдельный экран "Профиль собеседника", могу сделать следующим шагом
- Поиск ищет только по префиксу (началу строки) — если ввести середину имени, не найдёт. Полноценный полнотекстовый поиск в Firestore потребовал бы стороннего сервиса (Algolia/Meilisearch)
