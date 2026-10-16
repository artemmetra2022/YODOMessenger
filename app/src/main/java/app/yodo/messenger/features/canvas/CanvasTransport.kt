package app.yodo.messenger.features.canvas

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class CanvasLinkPhase { IDLE, HOSTING, SEARCHING, CONNECTING, VERIFYING, CONNECTED }
data class CanvasPeer(val endpoint:String,val room:String)
/** online=true — сессия через интернет (этап 5); peerName — имя того, кто просит присоединиться/подключился. */
data class CanvasLinkState(val phase:CanvasLinkPhase=CanvasLinkPhase.IDLE,val host:Boolean=false,val room:String?=null,
    val code:String?=null,val peers:List<CanvasPeer> = emptyList(),val error:String?=null,
    val online:Boolean=false,val peerName:String?=null)

interface CanvasTransport {
    val state:StateFlow<CanvasLinkState>
    val incoming:Flow<ByteArray>
    val epoch:Long
    /** Сколько ждать подтверждения подключения. Онлайн дольше: ведущему нужно время, чтобы разрешить вход. */
    val connectTimeoutMs:Long get()=30000L
    fun host()
    fun discover()
    fun connect(peer:CanvasPeer)
    fun verify(matches:Boolean)
    suspend fun send(bytes:ByteArray)
    suspend fun cursor(bytes:ByteArray)
    fun stop(error:String?=null)
}
