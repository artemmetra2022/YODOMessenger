package app.yodo.messenger.features.canvas

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class CanvasCollabState(val link:CanvasLinkState=CanvasLinkState(),val active:Boolean=false,val syncing:Boolean=false,
    val pending:Boolean=false,val revision:Long=0,val canUndo:Boolean=false,val canRedo:Boolean=false,val cursor:CanvasRemoteCursor?=null)

/** All mutations are sequenced under one mutex; guests render only host-confirmed operations. */
class CanvasCollaboration(private val transport:CanvasTransport,private val scope:CoroutineScope,
    private val getDocument:()->CanvasDocument?,private val onDocument:suspend (CanvasDocument)->Unit,
    private val onError:(String)->Unit,private val onEnd:()->Unit) {
    private val _state=MutableStateFlow(CanvasCollabState());val state=_state.asStateFlow()
    val running get()=transport.state.value.phase!=CanvasLinkPhase.IDLE
    private val mutex=Mutex()
    private var authority:CanvasAuthority?=null
    private var wireDoc:CanvasDocument?=null
    private var localId:String?=null
    private var session:String?=null
    private var sequence=0L
    private var cursorSequence=0L
    private var lastPeerCursor=-1L
    private var pendingId:String?=null
    private var timeout:Job?=null
    private var cursorJob:Job?=null
    private var nextCursor:CanvasWire.Cursor?=null
    private var lastCursorSent=0L
    private fun now()=System.nanoTime()/1_000_000
    init {
        scope.launch {transport.state.collect { link->
            val linkEpoch=transport.epoch
            val previous=_state.value.link.phase;_state.update{it.copy(link=link)}
            if(link.phase!=previous) {
                timeout?.cancel()
                when(link.phase) {
                    CanvasLinkPhase.CONNECTED->{
                        session=requireNotNull(link.room);sequence=0;cursorSequence=0;lastPeerCursor=-1;pendingId=null
                        if(!link.host){authority=null;wireDoc=null;localId=UUID.randomUUID().toString()}
                        _state.update{it.copy(syncing=true,active=false)}
                        timeout=scope.launch{delay(60000);if(linkEpoch==transport.epoch&&_state.value.syncing)transport.stop("Синхронизация не завершилась. Попробуйте подключиться снова.")}
                        scope.launch {guard(linkEpoch) {mutex.withLock {
                            if(linkEpoch!=transport.epoch)return@withLock
                            session=requireNotNull(link.room);sequence=0;cursorSequence=0;lastPeerCursor=-1;pendingId=null
                            if(link.host) {
                                val doc=requireNotNull(getDocument());localId=doc.id;wireDoc=doc
                                val auth=CanvasAuthority(doc,UUID.randomUUID().toString(),UUID.randomUUID().toString());authority=auth
                                withTimeout(60000){transport.send(CanvasCollabWire.encode(CanvasWire.Snapshot(session!!,doc,0,auth.hostActor,auth.guestActor)))}
                                if(linkEpoch!=transport.epoch)return@withLock
                                timeout?.cancel();_state.update{it.copy(active=true,syncing=false,revision=0)}
                            }
                        } } }
                    }
                    CanvasLinkPhase.CONNECTING,CanvasLinkPhase.VERIFYING-> { timeout=scope.launch{delay(transport.connectTimeoutMs);if(linkEpoch==transport.epoch)transport.stop("Время подключения или сверки кода истекло")}}
                    CanvasLinkPhase.IDLE->{
                        val hadSession=wireDoc!=null
                        authority=null;wireDoc=null;session=null;pendingId=null;cursorJob?.cancel();nextCursor=null
                        _state.value=CanvasCollabState(link=link)
                        if(hadSession)onEnd()
                        link.error?.let(onError)
                    }
                    else->Unit
                }
            } else if(link.phase==CanvasLinkPhase.IDLE&&link.error!=null)onError(link.error)
        } }
        scope.launch {transport.incoming.collect { bytes->guard {
            val message=withContext(Dispatchers.Default){CanvasCollabWire.decode(bytes)}
            mutex.withLock { if(transport.state.value.phase==CanvasLinkPhase.CONNECTED)receive(message) }
        } } }
        scope.launch {while(isActive){delay(500);_state.update{if(it.cursor!=null&&now()-it.cursor.seenAt>3000)it.copy(cursor=null)else it}}}
    }
    fun start(host:Boolean){if(running)return;if(host)transport.host()else transport.discover()}
    fun connect(peer:CanvasPeer)=transport.connect(peer)
    fun verify(matches:Boolean)=transport.verify(matches)
    fun stop()=transport.stop()
    private suspend fun guard(epoch:Long=transport.epoch,block:suspend ()->Unit){try{block()}catch(e:Exception){if(e is CancellationException && e !is TimeoutCancellationException)throw e;if(epoch==transport.epoch)transport.stop(e.message?.take(200)?:"Ошибка протокола доски")}}
    private fun sessionOf(m:CanvasWire)=when(m){is CanvasWire.Snapshot->m.session;is CanvasWire.Propose->m.session;is CanvasWire.Committed->m.session;is CanvasWire.Rejected->m.session;is CanvasWire.Cursor->m.session}
    private suspend fun receive(message:CanvasWire){
        require(sessionOf(message)==transport.state.value.room) { "Другая сессия доски" }
        val host=transport.state.value.host
        val epoch=transport.epoch
        when(message){
            is CanvasWire.Snapshot->{
                require(!host&&_state.value.syncing&&wireDoc==null&&message.revision==0L&&message.hostActor!=message.guestActor)
                session=message.session;wireDoc=message.doc
                onDocument(message.doc.copy(id=requireNotNull(localId)))
                if(epoch!=transport.epoch)return
                timeout?.cancel();_state.update{it.copy(active=true,syncing=false,revision=0,canUndo=false,canRedo=false)}
            }
            is CanvasWire.Propose->{
                require(host&&_state.value.active);val auth=requireNotNull(authority)
                val committed=try{withContext(Dispatchers.Default){auth.execute(auth.guestActor,message.command)}}catch(e:IllegalArgumentException){
                    transport.send(CanvasCollabWire.encode(CanvasWire.Rejected(message.session,message.command.id,auth.revision,e.message?:"Операция отклонена")));return
                }
                applyHost(committed)
                transport.send(CanvasCollabWire.encode(CanvasWire.Committed(message.session,committed)))
            }
            is CanvasWire.Committed->{
                require(!host&&_state.value.active&&message.commit.revision==_state.value.revision+1) { "Пропуск операции. Требуется новое подключение." }
                val next=message.commit.delta.apply(requireNotNull(wireDoc));wireDoc=next
                onDocument(next.copy(id=requireNotNull(localId)))
                val own=message.commit.requestId==pendingId
                if(own){pendingId=null;timeout?.cancel()}
                _state.update{it.copy(revision=message.commit.revision,canUndo=message.commit.guestUndo,canRedo=message.commit.guestRedo,pending=if(own)false else it.pending)}
            }
            is CanvasWire.Rejected->{
                require(!host&&message.requestId==pendingId&&message.revision==_state.value.revision)
                pendingId=null;timeout?.cancel();_state.update{it.copy(pending=false)};onError(message.reason)
            }
            is CanvasWire.Cursor->{
                if(!_state.value.active||message.sequence<=lastPeerCursor)return
                val doc=wireDoc?:return;message.point?.let{require(it.x<=doc.width&&it.y<=doc.height)}
                lastPeerCursor=message.sequence;_state.update{it.copy(cursor=message.point?.let{p->CanvasRemoteCursor(p,message.color,now())})}
            }
        }
    }
    private suspend fun applyHost(commit:CanvasCommit){
        val doc=requireNotNull(authority).document;wireDoc=doc;onDocument(doc.copy(id=requireNotNull(localId)))
        _state.update{it.copy(revision=commit.revision,canUndo=commit.hostUndo,canRedo=commit.hostRedo)}
    }
    fun edit(after:CanvasDocument){
        if(!_state.value.active||_state.value.pending)return
        val before=wireDoc?:return
        try{val delta=CanvasDelta.between(before.copy(id=requireNotNull(localId)),after);if(delta!=CanvasDelta())submit(CanvasCommandKind.EDIT,delta)}
        catch(e:Exception){onError(e.message?:"Операция недоступна в сессии")}
    }
    fun editGesture(before:CanvasDocument,after:CanvasDocument,baseRevision:Long){
        if(!_state.value.active||_state.value.pending)return
        try{require(before.id==localId);val delta=CanvasDelta.between(before,after)
            if(delta!=CanvasDelta())submit(CanvasCommandKind.EDIT,delta,baseRevision)
        }catch(e:Exception){onError(e.message?:"Жест не применён")}
    }
    fun undo(redo:Boolean){if(!_state.value.active||_state.value.pending)return;submit(if(redo)CanvasCommandKind.REDO else CanvasCommandKind.UNDO,CanvasDelta())}
    private fun submit(kind:CanvasCommandKind,delta:CanvasDelta,baseRevision:Long=_state.value.revision){
        val room=session?:return;val command=CanvasCommand(sequence=++sequence,baseRevision=baseRevision,kind=kind,delta=delta)
        val epoch=transport.epoch
        pendingId=command.id;_state.update{it.copy(pending=true)}
        scope.launch{guard(epoch) {mutex.withLock {
            if(epoch!=transport.epoch)return@withLock
            require(room==session&&transport.state.value.phase==CanvasLinkPhase.CONNECTED)
            if(transport.state.value.host){
                val auth=requireNotNull(authority)
                val committed=try{withContext(Dispatchers.Default){auth.execute(auth.hostActor,command)}}catch(e:IllegalArgumentException){pendingId=null;_state.update{it.copy(pending=false)};onError(e.message?:"Операция отклонена");return@withLock}
                applyHost(committed);transport.send(CanvasCollabWire.encode(CanvasWire.Committed(room,committed)))
                pendingId=null;_state.update{it.copy(pending=false)}
            }else{
                transport.send(CanvasCollabWire.encode(CanvasWire.Propose(room,command)))
                timeout?.cancel();timeout=scope.launch{delay(30000);if(epoch==transport.epoch&&pendingId==command.id)transport.stop("Нет подтверждения операции. Последняя неподтверждённая правка не применена.")}
            }
        } } }
    }
    fun cursor(point:CanvasPoint?,color:Int){
        if(!_state.value.active)return
        val time=now();if(point!=null&&time-lastCursorSent<80)return
        val room=session?:return;lastCursorSent=time
        nextCursor=CanvasWire.Cursor(room,++cursorSequence,point,color or 0xff000000.toInt())
        if(cursorJob?.isActive==true)return
        val epoch=transport.epoch
        cursorJob=scope.launch{guard(epoch) {while(true){val message=nextCursor?:break;nextCursor=null
            if(message.session!=session)break
            transport.cursor(CanvasCollabWire.encode(message))
        }}}
    }
    fun close(){transport.stop()}
}
