package app.yodo.messenger.features.canvas

import app.yodo.messenger.ui.components.Button
import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.OutlinedButton
import app.yodo.messenger.ui.components.IconButton
import app.yodo.messenger.ui.components.FilterChip
import app.yodo.messenger.ui.components.DropdownMenuItem
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.yodo.messenger.ui.components.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var confirmImport by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf<List<CanvasRecent>?>(null) }
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
            Text(state.doc?.title?:"Интерактивная доска",maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(if(state.busy) "Подождите…" else if(state.saved) "Локально · сохранено" else "Локально · не сохранено",style=MaterialTheme.typography.labelSmall)
        }},navigationIcon={IconButton(enabled=!state.busy,onClick={leave()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}},
            actions={TextButton(enabled=!state.busy && state.doc!=null && localSettings,onClick={title=state.doc?.title.orEmpty();rename=true}){Text("Имя")}})},
        snackbarHost={SnackbarHost(snack)}
    ) { padding -> Column(Modifier.fillMaxSize().padding(padding)) {
        if(state.busy || collab.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        CanvasCollabPanel(viewModel,collab,enabled=!state.busy&&state.doc!=null)
        CanvasToolbar {
            CanvasTool.entries.forEach { option -> FilterChip(selected=tool==option,onClick={tool=option},enabled=!state.busy,
                label={Text(when(option){CanvasTool.PEN->"Карандаш";CanvasTool.HIGHLIGHTER->"Маркер";CanvasTool.LINE->"Линия";CanvasTool.ARROW->"Стрелка";CanvasTool.RECTANGLE->"Прямоугольник";CanvasTool.ROUNDED_RECT->"Скруглённый";CanvasTool.CIRCLE->"Окружность";CanvasTool.ELLIPSE->"Эллипс";CanvasTool.TRIANGLE->"Треугольник";CanvasTool.DIAMOND->"Ромб";CanvasTool.TEXT->"Текст";CanvasTool.FORMULA->"Формула";CanvasTool.ERASER->"Ластик";CanvasTool.HAND->"Рука"})}) }
        }
        CanvasToolbar {
            listOf("Чёрный" to 0xff1e293b.toInt(),"Синий" to 0xff2563eb.toInt(),"Красный" to 0xffdc2626.toInt(),"Зелёный" to 0xff16a34a.toInt(),"Фиолетовый" to 0xff9333ea.toInt(),"Жёлтый" to 0xffeab308.toInt(),"Оранжевый" to 0xfff97316.toInt()).forEach { (name,value) ->
                Box(Modifier.size(44.dp).semantics { contentDescription="Цвет: $name" }.clickable(enabled=!state.busy){color=value},contentAlignment=Alignment.Center) {
                    Box(Modifier.size(28.dp).background(Color(value),CircleShape).border(if(color==value)3.dp else 0.dp,MaterialTheme.colorScheme.onSurface,CircleShape))
                }
            }
            if(tool==CanvasTool.TEXT||tool==CanvasTool.FORMULA) listOf(24f,32f,48f).forEach { value->FilterChip(selected=fontSize==value,onClick={fontSize=value},label={Text("${value.toInt()} px")}) }
            else listOf(2f,4f,8f,12f).forEach{value->FilterChip(selected=width==value,onClick={width=value},label={Text("${value.toInt()} px")})}
        }
        CanvasToolbar {
            var gridMenu by remember{mutableStateOf(false)}; var templateMenu by remember{mutableStateOf(false)}
            Box { OutlinedButton(enabled=!state.busy&&localSettings,onClick={gridMenu=true}){Text(when(state.doc?.grid){CanvasGrid.SQUARES->"Клетка 24 px";CanvasGrid.DOTS->"Точки";else->"Без сетки"})}
                DropdownMenu(expanded=gridMenu,onDismissRequest={gridMenu=false}){CanvasGrid.entries.forEach{item->DropdownMenuItem(text={Text(when(item){CanvasGrid.NONE->"Без сетки";CanvasGrid.SQUARES->"Клетка 24 px";CanvasGrid.DOTS->"Точки"})},onClick={gridMenu=false;viewModel.updateGrid(item)})}} }
            Box { OutlinedButton(enabled=!state.busy&&localSettings,onClick={templateMenu=true}){Text("Шаблон")}
                DropdownMenu(expanded=templateMenu,onDismissRequest={templateMenu=false}){CanvasTemplate.entries.forEach{item->DropdownMenuItem(text={Text(when(item){CanvasTemplate.NONE->"Без шаблона";CanvasTemplate.AXES->"Оси X/Y";CanvasTemplate.TRIANGLE->"Треугольник";CanvasTemplate.RIGHT_TRIANGLE->"Прямоугольный треугольник"})},onClick={templateMenu=false;viewModel.updateTemplate(item)})}} }
            OutlinedButton(enabled=(if(sessionRunning)collab.canUndo else state.canUndo)&&!state.busy&&sessionReady,onClick={viewModel.undo()}){Text("Отмена")}
            OutlinedButton(enabled=(if(sessionRunning)collab.canRedo else state.canRedo)&&!state.busy&&sessionReady,onClick={viewModel.undo(true)}){Text("Повтор")}
            TextButton(onClick={drawingView?.resetViewport()}){Text("Вписать")}
            FilterChip(selected=shapeAssist,onClick={shapeAssist=!shapeAssist},label={Text("Умные фигуры")})
            FilterChip(selected=snapGrid,onClick={snapGrid=!snapGrid},label={Text("К сетке")})
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xffe9edf3))) {
            val doc=state.doc
            if(doc!=null) AndroidView(modifier=Modifier.fillMaxSize(),factory={context->CanvasDrawingView(context).also{drawingView=it}},update={view->
                val changed=view.document?.id!=doc.id
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
        Text("Два пальца — масштаб и перемещение · задержите палец после штриха карандашом — выровняется в фигуру · ластик удаляет объект целиком",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
        CanvasToolbar {
            Button(enabled=!state.busy&&state.doc!=null&&sessionReady,onClick={publish=true}){Text("Отправить в чат")}
            OutlinedButton(enabled=!state.busy&&state.doc!=null,onClick={viewModel.save()}){Text("Сохранить")}
            OutlinedButton(enabled=!state.busy&&state.doc!=null&&localSettings,onClick={confirmImport=true}){Text("Картинка")}
            OutlinedButton(enabled=!state.busy&&state.doc!=null,onClick={export.launch("yodo-canvas.png")}){Text("Экспорт PNG")}
            TextButton(enabled=!state.busy&&localSettings,onClick={viewModel.recent{recent=it}}){Text("Мои доски")}
            TextButton(enabled=!state.busy&&localSettings,onClick={createNew=true}){Text("Новая")}
        }
    } }
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
    if(confirmImport)AlertDialog(onDismissRequest={confirmImport=false},title={Text("Открыть картинку")},text={Text("Картинка заменит текущий фон. Рисунок останется сверху. Это действие можно отменить.")},confirmButton={TextButton(onClick={confirmImport=false;gallery.launch(arrayOf("image/*"))}){Text("Выбрать")}},dismissButton={TextButton(onClick={confirmImport=false}){Text("Отмена")}})
    if(createNew)AlertDialog(onDismissRequest={createNew=false},title={Text("Создать новую доску?")},text={Text("Текущая доска сохранится в «Мои доски» для этого чата на этом устройстве.")},confirmButton={TextButton(onClick={createNew=false;viewModel.newBoard()}){Text("Создать")}},dismissButton={TextButton(onClick={createNew=false}){Text("Отмена")}})
    recent?.let{docs->AlertDialog(onDismissRequest={recent=null},title={Text("Мои доски · этот чат")},text={
        if(docs.isEmpty())Text("Сохранённых досок пока нет") else LazyColumn(Modifier.heightIn(max=320.dp)){items(docs,key={it.id}){doc->TextButton(modifier=Modifier.fillMaxWidth(),onClick={recent=null;viewModel.openBoard(doc.id)}){Text(doc.title,maxLines=2,overflow=TextOverflow.Ellipsis)}}}
    },confirmButton={TextButton(onClick={recent=null}){Text("Закрыть")}})}
}
@Composable
internal fun CanvasToolbar(content:@Composable RowScope.()->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp,vertical=2.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically,content=content)}
