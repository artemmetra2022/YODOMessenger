package app.yodo.messenger.features.chats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                // Как в Telegram: пружинистый bounce при переключении "моей" реакции
                // (ставим/снимаем), без задержек — состояние уже обновлено оптимистично
                // на уровне ViewModel, здесь только сопровождающая анимация.
                // remember(mine) создаёт новый Animatable при каждом переключении —
                // он стартует с 0.6 и пружиной "вылетает" до 1.0, давая характерный bounce.
                val bounceAnim = remember(mine) { Animatable(0.6f) }
                LaunchedEffect(mine) {
                    bounceAnim.animateTo(
                        1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                val bounceScale = bounceAnim.value
                val pillShape = RoundedCornerShape(50)
                val containerColor by animateFloatAsState(
                    targetValue = if (mine) 1f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "reactionChipFill"
                )
                Row(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 40.dp, minHeight = 32.dp)
                        .graphicsLayer {
                            scaleX = bounceScale
                            scaleY = bounceScale
                        }
                        .clip(pillShape)
                        .background(
                            Color(
                                red = (accent.red * containerColor + textColor.red * 0.06f * (1 - containerColor)),
                                green = (accent.green * containerColor + textColor.green * 0.06f * (1 - containerColor)),
                                blue = (accent.blue * containerColor + textColor.blue * 0.06f * (1 - containerColor)),
                                alpha = 0.16f + 0.02f * containerColor
                            )
                        )
                        .border(
                            width = if (mine) 1.dp else 0.dp,
                            color = if (mine) accent.copy(alpha = 0.55f) else Color.Transparent,
                            shape = pillShape
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Реакция $emoji: ${users.size}"
                            selected = mine
                        }
                        .clickable(role = Role.Button) { onReact(emoji) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emoji, fontSize = 15.sp)
                    if (users.size > 1) {
                        Text(
                            "${users.size}",
                            color = if (mine) accent else textColor.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
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
