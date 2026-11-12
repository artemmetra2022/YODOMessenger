package app.yodo.messenger.features.canvas

import app.yodo.messenger.ui.components.Button
import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.DropdownMenuItem
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(onBackClick:()->Unit, viewModel:CanvasViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val collab by viewModel.collaboration.collectAsState()
    val sessionRunning=collab.link.phase!=CanvasLinkPhase.IDLE
    val sessionReady=!sessionRunning || (collab.active&&!collab.pending)
    val localSettings=!sessionRunning
    var tool by remember { mutableStateOf(CanvasTool.PEN) }
    var color by remember { mutableIntStateOf(0xff1e293b.toInt()) }
    var width by remember { mutableFloatStateOf(4f) }
    var fontSize by remember { mutableFloatStateOf(32f) }
    var shapeAssist by remember { mutableStateOf(true) }
    var snapGrid by remember { mutableStateOf(false) }
    var hasSelection by remember { mutableStateOf(false) }
    var drawingView by remember { mutableStateOf<CanvasDrawingView?>(null) }
    var textPoint by remember { mutableStateOf<CanvasPoint?>(null) }
    var formulaPoint by remember { mutableStateOf<CanvasPoint?>(null) }
    var editingFormula by remember { mutableStateOf<CanvasObject?>(null) }
    var enteredText by remember { mutableStateOf("") }
    var exiting by remember { mutableStateOf(false) }
    var exitWarning by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var publish by remember { mutableStateOf(false) }
    var createNew by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CanvasRecent?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf<List<CanvasRecent>?>(null) }
    var moreMenu by remember { mutableStateOf(false) }
    var boardOptions by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var collaborationMenu by remember { mutableStateOf(false) }
    val workspaceColor = MaterialTheme.colorScheme.surfaceVariant
    val paperEdgeColor = MaterialTheme.colorScheme.outlineVariant
    val snack=remember { SnackbarHostState() }
    val boardSettings by viewModel.boardSettings.collectAsState()
    // Значения по умолчанию из настроек раздела «Интерактивная доска».
    LaunchedEffect(boardSettings){color=boardSettings.inkColor;width=boardSettings.inkWidth;shapeAssist=boardSettings.shapeAssist;snapGrid=boardSettings.snapToGrid}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importImage) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri -> uri?.let(viewModel::export) }
    fun leave(){ if(state.doc==null) onBackClick() else { exiting=true; viewModel.save { exiting=false; onBackClick() } } }
    BackHandler { if(!state.busy) leave() }
    LaunchedEffect(state.doc?.id){if(state.doc==null){formulaPoint=null;editingFormula=null;textPoint=null;enteredText=""}}
    LaunchedEffect(state.error,state.notice) {
        if(state.error!=null && exiting){ exiting=false; exitWarning=true }
        (state.error?:state.notice)?.let { snack.showSnackbar(it); viewModel.clearFeedback() }
    }
    Scaffold(
        topBar={TopAppBar(title={Column {
            Text(state.doc?.title?:"Интерактивная доска",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
            Text(if(state.busy) "Подождите…" else if(sessionRunning) {
                if(collab.syncing) "Совместно · синхронизация…" else if(collab.pending) "Совместно · подтверждение…"
                else if(collab.active) "Совместно · версия ${collab.revision}" else "Совместно · подключение…"
            } else if(state.saved) "Локально · сохранено" else "Есть изменения",style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
        }},navigationIcon={CanvasActionIcon(Icons.AutoMirrored.Filled.ArrowBack,"Назад",!state.busy){leave()}},
            actions={
                CanvasActionIcon(Icons.Default.Group,"Рисовать вместе",!state.busy&&state.doc!=null){collaborationMenu=true}
                Box {
                    CanvasActionIcon(Icons.Default.MoreVert,"Действия с доской"){moreMenu=true}
                    DropdownMenu(expanded=moreMenu,onDismissRequest={moreMenu=false}) {
                        DropdownMenuItem(text={Text("Настройки холста")},onClick={moreMenu=false;boardOptions=true})
                        DropdownMenuItem(text={Text("Мои доски")},enabled=!state.busy&&localSettings,onClick={moreMenu=false;viewModel.recent{recent=it}})
                        DropdownMenuItem(text={Text("Новая доска")},enabled=!state.busy&&localSettings,onClick={moreMenu=false;createNew=true})
                        DropdownMenuItem(text={Text("Переименовать")},enabled=!state.busy&&state.doc!=null&&localSettings,onClick={moreMenu=false;title=state.doc?.title.orEmpty();rename=true})
                        DropdownMenuItem(text={Text("Добавить картинку")},enabled=!state.busy&&state.doc!=null&&localSettings,onClick={moreMenu=false;confirmImport=true})
                        DropdownMenuItem(text={Text("Экспорт PNG")},enabled=!state.busy&&state.doc!=null,onClick={moreMenu=false;export.launch("yodo-canvas.png")})
                        HorizontalDivider()
                        DropdownMenuItem(text={Text("Очистить всё",color=MaterialTheme.colorScheme.error)},enabled=!state.busy&&state.doc!=null&&localSettings,onClick={moreMenu=false;confirmClear=true})
                        DropdownMenuItem(text={Text("Удалить доску",color=MaterialTheme.colorScheme.error)},enabled=!state.busy&&state.doc!=null&&localSettings,onClick={moreMenu=false;state.doc?.let{deleting=CanvasRecent(it.id,it.title)}})
                        HorizontalDivider()
                        DropdownMenuItem(text={Text("Как рисовать")},onClick={moreMenu=false;help=true})
                    }
                }
            })},
        snackbarHost={SnackbarHost(snack)}
    ) { padding -> Column(Modifier.fillMaxSize().padding(padding)) {
        if(state.busy || collab.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        // Keep permission launchers and incoming verification dialogs composed while the sheet is closed.
        CanvasCollabPanel(viewModel,collab,enabled=!state.busy&&state.doc!=null,
            controlsExpanded=collaborationMenu,onDismiss={collaborationMenu=false})
        CanvasHistoryBar(
            canUndo=(if(sessionRunning)collab.canUndo else state.canUndo)&&!state.busy&&sessionReady,
            canRedo=(if(sessionRunning)collab.canRedo else state.canRedo)&&!state.busy&&sessionReady,
            enabled=state.doc!=null,onUndo={viewModel.undo()},onRedo={viewModel.undo(true)},onFit={drawingView?.resetViewport()}) {
            CanvasActionIcon(Icons.Default.Save,"Сохранить доску",!state.busy&&state.doc!=null){viewModel.save()}
            Button(enabled=!state.busy&&state.doc!=null&&sessionReady,onClick={publish=true},contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.heightIn(min=48.dp)){
                Text("В чат")
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(workspaceColor)) {
            val doc=state.doc
            if(doc!=null) AndroidView(modifier=Modifier.fillMaxSize(),factory={context->CanvasDrawingView(context).also{drawingView=it}},update={view->
                val changed=view.document?.id!=doc.id
                view.onSelectionChange={hasSelection=it};view.onEditError=viewModel::reportEditError
                view.workspaceColor=workspaceColor.toArgb();view.paperEdgeColor=paperEdgeColor.toArgb()
                view.document=doc;view.backgroundImage=state.background;view.tool=tool;view.inkColor=color;view.inkWidth=width;view.fontSize=fontSize;view.shapeAssist=shapeAssist;view.snapToGrid=snapGrid;view.haptics=boardSettings.haptics
                view.isEnabled=!state.busy&&sessionReady
                view.remoteCursor=collab.cursor
                view.onCursor={point->viewModel.sendCursor(point,color)}
                view.documentRevision=collab.revision
                view.onGesture=viewModel::commitGesture
                view.onCommit=viewModel::commit
                view.onText={point->textPoint=point;enteredText=""}
                view.onFormula={point,existing->formulaPoint=point;editingFormula=existing}
                view.onLimit={viewModel.reportLimit()}
                if(changed) view.resetViewport()
                view.invalidate()
            }) else Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Text(if(state.busy)"Открываем доску…" else state.error?:"Доска недоступна")
                if(!state.busy) TextButton(onClick={viewModel.newBoard()}){Text("Создать новую")}
            }
        }
        CanvasEditorDock(tool,{tool=it},color,{color=it},width,{width=it},fontSize,{fontSize=it},
            enabled=!state.busy&&state.doc!=null, onImage=if(localSettings) ({confirmImport=true}) else null,selectionView=drawingView,hasSelection=hasSelection)
    } }
    if(boardOptions) CanvasBoardOptions(state.doc?.grid,viewModel::updateGrid,!state.busy&&localSettings,
        shapeAssist,{shapeAssist=it},snapGrid,{snapGrid=it},{boardOptions=false},state.doc?.template,viewModel::updateTemplate)
    if(help) CanvasHelp { help=false }
    if(state.publishSent) AlertDialog(
        onDismissRequest={viewModel.dismissPublishConfirmation()},
        title={Text("Отправлено в чат")},
        text={Text("Превью доски успешно отправлено. Редактируемая доска сохранена на этом устройстве.")},
        confirmButton={TextButton(enabled=!state.busy,onClick={viewModel.dismissPublishConfirmation()}){Text("Продолжить рисовать")}},
        dismissButton={TextButton(enabled=!state.busy,onClick={viewModel.dismissPublishConfirmation();onBackClick()}){Text("В чат")}}
    )
    formulaPoint?.let { point -> CanvasFormulaDialog(editingFormula,fontSize,editingFormula?.color?:color,
        enabled=!state.busy&&sessionReady,onDismiss={formulaPoint=null;editingFormula=null},onInsert={source,size->
            if(viewModel.putFormula(point,source,size,editingFormula?.color?:color,editingFormula))null else viewModel.state.value.error?:"Формулу не удалось добавить"
        }) }
    textPoint?.let{point->AlertDialog(onDismissRequest={textPoint=null},title={Text("Текст на доске")},
        text={Column{Text("Для дробей, корней и индексов используйте отдельный инструмент «Формула».",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=enteredText,onValueChange={if(it.length<=500&&it.lines().size<=8)enteredText=it},label={Text("Текст (до 8 строк)")},maxLines=8)}},
        confirmButton={TextButton(enabled=enteredText.isNotBlank(),onClick={viewModel.addText(point,enteredText,color,fontSize);textPoint=null}){Text("Добавить")}},dismissButton={TextButton(onClick={textPoint=null}){Text("Отмена")}})}
    if(exitWarning)AlertDialog(onDismissRequest={exitWarning=false},title={Text("Доска не сохранена")},text={Text("Сохранение не удалось. Остаться в редакторе и повторить попытку или выйти без сохранения последних изменений?")},confirmButton={TextButton(onClick={exitWarning=false;onBackClick()}){Text("Выйти без сохранения")}},dismissButton={TextButton(onClick={exitWarning=false}){Text("Остаться")}})
    if(rename)AlertDialog(onDismissRequest={rename=false},title={Text("Название доски")},text={OutlinedTextField(value=title,onValueChange={if(it.length<=100)title=it},singleLine=true)},
        confirmButton={TextButton(enabled=title.isNotBlank(),onClick={viewModel.rename(title);rename=false}){Text("Сохранить")}},dismissButton={TextButton(onClick={rename=false}){Text("Отмена")}})
    if(publish)AlertDialog(onDismissRequest={publish=false},title={Text("Отправить PNG-превью?")},text={Text("В чат отправится картинка, а не редактируемый проект. Получатель сможет открыть её как фон новой доски. Изображения пока не защищены сквозным шифрованием. Совместная сессия Nearby этой картинкой не передаётся.")},
        confirmButton={TextButton(onClick={publish=false;viewModel.publish()}){Text("Отправить")}},dismissButton={TextButton(onClick={publish=false}){Text("Отмена")}})
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Очистить доску?")},text={Text("Все рисунки, текст, формулы, фон и шаблон будут удалены без возможности отмены. Название и сетка сохранятся.")},confirmButton={TextButton(onClick={confirmClear=false;formulaPoint=null;editingFormula=null;textPoint=null;viewModel.clearBoard()}){Text("Очистить всё")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("Отмена")}})
    deleting?.let { target -> AlertDialog(onDismissRequest={deleting=null},title={Text("Удалить доску?")},text={Text("«${target.title}» будет удалена с этого устройства без возможности восстановления. Отправленные в чат изображения останутся.")},confirmButton={TextButton(onClick={val isCurrent=state.doc?.id==target.id;deleting=null;recent=null;viewModel.deleteBoard(target.id){if(isCurrent)onBackClick()}}){Text("Удалить")}},dismissButton={TextButton(onClick={deleting=null}){Text("Отмена")}}) }
    if(confirmImport)AlertDialog(onDismissRequest={confirmImport=false},title={Text("Открыть картинку")},text={Text("Картинка заменит текущий фон. Рисунок останется сверху. Это действие можно отменить.")},confirmButton={TextButton(onClick={confirmImport=false;gallery.launch(arrayOf("image/*"))}){Text("Выбрать")}},dismissButton={TextButton(onClick={confirmImport=false}){Text("Отмена")}})
    if(createNew)AlertDialog(onDismissRequest={createNew=false},title={Text("Создать новую доску?")},text={Text("Текущая доска сохранится в «Мои доски» для этого чата на этом устройстве.")},confirmButton={TextButton(onClick={createNew=false;viewModel.newBoard()}){Text("Создать")}},dismissButton={TextButton(onClick={createNew=false}){Text("Отмена")}})
    recent?.let{docs->AlertDialog(onDismissRequest={recent=null},title={Text("Мои доски · этот чат")},text={
        if(docs.isEmpty())Text("Сохранённых досок пока нет") else LazyColumn(Modifier.heightIn(max=320.dp)){items(docs,key={it.id}){doc->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(modifier=Modifier.weight(1f),onClick={recent=null;viewModel.openBoard(doc.id)}){Text(doc.title,maxLines=2,overflow=TextOverflow.Ellipsis)};TextButton(onClick={deleting=doc}){Text("Удалить")}}}}
    },confirmButton={TextButton(onClick={recent=null}){Text("Закрыть")}})}
}
