package app.yodo.messenger.notifications

import org.junit.Assert.*
import org.junit.Test

class NotificationPolicyTest {
    @Test fun `private chats retain all actions`() {assertEquals(ChatNotificationActions(true,true,true),chatNotificationActions("PRIVATE",true,false,false))}
    @Test fun `groups allow members to reply read and mute`() {assertEquals(ChatNotificationActions(true,true,true),chatNotificationActions("GROUP",true,false,false))}
    @Test fun `channel subscribers cannot publish via notification`() {assertEquals(ChatNotificationActions(true,false,true),chatNotificationActions("CHANNEL",true,false,false))}
    @Test fun `channel publisher can publish`() {assertTrue(chatNotificationActions("CHANNEL",true,true,false).reply)}
    @Test fun `hidden preview prevents blind inline reply`() {listOf("PRIVATE","GROUP","CHANNEL").forEach { assertFalse(chatNotificationActions(it,true,true,true).reply) }}
    @Test fun `no actions for former member even with stale publisher flag`() {assertEquals(ChatNotificationActions(false,false,false),chatNotificationActions("GROUP",false,true,false))}
    @Test fun `unknown chat type fails closed`() {assertEquals(ChatNotificationActions(false,false,false),chatNotificationActions("OTHER",true,true,false))}
    @Test fun `sound only mute does not turn on disabled global sound`() {assertEquals(false to true,notificationAlerts(false,true,false))}
    @Test fun `local mute removes sound and vibration`() {assertEquals(false to false,notificationAlerts(true,true,true))}
    @Test fun `independent sound vibration preferences remain independent`() {assertEquals(true to false,notificationAlerts(true,false,false))}
}
