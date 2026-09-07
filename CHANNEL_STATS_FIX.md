# Баг 21: Статистика канала не открывается

## Причина
В `firestore.rules` отсутствует правило доступа для подколлекции
`chats/{chatId}/subscriberEvents` (используется новой фичей статистики
канала — график роста подписчиков). Firestore по умолчанию запрещает
любой путь без явного `allow`.

Из-за этого:
1. Запрос к `subscriberEvents` в `getChannelStats()`
   (`ChatRepositoryImpl.kt`, метод `getChannelStats`, ~строка 1019)
   падает с `PERMISSION_DENIED`.
2. Вся функция обёрнута в общий `try/catch(e: Exception) { null }`,
   поэтому ошибка от одного под-запроса гасит весь результат.
3. `ChannelStatsViewModel` получает `stats == null` и выставляет
   `accessDenied = true` — владелец канала видит "нет доступа",
   хотя доступ у него есть.

Побочный эффект того же пробела: запись событий подписки/отписки
(`subscribeToChannel` / `unsubscribeFromChannel`) тоже молча не проходит
(обёрнута в `runCatching {}`), поэтому график роста аудитории всегда
будет пустым, даже после починки первого пункта.

## Исправление

1. Добавить в `firestore.rules` внутри `match /chats/{chatId} { ... }`:

```
match /subscriberEvents/{eventId} {
  allow read: if isSignedIn() &&
    (request.auth.uid == get(/databases/$(database)/documents/chats/$(chatId)).data.createdBy ||
     request.auth.uid in (get(/databases/$(database)/documents/chats/$(chatId)).data.adminIds is list
       ? get(/databases/$(database)/documents/chats/$(chatId)).data.adminIds : []));
  allow create: if isSignedIn();
  allow update, delete: if false;
}
```

2. Задеплоить правила: `firebase deploy --only firestore:rules`.

3. (Рекомендация) В `getChannelStats()` не оборачивать весь метод одним
   `try/catch`, а изолировать permission-чувствительные под-запросы
   отдельно — чтобы сбой вспомогательных данных (история подписчиков)
   не гасил всю статистику целиком.
