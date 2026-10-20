package app.yodo.messenger.features.settings

import app.yodo.messenger.ui.components.GlassScaffold
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.yodo.messenger.features.canvas.CanvasSavedBoard

/** The catalogue has its own navigation entry, scroll position and ViewModel. */
@Composable
fun CanvasLibraryScreen(
    onBackClick: () -> Unit,
    onOpenBoard: (String, String) -> Unit,
    viewModel: CanvasLibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner=LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner,viewModel) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME)viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    CanvasLibraryContent(state,onBackClick,onOpenBoard,viewModel::refresh,viewModel::rename,viewModel::delete,
        onDeleteSelected={viewModel.deleteSelected(it,state.accountKey)})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CanvasLibraryContent(
    state: CanvasLibraryState,
    onBackClick: () -> Unit,
    onOpenBoard: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onRename: (CanvasSavedBoard, String) -> Unit,
    onDelete: (CanvasSavedBoard) -> Unit,
    onDeleteSelected: (List<CanvasSavedBoard>) -> Unit = {}
) {
    val listState=rememberLazyListState()
    val selectionSaver=remember { listSaver<CanvasLibrarySelection,String>(
        save={listOf(if(it.active)"1" else "0")+it.keys.toList()},
        restore={CanvasLibrarySelection(it.firstOrNull()=="1",it.drop(1).toSet())}) }
    var selection by rememberSaveable(state.accountKey,stateSaver=selectionSaver) { mutableStateOf(CanvasLibrarySelection()) }
    var pendingDelete by remember(state.accountKey) { mutableStateOf<List<CanvasSavedBoard>?>(null) }
    val available=state.boards.map { "${it.storageChatHash}/${it.id}" }.toSet()
    val selected=state.boards.filter { "${it.storageChatHash}/${it.id}" in selection.keys }
    val enabled=!state.busy&&!state.loading
    BackHandler(enabled=selection.active||state.busy) { if(!state.busy)selection=CanvasLibrarySelection() }
    var renameBoard by remember(state.accountKey) { mutableStateOf<CanvasSavedBoard?>(null) }
    var deleteBoard by remember(state.accountKey) { mutableStateOf<CanvasSavedBoard?>(null) }
    LaunchedEffect(state.boards,state.accountKey) {
        selection=selection.retain(available)
        if(renameBoard!=null && state.boards.none { it==renameBoard })renameBoard=null
        if(deleteBoard!=null && state.boards.none { it==deleteBoard })deleteBoard=null
    }
    GlassScaffold(
        topBar={TopAppBar(
            title={Text(if(selection.active)"Выбрано: ${selected.size}" else "Сохранённые доски",
                style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)},
            navigationIcon={IconButton(enabled=!state.busy,onClick={if(selection.active)selection=CanvasLibrarySelection()else onBackClick()}) {
                Icon(if(selection.active)Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    if(selection.active)"Отменить выбор" else "Назад к настройкам доски")
            }},
            actions={if(!selection.active)IconButton(enabled=enabled&&state.boards.isNotEmpty(),onClick={selection=CanvasLibrarySelection(active=true)}) {
                Icon(Icons.Default.Checklist,"Выбрать доски")
            }}
        )}
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(selection.active)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(enabled=enabled&&available.isNotEmpty(),onClick={selection=selection.toggleAll(available)},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                    Text(if(available.isNotEmpty()&&selection.keys.containsAll(available))"Снять все"else "Выбрать все")
                }
                TextButton(enabled=enabled&&selected.isNotEmpty(),onClick={pendingDelete=selected.toList()},
                    modifier=Modifier.weight(1f).heightIn(min=48.dp),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {
                    Text("Удалить (${selected.size})")
                }
            }
            if(state.loading||state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.busy&&state.deleteTotal>0)Text("Обработано: ${state.deleteDone} / ${state.deleteTotal}",modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp))
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp)) }
            state.notice?.let { Text(it,modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp)) }
            LazyColumn(state=listState,modifier=Modifier.weight(1f).fillMaxWidth().testTag("saved_boards_list"),contentPadding=PaddingValues(bottom=24.dp)) {
                item(key="library_header") {
                    if(!selection.active) {
                        Text("Все локальные доски текущего аккаунта на этом устройстве.",
                            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier=Modifier.padding(horizontal=20.dp,vertical=12.dp))
                        TextButton(enabled=enabled,onClick=onRefresh,modifier=Modifier.padding(horizontal=12.dp).heightIn(min=48.dp)) {Text("Обновить список")}
                    }
                    if(state.boards.isEmpty()&&!state.loading&&state.error==null)Text("Сохранённых досок пока нет",modifier=Modifier.padding(20.dp))
                }
                items(state.boards,key={"${it.storageChatHash}/${it.id}"}) { entry ->
                    CanvasSavedBoardCard(entry,enabled,
                        {entry.chatId?.let { onOpenBoard(it,entry.id) }},{renameBoard=entry},{deleteBoard=entry},
                        selectionMode=selection.active,selected="${entry.storageChatHash}/${entry.id}" in selection.keys,
                        onToggle={selection=selection.toggle("${entry.storageChatHash}/${entry.id}")})
                }
            }
        }
    }
    pendingDelete?.let { entries ->
        AlertDialog(onDismissRequest={pendingDelete=null},title={Text("Удалить выбранные доски?")},
            text={Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState())) {
                Text("Досок: ${entries.size}. Они будут полностью удалены с этого устройства. Отменить удаление нельзя. Сообщения и общие доски чатов не изменятся.")
                Spacer(Modifier.height(12.dp))
                entries.take(5).forEach { Text("• ${it.title}") }
                if(entries.size>5)Text("И ещё: ${entries.size-5}")
            }},
            confirmButton={TextButton(enabled=enabled,onClick={pendingDelete=null;onDeleteSelected(entries)},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Text("Удалить ${entries.size}")}},
            dismissButton={TextButton(onClick={pendingDelete=null}) {Text("Отмена")}})
    }
    CanvasLibraryDialogs(renameBoard,deleteBoard,!state.busy,{renameBoard=null;deleteBoard=null},onRename,onDelete)
}
