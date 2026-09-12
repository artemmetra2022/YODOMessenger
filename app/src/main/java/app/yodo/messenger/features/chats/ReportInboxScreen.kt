package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.ReportStatus
import app.yodo.messenger.domain.model.ReportReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * НОВОЕ (AC): глобальный раздел «Жалобы» — все жалобы и обжалования в одном месте.
 * Доступен только главным админам (2 почты).
 */
@Composable
private fun InboxReasonChart(counts: Map<ReportReason, Int>) {
    val ordered = listOf(
        ReportReason.SPAM, ReportReason.HARASSMENT, ReportReason.NSFW,
        ReportReason.ADVERTISEMENT, ReportReason.OTHER
    )
    val data = ordered.map { it to (counts[it] ?: 0) }.filter { it.second > 0 }
    val total = data.sumOf { it.second }
    if (total == 0) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Распределение жалоб", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(100.dp)) {
                var start = -90f
                val colors = listOf(
                    MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.outline
                )
                data.forEachIndexed { index, (_, value) ->
                    val sweep = value.toFloat() / total * 360f
                    drawArc(colors[index % colors.size], start, sweep, true, Offset.Zero, size)
                    start += sweep
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                data.forEach { (reason, count) ->
                    Text("${reason.label}: $count", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportInboxScreen(
    onBack: () -> Unit,
    onOpenReport: (chatId: String, reportId: String) -> Unit,
    viewModel: ReportInboxViewModel = hiltViewModel()
) {
    val reports by viewModel.reports.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val reasonFilter by viewModel.reasonFilter.collectAsState()
    val reasonCounts by viewModel.reasonCounts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Жалобы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!viewModel.isAdmin) {
                Text(
                    "Раздел доступен только администраторам.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center
                )
                return@Box
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == ReportStatus.PENDING,
                        onClick = { viewModel.setStatusFilter(ReportStatus.PENDING) },
                        label = { Text("На рассмотрении") }
                    )
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text("Все") }
                    )
                }

                InboxReasonChart(reasonCounts)

                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = reasonFilter == null,
                            onClick = { viewModel.setReasonFilter(null) },
                            label = { Text("Все типы") }
                        )
                    }
                    listOf(
                        ReportReason.SPAM, ReportReason.HARASSMENT, ReportReason.NSFW,
                        ReportReason.ADVERTISEMENT, ReportReason.OTHER
                    ).forEach { reason ->
                        item {
                            FilterChip(
                                selected = reasonFilter == reason,
                                onClick = { viewModel.setReasonFilter(reason) },
                                label = { Text(reason.label) }
                            )
                        }
                    }
                }

                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    reports.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Жалоб пока нет.", style = MaterialTheme.typography.bodyLarge)
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(reports, key = { it.chatId + "/" + it.id }) { report ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .clickable { onOpenReport(report.chatId, report.id) }
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (report.isAppeal) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF8E24AA).copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("ОБЖАЛОВАНИЕ", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E24AA), fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.size(8.dp))
                                    } else {
                                        Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.size(8.dp))
                                    }
                                    Text(
                                        report.reason.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        report.status.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!report.targetMessagePreview.isNullOrBlank()) {
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        "«${report.targetMessagePreview}»",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    "От: ${report.reporterName} · " +
                                        SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(report.createdAt)),
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
}
