package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class NewsReaderState(val data: Map<String,Any?> = emptyMap(), val loaded: Boolean=false)

@HiltViewModel
class NewsDetailsViewModel @Inject constructor(private val firestore: FirebaseFirestore, private val auth: FirebaseAuth): ViewModel(){
    private val _state=MutableStateFlow(NewsReaderState()); val state:StateFlow<NewsReaderState> = _state.asStateFlow()
    fun load(campaignId:String){
        val uid=auth.currentUser?.uid ?: return
        firestore.collection("newsCampaigns").document(campaignId).get().addOnSuccessListener{doc->
            _state.value=NewsReaderState(doc.data.orEmpty(),true)
            val userRef=firestore.collection("users").document(uid)
            userRef.get().addOnSuccessListener{u->
                val displayName=u.getString("displayName") ?: "Пользователь"
                val classId=u.getString("classId") ?: u.getString("class_id") ?: ""
                val groupId=u.getString("groupId") ?: u.getString("group_id") ?: ""
                val vr=doc.reference.collection("views").document(uid)
                firestore.runTransaction{tx ->
                    val snap=tx.get(doc.reference)
                    val view=tx.get(vr)
                    val viewData=mapOf("displayName" to displayName,"classId" to classId,"groupId" to groupId,"viewedAtMillis" to (view.getLong("viewedAtMillis") ?: System.currentTimeMillis()),"reaction" to (view.getString("reaction") ?: ""))
                    tx.set(vr,viewData,com.google.firebase.firestore.SetOptions.merge())
                    if(!view.exists()){ val old=snap.getLong("viewCount") ?: 0L; tx.update(doc.reference,"viewCount",old+1L) }
                    null
                }
            }
        }
    }
    fun react(campaignId:String, emoji:String){
        val uid=auth.currentUser?.uid ?: return
        val ref=firestore.collection("newsCampaigns").document(campaignId)
        val rr=ref.collection("views").document(uid)
        firestore.runTransaction{tx -> val view=tx.get(rr); val old=view.getString("reaction") ?: ""; val count=tx.get(ref).getLong("reactionCount") ?: 0L; if(old==emoji) { tx.set(rr,mapOf("reaction" to ""),com.google.firebase.firestore.SetOptions.merge()); tx.update(ref,"reactionCount",(count-1).coerceAtLeast(0)) } else { if(old.isEmpty()) tx.update(ref,"reactionCount",count+1); tx.set(rr,mapOf("reaction" to emoji),com.google.firebase.firestore.SetOptions.merge()) }; null }.addOnSuccessListener{load(campaignId)}
    }
    fun addComment(campaignId:String,text:String,onDone:()->Unit){
        val uid=auth.currentUser?.uid ?: return
        if(text.isBlank())return
        firestore.collection("users").document(uid).get().addOnSuccessListener{u->
            firestore.collection("newsCampaigns").document(campaignId).collection("comments").add(mapOf("uid" to uid,"displayName" to (u.getString("displayName") ?: "Пользователь"),"text" to text.trim().take(2000),"createdAtMillis" to System.currentTimeMillis())).addOnSuccessListener{onDone()}
        }
    }
    fun openAttachment(url:String, context:android.content.Context){ runCatching{context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)))} }
}
