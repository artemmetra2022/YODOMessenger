package app.yodo.messenger.features.canvas

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.Canvas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import kotlin.math.min

/** Данные живой карточки общей доски внутри чата. */
@HiltViewModel
class ChatBoardViewModel @Inject constructor(
    private val repo: SharedBoardRepository, settingsStore: CanvasSettingsStore
) : ViewModel() {
    private val chat = MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val board: StateFlow<SharedBoardState> = chat.flatMapLatest { id ->
        if (id == null) flowOf(SharedBoardState.Loading) else repo.observe(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharedBoardState.Loading)
    val settings: StateFlow<BoardSettings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardSettings())
    /** Подписываемся на доску только в личных чатах и группах; в каналах, поддержке и «Избранном» её нет. */
    fun bind(chatId: String, chatType: String) { chat.value = if (chatType == "PRIVATE" || chatType == "GROUP") chatId else null }
}

/**
 * Живая доска прямо в чате: показывает все изменения участников в реальном времени, но сама не редактируется.
 * Чтобы что-то добавить, нужно нажать на доску — откроется редактор. Если общей доски в чате ещё нет, карточка не видна
 * (создать её можно в меню вложений «Общая доска чата»).
 */
@Composable
fun ChatBoardCard(chatId: String, chatType: String, onOpen: () -> Unit, viewModel: ChatBoardViewModel = hiltViewModel()) {
    LaunchedEffect(chatId, chatType) { viewModel.bind(chatId, chatType) }
    val state by viewModel.board.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val ready = (state as? SharedBoardState.Ready)?.board ?: return
    if (!settings.showInChat) return
    var collapsed by rememberSaveable(chatId, settings.collapsedInChat) { mutableStateOf(settings.collapsedInChat) }
    val doc = remember(ready) { ready.toDocument() }
    val edited = ready.updatedAtMs?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { collapsed = !collapsed }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("Общая доска", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    buildString { append("${ready.objects.size} объектов"); if (edited != null) append(" · изменена $edited") },
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Icon(if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = if (collapsed) "Развернуть доску" else "Свернуть доску", modifier = Modifier.size(22.dp))
        }
        if (!collapsed) {
            Box(
                Modifier.fillMaxWidth().height(settings.previewHeight.dp.dp).padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xffe9edf3))
                    .semantics { contentDescription = "Общая доска чата. Нажмите, чтобы рисовать" }
                    .clickable(onClick = onOpen)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val scale = min(size.width / doc.width, size.height / doc.height)
                    drawIntoCanvas { c ->
                        val n = c.nativeCanvas
                        n.save()
                        n.translate((size.width - doc.width * scale) / 2f, (size.height - doc.height * scale) / 2f)
                        n.scale(scale, scale)
                        CanvasRenderer.draw(n, doc, null)
                        n.restore()
                    }
                }
                Text(
                    "Нажмите, чтобы рисовать", style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
