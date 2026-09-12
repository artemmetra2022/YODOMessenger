package app.yodo.messenger.features.chats

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.FaqQuestion
import app.yodo.messenger.domain.model.FaqSection
import app.yodo.messenger.domain.model.SupportFaqData
import app.yodo.messenger.domain.model.SupportFaqRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AdminFaqViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    val isAdmin = auth.currentUser?.email?.lowercase() in
        app.yodo.messenger.domain.repository.ChatRepository.ADMIN_EMAILS.map { it.lowercase() } || false

    private val _sections = MutableStateFlow(SupportFaqData.sections)
    val sections: StateFlow<List<FaqSection>> = _sections
    private val _stats = MutableStateFlow<Map<String, FaqStat>>(emptyMap())
    val stats: StateFlow<Map<String, FaqStat>> = _stats
    private val _history = MutableStateFlow<List<FaqVersion>>(emptyList())
    val history: StateFlow<List<FaqVersion>> = _history
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        SupportFaqRepository.load()
        _sections.value = SupportFaqRepository.sections.value
        try {
            val statsSnap = db.collection("faqStats").get().await()
            _stats.value = statsSnap.documents.associate { d ->
                d.id to FaqStat(d.getLong("views") ?: 0, d.getLong("helpful") ?: 0, d.getLong("notHelpful") ?: 0)
            }
            val historySnap = db.collection("faqVersions").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50).get().await()
            _history.value = historySnap.documents.map { d ->
                FaqVersion(d.id, d.getString("action") ?: "update", d.getString("author") ?: "unknown", d.getTimestamp("createdAt")?.toDate(), d.getLong("questionCount") ?: 0)
            }
        } catch (e: Exception) { _message.value = e.message ?: "Не удалось загрузить FAQ" }
    }

    fun save(sections: List<FaqSection>) = viewModelScope.launch {
        try { SupportFaqRepository.saveSections(sections, "update"); _sections.value = sections; _message.value = "FAQ сохранён"; refresh() }
        catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
    }

    fun importJson(text: String) = viewModelScope.launch { importData(parseJson(text), "import_json") }
    fun importCsv(text: String) = viewModelScope.launch { importData(parseCsv(text), "import_csv") }

    private suspend fun importData(items: List<ImportedFaq>, action: String) {
        if (items.isEmpty()) { _message.value = "Файл не содержит вопросов"; return }
        val sections = items.groupBy { it.sectionId }.map { (id, rows) ->
            FaqSection(id, rows.first().sectionTitle, rows.first().emoji, rows.map { FaqQuestion(it.id.ifBlank { UUID.randomUUID().toString() }, it.question, it.answer) })
        }
        try { SupportFaqRepository.saveSections(sections, action); _sections.value = sections; _message.value = "Импортировано вопросов: ${items.size}"; refresh() }
        catch (e: Exception) { _message.value = "Ошибка импорта: ${e.message}" }
    }

    fun exportJson(output: OutputStream) { write(output) { buildJson(_sections.value).toString(2) } }
    fun exportCsv(output: OutputStream) { write(output) { buildCsv(_sections.value) } }
    fun exportWord(output: OutputStream) { write(output) { buildWord(_sections.value) } }
    fun exportPdf(output: OutputStream) { exportPdfInternal(output, _sections.value) }

    private fun write(out: OutputStream, producer: () -> String) { viewModelScope.launch { try { out.use { it.write(producer().toByteArray(Charsets.UTF_8)) }; _message.value = "Экспорт готов" } catch (e: Exception) { _message.value = "Ошибка экспорта: ${e.message}" } } }

    private fun buildJson(sections: List<FaqSection>) = JSONArray().apply { sections.forEach { s -> s.questions.forEach { q -> put(JSONObject().apply { put("id", q.id); put("sectionId", s.id); put("sectionTitle", s.title); put("emoji", s.emoji); put("question", q.question); put("answer", q.answer) }) } } }
    private fun buildCsv(sections: List<FaqSection>): String = buildString { append("id,sectionId,sectionTitle,emoji,question,answer\n"); sections.forEach { s -> s.questions.forEach { q -> append(listOf(q.id,s.id,s.title,s.emoji,q.question,q.answer).joinToString(",") { csv(it) }).append('\n') } } }
    private fun csv(v: String) = "\"${v.replace("\"", "\"\"").replace("\n", "\\n")}\""
    private fun buildWord(sections: List<FaqSection>) = "<html><head><meta charset=\"utf-8\"></head><body><h1>YodoMessenger FAQ</h1>" + sections.joinToString("") { s -> "<h2>${html(s.emoji + " " + s.title)}</h2>" + s.questions.joinToString("") { q -> "<h3>${html(q.question)}</h3><p>${html(q.answer).replace("\n", "<br>")}</p>" } } + "</body></html>"
    private fun html(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun parseJson(text: String): List<ImportedFaq> { val a = JSONArray(text); return (0 until a.length()).map { val o=a.getJSONObject(it); ImportedFaq(o.optString("id"),o.optString("sectionId","imported"),o.optString("sectionTitle","Импорт"),o.optString("emoji","❓"),o.optString("question"),o.optString("answer")) } }
    private fun parseCsv(text: String): List<ImportedFaq> = text.lineSequence().drop(1).filter { it.isNotBlank() }.mapNotNull { row -> val p=parseCsvLine(row); if(p.size<6) null else ImportedFaq(p[0],p[1],p[2],p[3],p[4],p[5]) }.toList()
    private fun parseCsvLine(line: String): List<String> { val r=mutableListOf<String>(); val b=StringBuilder(); var q=false; var i=0; while(i<line.length){ val c=line[i]; if(c=='\"'){ if(q && i+1<line.length && line[i+1]=='\"'){b.append('\"');i++} else q=!q } else if(c==',' && !q){r+=b.toString().replace("\\n","\n");b.clear()} else b.append(c);i++ }; r+=b.toString().replace("\\n","\n"); return r }
    private fun exportPdfInternal(out: OutputStream, sections: List<FaqSection>) { val doc=android.graphics.pdf.PdfDocument(); var page=1; var y=40f; var p=doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,page).create()); val c=p.canvas; val paint=android.graphics.Paint().apply{textSize=14f}; sections.forEach { s -> if(y>790){doc.finishPage(p);page++;p=doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,page).create());y=40f}; c.drawText(s.emoji+" "+s.title,24f,y,paint);y+=22; s.questions.forEach { q -> if(y>790){doc.finishPage(p);page++;p=doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,page).create());y=40f}; c.drawText(q.question.take(80),32f,y,paint);y+=18; q.answer.replace("\n"," ").chunked(85).forEach { line -> if(y>790){doc.finishPage(p);page++;p=doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,page).create());y=40f}; c.drawText(line,40f,y,paint);y+=16 };y+=6 } };doc.finishPage(p);doc.writeTo(out);doc.close() }

    data class FaqStat(val views: Long, val helpful: Long, val notHelpful: Long)
    data class FaqVersion(val id: String, val action: String, val author: String, val createdAt: Date?, val questionCount: Long)
    private data class ImportedFaq(val id:String,val sectionId:String,val sectionTitle:String,val emoji:String,val question:String,val answer:String)
}
