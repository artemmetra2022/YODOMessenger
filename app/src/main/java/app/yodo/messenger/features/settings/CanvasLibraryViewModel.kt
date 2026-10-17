package app.yodo.messenger.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.features.canvas.CanvasSavedBoard
import app.yodo.messenger.features.canvas.CanvasStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class CanvasLibraryState(
    val boards: List<CanvasSavedBoard> = emptyList(), val loading: Boolean = false,
    val busy: Boolean = false, val error: String? = null
)

@HiltViewModel
class CanvasLibraryViewModel @Inject constructor(
    private val store: CanvasStore, private val auth: FirebaseAuth, private val firestore: FirebaseFirestore
) : ViewModel() {
    private val _state=MutableStateFlow(CanvasLibraryState())
    val state=_state.asStateFlow()
    private var owner=auth.currentUser?.uid
    private var job: Job?=null
    private val authListener=FirebaseAuth.AuthStateListener {
        if(owner!=it.currentUser?.uid) {
            job?.cancel();owner=it.currentUser?.uid;_state.value=CanvasLibraryState();refresh()
        }
    }
    init { auth.addAuthStateListener(authListener);refresh() }
    private fun checkOwner(uid: String) { check(auth.currentUser?.uid==uid && owner==uid) { "Аккаунт изменился" } }
    fun refresh() {
        if(_state.value.busy)return
        val uid=owner?:run { _state.value=CanvasLibraryState(error="Войдите в аккаунт, чтобы увидеть свои доски");return }
        job?.cancel()
        job=viewModelScope.launch {
            _state.update { it.copy(loading=true,error=null) }
            try {
                val initial=store.listAll(uid);checkOwner(uid);_state.update { it.copy(boards=initial) }
                if(initial.any { it.chatId==null }) {
                    // Legacy boards stored only a hash of the chat. Try local cache first, then server.
                    val query=firestore.collection("chats").whereArrayContains("participantIds",uid)
                    val known=mutableSetOf<String>()
                    try { known+=withTimeout(3_000) { query.get(Source.CACHE).await().documents.map { it.id } } }
                    catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException)throw e }
                    var boards=store.listAll(uid,known);checkOwner(uid);_state.update { it.copy(boards=boards) }
                    if(boards.any { it.chatId==null }) {
                        try { known+=withTimeout(6_000) { query.get(Source.SERVER).await().documents.map { it.id } } }
                        catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException)throw e }
                        boards=store.listAll(uid,known);checkOwner(uid);_state.update { it.copy(boards=boards) }
                    }
                }
            } catch(e: Exception) {
                if(e is CancellationException)throw e
                if(owner==uid)_state.update { it.copy(error=e.message?:"Не удалось прочитать список досок") }
            } finally { if(owner==uid)_state.update { it.copy(loading=false) } }
        }
    }
    fun rename(entry: CanvasSavedBoard, title: String) = mutate { uid -> store.renameSaved(uid,entry,title) }
    fun delete(entry: CanvasSavedBoard) = mutate { uid -> store.deleteSaved(uid,entry) }
    fun clearError() { _state.update { it.copy(error=null) } }
    private fun mutate(block: suspend (String)->Unit) {
        if(_state.value.busy)return
        val uid=owner?:return
        job?.cancel()
        _state.update { it.copy(busy=true,loading=false,error=null) }
        job=viewModelScope.launch {
            try {
                checkOwner(uid);block(uid);checkOwner(uid)
                val boards=store.listAll(uid);checkOwner(uid);_state.update { it.copy(boards=boards) }
            } catch(e: Exception) {
                if(e is CancellationException)throw e
                if(owner==uid)_state.update { it.copy(error=e.message?:"Не удалось изменить доску") }
            } finally { if(owner==uid)_state.update { it.copy(busy=false) } }
        }
    }
    override fun onCleared() { auth.removeAuthStateListener(authListener);super.onCleared() }
}
