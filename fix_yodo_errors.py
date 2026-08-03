#!/usr/bin/env python3
"""
fix_yodo_errors.py
Автоматический скрипт для исправления известных ошибок в проекте YodoMessenger.
Добавлена детализация лога: какие файлы изменены, какие не требовали изменений, какие не найдены/ошибки.
Запуск: python3 fix_yodo_errors.py (из корня проекта)
"""

import os
import re
import sys

# --- Пути к файлам ---
BASE_DIR = "app/src/main/java/app/yodo/messenger"
MESSAGE_REPO_PATH = os.path.join(BASE_DIR, "data/repository/MessageRepositoryImpl.kt")
CHAT_REPO_PATH = os.path.join(BASE_DIR, "data/repository/ChatRepositoryImpl.kt")
DOMAIN_CHAT_REPO_PATH = os.path.join(BASE_DIR, "domain/repository/ChatRepository.kt")
CHAT_LIST_VM_PATH = os.path.join(BASE_DIR, "features/chats/ChatListViewModel.kt")
CHAT_LIST_SCREEN_PATH = os.path.join(BASE_DIR, "features/chats/ChatListScreen.kt")
SEARCH_VM_PATH = os.path.join(BASE_DIR, "features/search/SearchViewModel.kt")
SEARCH_SCREEN_PATH = os.path.join(BASE_DIR, "features/search/SearchScreen.kt")
NEW_GROUP_SCREEN_PATH = os.path.join(BASE_DIR, "features/chats/NewGroupScreen.kt")
IMAGE_UTILS_PATH = os.path.join(BASE_DIR, "util/ImageUtils.kt")

# --- Статистика ---
stats = {
    "changed": [],
    "unchanged": [],
    "errors": []
}

def log_change(status, path, details=""):
    """Логирует статус обработки файла."""
    # Используем status.lower(), чтобы гарантировать правильный ключ
    # stats имеет ключ 'errors', а не 'error'.
    stats_key = status.lower()
    if stats_key not in stats:
        # На всякий случай, если передан неверный статус
        stats_key = 'errors'
        
    full_msg = f" [{status}] {path}"
    if details:
        full_msg += f" ({details})"
    print(full_msg)
    stats[stats_key].append((path, details))

def safe_read_file(file_path):
    """Безопасно читает файл, возвращает содержимое или None."""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            return f.read()
    except FileNotFoundError:
        log_change("ERROR", file_path, "Файл не найден")
        return None
    except UnicodeDecodeError:
        print(f"⚠️ Ошибка кодировки при чтении: {file_path}, пробую cp1251...")
        try:
            with open(file_path, 'r', encoding='cp1251') as f:
                return f.read()
        except Exception as e:
            log_change("ERROR", file_path, f"Ошибка кодировки: {e}")
            return None
    except Exception as e:
        log_change("ERROR", file_path, f"Ошибка чтения: {e}")
        return None

def safe_write_file(file_path, content):
    """Безопасно записывает содержимое в файл."""
    try:
        # Создаём директории, если они не существуют
        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    except Exception as e:
        log_change("ERROR", file_path, f"Ошибка записи: {e}")
        return False


def fix_empty_list_typing():
    """Исправляет emptyList() без типа в MessageRepositoryImpl.kt и ChatRepositoryImpl.kt."""
    print("\n--- Исправление: emptyList() без типа ---")

    # 1. MessageRepositoryImpl.kt
    content = safe_read_file(MESSAGE_REPO_PATH)
    if content is not None:
        original_content = content
        # Исправление для getRecentMessages
        content = re.sub(
            r'as\?\s+List<\*>',
            'as? List<Message> ?: emptyList<Message>()',
            content
        )
        content = re.sub(
            r'return\ emptyList\(\)',
            'return emptyList<Message>()',
            content
        )
        # Исправление для observeMessages (аналогично из контекста)
        content = content.replace(
            '(chatSnapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()',
            '(chatSnapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList<String>()'
        )
        content = content.replace(
            'trySend(emptyList())',
            'trySend(emptyList<Message>())'
        )

        if content != original_content:
            if safe_write_file(MESSAGE_REPO_PATH, content):
                log_change("CHANGED", MESSAGE_REPO_PATH)
            else:
                # Ошибка записи уже будет залогирована safe_write_file
                pass
        else:
            log_change("UNCHANGED", MESSAGE_REPO_PATH, "Патчи не применимы или уже применены")

    # 2. ChatRepositoryImpl.kt (уже частично сделано в контексте, но добавим универсальный патч)
    content = safe_read_file(CHAT_REPO_PATH)
    if content is not None:
        original_content = content
        # Патч для участников чата
        content = re.sub(
            r'\?\:\s*emptyList\(\)',
            '?: emptyList<String>()',
            content
        )
        # Патч для пустого списка сообщений в наблюдателе (если встречается)
        content = content.replace(
            'trySend(emptyList())',
            'trySend(emptyList<Message>())'
        )

        if content != original_content:
            if safe_write_file(CHAT_REPO_PATH, content):
                log_change("CHANGED", CHAT_REPO_PATH)
            else:
                # Ошибка записи уже будет залогирована safe_write_file
                pass
        else:
            log_change("UNCHANGED", CHAT_REPO_PATH, "Патчи не применимы или уже применены")


def fix_tasks_whenall_success():
    """Заменяет Tasks.whenAllSuccess на tasks.map { it.await() } в ChatRepositoryImpl.kt."""
    print("\n--- Исправление: Tasks.whenAllSuccess -> tasks.map { it.await() } ---")
    content = safe_read_file(CHAT_REPO_PATH)
    if content is None:
        return # Ошибка уже залогирована в safe_read_file

    original_content = content

    # Паттерн для замены старого блока
    old_block_pattern = re.compile(
        r'val memberDocs = if \(participantIds\.isNotEmpty\(\)\)\s*\{.*?'
        r'com\.google\.android\.gms\.tasks\.Tasks\.whenAllSuccess<com\.google\.firebase\.firestore\.DocumentSnapshot>\(tasks\)\.await\(\)'
        r'.*?\}\s*else\s+emptyList\(\)\s*'
        r'val members = memberDocs\.mapNotNull\s*\{\s*memberDoc\s*->',
        re.DOTALL
    )

    replacement_block = (
        'val members = participantIds.mapNotNull { id ->\n'
        '        val memberDoc = firestore.collection("users").document(id).get().await()\n'
        '        memberDoc.toYodoUser(id)\n'
        '    }\n'
    )

    updated_content = old_block_pattern.sub(replacement_block, content)

    # Если первый паттерн не сработал, пробуем второй (более простой)
    if updated_content == content:
        updated_content = updated_content.replace(
            'com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(tasks).await()',
            'tasks.map { it.await() }'
        )

    if updated_content != original_content:
        if safe_write_file(CHAT_REPO_PATH, updated_content):
            log_change("CHANGED", CHAT_REPO_PATH)
        else:
            # Ошибка записи уже будет залогирована safe_write_file
            pass
    else:
        log_change("UNCHANGED", CHAT_REPO_PATH, "Паттерн Tasks.whenAllSuccess не найден или уже заменён")


def fix_exception_handling():
    """Исправляет обработку исключений в MessageRepositoryImpl.kt."""
    print("\n--- Исправление: Обработка исключений ---")
    content = safe_read_file(MESSAGE_REPO_PATH)
    if content is None:
        return # Ошибка уже залогирована в safe_read_file

    original_content = content
    # Исправление в getRecentMessages
    content = re.sub(
        r'} catch \(e: Exception\) {\s*SendMessageResult\.Error\(e\.toUserMessage\("Не удалось отправить"\)\)\s*}',
        '} catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось получить")) }',
        content
    )
    # Исправление в observeMessages (если ошибка в маппинге документа)
    # Этот патч более общий, для обработки ошибок внутри mapNotNull
    # Мы не можем легко обернуть mapNotNull, но можем улучшить логику внутри mapDocToMessage,
    # если она вызывается из observeMessages. Основная логика уже в контексте, но может потребоваться обёртка try-catch
    # для вызова mapDocToMessage внутри observeMessages flow.
    # Текущий контекст показывает mapDocToMessage, который уже обернут в try-catch.
    # Главное - это исправление в getRecentMessages, как показано выше.

    if content != original_content:
        if safe_write_file(MESSAGE_REPO_PATH, content):
            log_change("CHANGED", MESSAGE_REPO_PATH)
        else:
            # Ошибка записи уже будет залогирована safe_write_file
            pass
    else:
        log_change("UNCHANGED", MESSAGE_REPO_PATH, "Патчи обработки исключений не найдены или уже применены")


def fix_new_group_screen():
    """Исправляет NewGroupScreen.kt - добавляет fillMaxWidth."""
    print("\n--- Исправление: NewGroupScreen.kt fillMaxWidth ---")
    content = safe_read_file(NEW_GROUP_SCREEN_PATH)
    if content is None:
        return # Ошибка уже залогирована в safe_read_file

    original_content = content
    # Ищем OutlinedTextField с label "Название" и добавляем .fillMaxWidth()
    # Предполагаем, что модификатор находится на той же строке или на следующей
    pattern = r'(OutlinedTextField\([^)]*label = \{ Text\("Название"[^)]*\)\)[\s\n]*modifier = Modifier\.fillMaxWidth\(\))'
    if not re.search(pattern, content):
         # Если fillMaxWidth уже есть, пропускаем
         content = re.sub(
             r'(OutlinedTextField\([^)]*label = \{ Text\("Название"[^)]*\)\)[\s\n]*modifier = Modifier)',
             r'\1.fillMaxWidth()',
             content
         )
         # Если модификатора не было, добавляем его
         content = re.sub(
             r'(OutlinedTextField\([^)]*label = \{ Text\("Название"[^)]*\)\)(?!\s*modifier))',
             r'\1\n                    modifier = Modifier.fillMaxWidth()',
             content
         )

    if content != original_content:
        if safe_write_file(NEW_GROUP_SCREEN_PATH, content):
            log_change("CHANGED", NEW_GROUP_SCREEN_PATH)
        else:
            # Ошибка записи уже будет залогирована safe_write_file
            pass
    else:
        log_change("UNCHANGED", NEW_GROUP_SCREEN_PATH, "fillMaxWidth уже установлен или не требуется")


def fix_viewmodel_consume_error():
    """Исправляет consumeError в SearchViewModel.kt и ChatListViewModel.kt."""
    print("\n--- Исправление: ViewModel consumeError ---")

    # SearchViewModel.kt
    content = safe_read_file(SEARCH_VM_PATH)
    if content is not None:
        original_content = content
        content = content.replace(
            'fun consumeError() { _uiState.value = _uiState.value.copy(errorMessage = result.message) }',
            'fun consumeError() { _uiState.value = _uiState.value.copy(errorMessage = null) }'
        )
        if content != original_content:
            if safe_write_file(SEARCH_VM_PATH, content):
                log_change("CHANGED", SEARCH_VM_PATH)
            else:
                # Ошибка записи уже будет залогирована safe_write_file
                pass
        else:
            log_change("UNCHANGED", SEARCH_VM_PATH, "consumeError не найден или уже исправлен")

    # ChatListViewModel.kt
    content = safe_read_file(CHAT_LIST_VM_PATH)
    if content is not None:
        original_content = content
        content = content.replace(
            'fun consumeError() { _uiState.value = _uiState.value.copy(errorMessage = result.message) }',
            'fun consumeError() { _uiState.value = _uiState.value.copy(errorMessage = null) }'
        )
        if content != original_content:
            if safe_write_file(CHAT_LIST_VM_PATH, content):
                log_change("CHANGED", CHAT_LIST_VM_PATH)
            else:
                # Ошибка записи уже будет залогирована safe_write_file
                pass
        else:
            log_change("UNCHANGED", CHAT_LIST_VM_PATH, "consumeError не найден или уже исправлен")


def fix_image_utils_context():
    """Проверяет и исправляет Context в ImageUtils.kt."""
    print("\n--- Исправление: ImageUtils.kt findActivity ---")
    content = safe_read_file(IMAGE_UTILS_PATH)
    if content is None:
        return # Ошибка уже залогирована в safe_read_file

    # Логика из контекста уже корректна. Проверим наличие функции findActivity.
    if "fun Context.findActivity()" in content:
        log_change("UNCHANGED", IMAGE_UTILS_PATH, "findActivity функция найдена, вероятно, корректна.")
    else:
        log_change("ERROR", IMAGE_UTILS_PATH, "findActivity функция не найдена. Требуется ручная проверка.")


def update_chat_repository_interface():
    """Обновляет domain/repository/ChatRepository.kt для ChatListResult."""
    print("\n--- Обновление: domain ChatRepository.kt (ChatListResult) ---")
    new_interface_content = '''package app.yodo.messenger.domain.repository

import android.graphics.Bitmap
import app.yodo.messenger.domain.model.ChannelProfile
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.GroupProfile
import app.yodo.messenger.domain.model.YodoUser
import kotlinx.coroutines.flow.Flow

// --- Типы для результатов ---

sealed interface ChatListResult {
    data class Success(val chats: List<ChatPreview>) : ChatListResult
    data class Error(val message: String) : ChatListResult
}

// --- Интерфейс репозитория ---

interface ChatRepository {
    fun observeChatPreviews(uid: String): Flow<ChatListResult>
    suspend fun createPrivateChat(otherUserId: String, otherUserInfo: YodoUser): Boolean
    suspend fun createGroupChat(title: String, avatarBitmap: Bitmap?, participants: List<YodoUser>): Boolean
    suspend fun updateGroupInfo(chatId: String, newTitle: String?, newAvatarBitmap: Bitmap?): Boolean
    suspend fun leaveGroup(chatId: String): Boolean
    suspend fun deleteChat(chatId: String): Boolean
    suspend fun markChatAsRead(chatId: String, userIds: List<String>): Boolean
    suspend fun addChannelAdmin(chatId: String, userId: String): Boolean
    suspend fun removeChannelAdmin(chatId: String, userId: String): Boolean
    suspend fun updateChannelInfo(chatId: String, newTitle: String?, newAvatarBitmap: Bitmap?): Boolean
    suspend fun searchPublicChannels(query: String): List<ChannelSearchItem>
    suspend fun joinChannel(channelId: String, currentUserId: String): Boolean
    suspend fun inviteToGroup(chatId: String, userIds: List<String>): Boolean
    suspend fun kickFromGroup(chatId: String, userId: String): Boolean
    suspend fun getGroupProfile(chatId: String): GroupProfile?
    suspend fun getChannelProfile(chatId: String): ChannelProfile?
    suspend fun toggleChatPin(chatId: String, userId: String, isCurrentlyPinned: Boolean): Boolean
    suspend fun toggleChatMute(chatId: String, userId: String, isCurrentlyMuted: Boolean): Boolean
    suspend fun setChatNotificationLevel(chatId: String, userId: String, level: NotificationLevel): Boolean
    suspend fun getChatNotificationLevel(chatId: String, userId: String): NotificationLevel
    suspend fun searchChats(query: String, currentUserId: String): List<ChatPreview>
    suspend fun createChatFromSearch(user: YodoUser, currentUserId: String): String? // Возвращает ID чата или null
}

enum class NotificationLevel { ALL_MESSAGES, MENTIONS_ONLY, MUTED }
'''
    if safe_write_file(DOMAIN_CHAT_REPO_PATH, new_interface_content):
        log_change("CHANGED", DOMAIN_CHAT_REPO_PATH, "Перезаписан интерфейс с ChatListResult")
    else:
        # Ошибка записи уже будет залогирована safe_write_file
        pass


def update_chat_repository_impl():
    """Обновляет data/repository/ChatRepositoryImpl.kt для корректной обработки ошибок."""
    print("\n--- Обновление: data ChatRepositoryImpl.kt (обработка ошибок) ---")
    # Этот файл сложнее обновить автоматически без полного понимания структуры.
    # Лучше предоставить шаблон или обновить вручную на основе контекста.
    # Однако, можно автоматизировать основной паттерн для observeChatPreviews.
    content = safe_read_file(CHAT_REPO_PATH)
    if content is None:
        return # Ошибка уже залогирована в safe_read_file

    original_content = content

    # Паттерн для обновления observeChatPreviews
    # Заменяем старую логику на новую с ChatListResult
    # Это грубый пример. Реальная замена должна быть точной.
    # Контекст показывает:
    # trySend(ChatPreview(...))
    # ->
    # trySend(ChatListResult.Success(listOf(ChatPreview(...))))
    # И обработка ошибок:
    # try { ... } catch (e: Exception) { trySend(ChatListResult.Error(...)); return@addSnapshotListener }

    # Более реалистичный патч для начала функции observeChatPreviews
    start_of_obs_func = "override fun observeChatPreviews(uid: String): Flow<ChatListResult> = callbackFlow"
    if start_of_obs_func in content:
        # Ищем тело функции
        func_match = re.search(
            r'(override fun observeChatPreviews\(uid: String\): Flow<ChatListResult> = callbackFlow \{[^}]*addSnapshotListener \{ snapshot, error ->\s*)(.*?)(\s*val chats =)',
            content,
            re.DOTALL
        )
        if func_match:
            before_chats_logic = func_match.group(1)
            inside_snapshot_logic = func_match.group(2)

            # Проверяем, содержит ли логика уже правильную обработку ошибок из контекста
            if "ChatListResult.Error" in inside_snapshot_logic and "trySend(ChatListResult.Success(chats))" in content:
                 log_change("UNCHANGED", CHAT_REPO_PATH, "observeChatPreviews уже обновлён (частично)")
                 return # Выходим, если уже обновлено
            # Обновляем логику внутри addSnapshotListener
            updated_inside_logic = inside_snapshot_logic
            # Обработка ошибки snapshot
            updated_inside_logic = re.sub(
                r'if \(snapshot == null.*?\)',
                'if (error != null) {\n        trySend(ChatListResult.Error(error.message ?: "Неизвестная ошибка Firestore"))\n        return@addSnapshotListener\n    }',
                updated_inside_logic, re.DOTALL
            )
            # Обработка успешного snapshot
            # Заменяем старую отправку на новую структуру
            # Это сложный патч, лучше сделать вручную или с более точным знанием структуры до и после.
            # Предположим, что основная логика получения списка chats уже есть.
            # Нужно найти место, где отправляется результат и обернуть его.
            # content = content.replace('OLD_SEND_LOGIC', 'trySend(ChatListResult.Success(chats))')

            # Пример замены отправки (очень приблизительно, требует точного паттерна)
            # content = content.replace('trySend(chats)', 'trySend(ChatListResult.Success(chats))')
            # Или, если chats формируется как переменная, то после её формирования:
            post_chats_var = content.replace(func_match.group(0), before_chats_logic + updated_inside_logic + func_match.group(3))

            # Теперь находим, где отправляется `chats` и меняем
            # Это гипотетический патч, требующий точного соответствия.
            # Более надёжно будет предоставить готовый фрагмент из контекста и заменить им весь блок.
            # Пока оставим предупреждение.
            log_change("ERROR", CHAT_REPO_PATH, "observeChatPreviews требует ручной доработки. Проверьте файл вручную.")
            # safe_write_file(CHAT_REPO_PATH, post_chats_var) # Не пишем автоматически
        else:
             log_change("ERROR", CHAT_REPO_PATH, "Не найден паттерн для observeChatPreviews. Требует ручной проверки.")
    else:
        log_change("ERROR", CHAT_REPO_PATH, "Интерфейс функции observeChatPreviews не найден. Требует ручной проверки.")


def main():
    print("Запуск скрипта исправления ошибок для YodoMessenger...")
    print("="*60)

    # Проверка корня проекта
    if not os.path.isdir("app"):
        print("❌ ОШИБКА: Папка 'app/' не найдена!")
        print(" Запустите скрипт из КОРНЯ проекта YodoMessenger")
        sys.exit(1)

    fix_empty_list_typing()
    fix_tasks_whenall_success()
    fix_exception_handling()
    fix_new_group_screen()
    fix_viewmodel_consume_error()
    fix_image_utils_context()
    update_chat_repository_interface()
    # update_chat_repository_impl() # Комментируем, так как требует ручной доработки

    print("\n" + "="*60)
    print("СТАТИСТИКА:")
    print(f"  Изменено: {len(stats['changed'])} файлов")
    for path, _ in stats['changed']:
        print(f"    - {path}")
    print(f"  Без изменений: {len(stats['unchanged'])} файлов")
    for path, reason in stats['unchanged']:
        print(f"    - {path}: {reason}")
    print(f"  Ошибки: {len(stats['errors'])} файлов")
    for path, reason in stats['errors']:
        print(f"    - {path}: {reason}")

    print("\nПроверьте файлы и соберите проект для тестирования.")


if __name__ == "__main__":
    main()