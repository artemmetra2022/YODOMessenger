package app.yodo.messenger.features.canvas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharedBoardUi(
    val doc: CanvasDocument? = null, val loading: Boolean = true, val error: String? = null,
    val canUndo: Boolean = false, val canRedo: Boolean = false, val truncated: Boolean = false, val busy: Boolean = false
)

/** Редактор общей доски чата. Изменения сразу видны всем: Firestore показывает их локально ещё до подтверждения сервером. */
@HiltViewModel
class SharedBoardViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: SharedBoardRepository,
    private val settingsStore: CanvasSettingsStore
) : ViewModel() {
    val chatId: String = savedState.get<String>("chatId").orEmpty()
    private val _ui = MutableStateFlow(SharedBoardUi())
    val ui: StateFlow<SharedBoardUi> = _ui.asStateFlow()
    val settings: StateFlow<BoardSettings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, BoardSettings())
    private val undoStack = ArrayDeque<SharedBoardOps.Delta>()
    private val redoStack = ArrayDeque<SharedBoardOps.Delta>()
    private var creating = false

    init {
        if (chatId.isBlank()) _ui.value = SharedBoardUi(loading = false, error = "Чат не найден")
        else viewModelScope.launch {
            repo.observe(chatId).collect { state ->
                when (state) {
                    SharedBoardState.Loading -> Unit
                    SharedBoardState.Missing -> if (!creating) {
                        creating = true
                        try { repo.create(chatId, settingsStore.settings.first().defaultGrid) }
                        catch (e: Exception) { if (e is CancellationException) throw e; creating = false; fail(e) }
                    }
                    is SharedBoardState.Ready -> _ui.update { it.copy(doc = state.board.toDocument(), loading = false, error = null, truncated = state.board.truncated) }
                    is SharedBoardState.Failed -> _ui.update { it.copy(loading = false, error = state.message) }
                }
            }
        }
    }

    private fun fail(e: Throwable) { _ui.update { it.copy(error = repo.describe(e), busy = false) } }
    fun clearError() { _ui.update { it.copy(error = null) } }
    private fun refreshFlags() { _ui.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) } }

    fun commitGesture(@Suppress("UNUSED_PARAMETER") before: CanvasDocument, after: CanvasDocument, @Suppress("UNUSED_PARAMETER") revision: Long) = submit(after)
    fun commit(after: CanvasDocument) = submit(after)

    private fun submit(after: CanvasDocument) {
        val current = _ui.value.doc ?: return
        val delta = SharedBoardOps.delta(current.objects, after.objects)
        if (delta.isEmpty) return
        viewModelScope.launch {
            try {
                SharedBoardOps.checkLimit(current.objects.size, delta)
                repo.apply(chatId, delta.adds, delta.removes.map { it.id })
                undoStack.addLast(delta); while (undoStack.size > 60) undoStack.removeFirst()
                redoStack.clear(); refreshFlags()
            } catch (e: Exception) { if (e is CancellationException) throw e; fail(e) }
        }
    }

    fun undo(redo: Boolean = false) {
        val from = if (redo) redoStack else undoStack; val to = if (redo) undoStack else redoStack
        val step = from.removeLastOrNull() ?: return
        val present = _ui.value.doc?.objects?.map { it.id }?.toSet().orEmpty()
        val plan = SharedBoardOps.reverse(present, step)
        if (plan.isEmpty) { refreshFlags(); _ui.update { it.copy(error = "Нечего ${if (redo) "повторять" else "отменять"}: объекты уже изменены другими") }; return }
        viewModelScope.launch {
            try {
                repo.apply(chatId, plan.adds, plan.removes.map { it.id })
                to.addLast(plan); refreshFlags()
            } catch (e: Exception) { if (e is CancellationException) throw e; from.addLast(step); refreshFlags(); fail(e) }
        }
    }

    fun addText(point: CanvasPoint, text: String, color: Int, size: Float) {
        val doc = _ui.value.doc ?: return
        submit(doc.copy(objects = doc.objects + CanvasObject(kind = CanvasObjectKind.TEXT, points = listOf(point), text = text.trim(), color = color, fontSize = size)))
    }

    fun putFormula(point: CanvasPoint, source: String, size: Float, color: Int, expected: CanvasObject?): Boolean = try {
        val doc = _ui.value.doc ?: return false
        if (expected != null) require(doc.objects.any { it == expected }) { "Формула уже изменена или удалена. Откройте её заново." }
        val normalized = CanvasMath.normalize(source)
        val layout = CanvasMathNative.layout(normalized, size)
        val bounds = CanvasMathLayout.bounds(point, layout, doc)
        val obj = CanvasObject(kind = CanvasObjectKind.FORMULA, points = bounds, text = normalized, fontSize = size, color = color)
        submit(doc.copy(objects = doc.objects.filterNot { it.id == expected?.id } + obj))
        true
    } catch (e: Exception) { if (e is CancellationException) throw e; _ui.update { it.copy(error = e.message ?: "Формулу не удалось добавить") }; false }

    fun updateGrid(grid: CanvasGrid) {
        viewModelScope.launch { try { repo.updateGrid(chatId, grid) } catch (e: Exception) { if (e is CancellationException) throw e; fail(e) } }
    }
    fun reportLimit() { _ui.update { it.copy(error = "Слишком длинный штрих: рисунок на общей доске ограничен") } }

    override fun onCleared() { CanvasMathNative.clearCache(); super.onCleared() }
}
