#!/usr/bin/env python3
"""
YODOMessenger v0.2.0 — Автоматическое обновление
Запуск: python3 apply_update.py (в корне проекта)
"""

import os
import re
import shutil
import sys

BASE = "app/src/main/java/app/yodo/messenger"
RES = "app/src/main/res"
MANIFEST = "app/src/main/AndroidManifest.xml"

# ─────────────────────────────────────────────
# 1. ImageUtils.kt
# ─────────────────────────────────────────────
IMAGE_UTILS = r'''package app.yodo.messenger.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {

    private const val AVATAR_MAX_DIMENSION = 400
    private const val AVATAR_JPEG_QUALITY = 60
    private const val AVATAR_MAX_BASE64 = 700_000

    private const val CHAT_MAX_DIMENSION = 1080
    private const val CHAT_JPEG_QUALITY = 82
    private const val CHAT_MAX_BASE64 = 950_000

    fun compressAvatarToBase64(context: Context, uri: Uri): String? {
        return compressToBase64(context, uri, AVATAR_MAX_DIMENSION, AVATAR_JPEG_QUALITY, AVATAR_MAX_BASE64)
    }

    fun compressChatImageToBase64(context: Context, uri: Uri): String? {
        return compressToBase64(context, uri, CHAT_MAX_DIMENSION, CHAT_JPEG_QUALITY, CHAT_MAX_BASE64)
    }

    @Deprecated("Use compressChatImageToBase64 or compressAvatarToBase64")
    fun compressImageToBase64(context: Context, uri: Uri): String? {
        return compressChatImageToBase64(context, uri)
    }

    private fun compressToBase64(
        context: Context, uri: Uri,
        maxDimension: Int, quality: Int, maxBase64Length: Int
    ): String? {
        return try {
            val original = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null
            val resized = resizeBitmap(original, maxDimension)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (base64.length > maxBase64Length) null else base64
        } catch (e: Exception) { null }
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
    }

    fun decodeBase64ToBytes(base64: String): ByteArray? {
        return try { Base64.decode(base64, Base64.NO_WRAP) }
        catch (e: Exception) { null }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (width > height) {
            maxDimension to (maxDimension / ratio).toInt()
        } else {
            (maxDimension * ratio).toInt() to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
'''

# ─────────────────────────────────────────────
# 2. ColorTheme.kt (НОВЫЙ)
# ─────────────────────────────────────────────
COLOR_THEME = r'''package app.yodo.messenger.ui.theme

import androidx.compose.ui.graphics.Color

enum class ColorThemeName(val displayName: String) {
    BLUE("Синяя"), GREEN("Зелёная"), RED("Красная"), PURPLE("Фиолетовая"),
    ORANGE("Оранжевая"), PINK("Розовая"), TEAL("Бирюзовая"), BEIGE("Бежевая")
}

data class ColorTheme(
    val name: ColorThemeName,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val bubbleOwn: Color,
    val bubbleOwnText: Color,
    val bubbleOther: Color,
    val bubbleOtherText: Color,
    val backgroundDark: Color,
    val surfaceDark: Color,
    val backgroundLight: Color,
    val surfaceLight: Color,
    val onSurfaceDark: Color,
    val onSurfaceLight: Color,
    val online: Color = Color(0xFF22C55E),
    val error: Color = Color(0xFFEF4444)
)

val BlueTheme = ColorTheme(
    name = ColorThemeName.BLUE,
    primary = Color(0xFF3B82F6), secondary = Color(0xFF2563EB), accent = Color(0xFF06B6D4),
    bubbleOwn = Color(0xFF3B82F6), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF1E293B), bubbleOtherText = Color(0xFFE2E8F0),
    backgroundDark = Color(0xFF0F172A), surfaceDark = Color(0xFF1E293B),
    backgroundLight = Color(0xFFF8FAFC), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFE2E8F0), onSurfaceLight = Color(0xFF0F172A)
)

val GreenTheme = ColorTheme(
    name = ColorThemeName.GREEN,
    primary = Color(0xFF22C55E), secondary = Color(0xFF16A34A), accent = Color(0xFF4ADE80),
    bubbleOwn = Color(0xFF22C55E), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF1A2E1A), bubbleOtherText = Color(0xFFD1FAE5),
    backgroundDark = Color(0xFF0A1A0A), surfaceDark = Color(0xFF1A2E1A),
    backgroundLight = Color(0xFFF0FDF4), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFD1FAE5), onSurfaceLight = Color(0xFF0A1A0A)
)

val RedTheme = ColorTheme(
    name = ColorThemeName.RED,
    primary = Color(0xFFEF4444), secondary = Color(0xFFDC2626), accent = Color(0xFFF87171),
    bubbleOwn = Color(0xFFEF4444), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF2E1A1A), bubbleOtherText = Color(0xFFFEE2E2),
    backgroundDark = Color(0xFF1A0A0A), surfaceDark = Color(0xFF2E1A1A),
    backgroundLight = Color(0xFFFEF2F2), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFFEE2E2), onSurfaceLight = Color(0xFF1A0A0A)
)

val PurpleTheme = ColorTheme(
    name = ColorThemeName.PURPLE,
    primary = Color(0xFF8B5CF6), secondary = Color(0xFF7C3AED), accent = Color(0xFFA78BFA),
    bubbleOwn = Color(0xFF8B5CF6), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF231A3E), bubbleOtherText = Color(0xFFEDE9FE),
    backgroundDark = Color(0xFF130A2A), surfaceDark = Color(0xFF231A3E),
    backgroundLight = Color(0xFFF5F3FF), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFEDE9FE), onSurfaceLight = Color(0xFF130A2A)
)

val OrangeTheme = ColorTheme(
    name = ColorThemeName.ORANGE,
    primary = Color(0xFFF97316), secondary = Color(0xFFEA580C), accent = Color(0xFFFB923C),
    bubbleOwn = Color(0xFFF97316), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF2E2010), bubbleOtherText = Color(0xFFFFEDD5),
    backgroundDark = Color(0xFF1A1005), surfaceDark = Color(0xFF2E2010),
    backgroundLight = Color(0xFFFFF7ED), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFFFEDD5), onSurfaceLight = Color(0xFF1A1005)
)

val PinkTheme = ColorTheme(
    name = ColorThemeName.PINK,
    primary = Color(0xFFEC4899), secondary = Color(0xFFDB2777), accent = Color(0xFFF472B6),
    bubbleOwn = Color(0xFFEC4899), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF2E1A24), bubbleOtherText = Color(0xFFFCE7F3),
    backgroundDark = Color(0xFF1A0A12), surfaceDark = Color(0xFF2E1A24),
    backgroundLight = Color(0xFFFDF2F8), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFFCE7F3), onSurfaceLight = Color(0xFF1A0A12)
)

val TealTheme = ColorTheme(
    name = ColorThemeName.TEAL,
    primary = Color(0xFF14B8A6), secondary = Color(0xFF0D9488), accent = Color(0xFF2DD4BF),
    bubbleOwn = Color(0xFF14B8A6), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF102E2A), bubbleOtherText = Color(0xFFCCFBF1),
    backgroundDark = Color(0xFF051A17), surfaceDark = Color(0xFF102E2A),
    backgroundLight = Color(0xFFF0FDFA), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFCCFBF1), onSurfaceLight = Color(0xFF051A17)
)

val BeigeTheme = ColorTheme(
    name = ColorThemeName.BEIGE,
    primary = Color(0xFFD97706), secondary = Color(0xFFB45309), accent = Color(0xFFFBBF24),
    bubbleOwn = Color(0xFFD97706), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFFF5F0E8), bubbleOtherText = Color(0xFF44403C),
    backgroundDark = Color(0xFF1C1917), surfaceDark = Color(0xFF292524),
    backgroundLight = Color(0xFFFAF8F5), surfaceLight = Color(0xFFFFFBF5),
    onSurfaceDark = Color(0xFFE7E5E4), onSurfaceLight = Color(0xFF292524)
)

val allColorThemes = listOf(
    BlueTheme, GreenTheme, RedTheme, PurpleTheme,
    OrangeTheme, PinkTheme, TealTheme, BeigeTheme
)

fun getColorThemeByName(name: String): ColorTheme {
    return allColorThemes.find { it.name.name == name } ?: BlueTheme
}
'''

# ─────────────────────────────────────────────
# 3. Color.kt
# ─────────────────────────────────────────────
COLOR_KT = r'''package app.yodo.messenger.ui.theme

import androidx.compose.ui.graphics.Color

val YodoPrimary = Color(0xFF3B82F6)
val YodoSecondary = Color(0xFF2563EB)
val YodoAccent = Color(0xFF06B6D4)
val YodoBackground = Color(0xFF0F172A)
val YodoSurface = Color(0xFF1E293B)
val YodoOnSurface = Color(0xFFE2E8F0)
val YodoSuccess = Color(0xFF22C55E)
val YodoError = Color(0xFFEF4444)

val YodoBackgroundLight = Color(0xFFF8FAFC)
val YodoSurfaceLight = Color(0xFFFFFFFF)
val YodoOnSurfaceLight = Color(0xFF0F172A)

val YodoOnline = Color(0xFF22C55E)
val YodoOffline = Color(0xFF94A3B8)
val YodoBadge = Color(0xFF3B82F6)
'''

# ─────────────────────────────────────────────
# 4. Theme.kt
# ─────────────────────────────────────────────
THEME_KT = r'''package app.yodo.messenger.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalColorTheme = compositionLocalOf { BlueTheme }

@Composable
fun YodoMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = BlueTheme,
    dynamicColor: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = colorTheme.primary,
            secondary = colorTheme.secondary,
            tertiary = colorTheme.accent,
            background = colorTheme.backgroundDark,
            surface = colorTheme.surfaceDark,
            onBackground = colorTheme.onSurfaceDark,
            onSurface = colorTheme.onSurfaceDark,
            error = colorTheme.error
        )
        else -> lightColorScheme(
            primary = colorTheme.primary,
            secondary = colorTheme.secondary,
            tertiary = colorTheme.accent,
            background = colorTheme.backgroundLight,
            surface = colorTheme.surfaceLight,
            onBackground = colorTheme.onSurfaceLight,
            onSurface = colorTheme.onSurfaceLight,
            error = colorTheme.error
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val typography = remember(fontScale) { scaledTypography(fontScale) }

    CompositionLocalProvider(LocalColorTheme provides colorTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

private fun scaledTypography(scale: Float): Typography {
    if (scale == 1f) return YodoTypography
    return YodoTypography.copy(
        displayLarge = YodoTypography.displayLarge.copy(fontSize = YodoTypography.displayLarge.fontSize * scale),
        headlineLarge = YodoTypography.headlineLarge.copy(fontSize = YodoTypography.headlineLarge.fontSize * scale),
        titleLarge = YodoTypography.titleLarge.copy(fontSize = YodoTypography.titleLarge.fontSize * scale),
        bodyLarge = YodoTypography.bodyLarge.copy(fontSize = YodoTypography.bodyLarge.fontSize * scale),
        bodyMedium = YodoTypography.bodyMedium.copy(fontSize = YodoTypography.bodyMedium.fontSize * scale),
        labelLarge = YodoTypography.labelLarge.copy(fontSize = YodoTypography.labelLarge.fontSize * scale),
        labelMedium = YodoTypography.labelMedium.copy(fontSize = YodoTypography.labelMedium.fontSize * scale)
    )
}
'''

# ─────────────────────────────────────────────
# 5. ThemePreferences.kt
# ─────────────────────────────────────────────
THEME_PREFS = r'''package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "yodo_settings")

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme_enabled")
    private val useSystemThemeKey = booleanPreferencesKey("use_system_theme")
    private val colorThemeKey = stringPreferencesKey("color_theme_name")

    val useSystemTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[useSystemThemeKey] ?: true
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[darkThemeKey] ?: true
    }

    val colorThemeName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[colorThemeKey] ?: "BLUE"
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[darkThemeKey] = enabled
            prefs[useSystemThemeKey] = false
        }
    }

    suspend fun setUseSystemTheme() {
        context.dataStore.edit { prefs ->
            prefs[useSystemThemeKey] = true
        }
    }

    suspend fun setColorTheme(name: String) {
        context.dataStore.edit { prefs ->
            prefs[colorThemeKey] = name
        }
    }
}
'''

# ─────────────────────────────────────────────
# 6. UserSettingsPreferences.kt
# ─────────────────────────────────────────────
USER_SETTINGS_PREFS = r'''package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "yodo_user_settings")

enum class FontSize(val scale: Float) {
    SMALL(0.9f), MEDIUM(1.0f), LARGE(1.15f)
}

@Singleton
class UserSettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sendOnEnterKey = booleanPreferencesKey("send_on_enter")
    private val fontSizeKey = stringPreferencesKey("font_size")
    private val showOnlineStatusKey = booleanPreferencesKey("show_online_status")
    private val showReadReceiptsKey = booleanPreferencesKey("show_read_receipts")
    private val autoDownloadImagesKey = booleanPreferencesKey("auto_download_images")
    private val notificationSoundKey = booleanPreferencesKey("notification_sound")
    private val notificationVibrationKey = booleanPreferencesKey("notification_vibration")
    private val muteAllNotificationsKey = booleanPreferencesKey("mute_all_notifications")
    private val hideKeyboardOnSendKey = booleanPreferencesKey("hide_keyboard_on_send")

    val sendOnEnter: Flow<Boolean> = context.settingsDataStore.data.map { it[sendOnEnterKey] ?: true }
    val fontSize: Flow<FontSize> = context.settingsDataStore.data.map { prefs ->
        prefs[fontSizeKey]?.let { raw -> runCatching { FontSize.valueOf(raw) }.getOrNull() } ?: FontSize.MEDIUM
    }
    val showOnlineStatus: Flow<Boolean> = context.settingsDataStore.data.map { it[showOnlineStatusKey] ?: true }
    val showReadReceipts: Flow<Boolean> = context.settingsDataStore.data.map { it[showReadReceiptsKey] ?: true }
    val autoDownloadImages: Flow<Boolean> = context.settingsDataStore.data.map { it[autoDownloadImagesKey] ?: true }
    val notificationSound: Flow<Boolean> = context.settingsDataStore.data.map { it[notificationSoundKey] ?: true }
    val notificationVibration: Flow<Boolean> = context.settingsDataStore.data.map { it[notificationVibrationKey] ?: true }
    val muteAllNotifications: Flow<Boolean> = context.settingsDataStore.data.map { it[muteAllNotificationsKey] ?: false }
    val hideKeyboardOnSend: Flow<Boolean> = context.settingsDataStore.data.map { it[hideKeyboardOnSendKey] ?: true }

    suspend fun setSendOnEnter(enabled: Boolean) { context.settingsDataStore.edit { it[sendOnEnterKey] = enabled } }
    suspend fun setFontSize(size: FontSize) { context.settingsDataStore.edit { it[fontSizeKey] = size.name } }
    suspend fun setShowOnlineStatus(enabled: Boolean) { context.settingsDataStore.edit { it[showOnlineStatusKey] = enabled } }
    suspend fun setShowReadReceipts(enabled: Boolean) { context.settingsDataStore.edit { it[showReadReceiptsKey] = enabled } }
    suspend fun setAutoDownloadImages(enabled: Boolean) { context.settingsDataStore.edit { it[autoDownloadImagesKey] = enabled } }
    suspend fun setNotificationSound(enabled: Boolean) { context.settingsDataStore.edit { it[notificationSoundKey] = enabled } }
    suspend fun setNotificationVibration(enabled: Boolean) { context.settingsDataStore.edit { it[notificationVibrationKey] = enabled } }
    suspend fun setMuteAllNotifications(enabled: Boolean) { context.settingsDataStore.edit { it[muteAllNotificationsKey] = enabled } }
    suspend fun setHideKeyboardOnSend(enabled: Boolean) { context.settingsDataStore.edit { it[hideKeyboardOnSendKey] = enabled } }
}
'''

# ─────────────────────────────────────────────
# 7. YodoUser.kt
# ─────────────────────────────────────────────
YODO_USER = r'''package app.yodo.messenger.domain.model

data class YodoUser(
    val uid: String,
    val displayName: String,
    val username: String? = null,
    val bio: String? = null,
    val email: String?,
    val phoneNumber: String?,
    val photoUrl: String?,
    val avatarBase64: String? = null,
    val nickname: String? = null,
    val aboutMe: String? = null,
    val birthDate: String? = null,
    val location: String? = null,
    val website: String? = null,
    val showBirthDate: Boolean = true,
    val showAboutMe: Boolean = true,
    val showLocation: Boolean = true,
    val showWebsite: Boolean = true,
    val showPhoneNumber: Boolean = false,
    val showEmail: Boolean = false
)
'''

# ─────────────────────────────────────────────
# 8. Message.kt
# ─────────────────────────────────────────────
MESSAGE = r'''package app.yodo.messenger.domain.model

enum class MessageStatus { SENDING, SENT, READ, FAILED }

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val status: MessageStatus,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val imageBase64: String? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val forwardedFromSenderName: String? = null,
    val isPinned: Boolean = false
)
'''

# ─────────────────────────────────────────────
# 9. UserRepository.kt
# ─────────────────────────────────────────────
USER_REPO = r'''package app.yodo.messenger.domain.repository

import android.net.Uri
import app.yodo.messenger.domain.model.YodoUser
import kotlinx.coroutines.flow.Flow

sealed class ProfileUpdateResult {
    data object Success : ProfileUpdateResult()
    data class Error(val message: String) : ProfileUpdateResult()
}

interface UserRepository {
    fun observeCurrentUser(): Flow<YodoUser?>
    suspend fun updateDisplayName(name: String): ProfileUpdateResult
    suspend fun updateBio(bio: String): ProfileUpdateResult
    suspend fun updateUsername(username: String): ProfileUpdateResult
    suspend fun uploadAvatar(imageUri: Uri): ProfileUpdateResult
    suspend fun searchUsers(query: String): List<YodoUser>
    suspend fun getUserById(uid: String): YodoUser?
    suspend fun updateNickname(nickname: String): ProfileUpdateResult
    suspend fun updateAboutMe(aboutMe: String): ProfileUpdateResult
    suspend fun updateBirthDate(birthDate: String): ProfileUpdateResult
    suspend fun updateLocation(location: String): ProfileUpdateResult
    suspend fun updateWebsite(website: String): ProfileUpdateResult
    suspend fun updatePrivacySettings(
        showBirthDate: Boolean, showAboutMe: Boolean, showLocation: Boolean,
        showWebsite: Boolean, showPhoneNumber: Boolean, showEmail: Boolean
    ): ProfileUpdateResult
    suspend fun blockUser(uid: String): ProfileUpdateResult
    suspend fun unblockUser(uid: String): ProfileUpdateResult
    suspend fun getBlockedUsers(): List<YodoUser>
    suspend fun isUserBlocked(uid: String): Boolean
}
'''

# ─────────────────────────────────────────────
# 10. ChatRepository.kt
# ─────────────────────────────────────────────
CHAT_REPO = r'''package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.YodoUser
import kotlinx.coroutines.flow.Flow

sealed class CreateChatResult {
    data class Success(val chatId: String) : CreateChatResult()
    data class Error(val message: String) : CreateChatResult()
}

data class ChatInfo(
    val title: String,
    val otherUserId: String?,
    val type: String,
    val avatarUrl: String? = null,
    val avatarBase64: String? = null,
    val otherUserPhotoUrl: String? = null,
    val otherUserAvatarBase64: String? = null
)

sealed class ChatListResult {
    data class Success(val chats: List<ChatPreview>) : ChatListResult()
    data class Error(val message: String) : ChatListResult()
}

data class GroupInfo(
    val title: String,
    val members: List<YodoUser>,
    val createdBy: String?
)

interface ChatRepository {
    fun observeChatList(): Flow<ChatListResult>
    suspend fun createOrGetPrivateChat(otherUserId: String): CreateChatResult
    suspend fun createGroupChat(title: String, memberIds: List<String>): CreateChatResult
    suspend fun getChatInfo(chatId: String): ChatInfo?
    suspend fun getGroupInfo(chatId: String): GroupInfo?
    suspend fun leaveGroup(chatId: String)
    suspend fun togglePinChat(chatId: String)
    suspend fun toggleMuteChat(chatId: String)
    suspend fun clearChatHistory(chatId: String)
    suspend fun deleteChat(chatId: String)
    suspend fun getOtherUserAvatar(chatId: String): Pair<String?, String?>?
}
'''

# ─────────────────────────────────────────────
# 11. MessageRepository.kt
# ─────────────────────────────────────────────
MSG_REPO = r'''package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.Message
import kotlinx.coroutines.flow.Flow

sealed class SendMessageResult {
    data object Success : SendMessageResult()
    data class Error(val message: String) : SendMessageResult()
}

data class ReplyContext(
    val messageId: String,
    val senderName: String,
    val text: String
)

interface MessageRepository {
    fun observeMessages(chatId: String): Flow<List<Message>>
    suspend fun sendMessage(chatId: String, text: String, replyTo: ReplyContext? = null): SendMessageResult
    suspend fun sendImageMessage(chatId: String, imageBase64: String, caption: String = ""): SendMessageResult
    suspend fun forwardMessage(targetChatId: String, originalMessage: Message, fromSenderName: String): SendMessageResult
    suspend fun editMessage(chatId: String, messageId: String, newText: String): SendMessageResult
    suspend fun deleteMessage(chatId: String, messageId: String): SendMessageResult
    suspend fun markChatAsRead(chatId: String)
    suspend fun toggleReaction(chatId: String, messageId: String, emoji: String)
    suspend fun togglePinMessage(chatId: String, messageId: String): SendMessageResult
    fun observePinnedMessages(chatId: String): Flow<List<Message>>
    suspend fun toggleBookmark(messageId: String, chatId: String)
    fun observeBookmarkedMessages(): Flow<List<Message>>
    suspend fun exportChatHistory(chatId: String): String
}
'''

# ─────────────────────────────────────────────
# 12. UserRepositoryImpl.kt
# ─────────────────────────────────────────────
USER_REPO_IMPL = r'''package app.yodo.messenger.data.repository

import android.net.Uri
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ProfileUpdateResult
import app.yodo.messenger.domain.repository.UserRepository
import app.yodo.messenger.util.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: android.content.Context
) : UserRepository {

    override fun observeCurrentUser(): Flow<YodoUser?> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) { trySend(null); close(); return@callbackFlow }
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(YodoUser(
                        uid = uid,
                        displayName = firebaseAuth.currentUser?.displayName.orEmpty(),
                        email = firebaseAuth.currentUser?.email,
                        phoneNumber = firebaseAuth.currentUser?.phoneNumber,
                        photoUrl = firebaseAuth.currentUser?.photoUrl?.toString()
                    ))
                    return@addSnapshotListener
                }
                trySend(snapshot.toYodoUser(uid))
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateDisplayName(name: String): ProfileUpdateResult {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return ProfileUpdateResult.Error("Имя не может быть пустым")
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(trimmed).build()).await()
            firestore.collection("users").document(user.uid)
                .update(mapOf("displayName" to trimmed, "displayNameLowercase" to trimmed.lowercase())).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить имя") }
    }

    override suspend fun updateBio(bio: String): ProfileUpdateResult {
        val trimmed = bio.trim().take(150)
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid).update("bio", trimmed).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить описание") }
    }

    override suspend fun updateUsername(username: String): ProfileUpdateResult {
        val normalized = username.trim().removePrefix("@").lowercase()
        if (normalized.isBlank()) return ProfileUpdateResult.Error("Введите username")
        if (!normalized.matches(Regex("^[a-z0-9_]{3,20}$")))
            return ProfileUpdateResult.Error("Username: 3-20 символов, только латиница, цифры и \"_\"")
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.runTransaction { transaction ->
                val usernameRef = firestore.collection("usernames").document(normalized)
                val usernameSnapshot = transaction.get(usernameRef)
                if (usernameSnapshot.exists() && usernameSnapshot.getString("uid") != user.uid)
                    throw IllegalStateException("USERNAME_TAKEN")
                val userRef = firestore.collection("users").document(user.uid)
                val userSnapshot = transaction.get(userRef)
                val oldUsername = userSnapshot.getString("usernameLowercase")
                if (oldUsername != null && oldUsername != normalized)
                    transaction.delete(firestore.collection("usernames").document(oldUsername))
                transaction.set(usernameRef, mapOf("uid" to user.uid))
                transaction.update(userRef, mapOf("username" to normalized, "usernameLowercase" to normalized))
            }.await()
            ProfileUpdateResult.Success
        } catch (e: Exception) {
            val message = if (e.message?.contains("USERNAME_TAKEN") == true) "Этот username уже занят"
            else e.message ?: "Не удалось сохранить username"
            ProfileUpdateResult.Error(message)
        }
    }

    override suspend fun uploadAvatar(imageUri: Uri): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            val base64 = withContext(Dispatchers.Default) {
                ImageUtils.compressAvatarToBase64(context, imageUri)
            } ?: return ProfileUpdateResult.Error("Не удалось обработать изображение")
            firestore.collection("users").document(user.uid)
                .update(mapOf("avatarBase64" to base64, "avatarUrl" to null)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось загрузить фото") }
    }

    override suspend fun searchUsers(query: String): List<YodoUser> {
        val normalized = query.trim().removePrefix("@").lowercase()
        if (normalized.isBlank()) return emptyList()
        val currentUid = firebaseAuth.currentUser?.uid
        val usersRef = firestore.collection("users")
        return try {
            val byName = usersRef.orderBy("displayNameLowercase")
                .startAt(normalized).endAt(normalized + "\uf8ff").limit(20).get().await()
            val byUsername = usersRef.orderBy("usernameLowercase")
                .startAt(normalized).endAt(normalized + "\uf8ff").limit(20).get().await()
            (byName.documents + byUsername.documents)
                .distinctBy { it.id }.filter { it.id != currentUid }
                .map { it.toYodoUser(it.id) }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getUserById(uid: String): YodoUser? {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) doc.toYodoUser(uid) else null
        } catch (e: Exception) { null }
    }

    override suspend fun updateNickname(nickname: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid)
                .update("nickname", nickname.trim().take(50)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить ник") }
    }

    override suspend fun updateAboutMe(aboutMe: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid)
                .update("aboutMe", aboutMe.trim().take(300)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить «О себе»") }
    }

    override suspend fun updateBirthDate(birthDate: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid)
                .update("birthDate", birthDate.trim()).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить дату рождения") }
    }

    override suspend fun updateLocation(location: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid)
                .update("location", location.trim().take(100)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить местоположение") }
    }

    override suspend fun updateWebsite(website: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid)
                .update("website", website.trim().take(200)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить сайт") }
    }

    override suspend fun updatePrivacySettings(
        showBirthDate: Boolean, showAboutMe: Boolean, showLocation: Boolean,
        showWebsite: Boolean, showPhoneNumber: Boolean, showEmail: Boolean
    ): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid).update(mapOf(
                "showBirthDate" to showBirthDate, "showAboutMe" to showAboutMe,
                "showLocation" to showLocation, "showWebsite" to showWebsite,
                "showPhoneNumber" to showPhoneNumber, "showEmail" to showEmail
            )).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить настройки") }
    }

    override suspend fun blockUser(uid: String): ProfileUpdateResult {
        val me = firebaseAuth.currentUser?.uid ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(me)
                .update("blockedUsers", FieldValue.arrayUnion(uid)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось заблокировать") }
    }

    override suspend fun unblockUser(uid: String): ProfileUpdateResult {
        val me = firebaseAuth.currentUser?.uid ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(me)
                .update("blockedUsers", FieldValue.arrayRemove(uid)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось разблокировать") }
    }

    override suspend fun getBlockedUsers(): List<YodoUser> {
        val me = firebaseAuth.currentUser?.uid ?: return emptyList()
        return try {
            val myDoc = firestore.collection("users").document(me).get().await()
            val blockedIds = (myDoc.get("blockedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            blockedIds.mapNotNull { getUserById(it) }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun isUserBlocked(uid: String): Boolean {
        val me = firebaseAuth.currentUser?.uid ?: return false
        return try {
            val myDoc = firestore.collection("users").document(me).get().await()
            val blockedIds = (myDoc.get("blockedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            uid in blockedIds
        } catch (e: Exception) { false }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toYodoUser(uid: String) = YodoUser(
        uid = uid,
        displayName = getString("displayName") ?: "",
        username = getString("username"),
        bio = getString("bio"),
        email = getString("email"),
        phoneNumber = getString("phoneNumber"),
        photoUrl = getString("avatarUrl"),
        avatarBase64 = getString("avatarBase64"),
        nickname = getString("nickname"),
        aboutMe = getString("aboutMe"),
        birthDate = getString("birthDate"),
        location = getString("location"),
        website = getString("website"),
        showBirthDate = getBoolean("showBirthDate") ?: true,
        showAboutMe = getBoolean("showAboutMe") ?: true,
        showLocation = getBoolean("showLocation") ?: true,
        showWebsite = getBoolean("showWebsite") ?: true,
        showPhoneNumber = getBoolean("showPhoneNumber") ?: false,
        showEmail = getBoolean("showEmail") ?: false
    )
}
'''

# ─────────────────────────────────────────────
# 13. ChatRepositoryImpl.kt
# ─────────────────────────────────────────────
CHAT_REPO_IMPL = r'''package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.ChatType
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatInfo
import app.yodo.messenger.domain.repository.ChatListResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import app.yodo.messenger.domain.repository.GroupInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ChatRepository {

    override fun observeChatList(): Flow<ChatListResult> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) { trySend(ChatListResult.Success(emptyList())); close(); return@callbackFlow }
        val query = firestore.collection("chats")
            .whereArrayContains("participantIds", uid)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(ChatListResult.Error(error.message ?: "Неизвестная ошибка Firestore"))
                return@addSnapshotListener
            }
            val chats = snapshot?.documents.orEmpty().mapNotNull { doc ->
                try {
                    val unreadCounts = doc.get("unreadCounts") as? Map<*, *>
                    val unreadForMe = (unreadCounts?.get(uid) as? Long)?.toInt() ?: 0
                    val titles = doc.get("titles") as? Map<*, *>
                    val personalizedTitle = titles?.get(uid) as? String
                    val title = personalizedTitle ?: doc.getString("title") ?: "Без названия"
                    val pinnedMap = doc.get("pinned") as? Map<*, *>
                    val mutedMap = doc.get("muted") as? Map<*, *>
                    ChatPreview(
                        chatId = doc.id, title = title,
                        avatarUrl = doc.getString("avatarUrl"),
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
                        unreadCount = unreadForMe,
                        isOnline = doc.getBoolean("isOnline") ?: false,
                        type = doc.getString("type")?.let { raw ->
                            runCatching { ChatType.valueOf(raw) }.getOrDefault(ChatType.PRIVATE)
                        } ?: ChatType.PRIVATE,
                        isPinned = pinnedMap?.get(uid) as? Boolean ?: false,
                        isMuted = mutedMap?.get(uid) as? Boolean ?: false
                    )
                } catch (e: Exception) { null }
            }
            val sorted = chats.sortedByDescending { it.isPinned }
            trySend(ChatListResult.Success(sorted))
        }
        awaitClose { listener.remove() }
    }

    override suspend fun createOrGetPrivateChat(otherUserId: String): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid ?: return CreateChatResult.Error("Вы не авторизованы")
        if (uid == otherUserId) return CreateChatResult.Error("Нельзя создать чат с самим собой")
        return try {
            val existing = firestore.collection("chats")
                .whereArrayContains("participantIds", uid)
                .whereEqualTo("type", "PRIVATE").get().await()
            val existingChat = existing.documents.firstOrNull { doc ->
                val participants = doc.get("participantIds") as? List<*>
                participants?.contains(otherUserId) == true
            }
            if (existingChat != null) return CreateChatResult.Success(existingChat.id)
            val myDoc = firestore.collection("users").document(uid).get().await()
            val otherDoc = firestore.collection("users").document(otherUserId).get().await()
            val myName = myDoc.getString("displayName") ?: "Пользователь"
            val otherName = otherDoc.getString("displayName") ?: "Пользователь"
            val newChatRef = firestore.collection("chats").document()
            newChatRef.set(mapOf(
                "participantIds" to listOf(uid, otherUserId),
                "type" to "PRIVATE",
                "titles" to mapOf(uid to otherName, otherUserId to myName),
                "lastMessage" to "", "lastMessageTimestamp" to System.currentTimeMillis(),
                "unreadCounts" to mapOf(uid to 0, otherUserId to 0),
                "isOnline" to false
            )).await()
            CreateChatResult.Success(newChatRef.id)
        } catch (e: Exception) { CreateChatResult.Error(e.message ?: "Не удалось создать чат") }
    }

    override suspend fun createGroupChat(title: String, memberIds: List<String>): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid ?: return CreateChatResult.Error("Вы не авторизованы")
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return CreateChatResult.Error("Введите название группы")
        val allParticipants = (memberIds + uid).distinct()
        if (allParticipants.size < 3) return CreateChatResult.Error("Выберите хотя бы 2 участников")
        return try {
            val newChatRef = firestore.collection("chats").document()
            newChatRef.set(mapOf(
                "participantIds" to allParticipants, "type" to "GROUP",
                "title" to trimmedTitle, "lastMessage" to "",
                "lastMessageTimestamp" to System.currentTimeMillis(),
                "unreadCounts" to allParticipants.associateWith { 0 },
                "isOnline" to false, "createdBy" to uid
            )).await()
            CreateChatResult.Success(newChatRef.id)
        } catch (e: Exception) { CreateChatResult.Error(e.message ?: "Не удалось создать группу") }
    }

    override suspend fun getChatInfo(chatId: String): ChatInfo? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (!doc.exists()) return null
            val type = doc.getString("type") ?: "PRIVATE"
            val titles = doc.get("titles") as? Map<*, *>
            val personalizedTitle = titles?.get(uid) as? String
            val title = personalizedTitle ?: doc.getString("title") ?: "Без названия"
            val otherUserId = if (type == "PRIVATE") {
                val participantIds = doc.get("participantIds") as? List<*>
                participantIds?.filterIsInstance<String>()?.firstOrNull { it != uid }
            } else null
            var otherPhotoUrl: String? = null
            var otherAvatarBase64: String? = null
            if (otherUserId != null) {
                val otherDoc = firestore.collection("users").document(otherUserId).get().await()
                otherPhotoUrl = otherDoc.getString("avatarUrl")
                otherAvatarBase64 = otherDoc.getString("avatarBase64")
            }
            ChatInfo(
                title = title, otherUserId = otherUserId, type = type,
                avatarUrl = doc.getString("avatarUrl"), avatarBase64 = null,
                otherUserPhotoUrl = otherPhotoUrl, otherUserAvatarBase64 = otherAvatarBase64
            )
        } catch (e: Exception) { null }
    }

    override suspend fun getGroupInfo(chatId: String): GroupInfo? {
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (!doc.exists()) return null
            val title = doc.getString("title") ?: "Группа"
            val createdBy = doc.getString("createdBy")
            val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val memberDocs = if (participantIds.isNotEmpty()) {
                val tasks = participantIds.map { firestore.collection("users").document(it).get() }
                com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(tasks).await()
            } else emptyList()
            val members = memberDocs.mapNotNull { memberDoc ->
                if (!memberDoc.exists()) return@mapNotNull null
                YodoUser(
                    uid = memberDoc.id,
                    displayName = memberDoc.getString("displayName") ?: "Пользователь",
                    username = memberDoc.getString("username"),
                    bio = memberDoc.getString("bio"),
                    email = memberDoc.getString("email"),
                    phoneNumber = memberDoc.getString("phoneNumber"),
                    photoUrl = memberDoc.getString("avatarUrl"),
                    avatarBase64 = memberDoc.getString("avatarBase64")
                )
            }
            GroupInfo(title = title, members = members, createdBy = createdBy)
        } catch (e: Exception) { null }
    }

    override suspend fun leaveGroup(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("chats").document(chatId)
                .update("participantIds", FieldValue.arrayRemove(uid)).await()
        } catch (e: Exception) { }
    }

    override suspend fun togglePinChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val pinnedMap = snapshot.get("pinned") as? Map<*, *>
            val currentlyPinned = pinnedMap?.get(uid) as? Boolean ?: false
            chatRef.update("pinned.$uid", !currentlyPinned).await()
        } catch (e: Exception) { }
    }

    override suspend fun toggleMuteChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val mutedMap = snapshot.get("muted") as? Map<*, *>
            val currentlyMuted = mutedMap?.get(uid) as? Boolean ?: false
            chatRef.update("muted.$uid", !currentlyMuted).await()
        } catch (e: Exception) { }
    }

    override suspend fun clearChatHistory(chatId: String) {
        try {
            val messagesRef = firestore.collection("chats").document(chatId).collection("messages")
            val snapshot = messagesRef.get().await()
            val batch = firestore.batch()
            snapshot.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
            firestore.collection("chats").document(chatId).update(
                mapOf("lastMessage" to "", "lastMessageTimestamp" to System.currentTimeMillis())
            ).await()
        } catch (e: Exception) { throw e }
    }

    override suspend fun deleteChat(chatId: String) {
        try {
            val messagesRef = firestore.collection("chats").document(chatId).collection("messages")
            val snapshot = messagesRef.get().await()
            val batch = firestore.batch()
            snapshot.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
            firestore.collection("chats").document(chatId).delete().await()
        } catch (e: Exception) { throw e }
    }

    override suspend fun getOtherUserAvatar(chatId: String): Pair<String?, String?>? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: return null
            val otherId = participantIds.firstOrNull { it != uid } ?: return null
            val otherDoc = firestore.collection("users").document(otherId).get().await()
            Pair(otherDoc.getString("avatarUrl"), otherDoc.getString("avatarBase64"))
        } catch (e: Exception) { null }
    }
}
'''

# ─────────────────────────────────────────────
# 14. MessageRepositoryImpl.kt
# ─────────────────────────────────────────────
MSG_REPO_IMPL = r'''package app.yodo.messenger.data.repository

import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.ReplyContext
import app.yodo.messenger.domain.repository.SendMessageResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val userSettingsPreferences: UserSettingsPreferences
) : MessageRepository {

    override fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val messages = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    try {
                        val reactionsRaw = doc.get("reactions") as? Map<*, *>
                        val reactions = reactionsRaw?.mapNotNull { (key, value) ->
                            val emoji = key as? String ?: return@mapNotNull null
                            val uids = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            emoji to uids
                        }?.toMap() ?: emptyMap()
                        Message(
                            id = doc.id, chatId = chatId,
                            senderId = doc.getString("senderId") ?: return@mapNotNull null,
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            status = doc.getString("status")?.let { raw ->
                                runCatching { MessageStatus.valueOf(raw) }.getOrDefault(MessageStatus.SENT)
                            } ?: MessageStatus.SENT,
                            replyToMessageId = doc.getString("replyToMessageId"),
                            replyToSenderName = doc.getString("replyToSenderName"),
                            replyToText = doc.getString("replyToText"),
                            reactions = reactions,
                            imageBase64 = doc.getString("imageBase64"),
                            isEdited = doc.getBoolean("isEdited") ?: false,
                            isDeleted = doc.getBoolean("isDeleted") ?: false,
                            forwardedFromSenderName = doc.getString("forwardedFromSenderName"),
                            isPinned = doc.getBoolean("isPinned") ?: false
                        )
                    } catch (e: Exception) { null }
                }
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    private suspend fun sendRawMessage(chatId: String, data: MutableMap<String, Any?>): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val chatSnapshot = chatRef.get().await()
            val participantIds = chatSnapshot.get("participantIds") as? List<*> ?: emptyList()
            val now = System.currentTimeMillis()
            data["senderId"] = uid
            data["timestamp"] = now
            data["status"] = "SENT"
            data["notified"] = false
            chatRef.collection("messages").add(data).await()
            val previewText = (data["text"] as? String)?.takeIf { it.isNotBlank() }
                ?: if (data.containsKey("imageBase64")) "📷 Фото" else ""
            val unreadUpdates = mutableMapOf<String, Any?>(
                "lastMessage" to previewText, "lastMessageTimestamp" to now
            )
            participantIds.filterIsInstance<String>().filter { it != uid }.forEach { otherUid ->
                unreadUpdates["unreadCounts.$otherUid"] = FieldValue.increment(1)
            }
            chatRef.update(unreadUpdates).await()
            SendMessageResult.Success
        } catch (e: Exception) { SendMessageResult.Error(e.message ?: "Не удалось отправить сообщение") }
    }

    override suspend fun sendMessage(chatId: String, text: String, replyTo: ReplyContext?): SendMessageResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendMessageResult.Error("Сообщение не может быть пустым")
        val data = mutableMapOf<String, Any?>("text" to trimmed)
        if (replyTo != null) {
            data["replyToMessageId"] = replyTo.messageId
            data["replyToSenderName"] = replyTo.senderName
            data["replyToText"] = replyTo.text
        }
        return sendRawMessage(chatId, data)
    }

    override suspend fun sendImageMessage(chatId: String, imageBase64: String, caption: String): SendMessageResult {
        val data = mutableMapOf<String, Any?>("imageBase64" to imageBase64, "text" to caption.trim())
        return sendRawMessage(chatId, data)
    }

    override suspend fun forwardMessage(targetChatId: String, originalMessage: Message, fromSenderName: String): SendMessageResult {
        val data = mutableMapOf<String, Any?>("text" to originalMessage.text, "forwardedFromSenderName" to fromSenderName)
        originalMessage.imageBase64?.let { data["imageBase64"] = it }
        return sendRawMessage(targetChatId, data)
    }

    override suspend fun editMessage(chatId: String, messageId: String, newText: String): SendMessageResult {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return SendMessageResult.Error("Сообщение не может быть пустым")
        return try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(mapOf("text" to trimmed, "isEdited" to true)).await()
            SendMessageResult.Success
        } catch (e: Exception) { SendMessageResult.Error(e.message ?: "Не удалось отредактировать") }
    }

    override suspend fun deleteMessage(chatId: String, messageId: String): SendMessageResult {
        return try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(mapOf("isDeleted" to true, "text" to "", "imageBase64" to FieldValue.delete())).await()
            SendMessageResult.Success
        } catch (e: Exception) { SendMessageResult.Error(e.message ?: "Не удалось удалить") }
    }

    override suspend fun markChatAsRead(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val showReadReceipts = userSettingsPreferences.showReadReceipts.first()
            if (showReadReceipts) {
                val messagesRef = firestore.collection("chats").document(chatId).collection("messages")
                val fromOthers = messagesRef.whereNotEqualTo("senderId", uid).get().await()
                val unreadDocs = fromOthers.documents.filter { it.getString("status") != "READ" }
                if (unreadDocs.isNotEmpty()) {
                    val batch = firestore.batch()
                    unreadDocs.forEach { doc -> batch.update(doc.reference, "status", "READ") }
                    batch.commit().await()
                }
            }
            firestore.collection("chats").document(chatId).update("unreadCounts.$uid", 0).await()
        } catch (e: Exception) { }
    }

    override suspend fun toggleReaction(chatId: String, messageId: String, emoji: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val messageRef = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(messageRef)
                val reactionsRaw = snapshot.get("reactions") as? Map<*, *>
                val currentUids = (reactionsRaw?.get(emoji) as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val updatedUids = if (uid in currentUids) currentUids - uid else currentUids + uid
                transaction.update(messageRef, "reactions.$emoji", updatedUids)
            }.await()
        } catch (e: Exception) { }
    }

    override suspend fun togglePinMessage(chatId: String, messageId: String): SendMessageResult {
        return try {
            val ref = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            val doc = ref.get().await()
            val isPinned = doc.getBoolean("isPinned") ?: false
            ref.update("isPinned", !isPinned).await()
            SendMessageResult.Success
        } catch (e: Exception) { SendMessageResult.Error(e.message ?: "Не удалось закрепить") }
    }

    override fun observePinnedMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("messages")
            .whereEqualTo("isPinned", true)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val messages = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    try {
                        Message(
                            id = doc.id, chatId = chatId,
                            senderId = doc.getString("senderId") ?: return@mapNotNull null,
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            status = MessageStatus.SENT, isPinned = true
                        )
                    } catch (e: Exception) { null }
                }
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleBookmark(messageId: String, chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val bookmarkRef = firestore.collection("users").document(uid)
                .collection("bookmarks").document(messageId)
            val doc = bookmarkRef.get().await()
            if (doc.exists()) {
                bookmarkRef.delete().await()
            } else {
                val msgDoc = firestore.collection("chats").document(chatId)
                    .collection("messages").document(messageId).get().await()
                bookmarkRef.set(mapOf(
                    "messageId" to messageId, "chatId" to chatId,
                    "senderId" to (msgDoc.getString("senderId") ?: ""),
                    "text" to (msgDoc.getString("text") ?: ""),
                    "timestamp" to (msgDoc.getLong("timestamp") ?: 0L),
                    "imageBase64" to msgDoc.getString("imageBase64"),
                    "savedAt" to System.currentTimeMillis()
                )).await()
            }
        } catch (e: Exception) { }
    }

    override fun observeBookmarkedMessages(): Flow<List<Message>> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) { trySend(emptyList()); close(); return@callbackFlow }
        val listener = firestore.collection("users").document(uid)
            .collection("bookmarks")
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val messages = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    try {
                        Message(
                            id = doc.getString("messageId") ?: doc.id,
                            chatId = doc.getString("chatId") ?: "",
                            senderId = doc.getString("senderId") ?: "",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            status = MessageStatus.SENT,
                            imageBase64 = doc.getString("imageBase64")
                        )
                    } catch (e: Exception) { null }
                }
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun exportChatHistory(chatId: String): String {
        return try {
            val snapshot = firestore.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING).get().await()
            val sb = StringBuilder()
            sb.appendLine("=== YODOMessenger — Экспорт чата ===")
            sb.appendLine("Дата экспорта: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date())}")
            sb.appendLine("=====================================")
            sb.appendLine()
            snapshot.documents.forEach { doc ->
                val senderId = doc.getString("senderId") ?: "?"
                val text = doc.getString("text") ?: ""
                val timestamp = doc.getLong("timestamp") ?: 0L
                val time = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru")).format(Date(timestamp))
                val hasImage = doc.getString("imageBase64") != null
                sb.appendLine("[$time] $senderId:")
                if (text.isNotBlank()) sb.appendLine("  $text")
                if (hasImage) sb.appendLine("  [📷 Фото]")
                sb.appendLine()
            }
            sb.toString()
        } catch (e: Exception) { "Ошибка экспорта: ${e.message}" }
    }
}
'''

# ─────────────────────────────────────────────
# 15. Routes.kt
# ─────────────────────────────────────────────
ROUTES = r'''package app.yodo.messenger.navigation

sealed class Routes(val route: String) {
    data object Welcome : Routes("welcome")
    data object Login : Routes("login")
    data object PhoneLogin : Routes("phone_login")
    data object Register : Routes("register")
    data object ChatList : Routes("chat_list")
    data object Search : Routes("search")
    data object CreateGroup : Routes("create_group")
    data object OfflineChat : Routes("offline_chat")
    data object NearbyPeople : Routes("nearby_people")
    data object ForwardMessage : Routes("forward_message")
    data object Profile : Routes("profile")
    data object Settings : Routes("settings")
    data object BlockedUsers : Routes("blocked_users")
    data object SavedMessages : Routes("saved_messages")

    data object GroupInfo : Routes("group_info/{chatId}") {
        fun createRoute(chatId: String) = "group_info/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }

    data object Chat : Routes("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }

    data object Call : Routes("call/{userId}") {
        fun createRoute(userId: String) = "call/$userId"
        const val ARG_USER_ID = "userId"
    }

    data object UserProfile : Routes("user_profile/{userId}") {
        fun createRoute(userId: String) = "user_profile/$userId"
        const val ARG_USER_ID = "userId"
    }

    data object ImageViewer : Routes("image_viewer/{imageBase64}/{senderName}/{timestamp}") {
        fun createRoute(imageBase64: String, senderName: String, timestamp: Long) =
            "image_viewer/$imageBase64/$senderName/$timestamp"
        const val ARG_IMAGE = "imageBase64"
        const val ARG_SENDER = "senderName"
        const val ARG_TIMESTAMP = "timestamp"
    }
}
'''

# ─────────────────────────────────────────────
# 16. NavGraph.kt
# ─────────────────────────────────────────────
NAV_GRAPH = r'''package app.yodo.messenger.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.yodo.messenger.features.auth.LoginScreen
import app.yodo.messenger.features.auth.PhoneLoginScreen
import app.yodo.messenger.features.auth.RegisterScreen
import app.yodo.messenger.features.auth.WelcomeScreen
import app.yodo.messenger.features.chats.ChatListScreen
import app.yodo.messenger.features.chats.ChatScreen
import app.yodo.messenger.features.chats.CreateGroupScreen
import app.yodo.messenger.features.chats.ForwardMessageScreen
import app.yodo.messenger.features.chats.GroupInfoScreen
import app.yodo.messenger.features.chats.ImageViewerScreen
import app.yodo.messenger.features.main.MainScreen
import app.yodo.messenger.features.nearby.NearbyPeopleScreen
import app.yodo.messenger.features.offline.OfflineChatScreen
import app.yodo.messenger.features.profile.ProfileScreen
import app.yodo.messenger.features.search.SearchScreen
import app.yodo.messenger.features.settings.SettingsScreen
import app.yodo.messenger.features.userprofile.UserProfileScreen

@Composable
fun YodoNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.Welcome.route) {
            WelcomeScreen(
                onLoginClick = { navController.navigate(Routes.Login.route) },
                onRegisterClick = { navController.navigate(Routes.Register.route) },
                onPhoneLoginClick = { navController.navigate(Routes.PhoneLogin.route) }
            )
        }

        composable(Routes.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.PhoneLogin.route) {
            PhoneLoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.ChatList.route) {
            MainScreen(
                onChatClick = { chatId -> navController.navigate(Routes.Chat.createRoute(chatId)) },
                onProfileClick = { navController.navigate(Routes.Profile.route) },
                onSettingsClick = { navController.navigate(Routes.Settings.route) },
                onSearchClick = { navController.navigate(Routes.Search.route) },
                onNearbyClick = { navController.navigate(Routes.NearbyPeople.route) },
                onOfflineClick = { navController.navigate(Routes.OfflineChat.route) }
            )
        }

        composable(
            route = Routes.Chat.route,
            arguments = listOf(navArgument(Routes.Chat.ARG_CHAT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString(Routes.Chat.ARG_CHAT_ID) ?: return@composable
            ChatScreen(
                chatId = chatId,
                onBackClick = { navController.popBackStack() },
                onOpenUserProfile = { userId -> navController.navigate(Routes.UserProfile.createRoute(userId)) },
                onOpenGroupInfo = { groupId -> navController.navigate(Routes.GroupInfo.createRoute(groupId)) },
                onForwardMessage = { navController.navigate(Routes.ForwardMessage.route) },
                onOpenImageViewer = { base64, sender, ts ->
                    navController.navigate(Routes.ImageViewer.createRoute(base64, sender, ts))
                }
            )
        }

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

        composable(Routes.Search.route) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onUserClick = { userId -> navController.navigate(Routes.UserProfile.createRoute(userId)) }
            )
        }

        composable(Routes.CreateGroup.route) {
            CreateGroupScreen(
                onBackClick = { navController.popBackStack() },
                onGroupCreated = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId)) {
                        popUpTo(Routes.ChatList.route)
                    }
                }
            )
        }

        composable(
            route = Routes.GroupInfo.route,
            arguments = listOf(navArgument(Routes.GroupInfo.ARG_CHAT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString(Routes.GroupInfo.ARG_CHAT_ID) ?: return@composable
            GroupInfoScreen(
                chatId = chatId,
                onBackClick = { navController.popBackStack() },
                onUserClick = { userId -> navController.navigate(Routes.UserProfile.createRoute(userId)) }
            )
        }

        composable(Routes.ForwardMessage.route) {
            ForwardMessageScreen(
                onBackClick = { navController.popBackStack() },
                onChatSelected = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId)) {
                        popUpTo(Routes.ChatList.route)
                    }
                }
            )
        }

        composable(Routes.Profile.route) {
            ProfileScreen(onBackClick = { navController.popBackStack() })
        }

        composable(
            route = Routes.UserProfile.route,
            arguments = listOf(navArgument(Routes.UserProfile.ARG_USER_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString(Routes.UserProfile.ARG_USER_ID) ?: return@composable
            UserProfileScreen(
                userId = userId,
                onBackClick = { navController.popBackStack() },
                onStartChat = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId)) {
                        popUpTo(Routes.ChatList.route)
                    }
                }
            )
        }

        composable(Routes.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.OfflineChat.route) {
            OfflineChatScreen(onBackClick = { navController.popBackStack() })
        }

        composable(Routes.NearbyPeople.route) {
            NearbyPeopleScreen(onBackClick = { navController.popBackStack() })
        }
    }
}
'''

# ─────────────────────────────────────────────
# 17. MainActivity.kt
# ─────────────────────────────────────────────
MAIN_ACTIVITY = r'''package app.yodo.messenger

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import app.yodo.messenger.data.local.ThemePreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.navigation.Routes
import app.yodo.messenger.navigation.YodoNavGraph
import app.yodo.messenger.ui.theme.getColorThemeByName
import app.yodo.messenger.ui.theme.YodoMessengerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var themePreferences: ThemePreferences
    @Inject lateinit var userSettingsPreferences: UserSettingsPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = if (authRepository.isLoggedIn()) {
            Routes.ChatList.route
        } else {
            Routes.Welcome.route
        }

        setContent {
            val isDarkTheme by themePreferences.isDarkTheme.collectAsState(initial = true)
            val colorThemeName by themePreferences.colorThemeName.collectAsState(initial = "BLUE")
            val fontSize by userSettingsPreferences.fontSize.collectAsState(
                initial = app.yodo.messenger.data.local.FontSize.MEDIUM
            )
            val colorTheme = getColorThemeByName(colorThemeName)

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            YodoMessengerTheme(
                darkTheme = isDarkTheme,
                colorTheme = colorTheme,
                fontScale = fontSize.scale
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    YodoNavGraph(navController = navController, startDestination = startDestination)
                }
            }
        }
    }
}
'''

# ─────────────────────────────────────────────
# 18. ImageViewerScreen.kt (НОВЫЙ)
# ─────────────────────────────────────────────
IMAGE_VIEWER = r'''package app.yodo.messenger.features.chats

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yodo.messenger.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImageViewerScreen(
    imageBase64: String,
    senderName: String,
    timestamp: Long,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isSaving by remember { mutableStateOf(false) }
    val bitmap = remember(imageBase64) { ImageUtils.decodeBase64ToBitmap(imageBase64) }
    val timeText = remember(timestamp) {
        if (timestamp > 0) SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(timestamp))
        else ""
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(senderName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        if (timeText.isNotBlank()) {
                            Text(timeText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            isSaving = true
                            val saved = saveImageToGallery(context, bitmap)
                            isSaving = false
                            snackbarHostState.showSnackbar(
                                if (saved) "Фото сохранено в галерею" else "Не удалось сохранить"
                            )
                        }
                    }) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        else Icon(Icons.Filled.Download, contentDescription = "Скачать", tint = Color.White)
                    }
                    IconButton(onClick = { scope.launch { shareImage(context, bitmap) } }) {
                        Icon(Icons.Filled.Share, contentDescription = "Поделиться", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.6f))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Просмотр фото",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                offset = Offset(x = offset.x + pan.x, y = offset.y + pan.y)
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offset.x, translationY = offset.y
                        )
                )
            } else {
                Text(
                    "Не удалось загрузить изображение",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (scale == 1f) {
                Text(
                    "Двумя пальцами — масштаб",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }
    }
}

private suspend fun saveImageToGallery(context: Context, bitmap: Bitmap?): Boolean {
    if (bitmap == null) return false
    return withContext(Dispatchers.IO) {
        try {
            val fileName = "YODO_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YODOMessenger")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                    }
                }
                uri != null
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YODOMessenger")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { fos -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos) }
                true
            }
        } catch (e: Exception) { false }
    }
}

private suspend fun shareImage(context: Context, bitmap: Bitmap?) {
    if (bitmap == null) return
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "shared_images")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "share_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { fos -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos) }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Поделиться фото"))
        } catch (e: Exception) { }
    }
}
'''

# ─────────────────────────────────────────────
# 19. file_paths.xml (НОВЫЙ)
# ─────────────────────────────────────────────
FILE_PATHS_XML = r'''<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shared_images" path="shared_images/" />
</paths>
'''

# ─────────────────────────────────────────────
# 20. MainScreen.kt
# ─────────────────────────────────────────────
MAIN_SCREEN = r'''package app.yodo.messenger.features.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNearbyClick: () -> Unit,
    onOfflineClick: () -> Unit
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
                    onClick = { onNearbyClick() },
                    icon = { Icon(Icons.Filled.NearMe, contentDescription = "Рядом") },
                    label = { Text("Рядом") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { onOfflineClick() },
                    icon = { Icon(Icons.Filled.WifiOff, contentDescription = "Офлайн") },
                    label = { Text("Офлайн") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { onProfileClick() },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Профиль") },
                    label = { Text("Профиль") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { onSettingsClick() },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Настройки") },
                    label = { Text("Настройки") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            ChatListScreen(
                onChatClick = onChatClick,
                onProfileClick = onProfileClick,
                onSettingsClick = onSettingsClick,
                onSearchClick = onSearchClick
            )
        }
    }
}
'''

# ─────────────────────────────────────────────
# 21. ChatScreen.kt
# ─────────────────────────────────────────────
CHAT_SCREEN = r'''package app.yodo.messenger.features.chats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

@Composable
fun ChatScreen(
    chatId: String,
    onBackClick: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenGroupInfo: (String) -> Unit,
    onForwardMessage: () -> Unit,
    onOpenImageViewer: (String, String, Long) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sendOnEnter by viewModel.sendOnEnter.collectAsState()
    val autoDownloadImages by viewModel.autoDownloadImages.collectAsState()
    val hideKeyboardOnSend by viewModel.hideKeyboardOnSend.collectAsState()
    val colorTheme = LocalColorTheme.current

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.initialDraft) {
        uiState.initialDraft?.let { if (inputText.isBlank()) inputText = it }
    }
    LaunchedEffect(uiState.editingMessage) {
        uiState.editingMessage?.let { inputText = it.text }
    }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val base64 = withContext(Dispatchers.Default) {
                    ImageUtils.compressChatImageToBase64(context, it)
                }
                if (base64 != null) viewModel.sendImage(base64)
                else snackbarHostState.showSnackbar("Не удалось обработать фото")
            }
        }
    }

    fun trySend() {
        if (inputText.isNotBlank()) {
            viewModel.sendMessage(inputText)
            inputText = ""
            if (hideKeyboardOnSend) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }
    }

    val displayedMessages = if (uiState.isSearchActive && uiState.searchQuery.isNotBlank()) {
        uiState.messages.filter { it.text.contains(uiState.searchQuery, ignoreCase = true) }
    } else {
        uiState.messages
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Поиск по сообщениям") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val otherUserId = uiState.otherUserId
                        Column(
                            modifier = when {
                                otherUserId != null -> Modifier.clickable { onOpenUserProfile(otherUserId) }
                                uiState.chatType == "GROUP" -> Modifier.clickable { onOpenGroupInfo(chatId) }
                                else -> Modifier
                            }
                        ) {
                            Text(text = uiState.chatTitle, style = MaterialTheme.typography.titleLarge)
                            val subtitle = when {
                                uiState.isOtherUserTyping -> "печатает..."
                                uiState.otherUserPresence?.isOnline == true -> "в сети"
                                uiState.otherUserPresence != null && uiState.otherUserPresence!!.lastSeenMillis > 0 ->
                                    "был(а) ${formatLastSeen(uiState.otherUserPresence!!.lastSeenMillis)}"
                                else -> null
                            }
                            subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (uiState.isOtherUserTyping) colorTheme.primary else Color.Gray
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = if (uiState.isSearchActive) { { viewModel.toggleSearch() } } else onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!uiState.isSearchActive) {
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск")
                        }
                        var showChatMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showChatMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                            }
                            DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Очистить историю") },
                                    onClick = { showChatMenu = false; viewModel.clearChatHistory() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Экспорт чата") },
                                    onClick = { showChatMenu = false; viewModel.exportChat(context) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить чат", color = MaterialTheme.colorScheme.error) },
                                    onClick = { showChatMenu = false; viewModel.deleteChat(); onBackClick() }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                if (uiState.pinnedMessages.isNotEmpty()) {
                    val pinned = uiState.pinnedMessages.first()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PushPin, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("Закреплённое сообщение", style = MaterialTheme.typography.labelSmall, color = colorTheme.primary)
                            Text(pinned.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                uiState.editingMessage?.let { editing ->
                    EditPreviewBar(message = editing, onCancel = { viewModel.setEditingMessage(null); inputText = "" })
                }
                uiState.replyingTo?.let { replyMessage ->
                    ReplyPreviewBar(
                        message = replyMessage,
                        isOwn = replyMessage.senderId == viewModel.currentUserId,
                        onCancel = { viewModel.setReplyingTo(null) }
                    )
                }
                MessageInputBar(
                    text = inputText,
                    onTextChange = { inputText = it; viewModel.onInputTextChanged(it) },
                    onSendClick = { trySend() },
                    onKeyboardSend = { if (sendOnEnter) trySend() },
                    sendOnEnter = sendOnEnter,
                    isSending = uiState.isSending,
                    onAttachClick = { imagePicker.launch("image/*") },
                    primaryColor = colorTheme.primary
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (displayedMessages.isEmpty()) {
                Text(
                    text = if (uiState.isSearchActive) "Ничего не найдено" else "Сообщений пока нет.\nНапишите первым!",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayedMessages, key = { it.id }) { message ->
                        SwipeableMessageBubble(
                            message = message,
                            isOwnMessage = message.senderId == viewModel.currentUserId,
                            currentUserId = viewModel.currentUserId,
                            autoDownloadImages = autoDownloadImages,
                            colorTheme = colorTheme,
                            onReply = { viewModel.setReplyingTo(message) },
                            onEdit = { viewModel.setEditingMessage(message) },
                            onDelete = { viewModel.deleteMessage(message) },
                            onForward = { viewModel.prepareForward(message); onForwardMessage() },
                            onReact = { emoji -> viewModel.toggleReaction(message.id, emoji) },
                            onPin = { viewModel.togglePinMessage(message.id) },
                            onBookmark = { viewModel.toggleBookmark(message.id) },
                            onImageClick = { base64 ->
                                onOpenImageViewer(base64, uiState.chatTitle, message.timestamp)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableMessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    currentUserId: String?,
    autoDownloadImages: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onBookmark: () -> Unit,
    onImageClick: (String) -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 80f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > swipeThreshold) onReply()
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(0f, 150f)
                    }
                )
            }
    ) {
        if (offsetX > 20f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Ответить",
                tint = colorTheme.primary.copy(alpha = (offsetX / swipeThreshold).coerceAtMost(1f)),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(24.dp)
            )
        }
        Box(modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }) {
            MessageBubble(
                message = message, isOwnMessage = isOwnMessage,
                currentUserId = currentUserId, autoDownloadImages = autoDownloadImages,
                colorTheme = colorTheme,
                onReply = onReply, onEdit = onEdit, onDelete = onDelete,
                onForward = onForward, onReact = onReact, onPin = onPin,
                onBookmark = onBookmark, onImageClick = onImageClick
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    currentUserId: String?,
    autoDownloadImages: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onBookmark: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val bubbleColor = if (isOwnMessage) colorTheme.bubbleOwn else colorTheme.bubbleOther
    val textColor = if (isOwnMessage) colorTheme.bubbleOwnText else colorTheme.bubbleOtherText
    val alignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    var revealImage by remember { mutableStateOf(autoDownloadImages) }

    if (message.isDeleted) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
            Text("Сообщение удалено", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.padding(8.dp))
        }
        return
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start) {
            if (message.isPinned) {
                Row(modifier = Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Закреплено", tint = colorTheme.primary, modifier = Modifier.size(12.dp))
                    Text("Закреплено", style = MaterialTheme.typography.labelSmall, color = colorTheme.primary)
                }
            }
            Box {
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                            bottomEnd = if (isOwnMessage) 4.dp else 16.dp
                        ))
                        .background(bubbleColor)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { showMenu = true }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    message.forwardedFromSenderName?.let {
                        Text("Переслано от $it", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.75f), fontWeight = FontWeight.Bold)
                    }
                    message.replyToText?.let { replyText ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(textColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(6.dp)
                        ) {
                            Text(message.replyToSenderName ?: "Сообщение", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textColor)
                            Text(replyText, style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.85f), maxLines = 1)
                        }
                    }
                    message.imageBase64?.let { base64 ->
                        if (revealImage) {
                            val bitmap = remember(base64) { ImageUtils.decodeBase64ToBitmap(base64) }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Фото",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth().height(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onImageClick(base64) }
                                        .padding(top = if (message.replyToText != null) 4.dp else 0.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(textColor.copy(alpha = 0.12f))
                                    .clickable { revealImage = true },
                                contentAlignment = Alignment.Center
                            ) { Text("Тап, чтобы загрузить фото", color = textColor) }
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text, color = textColor,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = if (message.replyToText != null || message.imageBase64 != null) 4.dp else 0.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isEdited) {
                            Text("изменено ", color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(formatMessageTime(message.timestamp), color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        if (isOwnMessage) {
                            val statusIcon = if (message.status == MessageStatus.READ) Icons.Filled.DoneAll else Icons.Filled.Done
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = if (message.status == MessageStatus.READ) "Прочитано" else "Отправлено",
                                tint = if (message.status == MessageStatus.READ) Color(0xFF60E6FF) else textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp).padding(start = 4.dp)
                            )
                        }
                    }
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        QUICK_REACTIONS.forEach { emoji ->
                            Text(emoji, fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                modifier = Modifier.clickable { onReact(emoji); showMenu = false }.padding(6.dp))
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Ответить") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) }, onClick = { showMenu = false; onReply() })
                    DropdownMenuItem(text = { Text(if (message.isPinned) "Открепить" else "Закрепить") }, leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) }, onClick = { showMenu = false; onPin() })
                    DropdownMenuItem(text = { Text("В избранное") }, leadingIcon = { Icon(Icons.Filled.BookmarkBorder, contentDescription = null) }, onClick = { showMenu = false; onBookmark() })
                    if (message.text.isNotBlank()) {
                        DropdownMenuItem(text = { Text("Копировать") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }, onClick = { showMenu = false; clipboardManager.setText(AnnotatedString(message.text)) })
                    }
                    DropdownMenuItem(text = { Text("Переслать") }, leadingIcon = { Icon(Icons.Filled.Forward, contentDescription = null) }, onClick = { showMenu = false; onForward() })
                    if (isOwnMessage) {
                        DropdownMenuItem(text = { Text("Редактировать") }, leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }, onClick = { showMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
            if (message.reactions.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    message.reactions.filterValues { it.isNotEmpty() }.forEach { (emoji, uids) ->
                        val reactedByMe = currentUserId in uids
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (reactedByMe) colorTheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onReact(emoji) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, style = MaterialTheme.typography.labelMedium)
                            if (uids.size > 1) Text(" ${uids.size}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPreviewBar(message: Message, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Edit, contentDescription = null, tint = LocalColorTheme.current.primary)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text("Редактирование", style = MaterialTheme.typography.labelLarge, color = LocalColorTheme.current.primary, fontWeight = FontWeight.Bold)
            Text(message.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Отменить") }
    }
}

@Composable
private fun ReplyPreviewBar(message: Message, isOwn: Boolean, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(width = 3.dp, height = 32.dp).background(LocalColorTheme.current.primary))
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(if (isOwn) "Вы" else "Ответ", style = MaterialTheme.typography.labelLarge, color = LocalColorTheme.current.primary, fontWeight = FontWeight.Bold)
            Text(message.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Отменить ответ") }
    }
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onKeyboardSend: () -> Unit,
    sendOnEnter: Boolean,
    isSending: Boolean,
    onAttachClick: () -> Unit,
    primaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttachClick) {
            Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить фото", tint = primaryColor)
        }
        OutlinedTextField(
            value = text, onValueChange = onTextChange,
            placeholder = { Text("Сообщение...") },
            modifier = Modifier.weight(1f), maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default),
            keyboardActions = KeyboardActions(onSend = { onKeyboardSend() })
        )
        IconButton(onClick = onSendClick, enabled = !isSending && text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = primaryColor)
        }
    }
}

private fun formatMessageTime(millis: Long): String {
    if (millis == 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}

private fun formatLastSeen(millis: Long): String {
    val diffMillis = System.currentTimeMillis() - millis
    val diffMinutes = diffMillis / 60_000
    return when {
        diffMinutes < 1 -> "только что"
        diffMinutes < 60 -> "$diffMinutes мин назад"
        diffMinutes < 24 * 60 -> "${diffMinutes / 60} ч назад"
        else -> SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))
    }
}
'''

# ─────────────────────────────────────────────
# 22. ChatViewModel.kt
# ─────────────────────────────────────────────
CHAT_VM = r'''package app.yodo.messenger.features.chats

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.UserPresence
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.domain.repository.ReplyContext
import app.yodo.messenger.domain.repository.SendMessageResult
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ChatUiState(
    val chatTitle: String = "Чат",
    val chatType: String = "PRIVATE",
    val otherUserId: String? = null,
    val messages: List<Message> = emptyList(),
    val pinnedMessages: List<Message> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val otherUserPresence: UserPresence? = null,
    val isOtherUserTyping: Boolean = false,
    val replyingTo: Message? = null,
    val editingMessage: Message? = null,
    val initialDraft: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val otherUserPhotoUrl: String? = null,
    val otherUserAvatarBase64: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val presenceRepository: PresenceRepository,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val draftsPreferences: DraftsPreferences,
    private val pendingForwardHolder: PendingForwardHolder,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    fun prepareForward(message: Message) { pendingForwardHolder.set(message) }

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    val sendOnEnter: StateFlow<Boolean> = userSettingsPreferences.sendOnEnter.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val autoDownloadImages: StateFlow<Boolean> = userSettingsPreferences.autoDownloadImages.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val hideKeyboardOnSend: StateFlow<Boolean> = userSettingsPreferences.hideKeyboardOnSend.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )

    private var typingResetJob: Job? = null
    private var isCurrentlyMarkedTyping = false

    init {
        loadChatInfo()
        observeMessages()
        observePinnedMessages()
        observeTyping()
        markAsRead()
        loadDraft()
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val draft = draftsPreferences.getDraft(chatId)
            if (draft.isNotBlank()) _uiState.value = _uiState.value.copy(initialDraft = draft)
        }
    }

    fun saveDraft(text: String) {
        viewModelScope.launch { draftsPreferences.saveDraft(chatId, text) }
    }

    private fun loadChatInfo() {
        viewModelScope.launch {
            chatRepository.getChatInfo(chatId)?.let { info ->
                _uiState.value = _uiState.value.copy(
                    chatTitle = info.title, chatType = info.type,
                    otherUserId = info.otherUserId,
                    otherUserPhotoUrl = info.otherUserPhotoUrl,
                    otherUserAvatarBase64 = info.otherUserAvatarBase64
                )
                info.otherUserId?.let { observePresence(it) }
            }
        }
    }

    private fun observePresence(otherUserId: String) {
        viewModelScope.launch {
            presenceRepository.observePresence(otherUserId).collect { presence ->
                _uiState.value = _uiState.value.copy(otherUserPresence = presence)
            }
        }
    }

    private fun observeTyping() {
        viewModelScope.launch {
            presenceRepository.observeTypingUsers(chatId).collect { typingUids ->
                val otherUserId = _uiState.value.otherUserId
                val isTyping = otherUserId != null && otherUserId in typingUids
                _uiState.value = _uiState.value.copy(isOtherUserTyping = isTyping)
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            messageRepository.observeMessages(chatId).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
    }

    private fun observePinnedMessages() {
        viewModelScope.launch {
            messageRepository.observePinnedMessages(chatId).collect { pinned ->
                _uiState.value = _uiState.value.copy(pinnedMessages = pinned)
            }
        }
    }

    private fun markAsRead() {
        viewModelScope.launch { messageRepository.markChatAsRead(chatId) }
    }

    fun onInputTextChanged(text: String) {
        saveDraft(text)
        typingResetJob?.cancel()
        if (text.isNotBlank()) {
            if (!isCurrentlyMarkedTyping) {
                isCurrentlyMarkedTyping = true
                viewModelScope.launch { presenceRepository.setTyping(chatId, true) }
            }
            typingResetJob = viewModelScope.launch {
                delay(3000)
                isCurrentlyMarkedTyping = false
                presenceRepository.setTyping(chatId, false)
            }
        } else {
            clearTypingStatus()
        }
    }

    private fun clearTypingStatus() {
        typingResetJob?.cancel()
        if (isCurrentlyMarkedTyping) {
            isCurrentlyMarkedTyping = false
            viewModelScope.launch { presenceRepository.setTyping(chatId, false) }
        }
    }

    fun setReplyingTo(message: Message?) {
        _uiState.value = _uiState.value.copy(replyingTo = message, editingMessage = null)
    }

    fun setEditingMessage(message: Message?) {
        _uiState.value = _uiState.value.copy(editingMessage = message, replyingTo = null)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        clearTypingStatus()
        viewModelScope.launch { draftsPreferences.clearDraft(chatId) }
        val editing = _uiState.value.editingMessage
        if (editing != null) {
            _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null, editingMessage = null)
            viewModelScope.launch {
                when (val result = messageRepository.editMessage(chatId, editing.id, text)) {
                    is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                    is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
                }
            }
            return
        }
        val replying = _uiState.value.replyingTo
        val replyContext = replying?.let {
            ReplyContext(
                messageId = it.id,
                senderName = if (it.senderId == currentUserId) "Вы" else _uiState.value.chatTitle,
                text = it.text
            )
        }
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null, replyingTo = null)
        viewModelScope.launch {
            when (val result = messageRepository.sendMessage(chatId, text, replyContext)) {
                is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
            }
        }
    }

    fun sendImage(base64: String) {
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = messageRepository.sendImageMessage(chatId, base64)) {
                is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
            }
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            when (val result = messageRepository.deleteMessage(chatId, message.id)) {
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
                else -> {}
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch { messageRepository.toggleReaction(chatId, messageId, emoji) }
    }

    fun togglePinMessage(messageId: String) {
        viewModelScope.launch { messageRepository.togglePinMessage(chatId, messageId) }
    }

    fun toggleBookmark(messageId: String) {
        viewModelScope.launch { messageRepository.toggleBookmark(messageId, chatId) }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            try { chatRepository.clearChatHistory(chatId) }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(errorMessage = "Не удалось очистить: ${e.message}") }
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            try { chatRepository.deleteChat(chatId) }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(errorMessage = "Не удалось удалить: ${e.message}") }
        }
    }

    fun exportChat(context: Context) {
        viewModelScope.launch {
            val text = messageRepository.exportChatHistory(chatId)
            val file = File(context.cacheDir, "yodo_chat_export.txt")
            file.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Экспорт чата"))
        }
    }

    fun toggleSearch() {
        _uiState.value = _uiState.value.copy(isSearchActive = !_uiState.value.isSearchActive, searchQuery = "")
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        if (isCurrentlyMarkedTyping) {
            viewModelScope.launch { presenceRepository.setTyping(chatId, false) }
        }
    }
}
'''

# ─────────────────────────────────────────────
# 23. ChatListScreen.kt
# ─────────────────────────────────────────────
CHAT_LIST_SCREEN = r'''package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoOnline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Yodo Messenger",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSearchClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = "Поиск")
                }
                IconButton(onClick = onProfileClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Person, contentDescription = "Профиль")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is ChatListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ChatListUiState.Empty -> {
                    Text(
                        text = "У вас пока нет чатов.\nНачните новый разговор!",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is ChatListUiState.Content -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.chats, key = { it.chatId }) { chat ->
                            SwipeableChatListItem(
                                chat = chat,
                                colorTheme = colorTheme,
                                onClick = { onChatClick(chat.chatId) },
                                onTogglePin = { viewModel.togglePinChat(chat.chatId) },
                                onToggleMute = { viewModel.toggleMuteChat(chat.chatId) },
                                onDelete = { viewModel.deleteChat(chat.chatId) },
                                onClearHistory = { viewModel.clearChatHistory(chat.chatId) }
                            )
                        }
                    }
                }
                is ChatListUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Не удалось загрузить чаты", style = MaterialTheme.typography.titleLarge)
                        Text(state.message, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableChatListItem(
    chat: ChatPreview,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onDelete: () -> Unit,
    onClearHistory: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var showMenu by remember { mutableStateOf(false) }
    val deleteThreshold = -100f

    Box {
        if (offsetX < -20f) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.2f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = Color.Red,
                    modifier = Modifier.padding(end = 24.dp).size(28.dp))
            }
        }
        Box(modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }) {
            ChatListItem(
                chat = chat, colorTheme = colorTheme,
                onClick = onClick, onLongClick = { showMenu = true }
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text(if (chat.isPinned) "Открепить" else "Закрепить") }, onClick = { showMenu = false; onTogglePin() })
            DropdownMenuItem(text = { Text(if (chat.isMuted) "Включить уведомления" else "Отключить уведомления") }, onClick = { showMenu = false; onToggleMute() })
            DropdownMenuItem(text = { Text("Очистить историю") }, onClick = { showMenu = false; onClearHistory() })
            DropdownMenuItem(text = { Text("Удалить чат", color = Color.Red) }, onClick = { showMenu = false; onDelete() })
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().pointerInput(chat.chatId) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (offsetX < deleteThreshold) onDelete()
                    offsetX = 0f
                },
                onDragCancel = { offsetX = 0f },
                onHorizontalDrag = { _, dragAmount ->
                    offsetX = (offsetX + dragAmount).coerceIn(-200f, 0f)
                }
            )
        }
    ) {}
}

@Composable
private fun ChatListItem(
    chat: ChatPreview,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            UserAvatar(
                displayName = chat.title,
                photoUrl = chat.avatarUrl,
                avatarBase64 = null,
                size = 56.dp
            )
            if (chat.isOnline) {
                Box(
                    modifier = Modifier.size(14.dp).align(Alignment.BottomEnd)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.background)
                        .padding(2.dp).clip(CircleShape).background(YodoOnline)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(chat.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(chat.lastMessage, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.isMuted) {
                    Icon(Icons.Filled.NotificationsOff, contentDescription = "Без звука", modifier = Modifier.size(14.dp).padding(end = 4.dp), tint = Color.Gray)
                }
                if (chat.isPinned) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Закреплён", modifier = Modifier.size(14.dp).padding(end = 4.dp), tint = Color.Gray)
                }
                Text(formatTimestamp(chat.lastMessageTimestamp), style = MaterialTheme.typography.labelMedium)
            }
            if (chat.unreadCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.clip(CircleShape).background(colorTheme.primary).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(
                        text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                        color = Color.White, style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    if (millis == 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}
'''

# ─────────────────────────────────────────────
# 24. ChatListViewModel.kt
# ─────────────────────────────────────────────
CHAT_LIST_VM = r'''package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.repository.ChatListResult
import app.yodo.messenger.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class ChatListUiState {
    data object Loading : ChatListUiState()
    data object Empty : ChatListUiState()
    data class Content(val chats: List<ChatPreview>) : ChatListUiState()
    data class Error(val message: String) : ChatListUiState()
}

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState

    init {
        observeChats()
        syncFcmToken()
    }

    private fun observeChats() {
        viewModelScope.launch {
            chatRepository.observeChatList().collect { result ->
                _uiState.value = when (result) {
                    is ChatListResult.Success -> {
                        if (result.chats.isEmpty()) ChatListUiState.Empty
                        else ChatListUiState.Content(result.chats)
                    }
                    is ChatListResult.Error -> ChatListUiState.Error(result.message)
                }
            }
        }
    }

    fun togglePinChat(chatId: String) {
        viewModelScope.launch { chatRepository.togglePinChat(chatId) }
    }

    fun toggleMuteChat(chatId: String) {
        viewModelScope.launch { chatRepository.toggleMuteChat(chatId) }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            try { chatRepository.deleteChat(chatId) } catch (e: Exception) { }
        }
    }

    fun clearChatHistory(chatId: String) {
        viewModelScope.launch {
            try { chatRepository.clearChatHistory(chatId) } catch (e: Exception) { }
        }
    }

    private fun syncFcmToken() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                firestore.collection("users").document(uid).update("fcmToken", token).await()
            }
        }
    }
}
'''

# ─────────────────────────────────────────────
# 25. SettingsScreen.kt
# ─────────────────────────────────────────────
SETTINGS_SCREEN = r'''package app.yodo.messenger.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.data.local.FontSize
import app.yodo.messenger.ui.theme.allColorThemes
import app.yodo.messenger.ui.theme.YodoError

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLoggedOut: () -> Unit = {},
    onOpenBlockedUsers: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val colorThemeName by viewModel.colorThemeName.collectAsState()
    val sendOnEnter by viewModel.sendOnEnter.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val showOnlineStatus by viewModel.showOnlineStatus.collectAsState()
    val showReadReceipts by viewModel.showReadReceipts.collectAsState()
    val autoDownloadImages by viewModel.autoDownloadImages.collectAsState()
    val hideKeyboardOnSend by viewModel.hideKeyboardOnSend.collectAsState()
    val notificationSound by viewModel.notificationSound.collectAsState()
    val notificationVibration by viewModel.notificationVibration.collectAsState()
    val muteAllNotifications by viewModel.muteAllNotifications.collectAsState()
    val accountDeleted by viewModel.accountDeleted.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(accountDeleted) { if (accountDeleted) onLoggedOut() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.consumeError() }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Удалить аккаунт?") },
            text = { Text("Это действие необратимо.") },
            confirmButton = {
                TextButton(onClick = { showDeleteAccountDialog = false; viewModel.deleteAccount() }) {
                    Text("Удалить", color = YodoError)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Отмена") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item { SectionTitle("Оформление") }
            item { SettingsSwitchRow("Тёмная тема", "Оформление интерфейса", isDarkTheme) { viewModel.setDarkTheme(it) } }
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("Цветовая тема", style = MaterialTheme.typography.bodyLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        allColorThemes.forEach { theme ->
                            val isSelected = colorThemeName == theme.name.name
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { viewModel.setColorTheme(theme.name.name) }
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(theme.primary)
                                        .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                )
                                Text(theme.name.displayName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("Размер шрифта", style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        FontSize.entries.forEach { size ->
                            FilterChip(
                                selected = fontSize == size,
                                onClick = { viewModel.setFontSize(size) },
                                label = {
                                    Text(when (size) {
                                        FontSize.SMALL -> "Мелкий"
                                        FontSize.MEDIUM -> "Обычный"
                                        FontSize.LARGE -> "Крупный"
                                    })
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
            item { HorizontalDivider() }
            item { SectionTitle("Чаты") }
            item { SettingsSwitchRow("Отправка по Enter", "Enter отправляет сообщение", sendOnEnter) { viewModel.setSendOnEnter(it) } }
            item { SettingsSwitchRow("Скрывать клавиатуру после отправки", "Клавиатура закрывается автоматически", hideKeyboardOnSend) { viewModel.setHideKeyboardOnSend(it) } }
            item { SettingsSwitchRow("Автозагрузка фото", "Показывать изображения сразу", autoDownloadImages) { viewModel.setAutoDownloadImages(it) } }
            item { HorizontalDivider() }
            item { SectionTitle("Конфиденциальность") }
            item { SettingsSwitchRow("Показывать статус «в сети»", "Другие видят, когда ты онлайн", showOnlineStatus) { viewModel.setShowOnlineStatus(it) } }
            item { SettingsSwitchRow("Показывать статус прочтения", "Собеседник видит двойную галочку", showReadReceipts) { viewModel.setShowReadReceipts(it) } }
            item { HorizontalDivider() }
            item { SectionTitle("Уведомления") }
            item { SettingsSwitchRow("Отключить все уведомления", "Полностью выключить push", muteAllNotifications) { viewModel.setMuteAllNotifications(it) } }
            item { SettingsSwitchRow("Звук", "Звуковой сигнал", notificationSound, enabled = !muteAllNotifications) { viewModel.setNotificationSound(it) } }
            item { SettingsSwitchRow("Вибрация", "Вибросигнал", notificationVibration, enabled = !muteAllNotifications) { viewModel.setNotificationVibration(it) } }
            item { HorizontalDivider() }
            item { SectionTitle("Аккаунт") }
            item {
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = YodoError),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text("Выйти из аккаунта", color = Color.White) }
            }
            item {
                TextButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp)
                ) { Text("Удалить аккаунт", color = YodoError) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun SettingsSwitchRow(
    title: String, subtitle: String, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
'''

# ─────────────────────────────────────────────
# 26. SettingsViewModel.kt
# ─────────────────────────────────────────────
SETTINGS_VM = r'''package app.yodo.messenger.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.data.local.FontSize
import app.yodo.messenger.data.local.ThemePreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val draftsPreferences: DraftsPreferences,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val isDarkTheme: StateFlow<Boolean> = themePreferences.isDarkTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val colorThemeName: StateFlow<String> = themePreferences.colorThemeName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "BLUE")
    val sendOnEnter: StateFlow<Boolean> = userSettingsPreferences.sendOnEnter.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val fontSize: StateFlow<FontSize> = userSettingsPreferences.fontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontSize.MEDIUM)
    val showOnlineStatus: StateFlow<Boolean> = userSettingsPreferences.showOnlineStatus.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showReadReceipts: StateFlow<Boolean> = userSettingsPreferences.showReadReceipts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoDownloadImages: StateFlow<Boolean> = userSettingsPreferences.autoDownloadImages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val hideKeyboardOnSend: StateFlow<Boolean> = userSettingsPreferences.hideKeyboardOnSend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notificationSound: StateFlow<Boolean> = userSettingsPreferences.notificationSound.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notificationVibration: StateFlow<Boolean> = userSettingsPreferences.notificationVibration.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val muteAllNotifications: StateFlow<Boolean> = userSettingsPreferences.muteAllNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _accountDeleted = MutableStateFlow(false)
    val accountDeleted: StateFlow<Boolean> = _accountDeleted
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { themePreferences.setDarkTheme(enabled) }
    fun setColorTheme(name: String) = viewModelScope.launch { themePreferences.setColorTheme(name) }
    fun setSendOnEnter(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setSendOnEnter(enabled) }
    fun setFontSize(size: FontSize) = viewModelScope.launch { userSettingsPreferences.setFontSize(size) }
    fun setShowOnlineStatus(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setShowOnlineStatus(enabled) }
    fun setShowReadReceipts(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setShowReadReceipts(enabled) }
    fun setAutoDownloadImages(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setAutoDownloadImages(enabled) }
    fun setHideKeyboardOnSend(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setHideKeyboardOnSend(enabled) }
    fun setNotificationSound(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setNotificationSound(enabled) }
    fun setNotificationVibration(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setNotificationVibration(enabled) }
    fun setMuteAllNotifications(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setMuteAllNotifications(enabled) }

    fun logout() { authRepository.logout() }
    fun clearAllDrafts() = viewModelScope.launch { draftsPreferences.clearAllDrafts() }

    fun deleteAccount() {
        val user = firebaseAuth.currentUser
        if (user == null) { _errorMessage.value = "Вы не авторизованы"; return }
        viewModelScope.launch {
            try {
                user.delete().await()
                _accountDeleted.value = true
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Не удалось удалить аккаунт."
            }
        }
    }

    fun consumeError() { _errorMessage.value = null }
}
'''

# ─────────────────────────────────────────────
# 27. ProfileScreen.kt
# ─────────────────────────────────────────────
PROFILE_SCREEN = r'''package app.yodo.messenger.features.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var aboutMe by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.user) {
        if (!initialized && uiState.user != null) {
            name = uiState.user?.displayName.orEmpty()
            username = uiState.user?.username.orEmpty()
            bio = uiState.user?.bio.orEmpty()
            nickname = uiState.user?.nickname.orEmpty()
            aboutMe = uiState.user?.aboutMe.orEmpty()
            birthDate = uiState.user?.birthDate.orEmpty()
            location = uiState.user?.location.orEmpty()
            website = uiState.user?.website.orEmpty()
            initialized = true
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.uploadAvatar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(96.dp).clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(
                    displayName = uiState.user?.displayName.orEmpty(),
                    photoUrl = uiState.user?.photoUrl,
                    avatarBase64 = uiState.user?.avatarBase64,
                    size = 96.dp
                )
                if (uiState.isUploadingAvatar) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color.White) }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(colorTheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Изменить фото", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            ProfileField("Имя", name, { name = it }, { viewModel.updateDisplayName(name) }, uiState.isSavingName)
            ProfileField("Username", username, { username = it }, { viewModel.updateUsername(username) }, uiState.isSavingUsername, prefix = "@")
            ProfileField("Ник", nickname, { nickname = it }, { viewModel.updateNickname(nickname) }, uiState.isSavingNickname)
            ProfileField("О себе (кратко)", bio, { if (it.length <= 150) bio = it }, { viewModel.updateBio(bio) }, uiState.isSavingBio)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("Расширенный профиль", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())

            ProfileField("Заметки «О себе»", aboutMe, { if (it.length <= 300) aboutMe = it }, { viewModel.updateAboutMe(aboutMe) }, uiState.isSavingAboutMe)
            ProfileField("Дата рождения (дд.мм.гггг)", birthDate, { birthDate = it }, { viewModel.updateBirthDate(birthDate) }, uiState.isSavingBirthDate)
            ProfileField("Местоположение", location, { location = it }, { viewModel.updateLocation(location) }, uiState.isSavingLocation)
            ProfileField("Сайт / ссылка", website, { website = it }, { viewModel.updateWebsite(website) }, uiState.isSavingWebsite)

            uiState.user?.phoneNumber?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            uiState.user?.email?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }

            uiState.errorMessage?.let { error ->
                Text(text = error, color = YodoError, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun ProfileField(
    label: String, value: String, onValueChange: (String) -> Unit,
    onSave: () -> Unit, isSaving: Boolean, prefix: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = prefix?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true
    )
    TextButton(onClick = onSave, enabled = !isSaving && value.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp))
        else Text("Сохранить")
    }
}
'''

# ─────────────────────────────────────────────
# 28. ProfileViewModel.kt
# ─────────────────────────────────────────────
PROFILE_VM = r'''package app.yodo.messenger.features.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ProfileUpdateResult
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: YodoUser? = null,
    val isUploadingAvatar: Boolean = false,
    val isSavingName: Boolean = false,
    val isSavingBio: Boolean = false,
    val isSavingUsername: Boolean = false,
    val isSavingNickname: Boolean = false,
    val isSavingAboutMe: Boolean = false,
    val isSavingBirthDate: Boolean = false,
    val isSavingLocation: Boolean = false,
    val isSavingWebsite: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        viewModelScope.launch {
            userRepository.observeCurrentUser().collect { user ->
                _uiState.value = _uiState.value.copy(user = user)
            }
        }
    }

    fun updateDisplayName(name: String) {
        _uiState.value = _uiState.value.copy(isSavingName = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateDisplayName(name)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingName = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingName = false, errorMessage = r.message)
            }
        }
    }

    fun updateBio(bio: String) {
        _uiState.value = _uiState.value.copy(isSavingBio = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateBio(bio)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingBio = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingBio = false, errorMessage = r.message)
            }
        }
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(isSavingUsername = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateUsername(username)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingUsername = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingUsername = false, errorMessage = r.message)
            }
        }
    }

    fun updateNickname(nickname: String) {
        _uiState.value = _uiState.value.copy(isSavingNickname = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateNickname(nickname)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingNickname = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingNickname = false, errorMessage = r.message)
            }
        }
    }

    fun updateAboutMe(aboutMe: String) {
        _uiState.value = _uiState.value.copy(isSavingAboutMe = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateAboutMe(aboutMe)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingAboutMe = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingAboutMe = false, errorMessage = r.message)
            }
        }
    }

    fun updateBirthDate(birthDate: String) {
        _uiState.value = _uiState.value.copy(isSavingBirthDate = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateBirthDate(birthDate)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingBirthDate = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingBirthDate = false, errorMessage = r.message)
            }
        }
    }

    fun updateLocation(location: String) {
        _uiState.value = _uiState.value.copy(isSavingLocation = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateLocation(location)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingLocation = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingLocation = false, errorMessage = r.message)
            }
        }
    }

    fun updateWebsite(website: String) {
        _uiState.value = _uiState.value.copy(isSavingWebsite = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateWebsite(website)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingWebsite = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingWebsite = false, errorMessage = r.message)
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        _uiState.value = _uiState.value.copy(isUploadingAvatar = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.uploadAvatar(uri)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isUploadingAvatar = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isUploadingAvatar = false, errorMessage = r.message)
            }
        }
    }

    fun consumeError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
}
'''

# ═══════════════════════════════════════════════
# ГЛАВНАЯ ФУНКЦИЯ
# ═══════════════════════════════════════════════

FILES = {
    f"{BASE}/util/ImageUtils.kt": IMAGE_UTILS,
    f"{BASE}/ui/theme/ColorTheme.kt": COLOR_THEME,
    f"{BASE}/ui/theme/Color.kt": COLOR_KT,
    f"{BASE}/ui/theme/Theme.kt": THEME_KT,
    f"{BASE}/data/local/ThemePreferences.kt": THEME_PREFS,
    f"{BASE}/data/local/UserSettingsPreferences.kt": USER_SETTINGS_PREFS,
    f"{BASE}/domain/model/YodoUser.kt": YODO_USER,
    f"{BASE}/domain/model/Message.kt": MESSAGE,
    f"{BASE}/domain/repository/UserRepository.kt": USER_REPO,
    f"{BASE}/domain/repository/ChatRepository.kt": CHAT_REPO,
    f"{BASE}/domain/repository/MessageRepository.kt": MSG_REPO,
    f"{BASE}/data/repository/UserRepositoryImpl.kt": USER_REPO_IMPL,
    f"{BASE}/data/repository/ChatRepositoryImpl.kt": CHAT_REPO_IMPL,
    f"{BASE}/data/repository/MessageRepositoryImpl.kt": MSG_REPO_IMPL,
    f"{BASE}/navigation/Routes.kt": ROUTES,
    f"{BASE}/navigation/NavGraph.kt": NAV_GRAPH,
    f"{BASE}/MainActivity.kt": MAIN_ACTIVITY,
    f"{BASE}/features/chats/ImageViewerScreen.kt": IMAGE_VIEWER,
    f"{RES}/xml/file_paths.xml": FILE_PATHS_XML,
    f"{BASE}/features/main/MainScreen.kt": MAIN_SCREEN,
    f"{BASE}/features/chats/ChatScreen.kt": CHAT_SCREEN,
    f"{BASE}/features/chats/ChatViewModel.kt": CHAT_VM,
    f"{BASE}/features/chats/ChatListScreen.kt": CHAT_LIST_SCREEN,
    f"{BASE}/features/chats/ChatListViewModel.kt": CHAT_LIST_VM,
    f"{BASE}/features/settings/SettingsScreen.kt": SETTINGS_SCREEN,
    f"{BASE}/features/settings/SettingsViewModel.kt": SETTINGS_VM,
    f"{BASE}/features/profile/ProfileScreen.kt": PROFILE_SCREEN,
    f"{BASE}/features/profile/ProfileViewModel.kt": PROFILE_VM,
}

def main():
    print("=" * 60)
    print("  YODOMessenger v0.2.0 — Автоматическое обновление")
    print("=" * 60)
    print()

    # Проверка: мы в корне проекта?
    if not os.path.isdir("app"):
        print("❌ ОШИБКА: Папка 'app/' не найдена!")
        print("   Запустите скрипт из КОРНЯ проекта (рядом с папкой app/)")
        sys.exit(1)

    # Шаг 1: Удалить дубликат UserRepositoryImpl из domain/
    dup = f"{BASE}/domain/repository/UserRepositoryImpl.kt"
    if os.path.exists(dup):
        os.remove(dup)
        print(f"🗑️  Удалён дубликат: {dup}")
    else:
        print(f"✅ Дубликат не найден (OK): {dup}")

    # Шаг 2: Создать/заменить все файлы
    created = 0
    replaced = 0
    for path, content in FILES.items():
        dir_path = os.path.dirname(path)
        os.makedirs(dir_path, exist_ok=True)

        if os.path.exists(path):
            replaced += 1
        else:
            created += 1

        with open(path, "w", encoding="utf-8") as f:
            f.write(content.strip() + "\n")

        print(f"  ✅ {path}")

    print()
    print(f"📦 Создано новых файлов: {created}")
    print(f"🔄 Заменено файлов: {replaced}")
    print(f"📊 Всего обработано: {created + replaced}")

    # Шаг 3: Обновить AndroidManifest.xml (добавить FileProvider)
    print()
    if os.path.exists(MANIFEST):
        with open(MANIFEST, "r", encoding="utf-8") as f:
            manifest = f.read()

        if "FileProvider" not in manifest:
            provider_block = """
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
"""
            # Вставить перед </application>
            manifest = manifest.replace("</application>", provider_block + "    </application>")
            with open(MANIFEST, "w", encoding="utf-8") as f:
                f.write(manifest)
            print("✅ AndroidManifest.xml обновлён (добавлен FileProvider)")
        else:
            print("✅ AndroidManifest.xml уже содержит FileProvider (OK)")
    else:
        print("⚠️  AndroidManifest.xml не найден — добавьте FileProvider вручную!")

    print()
    print("=" * 60)
    print("  ✅ ГОТОВО! Теперь выполните:")
    print()
    print("  git add -A")
    print('  git commit -m "v0.2.0: all new features and fixes"')
    print("  git push origin main")
    print("=" * 60)


if __name__ == "__main__":
    main()