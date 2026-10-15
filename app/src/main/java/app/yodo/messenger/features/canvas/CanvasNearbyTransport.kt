package app.yodo.messenger.features.canvas

import android.content.Context
import app.yodo.messenger.offline.NearbyRadioLease
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import javax.inject.Inject

/** A separate service/protocol, never the mesh auto-accept or public-node-ID derived crypto. */
class CanvasNearbyTransport @Inject constructor(@ApplicationContext context:Context) : CanvasTransport {
    private val client=Nearby.getConnectionsClient(context)
    private val token="canvas-${UUID.randomUUID()}"
    private val _state=MutableStateFlow(CanvasLinkState());override val state=_state.asStateFlow()
    private val packets=Channel<ByteArray>(8);override val incoming=packets.receiveAsFlow()
    private val assembler=CanvasFrames.Assembler()
    private val sending=Mutex()
    private val transfers=mutableMapOf<Long,CompletableDeferred<Unit>>()
    private var endpoint:String?=null
    private var verifiedLocally=false
    private var generation=0L
    override val epoch:Long get()=generation
    private fun now()=System.nanoTime()/1_000_000
    private val service="app.yodo.messenger.CANVAS_V3"
    override fun host(){ start(true) }
    override fun discover(){ start(false) }
    private fun start(host:Boolean){
        stop()
        if(!NearbyRadioLease.acquire(token)){_state.value=CanvasLinkState(error="Nearby занят офлайн-чатом. Сначала закройте офлайн-чат.");return}
        val gen=generation;val room=if(host)UUID.randomUUID().toString()else null
        _state.value=CanvasLinkState(if(host)CanvasLinkPhase.HOSTING else CanvasLinkPhase.SEARCHING,host,room)
        try {
            if(host)client.startAdvertising("YodoBoard/$room",service,lifecycle(gen),AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
                .addOnFailureListener{if(gen==generation)stop("Не удалось включить доску рядом: ${it.localizedMessage.orEmpty().take(100)}")}
            else client.startDiscovery(service,object:EndpointDiscoveryCallback(){
                override fun onEndpointFound(id:String,info:DiscoveredEndpointInfo){
                    if(gen!=generation||_state.value.phase!=CanvasLinkPhase.SEARCHING)return
                    val found=info.endpointName.removePrefix("YodoBoard/")
                    if(info.serviceId!=service||!info.endpointName.startsWith("YodoBoard/")||!CanvasLimits.validId(found))return
                    _state.update{it.copy(peers=(it.peers.filterNot{p->p.endpoint==id}+CanvasPeer(id,found)).take(20))}
                }
                override fun onEndpointLost(id:String){if(gen==generation)_state.update{it.copy(peers=it.peers.filterNot{p->p.endpoint==id})}}
            },DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()).addOnFailureListener{if(gen==generation)stop("Поиск Nearby недоступен: ${it.localizedMessage.orEmpty().take(100)}")}
        }catch(e:Exception){stop(e.message?:"Нет разрешений Nearby")}
    }
    override fun connect(peer:CanvasPeer){
        if(_state.value.phase!=CanvasLinkPhase.SEARCHING||peer !in _state.value.peers)return
        endpoint=peer.endpoint;val gen=generation
        _state.update{it.copy(phase=CanvasLinkPhase.CONNECTING,room=peer.room,peers=emptyList())}
        try{client.stopDiscovery();client.requestConnection("YodoGuest/${UUID.randomUUID()}",peer.endpoint,lifecycle(gen))
            .addOnFailureListener{if(gen==generation)stop("Не удалось подключиться")}}
        catch(e:Exception){stop(e.message?:"Ошибка подключения")}
    }
    private fun lifecycle(gen:Long)=object:ConnectionLifecycleCallback(){
        override fun onConnectionInitiated(id:String,info:ConnectionInfo){
            if(gen!=generation)return
            val state=_state.value
            val valid=if(state.host) state.phase==CanvasLinkPhase.HOSTING&&endpoint==null&&info.endpointName.startsWith("YodoGuest/")&&CanvasLimits.validId(info.endpointName.removePrefix("YodoGuest/"))
                else state.phase==CanvasLinkPhase.CONNECTING&&id==endpoint&&info.endpointName=="YodoBoard/${state.room}"
            val code=info.authenticationDigits
            if(!valid||code.isBlank()||code.length>32) { runCatching{client.rejectConnection(id)};return }
            endpoint=id;_state.update{it.copy(phase=CanvasLinkPhase.VERIFYING,code=code)}
        }
        override fun onConnectionResult(id:String,resolution:ConnectionResolution){
            if(gen!=generation||id!=endpoint)return
            if(resolution.status.statusCode!=ConnectionsStatusCodes.STATUS_OK){stop("Подключение не подтверждено");return}
            if(!verifiedLocally||_state.value.phase!=CanvasLinkPhase.CONNECTING){stop("Неподтверждённое подключение");return}
            try{client.stopAdvertising();client.stopDiscovery()}catch(_:Exception){stop("Доступ к Nearby отозван");return}
            _state.update{it.copy(phase=CanvasLinkPhase.CONNECTED,code=null,peers=emptyList())}
        }
        override fun onDisconnected(id:String){if(gen==generation&&id==endpoint)stop("Связь потеряна. Подтверждённые изменения сохранены локально. Создайте подключение заново.")}
    }
    override fun verify(matches:Boolean){
        if(_state.value.phase!=CanvasLinkPhase.VERIFYING)return
        val id=endpoint?:return
        if(!matches){runCatching{client.rejectConnection(id)};stop("Сверка кода отменена");return}
        val gen=generation;verifiedLocally=true;_state.update{it.copy(phase=CanvasLinkPhase.CONNECTING,code=null)}
        try{client.acceptConnection(id,object:PayloadCallback(){
            override fun onPayloadReceived(peer:String,payload:Payload){
                if(gen!=generation||peer!=endpoint||_state.value.phase!=CanvasLinkPhase.CONNECTED)return
                try{val bytes=requireNotNull(payload.asBytes());val assembled=assembler.accept(bytes,now())
                    if(assembled!=null&&!packets.trySend(assembled).isSuccess)stop("Перегрузка канала доски. Подключитесь заново.")
                }catch(_:Exception){stop("Получен некорректный пакет доски")}
            }
            override fun onPayloadTransferUpdate(peer:String,update:PayloadTransferUpdate){
                if(gen!=generation||peer!=endpoint)return
                when(update.status){
                    PayloadTransferUpdate.Status.SUCCESS->transfers.remove(update.payloadId)?.complete(Unit)
                    PayloadTransferUpdate.Status.FAILURE,PayloadTransferUpdate.Status.CANCELED->transfers.remove(update.payloadId)?.completeExceptionally(IllegalStateException("Передача прервана"))
                }
            }
        }).addOnFailureListener{if(gen==generation)stop("Не удалось подтвердить подключение")}}
        catch(e:Exception){stop(e.message?:"Ошибка подтверждения")}
    }
    override suspend fun send(bytes:ByteArray)=sending.withLock{sendLocked(bytes)}
    override suspend fun cursor(bytes:ByteArray){if(!sending.tryLock())return;try{sendLocked(bytes)}finally{sending.unlock()}}
    private suspend fun sendLocked(bytes:ByteArray){
        check(_state.value.phase==CanvasLinkPhase.CONNECTED);val id=requireNotNull(endpoint);val gen=generation
        for(frame in CanvasFrames.split(bytes)){
            check(gen==generation&&_state.value.phase==CanvasLinkPhase.CONNECTED)
            val payload=Payload.fromBytes(frame);val done=CompletableDeferred<Unit>();transfers[payload.id]=done
            try{withTimeout(20000){client.sendPayload(id,payload).await();done.await()}}finally{transfers.remove(payload.id)}
        }
    }
    override fun stop(error:String?){
        generation++
        if(NearbyRadioLease.owns(token)){
            runCatching{client.stopAdvertising()};runCatching{client.stopDiscovery()};runCatching{client.stopAllEndpoints()}
            NearbyRadioLease.release(token)
        }
        endpoint=null;verifiedLocally=false;assembler.clear();transfers.values.forEach{it.completeExceptionally(IllegalStateException("Соединение закрыто"))};transfers.clear()
        while(packets.tryReceive().isSuccess)Unit
        _state.value=CanvasLinkState(error=error)
    }
}
