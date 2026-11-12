package app.yodo.messenger.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yodo.messenger.core.mentions.*
import app.yodo.messenger.domain.model.YodoUser

@Composable
internal fun MentionSuggestions(text: String, members: List<YodoUser>, onSelect: (String)->Unit) {
    val query=mentionQueryAtEnd(text) ?: return
    val matches=members.filter { it.username?.startsWith(query,ignoreCase=true)==true }.distinctBy { it.uid }.take(3)
    if(matches.isEmpty())return
    val maxHeight=(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp/5).coerceIn(48,144).dp
    Surface(tonalElevation=3.dp) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=maxHeight)) {
            items(matches,key={it.uid}) { user ->
                Column(Modifier.fillMaxWidth().clickable { user.username?.let(onSelect) }.heightIn(min=48.dp).padding(horizontal=16.dp,vertical=6.dp)) {
                    Text("@${user.username}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold,
                        maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(user.displayName,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

internal fun annotatedMentions(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    mentionTokens(text).forEach { token ->
        addStyle(SpanStyle(color=color,fontWeight=FontWeight.Bold),token.start,token.end)
    }
}
