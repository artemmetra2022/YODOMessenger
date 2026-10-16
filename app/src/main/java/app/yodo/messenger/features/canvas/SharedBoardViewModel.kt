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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharedBoardUi(
    val doc: CanvasDocument? = null, val loading: Boolean = true, val error: String? = null,
    val canUndo: Boolean = false, val canRedo: Boolean = false, val truncated: Boolean = false, val busy: Boolean = false,
    val generation: Long = 0L, val clearing: Boolean = false
)

/** Редактор общей доски чата. Свои правки отображаются локально; чужие — с интервалом из веб-админки. */
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

    init {
        if (chatId.isBlank()) _ui.value = SharedBoardUi(loading = false, error = "Чат не найден")
        else viewModelScope.launch {
            // Do not block cached board snapshots while the create-if-missing transaction waits for the server.
            launch {
                try { repo.create(chatId, settingsStore.settings.first().defaultGrid) }
                catch (e: Exception) { if (e is CancellationException) throw e; fail(e) }
            }
            repo.observe(chatId).collect { state ->
                when (state) {
                    SharedBoardState.Loading -> Unit
                    SharedBoardState.Missing -> {
                        undoStack.clear(); redoStack.clear()
                        _ui.update { it.copy(doc = null, loading = false, canUndo = false, canRedo = false, clearing = false) }
                    }
                    is SharedBoardState.Ready -> {
                        val changed = _ui.value.generation != state.board.generation
                        if (changed) { undoStack.clear(); redoStack.clear() }
                        _ui.update { it.copy(doc = state.board.toDocument(), loading = false, error = null,
                            truncated = state.board.truncated, generation = state.board.generation, clearing = state.board.clearing,
                            canUndo = !state.board.clearing && undoStack.isNotEmpty(), canRedo = !state.board.clearing && redoStack.isNotEmpty()) }
                    }
                    is SharedBoardState.Failed -> _ui.update { it.copy(loading = false, error = state.message) }
                }
            }
        }
    }

    private fun fail(e: Throwable) { _ui.update { it.copy(error = repo.describe(e), busy = false) } }
    fun clearError() { _ui.update { it.copy(error = null) } }
    private fun refreshFlags() { _ui.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) } }

    fun commitGesture(before: CanvasDocument, after: CanvasDocument, revision: Long) {
        if (revision != _ui.value.generation || _ui.value.busy || _ui.value.clearing) return
        val current = _ui.value.doc ?: return
        val delta = SharedBoardOps.delta(before.objects, after.objects)
        val removed = delta.removes.map { it.id }.toSet()
        val present = current.objects.map { it.id }.toSet()
        submit(current.copy(objects = current.objects.filterNot { it.id in removed } + delta.adds.filterNot { it.id in present }))
    }
    fun commit(after: CanvasDocument) = submit(after)

    private fun submit(after: CanvasDocument) {
        if (_ui.value.busy || _ui.value.clearing) return
        val generation = _ui.value.generation
        val current = _ui.value.doc ?: return
        val delta = SharedBoardOps.delta(current.objects, after.objects)
        if (delta.isEmpty) return
        viewModelScope.launch {
            try {
                SharedBoardOps.checkLimit(current.objects.size, delta)
                repo.apply(chatId, delta.adds, delta.removes.map { it.id }, generation)
                if (_ui.value.generation != generation) return@launch
                undoStack.addLast(delta); while (undoStack.size > 60) undoStack.removeFirst()
                redoStack.clear(); refreshFlags()
            } catch (e: Exception) { if (e is CancellationException) throw e; fail(e) }
        }
    }

    fun undo(redo: Boolean = false) {
        if (_ui.value.busy || _ui.value.clearing) return
        val generation = _ui.value.generation
        val from = if (redo) redoStack else undoStack; val to = if (redo) undoStack else redoStack
        val step = from.removeLastOrNull() ?: return
        val present = _ui.value.doc?.objects?.map { it.id }?.toSet().orEmpty()
        val plan = SharedBoardOps.reverse(present, step)
        if (plan.isEmpty) { refreshFlags(); _ui.update { it.copy(error = "Нечего ${if (redo) "повторять" else "отменять"}: объекты уже изменены другими") }; return }
        viewModelScope.launch {
            try {
                repo.apply(chatId, plan.adds, plan.removes.map { it.id }, generation)
                if (_ui.value.generation != generation) return@launch
                to.addLast(plan); refreshFlags()
            } catch (e: Exception) { if (e is CancellationException) throw e; if (_ui.value.generation == generation) from.addLast(step); refreshFlags(); fail(e) }
        }
    }

    fun addText(point: CanvasPoint, text: String, color: Int, size: Float) {
        val doc = _ui.value.doc ?: return
        submit(doc.copy(objects = doc.objects + CanvasObject(kind = CanvasObjectKind.TEXT, points = listOf(point), text = text.trim(), color = color, fontSize = size)))
    }

    fun putFormula(point: CanvasPoint, source: String, size: Float, color: Int, expected: CanvasObject?): Boolean {
        if (_ui.value.busy || _ui.value.clearing) return false
        return try {
        val doc = _ui.value.doc ?: return false
        if (expected != null) require(doc.objects.any { it == expected }) { "Формула уже изменена или удалена. Откройте её заново." }
        val normalized = CanvasMath.normalize(source)
        val layout = CanvasMathNative.layout(normalized, size)
        val bounds = CanvasMathLayout.bounds(point, layout, doc)
        val obj = CanvasObject(kind = CanvasObjectKind.FORMULA, points = bounds, text = normalized, fontSize = size, color = color)
        submit(doc.copy(objects = doc.objects.filterNot { it.id == expected?.id } + obj))
        true
        } catch (e: Exception) { if (e is CancellationException) throw e; _ui.update { it.copy(error = e.message ?: "Формулу не удалось добавить") }; false }
    }

    fun reset(deleteBoard: Boolean, onDone: () -> Unit = {}) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repo.reset(chatId, deleteBoard)
                undoStack.clear(); redoStack.clear(); refreshFlags()
                onDone()
            } catch (e: Exception) { if (e is CancellationException) throw e; fail(e) }
            finally { _ui.update { it.copy(busy = false) } }
        }
    }
    fun createBoard() {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { repo.create(chatId, settingsStore.settings.first().defaultGrid, revive = true) }
            catch (e: Exception) { if (e is CancellationException) throw e; fail(e) }
            finally { _ui.update { it.copy(busy = false) } }
        }
    }
    fun updateGrid(grid: CanvasGrid) {
        if (_ui.value.busy || _ui.value.clearing) return
        viewModelScope.launch { try { repo.updateGrid(chatId, grid) } catch (e: Exception) { if (e is CancellationException) throw e; fail(e) } }
    }
    fun reportLimit() { _ui.update { it.copy(error = "Слишком длинный штрих: рисунок на общей доске ограничен") } }

    override fun onCleared() { CanvasMathNative.clearCache(); super.onCleared() }
}
