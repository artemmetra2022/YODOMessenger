package app.yodo.messenger.features.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * НОВОЕ (раздел «Школа»): новости гимназии из Firestore (перенос «Новости»
 * из Telegram-бота). Закреплённая новость (📌) показывается первой, дальше —
 * по дате публикации.
 */
@Composable
fun SchoolNewsScreen(
    onBackClick: () -> Unit,
    viewModel: SchoolViewModel = hiltViewModel()
) {
    val news by viewModel.news.collectAsState()

    Scaffold(
        topBar = { SchoolTopBar("Новости", onBackClick) },
        containerColor = Color.Transparent
    ) { padding ->
        if (news.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "📰 Новостей пока нет.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(news.size) { index ->
                    val item = news[index]
                    NewsCard(
                        pinned = item.pinned,
                        sender = item.sender,
                        pubDate = item.pubDate,
                        eventDate = item.eventDate,
                        text = item.text
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsCard(
    pinned: Boolean,
    sender: String,
    pubDate: Long,
    eventDate: String,
    text: String
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        if (pinned) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PushPin, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Закреплённая",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            "🔔 Новая новость!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "📩 От: $sender",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (pubDate > 0) {
            Text(
                "📅 Опубликовано: ${dateFormat.format(Date(pubDate))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (eventDate.isNotBlank() && eventDate != "Не указана") {
            Text(
                "📆 Дата проведения события: $eventDate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("Информация о событии:\n$text", style = MaterialTheme.typography.bodyMedium)
    }
}
