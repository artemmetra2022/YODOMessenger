package app.yodo.messenger.features.canvas

import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** One app-wide listener. Zero preserves realtime behavior; no periodic server polling. */
@Singleton
class BoardSyncSettings @Inject constructor(firestore: FirebaseFirestore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val intervalMs: StateFlow<Long> = callbackFlow {
        val registration = firestore.document("config/appSettings").addSnapshotListener { snap, error ->
            if (error == null) trySend(BoardSyncPolicy.normalize((snap?.get("boardSyncIntervalMs") as? Number)?.toLong()))
        }
        awaitClose { registration.remove() }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, BoardSyncPolicy.DEFAULT_MS)
}
