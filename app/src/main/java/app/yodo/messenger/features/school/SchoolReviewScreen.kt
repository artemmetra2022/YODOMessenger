package app.yodo.messenger.features.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * НОВОЕ (раздел «Школа»): оценка раздела (1-5 звёзд + что понравилось/не
 * понравилось) и отправка идей — перенос «Оценить бота»/«Предложить идею».
 * Оценку можно менять (в боте — review_change).
 */
@Composable
fun SchoolReviewScreen(
    onBackClick: () -> Unit,
    viewModel: SchoolViewModel = hiltViewModel()
) {
    val myStars by viewModel.myStars.collectAsState()
    val message by viewModel.message.collectAsState()

    var stars by remember { mutableIntStateOf(0) }
    var liked by remember { mutableStateOf("") }
    var disliked by remember { mutableStateOf("") }
    var ideaText by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SchoolTopBar("Оценка и идеи", onBackClick) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (myStars > 0) {
                Text(
                    "⭐ Ваша текущая оценка: ${"⭐".repeat(myStars)}. Можно изменить её ниже.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    if (myStars > 0) "✏️ Изменить оценку" else "⭐️ Оцените раздел «Школа»",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    (1..5).forEach { value ->
                        TextButton(
                            onClick = { stars = value },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (value <= stars) "⭐" else "☆",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (value <= stars) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = liked,
                    onValueChange = { liked = it },
                    label = { Text("✅ Что понравилось? (можно «-»)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = disliked,
                    onValueChange = { disliked = it },
                    label = { Text("❌ Что не понравилось? (можно «-»)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.submitReview(stars, liked, disliked)
                        stars = 0
                        liked = ""
                        disliked = ""
                    },
                    enabled = stars in 1..5,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (myStars > 0) "Обновить оценку" else "Отправить оценку")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    "💡 Предложить идею",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Напишите вашу идею — она будет отправлена разработчикам.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = ideaText,
                    onValueChange = { ideaText = it },
                    label = { Text("Ваша идея") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.submitIdea(ideaText)
                        ideaText = ""
                    },
                    enabled = ideaText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отправить идею")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
