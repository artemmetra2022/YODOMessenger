package app.yodo.messenger.features.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
// НОВОЕ (AK): нужно для прокручиваемого списка комментариев в окне.
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.yodo.messenger.domain.model.Post
import app.yodo.messenger.domain.model.PostComment
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.util.ImageUtils
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Секция "Посты" на стене профиля — как во ВКонтакте: лента постов автора,
 * плюс (если это мой профиль) поле для публикации нового поста с фото.
 */
@Composable
fun ProfilePostsSection(
    userId: String,
    isOwnProfile: Boolean,
    colorTheme: ColorTheme,
    viewModel: PostsViewModel
) {
    val posts by viewModel.posts.collectAsState()
    val isPosting by viewModel.isPosting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    // НОВОЕ (AK): состояние комментариев под постами.
    val openCommentsPostId by viewModel.openCommentsPostId.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isSendingComment by viewModel.isSendingComment.collectAsState()
    var showComposeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) { viewModel.startObserving(userId) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(4.dp).background(colorTheme.primary, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Посты",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colorTheme.primary
            )
        }

        if (isOwnProfile) {
            OutlinedButton(
                onClick = { showComposeDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Написать пост")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (posts.isEmpty()) {
            Text(
                text = if (isOwnProfile) "У вас пока нет постов" else "Пока нет постов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                posts.forEach { post ->
                    PostCard(
                        post = post,
                        canDelete = isOwnProfile,
                        currentUserId = viewModel.currentUserId,
                        colorTheme = colorTheme,
                        onDelete = { viewModel.deletePost(post.id) },
                        onLike = { viewModel.toggleLike(post.id) },
                        onRegisterView = { viewModel.registerView(post.id) },
                        onOpenComments = { viewModel.openComments(post.id) }
                    )
                }
            }
        }
    }

    if (showComposeDialog) {
        ComposePostDialog(
            isPosting = isPosting,
            onDismiss = { showComposeDialog = false },
            onPost = { text, uris ->
                viewModel.createPost(text, uris)
                showComposeDialog = false
            }
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeError() },
            confirmButton = { TextButton(onClick = { viewModel.consumeError() }) { Text("Ок") } },
            title = { Text("Не удалось опубликовать") },
            text = { Text(message) }
        )
    }

    // НОВОЕ: комментарии под постом в профиле (как в VK) — открываются по нажатию на иконку комментария.
    if (openCommentsPostId != null) {
        PostCommentsDialog(
            comments = comments,
            isSending = isSendingComment,
            currentUserId = viewModel.currentUserId,
            canModerate = isOwnProfile,
            colorTheme = colorTheme,
            onSend = { text -> viewModel.sendComment(text) },
            onDelete = { commentId -> viewModel.deleteComment(commentId) },
            onDismiss = { viewModel.closeComments() }
        )
    }
}

@Composable
private fun PostCard(
    post: Post,
    canDelete: Boolean,
    currentUserId: String?,
    colorTheme: ColorTheme,
    onDelete: () -> Unit,
    onLike: () -> Unit,
    onRegisterView: () -> Unit,
    onOpenComments: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // НОВОЕ (VK-стиль): засчитываем просмотр, когда карточка появляется.
    LaunchedEffect(post.id) { onRegisterView() }
    val isLiked = currentUserId != null && currentUserId in post.likedBy

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            UserAvatar(
                displayName = post.authorName,
                photoUrl = post.authorPhotoUrl,
                avatarBase64 = post.authorAvatarBase64,
                size = 36.dp,
                userId = post.authorId
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(post.authorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    formatPostTime(post.createdAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canDelete) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить пост", modifier = Modifier.size(18.dp))
                }
            }
        }

        if (post.text.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(post.text, style = MaterialTheme.typography.bodyMedium)
        }

        if (post.photosBase64.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            if (post.photosBase64.size == 1) {
                PostPhoto(
                    base64 = post.photosBase64.first(),
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp))
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(post.photosBase64) { base64 ->
                        PostPhoto(
                            base64 = base64,
                            modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }

        // НОВОЕ (VK-стиль): панель действий под постом — лайк, комментарии, поделиться и просмотры.
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            PostAction(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (isLiked) colorTheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                count = post.likeCount,
                contentDescription = "Нравится",
                onClick = onLike
            )
            Spacer(modifier = Modifier.width(16.dp))
            PostAction(
                icon = Icons.Filled.ChatBubbleOutline,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                count = post.commentCount,
                contentDescription = "Комментарии",
                onClick = onOpenComments
            )
            Spacer(modifier = Modifier.width(16.dp))
            PostAction(
                icon = Icons.Filled.Share,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                count = 0,
                contentDescription = "Поделиться",
                onClick = {
                    val shareText = buildString {
                        append(post.authorName)
                        if (post.text.isNotBlank()) { append(":\n"); append(post.text) }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Поделиться постом"))
                }
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.Visibility,
                contentDescription = "Просмотры",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                post.views.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") } },
            title = { Text("Удалить пост?") },
            text = { Text("Это действие нельзя отменить.") }
        )
    }
}

// НОВОЕ (VK-стиль): одна кнопка в панели действий поста (иконка + число).
@Composable
private fun PostAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    count: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        if (count > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@Composable
private fun PostPhoto(base64: String, modifier: Modifier) {
    val bitmap = remember(base64) { ImageUtils.decodeBase64ToBitmap(base64) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Composable
private fun ComposePostDialog(
    isPosting: Boolean,
    onDismiss: () -> Unit,
    onPost: (String, List<Uri>) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedUris by remember { mutableStateOf(listOf<Uri>()) }
    val maxPhotos = 4

    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = (selectedUris + uris).take(maxPhotos)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isPosting) onDismiss() },
        title = { Text("Новый пост") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("Что у вас нового?") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    enabled = !isPosting
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (selectedUris.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedUris) { uri ->
                            Box(modifier = Modifier.size(72.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(8.dp))
                                )
                                IconButton(
                                    onClick = { selectedUris = selectedUris - uri },
                                    modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Убрать фото",
                                        tint = Color.White,
                                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                TextButton(
                    onClick = { pickImages.launch("image/*") },
                    enabled = !isPosting && selectedUris.size < maxPhotos
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Добавить фото (${selectedUris.size}/$maxPhotos)")
                }
            }
        },
        confirmButton = {
            if (isPosting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Button(
                    onClick = { onPost(text, selectedUris) },
                    enabled = text.isNotBlank() || selectedUris.isNotEmpty()
                ) { Text("Опубликовать") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPosting) { Text("Отмена") }
        }
    )
}

/**
 * НОВОЕ (AK): окно комментариев под постом в профиле.
 * Комментарий можно удалить, если он ваш или если это ваш пост.
 */
@Composable
private fun PostCommentsDialog(
    comments: List<PostComment>,
    isSending: Boolean,
    currentUserId: String?,
    canModerate: Boolean,
    colorTheme: ColorTheme,
    onSend: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (comments.isEmpty()) "Комментарии" else "Комментарии (${comments.size})") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (comments.isEmpty()) {
                    Text(
                        "Комментариев пока нет. Напишите первым!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        comments.forEach { comment ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                UserAvatar(
                                    displayName = comment.authorName,
                                    photoUrl = comment.authorPhotoUrl,
                                    avatarBase64 = comment.authorAvatarBase64,
                                    size = 32.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        comment.authorName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colorTheme.primary
                                    )
                                    Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        formatPostTime(comment.createdAtMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (canModerate || (currentUserId != null && currentUserId == comment.authorId)) {
                                    IconButton(onClick = { onDelete(comment.id) }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Удалить комментарий",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ваш комментарий…") },
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Button(
                    onClick = {
                        onSend(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank()
                ) { Text("Отправить") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

private fun formatPostTime(millis: Long): String {
    if (millis <= 0L) return ""
    val format = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru"))
    return format.format(Date(millis))
}
