package app.yodo.messenger.features.chats

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yodo.messenger.domain.model.FaqQuestion
import app.yodo.messenger.domain.model.FaqSection
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AdminFaqScreen(
    onBack: () -> Unit,
    viewModel: AdminFaqViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val sections by viewModel.sections.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val history by viewModel.history.collectAsState()
    val message by viewModel.message.collectAsState()
    var editing by remember { mutableStateOf<FaqQuestion?>(null) }
    var editSection by remember { mutableStateOf<FaqSection?>(null) }
    val context = LocalContext.current
    val exportType = remember { mutableStateOf("json") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@rememberLauncherForActivityResult
        if (uri.toString().contains("json", true) || text.trimStart().startsWith("[")) viewModel.importJson(text) else viewModel.importCsv(text)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // The selected export type is stored in the tag below via exportType.
        when (exportType.value) {
            "json" -> context.contentResolver.openOutputStream(uri)?.let(viewModel::exportJson)
            "csv" -> context.contentResolver.openOutputStream(uri)?.let(viewModel::exportCsv)
            "word" -> context.contentResolver.openOutputStream(uri)?.let(viewModel::exportWord)
            "pdf" -> context.contentResolver.openOutputStream(uri)?.let(viewModel::exportPdf)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("FAQ — управление") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            })
        }
    ) { padding ->
        if (!viewModel.isAdmin) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Доступ только для администраторов") }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { importLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream")) }) { Icon(Icons.Filled.ImportExport, null); Spacer(Modifier.width(6.dp)); Text("Импорт") }
                        OutlinedButton(onClick = { exportType.value="json"; exportLauncher.launch("yodo_faq.json") }) { Text("JSON") }
                        OutlinedButton(onClick = { exportType.value="pdf"; exportLauncher.launch("yodo_faq.pdf") }) { Text("PDF") }
                        OutlinedButton(onClick = { exportType.value="word"; exportLauncher.launch("yodo_faq.doc") }) { Text("Word") }
                    }
                }
                item { Text("Статистика FAQ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                val all = sections.flatMap { s -> s.questions.map { s to it } }
                val totalViews = all.sumOf { stats[it.second.id]?.views ?: 0 }
                item { Text("Всего просмотров: $totalViews • вопросов: ${all.size}") }
                all.sortedBy { stats[it.second.id]?.views ?: 0 }.take(5).forEach { (s,q) ->
                    val st=stats[q.id] ?: AdminFaqViewModel.FaqStat(0,0,0)
                    item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                            Text(q.question, fontWeight=FontWeight.SemiBold)
                            Text("${st.views} просмотров • 👍 ${st.helpful} • 👎 ${st.notHelpful}", style=MaterialTheme.typography.bodySmall)
                            if (st.views < 3) Text("Кандидат на удаление/переработку", color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.labelSmall)
                        }}
                    }
                }
                item { Text("Вопросы и ответы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier=Modifier.padding(top=8.dp)) }
                items(sections, key={it.id}) { section ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${section.emoji} ${section.title}", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
                            section.questions.forEach { q ->
                                Row(Modifier.fillMaxWidth().clickable { editing=q; editSection=section }.padding(vertical=10.dp), verticalAlignment=Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(q.question); Text("${stats[q.id]?.views ?: 0} просмотров", style=MaterialTheme.typography.labelSmall) }
                                    Icon(Icons.Filled.Edit, null)
                                }
                            }
                        }
                    }
                }
                item { Text("История версий", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold, modifier=Modifier.padding(top=8.dp)) }
                items(history, key={it.id}) { h ->
                    ListItem(headlineContent={Text(h.action)}, supportingContent={Text("${h.author} • ${h.createdAt?.let { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(it) } ?: "дата обрабатывается"} • ${h.questionCount} вопросов")}, leadingContent={Icon(Icons.Filled.History,null)})
                }
            }
        }
    }

    message?.let { Text(it) }
    if (editing != null && editSection != null) {
        FaqEditDialog(editing!!, editSection!!, onDismiss={editing=null}, onSave={updated ->
            val newSections=sections.map { s -> if(s.id==editSection!!.id) s.copy(questions=s.questions.map{if(it.id==updated.id) updated else it}) else s }
            viewModel.save(newSections); editing=null
        })
    }
}


@Composable
private fun FaqEditDialog(q: FaqQuestion, section: FaqSection, onDismiss:()->Unit, onSave:(FaqQuestion)->Unit) {
    var question by remember(q.id) { mutableStateOf(q.question) }
    var answer by remember(q.id) { mutableStateOf(q.answer) }
    AlertDialog(onDismissRequest=onDismiss, title={Text("Редактор FAQ")}, text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Превью", style=MaterialTheme.typography.labelLarge)
            Card { Column(Modifier.padding(12.dp)) { Text(question, fontWeight=FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(answer) } }
            OutlinedTextField(question,{question=it},label={Text("Вопрос")},singleLine=true)
            OutlinedTextField(answer,{answer=it},label={Text("Ответ")},minLines=5)
        }
    }, confirmButton={Button(onClick={onSave(FaqQuestion(q.id,question,answer))}){Text("Сохранить")}}, dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
}
