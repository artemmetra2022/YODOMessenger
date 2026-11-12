package app.yodo.messenger.features.canvas

object BoardSyncPolicy {
    const val DEFAULT_MS = 0L
    const val MAX_MS = 5_000L
    fun normalize(value: Long?) = value?.takeIf { it in 0..MAX_MS } ?: DEFAULT_MS
}

