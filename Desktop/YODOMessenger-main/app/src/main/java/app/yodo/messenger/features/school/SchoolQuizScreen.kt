package app.yodo.messenger.features.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.yodo.messenger.ui.theme.YodoError
import app.yodo.messenger.ui.theme.YodoSuccess
import kotlin.random.Random

/**
 * НОВОЕ (раздел «Школа»): викторина (20 вопросов из бота) и игра
 * «Камень, ножницы, бумага» — перенос раздела «Для учеников».
 */
@Composable
fun SchoolQuizScreen(
    onBackClick: () -> Unit,
    viewModel: SchoolViewModel = hiltViewModel()
) {
    val quizCorrect by viewModel.quizCorrect.collectAsState()
    val quizTotal by viewModel.quizTotal.collectAsState()
    var currentQuestion by remember { mutableIntStateOf(Random.nextInt(SCHOOL_QUIZ_QUESTIONS.size)) }
    var answeredIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            SchoolTopBar("Викторина", onBackClick)
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "🧠 Правильных: $quizCorrect из $quizTotal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.resetQuizScore() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Сброс")
                    }
                }
            }
            item {
                val question = SCHOOL_QUIZ_QUESTIONS[currentQuestion]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Text(
                        "❓ ${question.question}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    question.options.forEachIndexed { index, option ->
                        val answered = answeredIndex
                        val isCorrect = index == question.correctIndex
                        val bg = when {
                            answered == null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                            isCorrect -> YodoSuccess.copy(alpha = 0.14f)
                            answered == index -> YodoError.copy(alpha = 0.14f)
                            else -> Color.Transparent
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .let { m ->
                                    if (answered == null) m.clickable {
                                        answeredIndex = index
                                        viewModel.recordQuizAnswer(isCorrect)
                                    } else m
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                option,
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    answered != null && isCorrect -> YodoSuccess
                                    answered == index && !isCorrect -> YodoError
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (answered != null && isCorrect) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                    if (answeredIndex != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val wasCorrect = answeredIndex == question.correctIndex
                        Text(
                            if (wasCorrect) "✅ Верно!" else "❌ Неверно! Правильный ответ: ${question.options[question.correctIndex]}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (wasCorrect) YodoSuccess else YodoError
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                currentQuestion = Random.nextInt(SCHOOL_QUIZ_QUESTIONS.size)
                                answeredIndex = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("➡️ Следующий вопрос")
                        }
                    }
                }
            }
        }
    }
}
