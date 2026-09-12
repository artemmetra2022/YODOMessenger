# Рассылки и новости

В админке добавлен раздел `Рассылки и новости`.

- Временное окно: `windowStartMinute` / `windowEndMinute`. Если окно пересекает полночь, используется логика `start > end`.
- Независимый push: `pushEnabled`, `pushScheduledAtMillis`, отдельное окно push и флаги `pushSent`.
- Сегментация: `ALL`, `ONLINE`, `CLASSES`; для классов используется `audienceClassIds`, а в документе пользователя поддерживаются `classId`/`class_id`.
- A/B: при `abEnabled=true` вариант стабильно определяется по UID пользователя, чтобы пользователь не "переезжал" между вариантами при повторном запуске worker.
- Шаблоны: коллекция `newsTemplates`.
- Кампании: коллекция `newsCampaigns`.

CTR требует накопления события открытия/клика в клиенте. Текущий worker передаёт `campaignId` и `variant` в FCM payload, поэтому следующий этап можно подключить без изменения формата кампаний.


### Архитектура отправки
Все push-уведомления, включая новости, отложенный push и служебные события, обрабатываются **только GitHub Actions**. Отдельный постоянно работающий push worker/Firebase Functions не используется. Workflow `.github/workflows/send-push-notifications.yml` запускает `.github/scripts/send-push-notifications.js` каждые 5 минут или вручную.
