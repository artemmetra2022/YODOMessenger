package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import app.yodo.messenger.util.extractFirstUrl
import androidx.core.net.toUri
import android.content.Intent

/**
 * Если в тексте сообщения есть ссылка — подгружает и показывает под ним
 * карточку превью (og:title/og:description/og:image). Пока превью не пришло
 * (или сайт его не отдал) — ничего не показывает, сообщение выглядит как обычно.
 */
@Composable
fun LinkPreviewSection(
    messageText: String,
    modifier: Modifier = Modifier,
    viewModel: LinkPreviewViewModel = hiltViewModel()
) {
    val url = remember(messageText) { extractFirstUrl(messageText) } ?: return
    val previewsByUrl by viewModel.previewsByUrl.collectAsState()

    LaunchedEffect(url) {
        viewModel.requestPreview(url)
    }

    val preview = previewsByUrl[url] ?: return
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, preview.url.toUri()))
                }
            }
    ) {
        if (preview.imageUrl != null) {
            AsyncImage(
                model = preview.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
        }
        Column(modifier = Modifier.padding(10.dp)) {
            if (preview.siteName != null) {
                Text(
                    text = preview.siteName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (preview.title != null) {
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (preview.description != null) {
                Text(
                    text = preview.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
