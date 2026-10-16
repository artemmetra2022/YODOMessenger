package app.yodo.messenger.features.canvas

import app.yodo.messenger.data.local.BoardPrefs
import app.yodo.messenger.data.local.UserSettingsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Высота карточки доски в чате. */
enum class BoardPreviewHeight(val dp: Int, val title: String) { SMALL(140, "Маленькая"), MEDIUM(200, "Средняя"), LARGE(300, "Большая") }

object BoardColors {
    val palette: List<Pair<String, Int>> = listOf(
        "Чёрный" to 0xff1e293b.toInt(), "Синий" to 0xff2563eb.toInt(), "Красный" to 0xffdc2626.toInt(),
        "Зелёный" to 0xff16a34a.toInt(), "Фиолетовый" to 0xff9333ea.toInt(), "Жёлтый" to 0xffeab308.toInt(),
        "Оранжевый" to 0xfff97316.toInt()
    )
    val DEFAULT: Int = palette.first().second
}
val BoardInkWidths: List<Int> = listOf(2, 4, 8, 12)

/** Настройки интерактивной доски (раздел «Интерактивная доска» в настройках). */
data class BoardSettings(
    val showInChat: Boolean = true,
    val previewHeight: BoardPreviewHeight = BoardPreviewHeight.MEDIUM,
    val collapsedInChat: Boolean = false,
    val inkColor: Int = BoardColors.DEFAULT,
    val inkWidth: Float = 4f,
    val shapeAssist: Boolean = true,
    val snapToGrid: Boolean = false,
    val defaultGrid: CanvasGrid = CanvasGrid.SQUARES,
    val haptics: Boolean = true
) {
    companion object {
        /** Любое неизвестное или испорченное значение заменяется значением по умолчанию. */
        fun from(p: BoardPrefs) = BoardSettings(
            showInChat = p.showInChat,
            previewHeight = p.previewHeight?.let { runCatching { BoardPreviewHeight.valueOf(it) }.getOrNull() } ?: BoardPreviewHeight.MEDIUM,
            collapsedInChat = p.collapsedInChat,
            inkColor = p.inkColor?.takeIf { c -> BoardColors.palette.any { it.second == c } } ?: BoardColors.DEFAULT,
            inkWidth = (p.inkWidth?.takeIf { it in BoardInkWidths } ?: 4).toFloat(),
            shapeAssist = p.shapeAssist, snapToGrid = p.snapToGrid,
            defaultGrid = p.defaultGrid?.let { runCatching { CanvasGrid.valueOf(it) }.getOrNull() } ?: CanvasGrid.SQUARES,
            haptics = p.haptics
        )
    }
}

@Singleton
class CanvasSettingsStore @Inject constructor(private val prefs: UserSettingsPreferences) {
    val settings: Flow<BoardSettings> = prefs.boardPrefs.map(BoardSettings::from)
    suspend fun setShowInChat(v: Boolean) = prefs.setBoardShowInChat(v)
    suspend fun setPreviewHeight(v: BoardPreviewHeight) = prefs.setBoardPreviewHeight(v.name)
    suspend fun setCollapsedInChat(v: Boolean) = prefs.setBoardCollapsedInChat(v)
    suspend fun setInkColor(v: Int) = prefs.setBoardInkColor(v)
    suspend fun setInkWidth(v: Int) = prefs.setBoardInkWidth(v)
    suspend fun setShapeAssist(v: Boolean) = prefs.setBoardShapeAssist(v)
    suspend fun setSnapToGrid(v: Boolean) = prefs.setBoardSnapToGrid(v)
    suspend fun setDefaultGrid(v: CanvasGrid) = prefs.setBoardDefaultGrid(v.name)
    suspend fun setHaptics(v: Boolean) = prefs.setBoardHaptics(v)
    suspend fun reset() = prefs.resetBoardSettings()
}
