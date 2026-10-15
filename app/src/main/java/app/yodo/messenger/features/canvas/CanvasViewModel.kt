package app.yodo.messenger.features.canvas

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.SendMessageResult
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class CanvasUiState(val doc: CanvasDocument?=null,val background: Bitmap?=null,val busy:Boolean=true,
    val saved:Boolean=false,val canUndo:Boolean=false,val canRedo:Boolean=false,val error:String?=null,val notice:String?=null)

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val savedState: SavedStateHandle, private val auth: FirebaseAuth,
    private val messages: MessageRepository, private val store: CanvasStore,
    transport: CanvasNearbyTransport,
    @ApplicationContext private val context: Context
): ViewModel() {
    private val owner=auth.currentUser?.uid.orEmpty()
    private val chat=savedState.get<String>("chatId").orEmpty()
    private val topic=savedState.get<String>("topicId")
    private val _state=MutableStateFlow(CanvasUiState())
    val state=_state.asStateFlow()
    private var history:CanvasHistory?=null
    private var autosave:Job?=null
    private val operation=Mutex()
    private val collab=CanvasCollaboration(transport,viewModelScope,{_state.value.doc}, { doc ->
        checkAccount()
        val background=if(doc.backgroundBase64==_state.value.doc?.backgroundBase64)_state.value.background else withContext(Dispatchers.IO){doc.backgroundBase64?.let(CanvasImages::fromBase64)}
        checkAccount();history=CanvasHistory(doc);savedState["activeCanvasId"]=doc.id
        _state.update{it.copy(doc=doc,background=background,saved=false,canUndo=false,canRedo=false)};scheduleSave()
    }, { error -> _state.update{it.copy(error=error)} }, {
        _state.value.doc?.let{history=CanvasHistory(it)};_state.update{it.copy(canUndo=false,canRedo=false)};scheduleSave()
    })
    val collaboration=collab.state
    fun startCollaboration(host:Boolean)=task {saveCurrent();collab.start(host)}
    fun connectCanvasPeer(peer:CanvasPeer)=collab.connect(peer)
    fun verifyCanvasPeer(matches:Boolean)=collab.verify(matches)
    fun stopCollaboration()=collab.stop()
    fun sendCursor(point:CanvasPoint?,color:Int)=collab.cursor(point,color)
    private fun localSettingsAllowed():Boolean {if(collab.running){_state.update{it.copy(error="Сначала завершите совместную сессию, чтобы сменить фон, шаблон или доску")};return false};return true}
    private val accountListener=FirebaseAuth.AuthStateListener { current ->
        if(current.currentUser?.uid!=owner){CanvasMathNative.clearCache();collab.stop();autosave?.cancel();history=null;_state.value=CanvasUiState(busy=false,error="Аккаунт изменился. Откройте доску заново.")}
    }
    override fun onCleared(){CanvasMathNative.clearCache();auth.removeAuthStateListener(accountListener);collab.close();super.onCleared()}
    private fun checkAccount(){ check(owner.isNotBlank() && auth.currentUser?.uid==owner) { "Аккаунт изменился. Откройте доску заново." } }
    init { auth.addAuthStateListener(accountListener);viewModelScope.launch { operation.withLock {
        try {
            checkAccount()
            val source=savedState.get<String>("sourceMessageId")
            val requested=savedState.get<String>("boardId")
            val resume=savedState.get<String>("activeCanvasId")
            val doc = if(resume!=null) requireNotNull(store.load(owner,chat,resume)) { "Локальная доска не найдена" }
            else if(source!=null) {
                val message=withTimeout(15000) { messages.observeMessage(chat,source).first() } ?: error("Сообщение недоступно")
                check(canImportCanvasImage(message.isViewOnce,message.isDeleted,message.expiresAt,System.currentTimeMillis())) { "Это изображение нельзя открыть на доске" }
                val local=if(requested!=null && message.senderId==owner && requested==message.canvasLocalId) store.load(owner,chat,requested) else null
                local ?: run {
                    val index=savedState.get<Int>("imageIndex")?:0
                    val image=if(message.imagesBase64.isNotEmpty()) message.imagesBase64.getOrNull(index) else message.imageBase64
                    val bitmap=withContext(Dispatchers.IO) { CanvasImages.fromBase64(requireNotNull(image)) }
                    val encoded=try { withContext(Dispatchers.IO) {
                        CanvasImages.background(bitmap)
                    } } finally { bitmap.recycle() }
                    CanvasDocument(title="Разбор изображения",grid=CanvasGrid.NONE,backgroundBase64=encoded)
                }
            } else if(requested!=null) requireNotNull(store.load(owner,chat,requested)) { "Локальная доска не найдена" }
            else CanvasDocument(title="Новая доска")
            install(doc); saveCurrent(); _state.update { it.copy(busy=false) }
        } catch(e:Exception) { if(e is CancellationException) throw e; _state.update { it.copy(busy=false,error=e.message?:"Не удалось открыть доску") } }
    } } }
    private suspend fun install(doc:CanvasDocument) {
        checkAccount(); CanvasLimits.validate(doc)
        val background=withContext(Dispatchers.IO) { doc.backgroundBase64?.let(CanvasImages::fromBase64) }
        checkAccount();history=CanvasHistory(doc); savedState["activeCanvasId"]=doc.id
        _state.value=CanvasUiState(doc=doc,background=background,busy=true)
    }
    private suspend fun saveCurrent() {
        checkAccount(); val doc=_state.value.doc?:return
        store.save(owner,chat,doc); checkAccount()
        _state.update { if(it.doc==doc) it.copy(saved=true) else it }
    }
    private fun scheduleSave(){ autosave?.cancel(); autosave=viewModelScope.launch {
        delay(600); operation.withLock { try { saveCurrent() } catch(e:Exception) { if(e is CancellationException) throw e; fail(e) } }
    } }
    private fun fail(e:Exception){ _state.update { it.copy(error=e.message?:"Операция не выполнена",busy=false) } }
    fun reportLimit(){_state.update { it.copy(error="Достигнут лимит: 300 объектов, 2048 точек в штрихе или 60000 точек на доске") }}
    fun clearFeedback(){_state.update{it.copy(error=null,notice=null)}}
    fun commit(doc:CanvasDocument){ if(_state.value.busy) return
        try { checkAccount()
            if(collab.running){collab.edit(doc);return}
            val h=history?:return; h.commit(doc)
            _state.update{it.copy(doc=h.current,saved=false,canUndo=h.canUndo,canRedo=h.canRedo)}; scheduleSave()
        } catch(e:Exception){ fail(e) }
    }
    fun commitGesture(before:CanvasDocument,after:CanvasDocument,revision:Long){
        if(_state.value.busy)return
        try{checkAccount();if(collab.running)collab.editGesture(before,after,revision)else commit(after)}catch(e:Exception){fail(e)}
    }
    fun updateGrid(grid:CanvasGrid){if(!localSettingsAllowed())return;_state.value.doc?.let{commit(it.copy(grid=grid))}}
    fun updateTemplate(template:CanvasTemplate){if(!localSettingsAllowed())return;_state.value.doc?.let{commit(it.copy(template=template))}}
    fun rename(title:String){if(!localSettingsAllowed())return;_state.value.doc?.let{commit(it.copy(title=title.trim()))}}
    fun addText(point:CanvasPoint,text:String,color:Int,size:Float){_state.value.doc?.let{
        commit(it.copy(objects=it.objects+CanvasObject(kind=CanvasObjectKind.TEXT,points=listOf(point),text=text.trim(),color=color,fontSize=size)))
    }}
    fun putFormula(point:CanvasPoint,source:String,size:Float,color:Int,expected:CanvasObject?):Boolean {
        if(_state.value.busy)return false
        if(collab.running&&(!collab.state.value.active||collab.state.value.pending)){_state.update{it.copy(error="Подождите подтверждения или синхронизации")};return false}
        return try {
            checkAccount();val doc=_state.value.doc?:return false
            if(expected!=null)require(doc.objects.any{it==expected}){"Формула уже изменена или удалена. Откройте её заново."}
            val normalized=CanvasMath.normalize(source);val layout=CanvasMathNative.layout(normalized,size)
            val bounds=CanvasMathLayout.bounds(point,layout,doc)
            val obj=CanvasObject(kind=CanvasObjectKind.FORMULA,points=bounds,text=normalized,fontSize=size,color=color)
            val next=doc.copy(objects=doc.objects.filterNot{it.id==expected?.id}+obj)
            CanvasLimits.validate(next);commit(next);true
        }catch(e:Exception){fail(e);false}
    }
    private fun task(block:suspend ()->Unit){ if(_state.value.busy) return; autosave?.cancel(); _state.update{it.copy(busy=true,error=null)}
        viewModelScope.launch { operation.withLock { try { checkAccount(); block() } catch(e:Exception){if(e is CancellationException)throw e;fail(e)} finally {_state.update{it.copy(busy=false)}} } }
    }
    fun undo(redo:Boolean=false) { if(collab.running){collab.undo(redo);return};task {
        val h=history?:return@task; val doc=if(redo)h.redo() else h.undo()
        val background=if(doc.backgroundBase64==_state.value.doc?.backgroundBase64) _state.value.background else withContext(Dispatchers.IO){doc.backgroundBase64?.let(CanvasImages::fromBase64)}
        _state.update{it.copy(doc=doc,background=background,saved=false,canUndo=h.canUndo,canRedo=h.canRedo)}; saveCurrent()
    }
    }
    fun save(onSaved:()->Unit={})=task { saveCurrent(); onSaved() }
    fun newBoard()=task {check(!collab.running) { "Сначала завершите совместную сессию" }; saveCurrent(); install(CanvasDocument(title="Новая доска")); _state.update{it.copy(busy=true)}; saveCurrent() }
    fun openBoard(id:String)=task {check(!collab.running) { "Сначала завершите совместную сессию" }; saveCurrent(); install(requireNotNull(store.load(owner,chat,id))); _state.update{it.copy(busy=true)}; saveCurrent() }
    fun recent(onLoaded:(List<CanvasRecent>)->Unit)=task {check(!collab.running) { "Сначала завершите совместную сессию" }; saveCurrent(); onLoaded(store.recent(owner,chat)) }
    fun importImage(uri:Uri)=task {check(!collab.running) { "Сначала завершите совместную сессию" };
        val doc=_state.value.doc?:return@task
        val encoded=withContext(Dispatchers.IO){CanvasImages.importUri(context,uri)}
        val bitmap=withContext(Dispatchers.IO){CanvasImages.fromBase64(encoded)}
        checkAccount(); val h=history?:return@task; h.commit(doc.copy(backgroundBase64=encoded,grid=CanvasGrid.NONE))
        _state.update{it.copy(doc=h.current,background=bitmap,saved=false,canUndo=h.canUndo,canRedo=h.canRedo)}; saveCurrent()
    }
    fun publish()=task {
        val doc=_state.value.doc?:return@task; saveCurrent()
        val background=_state.value.background
        val image=withContext(Dispatchers.Default){Base64.encodeToString(CanvasImages.png(doc,background,true),Base64.NO_WRAP)}
        checkAccount()
        when(val result=messages.sendCanvasSnapshot(chat,image,doc.title,doc.id,topic)){
            is SendMessageResult.Error -> error(result.message)
            is SendMessageResult.Success -> _state.update{it.copy(notice="Превью отправлено в чат. Редактируемая доска осталась на устройстве.")}
        }
    }
    fun export(uri:Uri)=task {
        val doc=_state.value.doc?:return@task; saveCurrent(); val background=_state.value.background
        withContext(Dispatchers.IO) { val png=CanvasImages.png(doc,background,false); checkAccount()
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(png) } }
        _state.update{it.copy(notice="PNG сохранён")}
    }
}
