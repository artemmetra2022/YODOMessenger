# Новости — расширенные функции

Добавлены: вложения PDF/документов через Firebase Storage, уникальные просмотры `newsCampaigns/{id}/views/{uid}`, реакции, комментарии и их модерация, а также просмотр архива кампаний в админ-панели.

Фоновые push выполняются только через GitHub Actions (`.github/workflows/send-push-notifications.yml`). Отдельный push-worker не используется.
