#!/usr/bin/env python3
"""
YODOMessenger — Автоисправление ошибок сборки v0.2.0
Работает на Pydroid 3 (Android) — без git
Запуск: python3 fix_build.py (в корне проекта, рядом с папкой app/)
"""

import os
import re
import sys

BASE = "app/src/main/java/app/yodo/messenger"

def read_file(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

# ═══════════════════════════════════════════════
# 1. Удалить дубликат NavGraph.kt
# ═══════════════════════════════════════════════
def fix_1_delete_navgraph():
    path = f"{BASE}/navigation/NavGraph.kt"
    if os.path.exists(path):
        os.remove(path)
        print(f"  ✅ Удалён: {path}")
    else:
        print(f"  ⏭️  Не найден (OK): {path}")

# ═══════════════════════════════════════════════
# 2. Создать совместимый MainScreen.kt
# ═══════════════════════════════════════════════
MAIN_SCREEN = r'''package app.yodo.messenger.features.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.yodo.messenger.features.chats.ChatListScreen

@Composable
fun MainScreen(
    onChatClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    onOfflineChatClick: () -> Unit,
    onNearbyPeopleClick: () -> Unit,
    onLoggedOut: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Chat, contentDescription = "Чаты") },
                    label = { Text("Чаты") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onNearbyPeopleClick() },
                    icon = { Icon(Icons.Filled.NearMe, contentDescription = "Рядом") },
                    label = { Text("Рядом") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { onOfflineChatClick() },
                    icon = { Icon(Icons.Filled.WifiOff, contentDescription = "Офлайн") },
                    label = { Text("Офлайн") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { onCreateGroupClick() },
                    icon = { Icon(Icons.Filled.Group, contentDescription = "Группа") },
                    label = { Text("Группа") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { onProfileClick() },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Профиль") },
                    label = { Text("Профиль") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            ChatListScreen(
                onChatClick = onChatClick,
                onProfileClick = onProfileClick,
                onSettingsClick = { onProfileClick() },
                onSearchClick = onSearchClick
            )
        }
    }
}
'''

def fix_2_mainscreen():
    path = f"{BASE}/features/main/MainScreen.kt"
    write_file(path, MAIN_SCREEN.strip() + "\n")
    print(f"  ✅ Создан совместимый: {path}")

# ═══════════════════════════════════════════════
# 3. Патч YodoNavGraph.kt
# ═══════════════════════════════════════════════
def fix_3_yodonavgraph():
    path = f"{BASE}/navigation/YodoNavGraph.kt"
    if not os.path.exists(path):
        print(f"  ⚠️  Файл не найден: {path}")
        return

    content = read_file(path)
    changed = False

    # 3a. Добавить импорты
    if "ImageViewerScreen" not in content:
        last_import_pos = content.rfind("\nimport ")
        if last_import_pos != -1:
            end_of_line = content.index("\n", last_import_pos + 1)
            new_imports = "\nimport androidx.navigation.NavType\nimport androidx.navigation.navArgument\nimport app.yodo.messenger.features.chats.ImageViewerScreen"
            content = content[:end_of_line] + new_imports + content[end_of_line:]
            changed = True
            print(f"  ✅ Добавлены импорты")

    # 3b. Добавить onOpenImageViewer в ChatScreen
    if "onOpenImageViewer" not in content:
        pattern = r'(onForwardMessage\s*=\s*\{[^}]*\})'
        match = re.search(pattern, content)
        if match:
            insert_pos = match.end()
            new_param = """,
                onOpenImageViewer = { base64, sender, ts ->
                    navController.navigate(Routes.ImageViewer.createRoute(base64, sender, ts))
                }"""
            content = content[:insert_pos] + new_param + content[insert_pos:]
            changed = True
            print(f"  ✅ Добавлен onOpenImageViewer")
        else:
            print(f"  ⚠️  Не найден onForwardMessage — добавьте onOpenImageViewer вручную")

    # 3c. Добавить маршрут ImageViewer
    if "ImageViewer.route" not in content:
        markers = [
            "composable(Routes.ForwardMessage.route)",
            "composable(Routes.Search.route)",
            "composable(Routes.Profile.route)"
        ]
        insert_marker = None
        for m in markers:
            if m in content:
                insert_marker = m
                break

        if insert_marker:
            route_block = """
        composable(
            route = Routes.ImageViewer.route,
            arguments = listOf(
                navArgument(Routes.ImageViewer.ARG_IMAGE) { type = NavType.StringType },
                navArgument(Routes.ImageViewer.ARG_SENDER) { type = NavType.StringType },
                navArgument(Routes.ImageViewer.ARG_TIMESTAMP) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val imageBase64 = backStackEntry.arguments?.getString(Routes.ImageViewer.ARG_IMAGE) ?: ""
            val senderName = backStackEntry.arguments?.getString(Routes.ImageViewer.ARG_SENDER) ?: ""
            val timestamp = backStackEntry.arguments?.getLong(Routes.ImageViewer.ARG_TIMESTAMP) ?: 0L
            ImageViewerScreen(
                imageBase64 = imageBase64,
                senderName = senderName,
                timestamp = timestamp,
                onBackClick = { navController.popBackStack() }
            )
        }

        """
            content = content.replace(insert_marker, route_block + insert_marker)
            changed = True
            print(f"  ✅ Добавлен маршрут ImageViewer")
        else:
            print(f"  ⚠️  Не найдено место для маршрута ImageViewer")

    if changed:
        write_file(path, content)
        print(f"  ✅ Сохранён: {path}")

# ═══════════════════════════════════════════════
# 4. Патч MessageRepositoryImpl.kt
# ═══════════════════════════════════════════════
def fix_4_message_repo():
    path = f"{BASE}/data/repository/MessageRepositoryImpl.kt"
    if not os.path.exists(path):
        print(f"  ⚠️  Файл не найден: {path}")
        return

    content = read_file(path)
    original = content

    # Исправить emptyList() без типа в sendRawMessage
    content = content.replace(
        'chatSnapshot.get("participantIds") as? List<*> ?: emptyList()',
        '(chatSnapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList<String>()'
    )

    # Исправить trySend(emptyList())
    content = content.replace(
        'trySend(emptyList())',
        'trySend(emptyList<Message>())'
    )

    if content != original:
        write_file(path, content)
        print(f"  ✅ Исправлен: {path}")
    else:
        print(f"  ⏭️  Не найдено для исправления (возможно уже OK)")

# ═══════════════════════════════════════════════
# 5. Патч ChatRepositoryImpl.kt
# ═══════════════════════════════════════════════
def fix_5_chat_repo():
    path = f"{BASE}/data/repository/ChatRepositoryImpl.kt"
    if not os.path.exists(path):
        print(f"  ⚠️  Файл не найден: {path}")
        return

    content = read_file(path)
    original = content

    if "Tasks.whenAllSuccess" in content:
        # Заменить блок Tasks.whenAllSuccess на простой цикл
        old_pattern = re.search(
            r'val memberDocs = if \(participantIds\.isNotEmpty\(\)\)\s*\{.*?\}\s*else\s+emptyList\(\)\s*'
            r'val members = memberDocs\.mapNotNull\s*\{\s*memberDoc\s*->',
            content,
            re.DOTALL
        )

        if old_pattern:
            new_code = 'val members = participantIds.mapNotNull { id ->\n'
            new_code += '            val memberDoc = firestore.collection("users").document(id).get().await()\n'
            content = content[:old_pattern.start()] + new_code + content[old_pattern.end():]
            print(f"  ✅ Заменён Tasks.whenAllSuccess")
        else:
            # Простая замена
            content = content.replace(
                'com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(tasks).await()',
                'tasks.map { it.await() }'
            )
            print(f"  ✅ Заменён Tasks.whenAllSuccess (вариант 2)")

    # Исправить emptyList() без типа
    content = re.sub(r'\?\:\s*emptyList\(\)', '?: emptyList<String>()', content)

    if content != original:
        write_file(path, content)
        print(f"  ✅ Сохранён: {path}")
    else:
        print(f"  ⏭️  Не найдено для исправления")

# ═══════════════════════════════════════════════
# 6. Патч SettingsViewModel.kt
# ═══════════════════════════════════════════════
def fix_6_settings_vm():
    path = f"{BASE}/features/settings/SettingsViewModel.kt"
    if not os.path.exists(path):
        print(f"  ⚠️  Файл не найден: {path}")
        return

    content = read_file(path)
    original = content

    # Заменить: fun setXxx(param: Type) = viewModelScope.launch { ... }
    # На:      fun setXxx(param: Type) { viewModelScope.launch { ... } }
    content = re.sub(
        r'fun (\w+)\(([^)]*)\)\s*=\s*viewModelScope\.launch\s*\{([^}]*)\}',
        r'fun \1(\2) { viewModelScope.launch {\3} }',
        content
    )

    if content != original:
        write_file(path, content)
        print(f"  ✅ Исправлен: {path}")
    else:
        print(f"  ⏭️  Не найдено для исправления")

# ═══════════════════════════════════════════════
# 7. Патч SettingsScreen.kt
# ═══════════════════════════════════════════════
def fix_7_settings_screen():
    path = f"{BASE}/features/settings/SettingsScreen.kt"
    if not os.path.exists(path):
        print(f"  ⚠️  Файл не найден: {path}")
        return

    content = read_file(path)
    original = content

    # Заменить: SettingsSwitchRow("Title", "Sub", value) { viewModel.setXxx(it) }
    # На:      SettingsSwitchRow(title = "Title", subtitle = "Sub", checked = value, onCheckedChange = { v -> viewModel.setXxx(v) })
    pattern = r'SettingsSwitchRow\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(\w+)\s*(?:,\s*enabled\s*=\s*([^)]+?))?\s*\)\s*\{\s*viewModel\.(\w+)\(it\)\s*\}'

    def replace_switch(match):
        title = match.group(1)
        subtitle = match.group(2)
        checked = match.group(3)
        enabled = match.group(4)
        method = match.group(5)

        result = f'SettingsSwitchRow(\n'
        result += f'                    title = "{title}",\n'
        result += f'                    subtitle = "{subtitle}",\n'
        result += f'                    checked = {checked},\n'
        result += f'                    onCheckedChange = {{ v -> viewModel.{method}(v) }}'
        if enabled:
            result += f',\n                    enabled = {enabled.strip()}'
        result += f'\n                )'
        return result

    content = re.sub(pattern, replace_switch, content)

    if content != original:
        write_file(path, content)
        print(f"  ✅ Исправлен: {path}")
    else:
        print(f"  ⏭️  Не найдено для исправления")

# ═══════════════════════════════════════════════
# ГЛАВНАЯ ФУНКЦИЯ
# ═══════════════════════════════════════════════
def main():
    print("=" * 60)
    print("  YODOMessenger — Автоисправление ошибок сборки")
    print("  (версия для Android / Pydroid 3 — без git)")
    print("=" * 60)
    print()

    if not os.path.isdir("app"):
        print("❌ ОШИБКА: Папка 'app/' не найдена!")
        print("   Запустите скрипт из КОРНЯ проекта")
        sys.exit(1)

    print("1/7: Удаление дубликата NavGraph.kt")
    fix_1_delete_navgraph()
    print()

    print("2/7: Создание совместимого MainScreen.kt")
    fix_2_mainscreen()
    print()

    print("3/7: Патч YodoNavGraph.kt (ImageViewer)")
    fix_3_yodonavgraph()
    print()

    print("4/7: Патч MessageRepositoryImpl.kt (emptyList)")
    fix_4_message_repo()
    print()

    print("5/7: Патч ChatRepositoryImpl.kt (Tasks.whenAllSuccess)")
    fix_5_chat_repo()
    print()

    print("6/7: Патч SettingsViewModel.kt (лямбды)")
    fix_6_settings_vm()
    print()

    print("7/7: Патч SettingsScreen.kt (SettingsSwitchRow)")
    fix_7_settings_screen()
    print()

    print("=" * 60)
    print("  ✅ ГОТОВО!")
    print()
    print("  Теперь запушьте изменения на GitHub:")
    print("  (через GitHub Web, Termux, или другое приложение)")
    print("=" * 60)

if __name__ == "__main__":
    main()