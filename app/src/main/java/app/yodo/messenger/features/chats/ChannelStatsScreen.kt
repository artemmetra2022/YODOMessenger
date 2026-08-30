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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.repository.ChannelStats
import app.yodo.messenger.domain.repository.ChannelSubscriberPoint
import app.yodo.messenger.domain.repository.ChannelTopPost
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.ui.theme.LocalColorTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * НОВОЕ (статистика для владельца канала): расширенная аналитика канала —
 * рост/отток аудитории за 30 дней, суммарные охваты и вовлечённость постов,
 * топ-5 постов по просмотрам. Открывается из ChannelProfileScreen (кнопка
 * владельца), в отличие от ChatStatsScreen (общая статистика любого чата).
 */
@Composable
fun ChannelStatsScreen(
    onBackClick: () -> Unit,
    viewModel: ChannelStatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Статистика канала", style = MaterialTheme.typography.titleLarge)
                        if (uiState.channelTitle.isNotBlank()) {
                            Text(
                                uiState.channelTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = colorTheme.primary)
                uiState.accessDenied || uiState.stats == null -> Text(
                    "Статистика доступна только владельцу или администратору канала",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                else -> {
                    val stats = uiState.stats!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { OverviewCard(stats, colorTheme) }
                        item { AudienceCard(stats, colorTheme) }
                        if (stats.subscriberHistory.isNotEmpty()) {
                            item { SubscriberHistoryCard(stats.subscriberHistory, colorTheme) }
                        }
                        if (stats.topPosts.isNotEmpty()) {
                            item { TopPostsCard(stats.topPosts, colorTheme) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSectionCard(title: String, content: @Composable () -> Unit) {
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
private fun OverviewCard(stats: ChannelStats, colorTheme: ColorTheme) {
    StatsSectionCard(title = "Обзор") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OverviewMetric(value = stats.subscriberCount, label = "подписчиков", colorTheme = colorTheme)
            OverviewMetric(value = stats.postsCount, label = "постов", colorTheme = colorTheme)
            OverviewMetric(value = stats.totalViews, label = "просмотров", colorTheme = colorTheme)
            OverviewMetric(value = stats.totalComments, label = "комментариев", colorTheme = colorTheme)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.RemoveRedEye, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "В среднем ${stats.avgViewsPerPost} просмотров на пост",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OverviewMetric(value: Int, label: String, colorTheme: ColorTheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            String.format(Locale("ru"), "%,d", value),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colorTheme.primary
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AudienceCard(stats: ChannelStats, colorTheme: ColorTheme) {
    StatsSectionCard(title = "Аудитория за 30 дней") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("+${stats.subscribersGained30d}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                Spacer(modifier = Modifier.width(4.dp))
                Text("новых", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("-${stats.subscribersLost30d}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(4.dp))
                Text("отписок", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val net = stats.subscribersGained30d - stats.subscribersLost30d
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (net >= 0) "Чистый прирост: +$net" else "Чистая убыль: $net",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colorTheme.primary
            )
        }
    }
}

@Composable
private fun SubscriberHistoryCard(history: List<ChannelSubscriberPoint>, colorTheme: ColorTheme) {
    val maxAbs = history.maxOfOrNull { kotlin.math.abs(it.delta) }?.coerceAtLeast(1) ?: 1
    StatsSectionCard(title = "Динамика подписок по дням") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(history) { point ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.width(24.dp).height(70.dp),
                        contentAlignment = if (point.delta >= 0) Alignment.BottomCenter else Alignment.TopCenter
                    ) {
                        val barColor = if (point.delta >= 0) Color(0xFF22C55E) else MaterialTheme.colorScheme.error
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height((kotlin.math.abs(point.delta).toFloat() / maxAbs * 60).dp.coerceAtLeast(3.dp))
                                .background(barColor, RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(point.dateLabel, style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (point.delta >= 0) "+${point.delta}" else "${point.delta}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TopPostsCard(topPosts: List<ChannelTopPost>, colorTheme: ColorTheme) {
    StatsSectionCard(title = "Лучшие посты") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            topPosts.forEachIndexed { index, post ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorTheme.primary,
                        modifier = Modifier.width(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            post.previewText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.RemoveRedEye, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${post.viewCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${post.commentsCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                formatPostDate(post.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatPostDate(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))
