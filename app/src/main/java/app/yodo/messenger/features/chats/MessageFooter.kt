package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yodo.messenger.ui.components.clickable

/** Reactions belong to the bubble footer, never to the outer message/avatar row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageFooter(
    reactions: Map<String, List<String>>,
    currentUserId: String?,
    accent: Color,
    textColor: Color,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
    metadata: @Composable RowScope.() -> Unit
) {
    val visible = reactions.filterValues { it.isNotEmpty() }
    if (visible.isEmpty()) {
        Row(
            modifier = modifier.padding(horizontal = 12.dp).padding(top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = metadata
        )
        return
    }
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        visible.forEach { (emoji, users) ->
            key(emoji) {
                val mine = currentUserId in users
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                        .clip(shape)
                        .background(if (mine) accent.copy(alpha = 0.18f) else textColor.copy(alpha = 0.07f))
                        .border(1.dp, if (mine) accent else Color.Transparent, shape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Реакция $emoji: ${users.size}"
                            selected = mine
                        }
                        .clickable(role = Role.Button) { onReact(emoji) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emoji, style = MaterialTheme.typography.bodyMedium)
                    if (users.size > 1) {
                        Text(" ${users.size}", color = textColor,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        // Metadata stays together. If it does not fit after the chips, FlowRow wraps it.
        Row(
            modifier = Modifier.defaultMinSize(minHeight = 44.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = metadata
        )
    }
}
