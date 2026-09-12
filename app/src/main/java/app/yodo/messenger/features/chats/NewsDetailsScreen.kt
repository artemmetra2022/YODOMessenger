package app.yodo.messenger.features.chats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun NewsDetailsScreen(campaignId:String,onBack:()->Unit,viewModel:NewsDetailsViewModel=hiltViewModel()){
    val state by viewModel.state.collectAsState(); val context=LocalContext.current
    var comment by rememberSaveable{mutableStateOf("")}; var sent by remember{mutableStateOf(false)}
    LaunchedEffect(campaignId){viewModel.load(campaignId)}
    Scaffold(topBar={TopAppBar(title={Text("Новость")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Filled.ArrowBack,"Назад")}})}){padding->
        if(!state.loaded){Box(Modifier.fillMaxSize().padding(padding),contentAlignment=androidx.compose.ui.Alignment.Center){CircularProgressIndicator()}} else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{Text(state.data["titleA"] as? String ?: "Новости",style=MaterialTheme.typography.headlineSmall)}
            item{Text(state.data["body"] as? String ?: "")}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("👍","❤️","😂","🔥","😢","😡").forEach{e->AssistChip(onClick={viewModel.react(campaignId,e)},label={Text(e)})}}}
            val atts=state.data["attachments"] as? List<*> ?: emptyList<Any>()
            if(atts.isNotEmpty()) item{Text("Документы",style=MaterialTheme.typography.titleMedium)}
            items(atts.filterIsInstance<Map<*,*>>()){a->Button(onClick={viewModel.openAttachment(a["url"] as? String ?: "",context)},Modifier.fillMaxWidth()){Text("📎 ${a["name"] ?: "Документ"}")}}
            item{Text("Комментарии",style=MaterialTheme.typography.titleMedium)}
            item{OutlinedTextField(comment,{comment=it},Modifier.fillMaxWidth(),label={Text("Написать комментарий")})}
            item{Button(onClick={viewModel.addComment(campaignId,comment){comment="";sent=true}},enabled=comment.isNotBlank()){Text("Отправить")}}
            if(sent)item{Text("Комментарий отправлен") }
        }
    }
}
