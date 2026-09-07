package app.yodo.messenger.features.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.theme.YodoError
import app.yodo.messenger.ui.theme.YodoSuccess
import kotlin.random.Random

/**
 * НОВОЕ (раздел «Школа»): игра «Камень, ножницы, бумага» из Telegram-бота.
 * Победы/поражения сохраняются в SchoolPreferences (в боте — game_wins в БД).
 */
private enum class GameChoice(val label: String, val icon: ImageVector) {
    ROCK("🪨 Камень", Icons.Filled.Terrain),
    SCISSORS("✂️ Ножницы", Icons.Filled.ContentCut),
    PAPER("📄 Бумага", Icons.Filled.Description)
}

private val beats: Map<GameChoice, GameChoice> = mapOf(
    GameChoice.ROCK to GameChoice.SCISSORS,
    GameChoice.SCISSORS to GameChoice.PAPER,
    GameChoice.PAPER to GameChoice.ROCK
)

@Composable
fun SchoolGameScreen(
    onBackClick: () -> Unit,
    viewModel: SchoolViewModel = hiltViewModel()
) {
    val wins by viewModel.gameWins.collectAsState()
    val losses by viewModel.gameLosses.collectAsState()
    var lastResult by remember { mutableStateOf<String?>(null) }
    var lastResultWin by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        topBar = { SchoolTopBar("Игра", onBackClick) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🎮 Камень, ножницы, бумага!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Счёт: Вы — $wins, Приложение — $losses",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                if (lastResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        lastResult ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (lastResultWin) {
                            true -> YodoSuccess
                            false -> YodoError
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Выберите:",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            GameChoice.entries.forEach { choice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            val botChoice = GameChoice.entries[Random.nextInt(GameChoice.entries.size)]
                            val result: String
                            val won: Boolean?
                            when {
                                choice == botChoice -> {
                                    result = "Ничья! 🤝 Вы: ${choice.label} | Приложение: ${botChoice.label}"
                                    won = null
                                }
                                beats[choice] == botChoice -> {
                                    result = "Вы победили! 🎉 Вы: ${choice.label} | Приложение: ${botChoice.label}"
                                    won = true
                                }
                                else -> {
                                    result = "Приложение победило! 🤖 Вы: ${choice.label} | Приложение: ${botChoice.label}"
                                    won = false
                                }
                            }
                            lastResult = result
                            lastResultWin = won
                            if (won != null) viewModel.recordGameRound(won)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(choice.icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        choice.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
