package app.yodo.messenger.features.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.yodo.messenger.features.canvas.CanvasSavedBoard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CanvasLibraryScreenTest {
    @get:Rule val compose=createComposeRule()
    private fun board(n:Int)=CanvasSavedBoard("board-$n","Доска $n","chat-hash","chat-id",0L,n,true)

    @Test fun longCatalogueScrollsOnItsOwnScreenAndOpensBoard() {
        val boards=List(80){board(it)}
        var opened: Pair<String,String>?=null
        var back=0
        compose.setContent { MaterialTheme {
            CanvasLibraryContent(CanvasLibraryState(boards=boards),{back++},{chat,id->opened=chat to id},{},{_,_->},{})
        } }
        compose.onNodeWithText("Сохранённые доски").assertIsDisplayed()
        compose.onNodeWithText("Показывать доску в чате").assertDoesNotExist()
        compose.onNodeWithTag("saved_boards_list").performScrollToIndex(80)
        compose.onNodeWithText("Доска 79").assertIsDisplayed()
        compose.onAllNodesWithText("Открыть и редактировать").onLast().performClick()
        compose.runOnIdle { assertEquals("chat-id" to "board-79",opened) }
        compose.onNodeWithContentDescription("Назад к настройкам доски").performClick()
        compose.runOnIdle { assertEquals(1,back) }
    }

    @Test fun renameAndDeleteStillRequireExplicitConfirmation() {
        val entry=board(1)
        var renamed: String?=null
        var deleted=false
        compose.setContent { MaterialTheme {
            CanvasLibraryContent(CanvasLibraryState(boards=listOf(entry)),{},{_,_->},{},{_,title->renamed=title},{deleted=true})
        } }
        compose.onNodeWithText("Переименовать").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Новое имя")
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertEquals("Новое имя",renamed) }
        compose.onNodeWithText("Удалить").performClick()
        compose.onNodeWithText("Удалить доску?").assertIsDisplayed()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertFalse(deleted) }
    }
    @Test fun selectedBoardsAreDeletedOnlyAfterBatchConfirmation() {
        var deleted=emptyList<CanvasSavedBoard>()
        compose.setContent { MaterialTheme {
            CanvasLibraryContent(CanvasLibraryState(boards=List(3){board(it)}),{},{_,_->},{},{_,_->},{},onDeleteSelected={deleted=it})
        } }
        compose.onNodeWithContentDescription("Выбрать доску: Доска 0").performClick()
        compose.onNodeWithText("Выбрано: 1").assertIsDisplayed()
        compose.onNodeWithText("Выбрать все").performClick()
        compose.onNodeWithText("Выбрано: 3").assertIsDisplayed()
        compose.onNodeWithText("Удалить (3)").performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        compose.onNodeWithText("Удалить (3)").performClick()
        compose.onNodeWithText("Удалить 3").performClick()
        compose.runOnIdle { assertEquals(listOf(board(0),board(1),board(2)),deleted) }
    }
    @Test fun leavingSelectionRestoresSingleBoardActions() {
        compose.setContent { MaterialTheme {
            CanvasLibraryContent(CanvasLibraryState(boards=listOf(board(1))),{},{_,_->},{},{_,_->},{})
        } }
        compose.onNodeWithContentDescription("Выбрать доски").performClick()
        compose.onNodeWithText("Открыть и редактировать").assertDoesNotExist()
        compose.onNodeWithContentDescription("Отменить выбор").performClick()
        compose.onNodeWithText("Открыть и редактировать").assertIsDisplayed()
    }
}
