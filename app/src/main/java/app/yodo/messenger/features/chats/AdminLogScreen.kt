package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.AdminActionType
import app.yodo.messenger.domain.model.AdminLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminLogScreen(
    onBackClick: () -> Unit,
    viewModel: AdminLogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал действий", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.filter.actionType == null,
                        onClick = { viewModel.setActionTypeFilter(null) },
                        label = { Text("Все действия") }
                    )
                }
                items(AdminActionType.entries.toList()) { type ->
                    FilterChip(
                        selected = uiState.filter.actionType == type,
                        onClick = {
                            viewModel.setActionTypeFilter(if (uiState.filter.actionType == type) null else type)
                        },
                        label = { Text(type.label) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else if (uiState.entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        "Действий пока нет",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        AdminLogRow(entry)
                    }
                    if (uiState.isLoadingMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center).size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun iconFor(type: AdminActionType): ImageVector = when (type) {
    AdminActionType.MESSAGE_DELETED -> Icons.Filled.Delete
    AdminActionType.USER_BANNED -> Icons.Filled.Block
    AdminActionType.USER_UNBANNED -> Icons.Filled.PersonAdd
    AdminActionType.USER_KICKED -> Icons.Filled.PersonRemove
    AdminActionType.ADMIN_ADDED -> Icons.Filled.AdminPanelSettings
    AdminActionType.ADMIN_REMOVED -> Icons.Filled.AdminPanelSettings
    AdminActionType.ROLE_ASSIGNED -> Icons.Filled.Security
    AdminActionType.ROLE_REMOVED -> Icons.Filled.Security
    AdminActionType.CUSTOM_ROLE_CREATED -> Icons.Filled.Security
    AdminActionType.CUSTOM_ROLE_EDITED -> Icons.Filled.Security
    AdminActionType.CUSTOM_ROLE_DELETED -> Icons.Filled.Security
    AdminActionType.CHAT_INFO_CHANGED -> Icons.Filled.Edit
    AdminActionType.MESSAGE_PINNED -> Icons.Filled.PushPin
    AdminActionType.MESSAGE_UNPINNED -> Icons.Filled.PushPin
    AdminActionType.USER_INVITED -> Icons.Filled.PersonAdd
    AdminActionType.OWNERSHIP_TRANSFERRED -> Icons.Filled.SwapHoriz
}

@Composable
private fun AdminLogRow(entry: AdminLogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                iconFor(entry.actionType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildString {
                    append(entry.actorName)
                    append(" — ")
                    append(entry.actionType.label.replaceFirstChar { it.lowercase() })
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            val subtitle = buildString {
                if (entry.targetUserName != null) append(entry.targetUserName)
                if (entry.details.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(entry.details)
                }
            }
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                formatLogTimestamp(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatLogTimestamp(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(millis))
