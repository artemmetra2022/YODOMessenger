package app.yodo.messenger.core.mentions

import java.util.Locale

/** Same username alphabet and length as profile registration; email/URL fragments are not mentions. */
private val mentionPattern=Regex("(?<![\\p{L}\\p{N}_/@.])@([a-zA-Z0-9_]{3,20})(?![\\p{L}\\p{N}_])")
data class MentionToken(val username: String, val start: Int, val end: Int)
fun mentionTokens(text: String): List<MentionToken> = mentionPattern.findAll(text).map {
    MentionToken(it.groupValues[1].lowercase(Locale.ROOT),it.range.first,it.range.last+1)
}.toList()
fun mentionsUsername(text: String, username: String?): Boolean {
    val normalized=username?.removePrefix("@")?.lowercase(Locale.ROOT) ?: return false
    return mentionTokens(text).any { it.username==normalized }
}
fun mentionQueryAtEnd(text: String): String? = Regex("(?:^|\\s)@([a-zA-Z0-9_]{0,20})$").find(text)?.groupValues?.get(1)
fun completeMention(text: String, username: String): String {
    if(mentionQueryAtEnd(text)==null || !username.matches(Regex("[a-zA-Z0-9_]{3,20}")))return text
    return text.substringBeforeLast('@')+"@"+username+" "
}
