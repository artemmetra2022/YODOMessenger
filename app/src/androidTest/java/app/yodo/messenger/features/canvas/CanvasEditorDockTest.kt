package app.yodo.messenger.features.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Device tests: deliberately constrain the dock to a small phone's width. */
class CanvasEditorDockTest {
    @get:Rule val compose = createComposeRule()

    private fun dock(onImage: (() -> Unit)? = null) {
        compose.setContent {
            var tool by remember { mutableStateOf(CanvasTool.PEN) }
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    CanvasEditorDock(tool, { tool = it }, BoardColors.DEFAULT, {}, 4f, {}, 32f, {}, true, onImage)
                }
            }
        }
    }

    @Test fun primaryToolsVisibleWithoutScrolling() {
        dock()
        listOf("Перо", "Маркер", "Ластик", "Фигуры", "Текст", "Рука").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Все цвета; выбран Чёрный").assertIsDisplayed()
        compose.onNodeWithText("4 px").assertIsDisplayed()
        compose.onNodeWithText("Ластик").performClick()
        compose.onNodeWithContentDescription("Ластик").assertIsSelected()
    }

    @Test fun shapeChooserKeepsEveryShapeReachable() {
        dock()
        compose.onNodeWithText("Фигуры").performClick()
        CanvasShapeTools.forEach { compose.onNodeWithText(canvasToolLabel(it)).assertExists() }
        compose.onNodeWithText("Стрелка").performClick()
        compose.onNodeWithContentDescription("Фигуры: Стрелка").assertIsSelected()
    }

    @Test fun textAndFormulaStayAvailableWithoutImageOnSharedBoard() {
        dock()
        compose.onNodeWithText("Текст").performClick()
        compose.onNodeWithText("Картинка из галереи").assertDoesNotExist()
        compose.onNodeWithText("Формула").performClick()
        compose.onNodeWithContentDescription("Формула").assertIsSelected()
        compose.onNodeWithText("32 px").assertIsDisplayed()
    }

    @Test fun imageActionRetainsLocalImportCallback() {
        var called = 0
        dock { called++ }
        compose.onNodeWithText("Текст").performClick()
        compose.onNodeWithText("Картинка из галереи").performClick()
        compose.runOnIdle { assertEquals(1, called) }
    }
}
