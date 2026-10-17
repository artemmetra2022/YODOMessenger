package app.yodo.messenger.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yodo.messenger.features.canvas.CanvasSavedBoard
import java.text.DateFormat
import java.util.Date

@Composable
internal fun CanvasSavedBoardCard(entry: CanvasSavedBoard, enabled: Boolean, onOpen: ()->Unit, onRename: ()->Unit, onDelete: ()->Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(entry.title,style=MaterialTheme.typography.titleMedium)
            Text(buildString {
                if(entry.modifiedAt>0)append(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.modifiedAt)))
                entry.objectCount?.let { append(" · Объектов: $it") }
            },style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            entry.error?.let { Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick=onOpen,enabled=enabled&&entry.readable&&entry.chatId!=null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Icon(Icons.Default.Edit,null);Spacer(Modifier.width(8.dp));Text("Открыть и редактировать")
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=onRename,enabled=enabled&&entry.readable,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=8.dp)) { Text("Переименовать") }
                TextButton(onClick=onDelete,enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=8.dp),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) { Text("Удалить") }
            }
        }
    }
}

@Composable
internal fun CanvasLibraryDialogs(rename: CanvasSavedBoard?, delete: CanvasSavedBoard?, enabled: Boolean,
    onDismiss: ()->Unit, onRename: (CanvasSavedBoard,String)->Unit, onDelete: (CanvasSavedBoard)->Unit) {
    rename?.let { entry ->
        var title by remember(entry.id,entry.storageChatHash) { mutableStateOf(entry.title) }
        AlertDialog(onDismissRequest=onDismiss,title={Text("Название доски")},text={
            OutlinedTextField(value=title,onValueChange={if(it.length<=100)title=it},label={Text("Название")},singleLine=true,modifier=Modifier.fillMaxWidth(),supportingText={Text("${title.length}/100")})
        },confirmButton={TextButton(enabled=enabled&&title.isNotBlank(),onClick={onRename(entry,title);onDismiss()}) {Text("Сохранить")}},
            dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
    }
    delete?.let { entry ->
        AlertDialog(onDismissRequest=onDismiss,title={Text("Удалить доску?")},text={Text("«${entry.title}» будет полностью удалена с этого устройства. Отменить это действие нельзя. Сообщения и общая доска чата не изменятся.")},
            confirmButton={TextButton(enabled=enabled,onClick={onDelete(entry);onDismiss()},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Удалить")}},
            dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
    }
}
