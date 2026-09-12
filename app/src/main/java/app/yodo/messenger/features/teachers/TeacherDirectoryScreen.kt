package app.yodo.messenger.features.teachers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@Composable
fun TeacherDirectoryScreen(onBack: () -> Unit, onOpenTeacher: (String) -> Unit, viewModel: TeacherViewModel = hiltViewModel()) {
    val teachers by viewModel.teachers.collectAsState()
    var subjectFilter by rememberSaveable { mutableStateOf("") }
    val filtered = teachers.filter { subjectFilter.isBlank() || it.subjects.any { s -> s.contains(subjectFilter, true) } }
        .sortedBy { it.displayName.lowercase() }
    Scaffold(topBar = { TopAppBar(title = { Text("Учителя") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Назад") } }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            OutlinedTextField(subjectFilter, { subjectFilter = it }, Modifier.fillMaxWidth(), label = { Text("Фильтр по предмету") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.userId }) { t ->
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenTeacher(t.userId) },
                        leadingContent = { if (t.photoUrl != null) AsyncImage(t.photoUrl, null, Modifier.size(52.dp), contentScale = ContentScale.Crop) else Icon(Icons.Filled.Person, null, Modifier.size(40.dp)) },
                        headlineContent = { Text(t.displayName) },
                        supportingContent = { Text(t.subjects.joinToString(", ") + if (t.questionHours.isBlank()) "" else " • " + t.questionHours) }
                    )
                }
            }
        }
    }
}

