package app.yodo.messenger.features.teachers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AdminTeachersScreen(onBack: () -> Unit, viewModel: TeacherViewModel = hiltViewModel()) {
    val teachers by viewModel.teachers.collectAsState()
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf(teachers.firstOrNull()?.userId.orEmpty()) }
    var uid by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var subjects by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf("0") }
    var showRating by rememberSaveable { mutableStateOf(true) }
    var vacationFrom by rememberSaveable { mutableStateOf("") }
    var vacationTo by rememberSaveable { mutableStateOf("") }
    var accepting by rememberSaveable { mutableStateOf(true) }
    var notifyQuestions by rememberSaveable { mutableStateOf(true) }
    var notifyMessages by rememberSaveable { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && uid.isNotBlank()) viewModel.uploadPhoto(uid, uri)
    }

    fun load(t: app.yodo.messenger.domain.model.TeacherProfile) {
        uid=t.userId; name=t.displayName; bio=t.shortBio; subjects=t.subjects.joinToString(", "); hours=t.questionHours; email=t.contactEmail; rating=t.rating.toString();
        showRating=t.showRating; vacationFrom=t.vacationFromMillis?.toString().orEmpty(); vacationTo=t.vacationToMillis?.toString().orEmpty();
        accepting=t.acceptingQuestions; notifyQuestions=t.notifyNewQuestions; notifyMessages=t.notifyMessages
    }

    Scaffold(topBar={TopAppBar(title={Text("Учителя — админка")}, navigationIcon={IconButton(onClick=onBack){Icon(Icons.Filled.ArrowBack,"Назад")}})}) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement=Arrangement.spacedBy(9.dp)) {
            item { Text("Выберите карточку", style=MaterialTheme.typography.titleMedium) }
            items(teachers, key={it.userId}) { t ->
                ListItem(headlineContent={Text(t.displayName)}, supportingContent={Text(t.subjects.joinToString(", "))}, modifier=Modifier.fillMaxWidth(), trailingContent={TextButton(onClick={selectedId=t.userId;load(t)}){Text("Изменить")}})
            }
            item { HorizontalDivider() }
            item { Text("Карточка учителя", style=MaterialTheme.typography.titleLarge) }
            item { OutlinedTextField(uid,{uid=it},Modifier.fillMaxWidth(),label={Text("UID пользователя")},singleLine=true) }
            item { OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Имя")},singleLine=true) }
            item { OutlinedTextField(bio,{bio=it},Modifier.fillMaxWidth(),label={Text("Краткая биография")}) }
            item { OutlinedTextField(subjects,{subjects=it},Modifier.fillMaxWidth(),label={Text("Предметы через запятую")}) }
            item { OutlinedTextField(hours,{hours=it},Modifier.fillMaxWidth(),label={Text("График приёма вопросов")}) }
            item { OutlinedTextField(email,{email=it},Modifier.fillMaxWidth(),label={Text("Контактный email")}) }
            item { OutlinedTextField(rating,{rating=it},Modifier.fillMaxWidth(),label={Text("Рейтинг 0–5")},singleLine=true) }
            item { Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Checkbox(showRating,{showRating=it}); Text("Показывать рейтинг") } }
            item { Text("Отпуск / недоступность — epoch milliseconds; оставьте пустым, чтобы убрать период") }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(vacationFrom,{vacationFrom=it},Modifier.weight(1f),label={Text("С")}); OutlinedTextField(vacationTo,{vacationTo=it},Modifier.weight(1f),label={Text("До")}) } }
            item { Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Checkbox(accepting,{accepting=it}); Text("Принимает вопросы") } }
            item { Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Checkbox(notifyQuestions,{notifyQuestions=it}); Text("Уведомлять о новых вопросах") } }
            item { Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Checkbox(notifyMessages,{notifyMessages=it}); Text("Уведомлять о сообщениях") } }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={viewModel.saveTeacher(uid,name,bio,subjects,hours,email,rating.toDoubleOrNull() ?: 0.0,showRating,vacationFrom.toLongOrNull(),vacationTo.toLongOrNull(),notifyQuestions,notifyMessages,accepting)}){Text("Сохранить")}; OutlinedButton(onClick={picker.launch("image/*")}){Icon(Icons.Filled.Image,null);Spacer(Modifier.width(6.dp));Text("Фото")}; TextButton(onClick={load(teachers.firstOrNull{it.userId==selectedId} ?: return@TextButton)}){Text("Обновить")}} }
            item { viewModel.message.collectAsState().value?.let { Text(it, color=MaterialTheme.colorScheme.primary) } }
        }
    }
}
