package app.yodo.messenger.features.canvas

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/**
 * Две независимые совместные сессии (рядом через Nearby и онлайн через Firestore) под одним фасадом.
 * Одновременно работает только одна; экран и ViewModel видят одно общее состояние.
 */
class CanvasCollabHub(
    nearbyTransport: CanvasTransport, private val onlineTransport: CanvasOnlineTransport, scope: CoroutineScope,
    getDocument: () -> CanvasDocument?, onDocument: suspend (CanvasDocument) -> Unit,
    onError: (String) -> Unit, onEnd: () -> Unit
) {
    private val nearby = CanvasCollaboration(nearbyTransport, scope, getDocument, onDocument, onError, onEnd)
    private val online = CanvasCollaboration(onlineTransport, scope, getDocument, onDocument, onError, onEnd)
    private val active get() = if (online.running) online else nearby
    val running get() = nearby.running || online.running
    val state: StateFlow<CanvasCollabState> = combine(nearby.state, online.state) { near, net ->
        if (net.link.phase != CanvasLinkPhase.IDLE) net else near
    }.stateIn(scope, SharingStarted.Eagerly, CanvasCollabState())
    fun start(host: Boolean) { if (!running) nearby.start(host) }
    fun startOnline(host: Boolean) { if (!running) online.start(host) }
    fun join(code: String) { if (!running) onlineTransport.join(code) }
    fun connect(peer: CanvasPeer) = nearby.connect(peer)
    fun verify(matches: Boolean) = active.verify(matches)
    fun stop() = active.stop()
    fun edit(after: CanvasDocument) = active.edit(after)
    fun editGesture(before: CanvasDocument, after: CanvasDocument, baseRevision: Long) = active.editGesture(before, after, baseRevision)
    fun undo(redo: Boolean) = active.undo(redo)
    fun cursor(point: CanvasPoint?, color: Int) = active.cursor(point, color)
    fun close() { nearby.close(); online.close() }
}
