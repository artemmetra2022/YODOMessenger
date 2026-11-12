package app.yodo.messenger.features.canvas

import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.OutlinedButton
import app.yodo.messenger.ui.components.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class FormulaPreviewResult(val source:String,val size:Float,val layout:MathLayout?,val error:String?)

@Composable
fun CanvasFormulaDialog(existing:CanvasObject?,defaultSize:Float,color:Int,enabled:Boolean,
    onDismiss:()->Unit,onInsert:(String,Float)->String?){
    var source by remember(existing?.id){mutableStateOf(existing?.text?:"")}
    var size by remember(existing?.id){mutableFloatStateOf(existing?.fontSize?:defaultSize)}
    var insertionError by remember{mutableStateOf<String?>(null)}
    val preview by produceState<FormulaPreviewResult?>(null,source,size){
        value=null;delay(200)
        value=withContext(Dispatchers.Default){try{FormulaPreviewResult(source,size,CanvasMathNative.layout(source,size),null)}
            catch(e:IllegalArgumentException){FormulaPreviewResult(source,size,null,e.message?:"Проверьте формулу")}}
    }
    val current=preview?.takeIf{it.source==source&&it.size==size}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null)"Формула LaTeX"else"Изменить формулу")},
        text={Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Школьное подмножество LaTeX · без интернета",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=source,onValueChange={if(it.length<=CanvasMath.MAX_SOURCE+4){source=it;insertionError=null}},
                label={Text("Формула")},placeholder={Text("\\frac{a}{b} + \\sqrt{x}")},minLines=2,maxLines=5,
                textStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace),modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                CanvasMath.examples.forEach{(label,latex)->OutlinedButton(onClick={source=latex;insertionError=null}){Text(label)}}
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf(24f,32f,48f,64f).forEach{value->FilterChip(selected=size==value,onClick={size=value;insertionError=null},label={Text("${value.toInt()} px")})}
            }
            val layout=current?.layout
            if(layout!=null)AndroidView(factory={context->CanvasFormulaPreview(context)},modifier=Modifier.fillMaxWidth().height(150.dp),update={view->view.layout=layout;view.inkColor=color;view.contentDescription="Предпросмотр формулы: $source";view.invalidate()})
            else if(source.isNotBlank()&&current==null)LinearProgressIndicator(Modifier.fillMaxWidth())
            (insertionError?:current?.error)?.takeIf{source.isNotBlank()}?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Text("Поддерживаются дроби, корни, степени/индексы, греческие буквы, знаки, скобки и основные функции. Макросы, матрицы, многострочная вёрстка и произвольные команды TeX не поддерживаются.",style=MaterialTheme.typography.bodySmall)
            Text("Чтобы изменить готовую формулу, выберите «Формула» и нажмите на неё. Замена — одно действие отмены.",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(enabled=enabled&&current?.layout!=null,onClick={
            val error=onInsert(source,size);if(error==null)onDismiss()else insertionError=error
        }){Text(if(existing==null)"Добавить"else"Заменить")}},dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
}
