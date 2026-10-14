package app.yodo.messenger.domain.model

/** Group checklist content is not E2EE. Completion writes are validated by Firestore Rules. */
data class SharedChecklist(
    val title: String,
    val items: List<String>,
    val checkedBy: Map<String, String> = emptyMap()
) {
    fun completedBy(index: Int): String? = checkedBy[index.toString()]?.takeIf { it.isNotBlank() }
    val completedCount: Int get() = items.indices.count { completedBy(it) != null }

    companion object {
        const val MAX_ITEMS = 20
        const val MAX_TITLE = 100
        const val MAX_ITEM_LENGTH = 200

        fun create(title: String, items: List<String>): SharedChecklist {
            val cleanTitle = title.trim()
            val cleanItems = items.map { it.trim() }
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= MAX_TITLE)
            require(cleanItems.size in 1..MAX_ITEMS)
            require(cleanItems.all { it.isNotEmpty() && it.length <= MAX_ITEM_LENGTH })
            return SharedChecklist(cleanTitle, cleanItems)
        }

        fun fromFields(title: Any?, items: Any?, checkedBy: Any?): SharedChecklist? {
            val labels = items as? List<*> ?: return null
            if (labels.any { it !is String }) return null
            return runCatching {
                val checklist = create(title as? String ?: return null, labels.filterIsInstance<String>())
                val checks = (checkedBy as? Map<*, *>)?.mapNotNull { (key, value) ->
                    val index = (key as? String)?.toIntOrNull()
                    if (index == null || index !in checklist.items.indices || key != index.toString() || value !is String) null
                    else key to value
                }?.toMap().orEmpty()
                checklist.copy(checkedBy = checks)
            }.getOrNull()
        }
    }
}
