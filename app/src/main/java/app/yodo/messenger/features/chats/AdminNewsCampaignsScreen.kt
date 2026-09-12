package app.yodo.messenger.features.chats

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminNewsCampaignsScreen(onBack: () -> Unit, viewModel: AdminNewsCampaignsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val campaigns by viewModel.campaigns.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val views by viewModel.selectedViews.collectAsState()
    val unread by viewModel.selectedUnread.collectAsState()
    val comments by viewModel.selectedComments.collectAsState()
    var titleA by rememberSaveable { mutableStateOf("") }
    var titleB by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var audience by rememberSaveable { mutableStateOf("ALL") }
    var classes by rememberSaveable { mutableStateOf("") }
    var windowStart by rememberSaveable { mutableStateOf("") }
    var windowEnd by rememberSaveable { mutableStateOf("") }
    var scheduledAt by rememberSaveable { mutableStateOf("") }
    var pushAt by rememberSaveable { mutableStateOf("") }
    var pushWindowStart by rememberSaveable { mutableStateOf("") }
    var pushWindowEnd by rememberSaveable { mutableStateOf("") }
    var ab by rememberSaveable { mutableStateOf(false) }
    var push by rememberSaveable { mutableStateOf(true) }
    var commentsEnabled by rememberSaveable { mutableStateOf(true) }
    var templateName by rememberSaveable { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var inspectCampaignId by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var showComments by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> attachments = uris }

    val parseMinute: (String)->Int? = { value -> runCatching { val p=value.trim().split(":"); require(p.size==2); (p[0].toInt()*60+p[1].toInt()).coerceIn(0,1439) }.getOrNull() }
    Scaffold(topBar = { TopAppBar(title={Text("Рассылки и новости")}, navigationIcon={ IconButton(onClick=onBack){Icon(Icons.Filled.ArrowBack, "Назад")} }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("Создание новости", style=MaterialTheme.typography.titleLarge) }
            item { OutlinedTextField(titleA,{titleA=it},Modifier.fillMaxWidth(),label={Text("Заголовок A")}) }
            if (ab) item { OutlinedTextField(titleB,{titleB=it},Modifier.fillMaxWidth(),label={Text("Заголовок B")}) }
            item { OutlinedTextField(body,{body=it},Modifier.fillMaxWidth().heightIn(min=150.dp),label={Text("Текст новости")}) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(audience=="ALL",{audience="ALL"},{Text("Все" )}); FilterChip(audience=="ONLINE",{audience="ONLINE"},{Text("Онлайн")}); FilterChip(audience=="CLASSES",{audience="CLASSES"},{Text("Классы")}); FilterChip(audience=="GROUPS",{audience="GROUPS"},{Text("Группы")}) } }
            if (audience=="CLASSES" || audience=="GROUPS") item { OutlinedTextField(classes,{classes=it},Modifier.fillMaxWidth(),label={Text("ID классов через запятую")}) }
            item { OutlinedTextField(scheduledAt,{scheduledAt=it},Modifier.fillMaxWidth(),label={Text("Дата/время новости (epoch ms)")}) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(windowStart,{windowStart=it},Modifier.weight(1f),label={Text("Окно с HH:mm")}); OutlinedTextField(windowEnd,{windowEnd=it},Modifier.weight(1f),label={Text("Окно до HH:mm")}) } }
            item { Row { Checkbox(ab,{ab=it}); Text("A/B-тест заголовков",Modifier.padding(top=12.dp)) } }
            item { Row { Checkbox(push,{push=it}); Text("Отдельный push",Modifier.padding(top=12.dp)) } }
            item { Row { Checkbox(commentsEnabled,{commentsEnabled=it}); Text("Разрешить комментарии",Modifier.padding(top=12.dp)) } }
            if (push) {
                item { OutlinedTextField(pushAt,{pushAt=it},Modifier.fillMaxWidth(),label={Text("Push дата/время (epoch ms)")}) }
                item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(pushWindowStart,{pushWindowStart=it},Modifier.weight(1f),label={Text("Push с HH:mm")}); OutlinedTextField(pushWindowEnd,{pushWindowEnd=it},Modifier.weight(1f),label={Text("Push до HH:mm")}) } }
            }
            item { Button(onClick={picker.launch(arrayOf("application/pdf","text/*","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))}) { Icon(Icons.Filled.AttachFile,null); Spacer(Modifier.width(8.dp)); Text(if(attachments.isEmpty()) "Прикрепить документы" else "Выбрано: ${attachments.size}") } }
            item { Button(onClick={
                viewModel.saveCampaign(titleA,titleB,body,audience,classes.split(',').map{it.trim()}.filter{it.isNotEmpty()},scheduledAt.toLongOrNull(),parseMinute(windowStart),parseMinute(windowEnd),push,pushAt.toLongOrNull(),parseMinute(pushWindowStart),parseMinute(pushWindowEnd),ab,commentsEnabled) { id -> viewModel.uploadAttachments(id,attachments,context.contentResolver); attachments=emptyList() }
            },enabled=titleA.isNotBlank()&&body.isNotBlank()){Text("Сохранить и запланировать")} }
            item { HorizontalDivider(); Text("Редактор / превью",style=MaterialTheme.typography.titleMedium) }
            item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(titleA.ifBlank{"Заголовок новости"},style=MaterialTheme.typography.titleLarge); Spacer(Modifier.height(6.dp)); Text(body.ifBlank{"Здесь появится превью текста новости."}); if(attachments.isNotEmpty()){Spacer(Modifier.height(6.dp));Text("📎 Документов: ${attachments.size}")} } } }
            item { Text("Шаблоны",style=MaterialTheme.typography.titleMedium) }
            item { OutlinedTextField(templateName,{templateName=it},Modifier.fillMaxWidth(),label={Text("Название шаблона")}) }
            item { Button(onClick={viewModel.saveTemplate(templateName,titleA,body)},enabled=templateName.isNotBlank()&&titleA.isNotBlank()){Text("Сохранить шаблон") } }
            items(templates){t-> Text("• ${t["name"] ?: "Без названия"}") }
            item { Text("Архив / статистика",style=MaterialTheme.typography.titleMedium) }
            item { OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("Поиск по старым новостям")}) }
            items(campaigns.filter { c -> search.isBlank() || (c["titleA"] as? String ?: "").contains(search,true) || (c["body"] as? String ?: "").contains(search,true) }){c-> val id=c["id"] as? String ?: return@items; Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${c["titleA"] ?: ""} • ${c["status"] ?: ""}",style=MaterialTheme.typography.titleMedium); Text("Просмотры: ${c["viewCount"] ?: c["openedCount"] ?: 0} • реакции: ${c["reactionCount"] ?: 0} • комментарии: ${c["commentCount"] ?: 0}"); Text("A/B: ${c["clicksA"] ?: 0}/${c["clicksB"] ?: 0} • CTR ${"%.2f".format((c["ctrA"] as? Number)?.toDouble() ?: 0.0)}/${"%.2f".format((c["ctrB"] as? Number)?.toDouble() ?: 0.0)}"); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={inspectCampaignId=id;viewModel.loadViews(id,(c["audienceType"] as? String) ?: "ALL",(c["audienceClassIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList())}){Text("Кто прочитал")};Button(onClick={inspectCampaignId=id;showComments=true;viewModel.loadComments(id)}){Text("Комментарии")};Button(onClick={context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${c["archiveUrl"] ?: "about:blank"}")))}){Text("Архив")}} } } }
        }
    }
    if (inspectCampaignId != null && !showComments) {
        val reactionSummary = views.filter { it.reacted.isNotBlank() }.groupingBy { it.reacted }.eachCount().entries.joinToString("  ") { "${it.key} ${it.value}" }
        AlertDialog(onDismissRequest={inspectCampaignId=null},confirmButton={TextButton({inspectCampaignId=null}){Text("Закрыть")}},title={Text("Просмотры и реакции")},text={Column{Text("Уникально прочитали: ${views.size}"); if(reactionSummary.isNotBlank()) Text("Реакции: $reactionSummary"); Text("Не прочитали: ${unread.size}"); unread.take(50).forEach{Text("Не прочитал: ${it.displayName} • класс ${it.classId}")}; views.take(100).forEach{Text("${it.displayName} • класс ${it.classId} • ${SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(Date(it.viewedAtMillis))}")} }})
    }
    if (showComments && inspectCampaignId != null) {
        AlertDialog(onDismissRequest={showComments=false},confirmButton={TextButton({showComments=false}){Text("Закрыть")}},title={Text("Комментарии")},text={Column{if(comments.isEmpty()) Text("Комментариев нет") else comments.forEach{c->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${c["displayName"] ?: "Пользователь"}: ${c["text"] ?: ""}",Modifier.weight(1f));TextButton({viewModel.deleteComment(inspectCampaignId!!,c["id"] as String)}){Text("Удалить")}}}}})
    }
}
