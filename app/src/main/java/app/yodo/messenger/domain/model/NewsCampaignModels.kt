package app.yodo.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class NewsAttachment(
    val name: String = "",
    val url: String = "",
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L
)

@Serializable
data class NewsCampaign(
    val id: String = "",
    val titleA: String = "",
    val titleB: String = "",
    val body: String = "",
    val createdBy: String = "",
    val createdAtMillis: Long = 0L,
    val status: String = "DRAFT",
    val scheduledAtMillis: Long? = null,
    val windowStartMinute: Int? = null,
    val windowEndMinute: Int? = null,
    val audienceType: String = "ALL",
    val audienceClassIds: List<String> = emptyList(),
    val abEnabled: Boolean = false,
    val pushEnabled: Boolean = true,
    val pushScheduledAtMillis: Long? = null,
    val pushWindowStartMinute: Int? = null,
    val pushWindowEndMinute: Int? = null,
    val templateId: String? = null,
    val attachments: List<NewsAttachment> = emptyList(),
    val commentsEnabled: Boolean = true,
    val sentCount: Long = 0L,
    val openedCount: Long = 0L,
    val viewCount: Long = 0L,
    val reactionCount: Long = 0L,
    val commentCount: Long = 0L,
    val clicksA: Long = 0L,
    val clicksB: Long = 0L,
    val sentA: Long = 0L,
    val sentB: Long = 0L,
    val ctrA: Double = 0.0,
    val ctrB: Double = 0.0
)

@Serializable
data class NewsTemplate(
    val id: String = "",
    val name: String = "",
    val title: String = "",
    val body: String = "",
    val createdBy: String = "",
    val updatedAtMillis: Long = 0L
)
