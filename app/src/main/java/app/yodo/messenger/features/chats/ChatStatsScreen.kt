package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ChatStatsScreen(
    onBackClick: () -> Unit,
    viewModel: ChatStatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_stats_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }

        if (uiState.totalMessages == 0) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    text = stringResource(R.string.chat_stats_empty),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SummaryCard(uiState) }
            item { SendersCard(uiState.senderStats, uiState.totalMessages) }
            item { ContentBreakdownCard(uiState.contentBreakdown) }
            item { HourlyActivityCard(uiState.hourlyActivity) }
            item { DailyActivityCard(uiState.dailyActivity) }
        }
    }
}

@Composable
private fun StatsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SummaryCard(state: ChatStatsUiState) {
    StatsCard(title = state.chatTitle) {
        Text(
            text = stringResource(R.string.chat_stats_total, state.totalMessages),
            style = MaterialTheme.typography.bodyLarge
        )
        state.firstMessageDateLabel?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.chat_stats_first_message, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SendersCard(senderStats: List<SenderStat>, total: Int) {
    StatsCard(title = stringResource(R.string.chat_stats_top_senders)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            senderStats.forEach { sender ->
                val fraction = if (total > 0) sender.messageCount.toFloat() / total else 0f
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sender.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text("${sender.messageCount}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ProgressBar(fraction = fraction)
                }
            }
        }
    }
}

@Composable
private fun ContentBreakdownCard(breakdown: ContentTypeBreakdown) {
    val rows = listOf(
        stringResource(R.string.chat_stats_type_text) to breakdown.textCount,
        stringResource(R.string.chat_stats_type_photo) to breakdown.imageCount,
        stringResource(R.string.chat_stats_type_voice) to breakdown.voiceCount,
        stringResource(R.string.chat_stats_type_files) to breakdown.fileCount,
        stringResource(R.string.chat_stats_type_location) to breakdown.locationCount,
        stringResource(R.string.chat_stats_type_polls) to breakdown.pollCount
    ).filter { it.second > 0 }

    StatsCard(title = stringResource(R.string.chat_stats_content_types)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (label, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text("$count", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun HourlyActivityCard(hourly: List<HourlyActivityPoint>) {
    val maxCount = hourly.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    StatsCard(title = stringResource(R.string.chat_stats_hourly)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            hourly.forEach { point ->
                val heightFraction = point.count.toFloat() / maxCount
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((heightFraction * 100).dp.coerceAtLeast(2.dp))
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", style = MaterialTheme.typography.labelSmall)
            Text("12:00", style = MaterialTheme.typography.labelSmall)
            Text("23:00", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DailyActivityCard(daily: List<DailyActivityPoint>) {
    if (daily.isEmpty()) return
    val maxCount = daily.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    StatsCard(title = stringResource(R.string.chat_stats_daily)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(daily) { point ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.width(28.dp).height(80.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height((point.count.toFloat() / maxCount * 80).dp.coerceAtLeast(2.dp))
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = point.dateLabel.substringBeforeLast("."),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text("${point.count}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
        )
    }
}
