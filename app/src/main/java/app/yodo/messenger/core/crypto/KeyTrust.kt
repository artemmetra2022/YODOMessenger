package app.yodo.messenger.core.crypto

import java.security.MessageDigest

fun keyFingerprint(publicKeyBytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(publicKeyBytes).joinToString("") { "%02X".format(it.toInt() and 0xff) }

fun formattedFingerprint(fingerprint: String): String = fingerprint.chunked(4).joinToString(" ")

enum class KeyTrustDecision { FIRST_USE, UNCHANGED, CHANGED }
fun keyTrustDecision(pinned: String?, current: String): KeyTrustDecision = when {
    pinned.isNullOrBlank() -> KeyTrustDecision.FIRST_USE
    pinned == current -> KeyTrustDecision.UNCHANGED
    else -> KeyTrustDecision.CHANGED
}

data class ConversationSecurity(
    val ownFingerprint: String,
    val peerFingerprint: String,
    val previousPeerFingerprint: String?,
    val peerVerified: Boolean,
    val ownKeyActive: Boolean,
    val publishedOwnFingerprint: String?
) {
    val peerKeyChanged: Boolean get() = keyTrustDecision(previousPeerFingerprint, peerFingerprint) == KeyTrustDecision.CHANGED
}

class SecureMessagingException(val userMessage: String) : Exception(userMessage)
