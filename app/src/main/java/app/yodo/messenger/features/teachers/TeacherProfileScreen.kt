package app.yodo.messenger.features.teachers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TeacherProfileScreen(userId: String, onBack: () -> Unit, viewModel: TeacherViewModel = hiltViewModel()) {
    val teachers by viewModel.teachers.collectAsState()
    LaunchedEffect(userId) { viewModel.select(userId) }
    val t = teachers.firstOrNull { it.userId == userId } ?: return
    val vacation = t.vacationFromMillis != null && t.vacationToMillis != null
    Scaffold(topBar = { TopAppBar(title = { Text(t.displayName) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Назад") } }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!t.photoUrl.isNullOrBlank()) AsyncImage(t.photoUrl, null, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
            if (t.shortBio.isNotBlank()) Text(t.shortBio, style = MaterialTheme.typography.bodyLarge)
            Text("Предметы: ${t.subjects.joinToString(", ").ifBlank { "Не указаны" }}")
            Text("Приём вопросов: ${t.questionHours.ifBlank { "По договорённости" }}")
            if (t.contactEmail.isNotBlank()) Text("Email: ${t.contactEmail}")
            if (t.showRating) Text("Рейтинг: ${"%.1f".format(Locale.getDefault(), t.rating)} / 5")
            if (vacation) {
                val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                Text("Недоступен: ${fmt.format(Date(t.vacationFromMillis!!))} — ${fmt.format(Date(t.vacationToMillis!!))}", color = MaterialTheme.colorScheme.error)
            } else Text(if (t.acceptingQuestions) "Принимает вопросы" else "Временно не принимает вопросы")
        }
    }
}
