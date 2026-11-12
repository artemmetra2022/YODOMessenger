package app.yodo.messenger.core.mentions

import org.junit.Assert.*
import org.junit.Test

class MentionsTest {
    @Test fun `recognizes username case insensitively`() {assertTrue(mentionsUsername("Привет, @Artem!","artem"))}
    @Test fun `supports several and repeated names`() {assertEquals(listOf("alice","bob","alice"),mentionTokens("@alice, @bob, @ALICE").map { it.username })}
    @Test fun `not substring match`() {assertFalse(mentionsUsername("@user_long","user"))}
    @Test fun `not email`() {assertFalse(mentionsUsername("name@user.com","user"))}
    @Test fun `not URL fragment`() {assertTrue(mentionTokens("https://example.com/@alice").isEmpty())}
    @Test fun `length and alphabet follow profile rules`() {
        assertTrue(mentionTokens("@ab @abcdefghijklmnopqrstu @юзер").isEmpty())
        assertEquals(2,mentionTokens("@abc @abcdefghijklmnopqrst").size)
    }
    @Test fun `username boundaries include Unicode letters`() {assertTrue(mentionTokens("я@alice @aliceя").isEmpty())}
    @Test fun `punctuation and newlines allowed`() {assertEquals(3,mentionTokens("(@alice)\n@bob: @user.").size)}
    @Test fun `token offsets preserve exact original text`() {
        val text="🙂 Привет @Alice!";val token=mentionTokens(text).single()
        assertEquals("@Alice",text.substring(token.start,token.end))
    }
    @Test fun `empty username is not a mention`() {assertFalse(mentionsUsername("@user",null));assertFalse(mentionsUsername("@user",""))}
    @Test fun `query supports bare at and partial name`() {assertEquals("",mentionQueryAtEnd("Привет @"));assertEquals("Al",mentionQueryAtEnd("@Al"))}
    @Test fun `no suggestions for email or finished token`() {assertNull(mentionQueryAtEnd("name@al"));assertNull(mentionQueryAtEnd("@alice "))}
    @Test fun `completion replaces only current tail`() {assertEquals("@bob привет @alice ",completeMention("@bob привет @al","alice"))}
    @Test fun `bad completion is not inserted`() {assertEquals("@al",completeMention("@al","bad name"));assertEquals("hello",completeMention("hello","alice"))}
}
