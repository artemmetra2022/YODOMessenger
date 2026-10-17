package app.yodo.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One visual language for chat, message, member and channel action menus. */
internal data class YodoMenuAction(
    val label: String,
    val icon: ImageVector,
    val group: String = "Действия",
    val description: String? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YodoActionSheet(
    title: String,
    subtitle: String? = null,
    actions: List<YodoMenuAction>,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * .78f).dp)
            .padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismissRequest) { Icon(Icons.Default.Close, "Закрыть меню") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                actions.groupBy { it.group }.forEach { (group, rows) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(group, modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))) {
                            rows.forEachIndexed { index, action ->
                                YodoActionRow(action) { onDismissRequest(); action.onClick() }
                                if (index < rows.lastIndex) HorizontalDivider(
                                    Modifier.padding(start = 64.dp, end = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YodoActionRow(action: YodoMenuAction, onClick: () -> Unit) {
    val color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp)
        .clickable(enabled = action.enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(iconColor.copy(alpha = if (action.enabled) .10f else .04f)), contentAlignment = Alignment.Center) {
            Icon(action.icon, null, Modifier.size(21.dp), tint = iconColor.copy(alpha = if (action.enabled) 1f else .38f))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(action.label, style = MaterialTheme.typography.bodyLarge,
                color = color.copy(alpha = if (action.enabled) 1f else .38f))
            action.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
