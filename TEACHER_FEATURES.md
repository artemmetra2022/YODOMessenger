# Teacher profiles

Добавлен отдельный каталог `teacherProfiles/{userId}`.

## Поля
- `shortBio` — краткая биография
- `subjects` — массив предметов
- `questionHours` — график приёма вопросов
- `contactEmail` — контактный email
- `photoUrl` — фото профиля
- `rating`, `showRating` — рейтинг и переключатель показа
- `vacationFromMillis`, `vacationToMillis` — период недоступности
- `acceptingQuestions` — принимает ли учитель вопросы
- `notifyNewQuestions`, `notifyMessages` — настройки уведомлений

## Экраны
`teachers` — каталог с фильтром по предмету и сортировкой по имени.
`teacher_profile/{userId}` — публичная карточка.
`admin_teachers` — администрирование карточек и фотографии.

## Push
Постоянного worker нет. Сообщения учителю попадают в `teacherNotificationQueue`; существующий `.github/workflows/send-push-notifications.yml` запускает `.github/scripts/send-push-notifications.js`, который отправляет FCM и помечает записи обработанными.

Для отдельного события «новый вопрос» используется та же очередь с `type=question`, `teacherId`, `title`, `body`, `action`, `notified=false`.
