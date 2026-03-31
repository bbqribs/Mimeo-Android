package com.mimeo.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ProblemReportCategory(
    val wireValue: String,
    val label: String,
) {
    CONTENT_PROBLEM("content_problem", "Content/display problem"),
    APP_PROBLEM("app_problem", "App operation issue"),
    SAVE_FAILURE("save_failure", "Save failure"),
    OTHER("other", "Other"),
}

@Serializable
data class ProblemReportRequest(
    val category: String,
    @SerialName("user_note") val userNote: String,
    @SerialName("item_id") val itemId: Int? = null,
    val url: String? = null,
    @SerialName("client_type") val clientType: String = "android",
    @SerialName("client_version") val clientVersion: String? = null,
    @SerialName("report_time") val reportTime: String? = null,
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_label") val sourceLabel: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("capture_kind") val captureKind: String? = null,
    @SerialName("article_title") val articleTitle: String? = null,
    @SerialName("article_text_excerpt") val articleTextExcerpt: String? = null,
)

@Serializable
data class ProblemReportResponse(
    val id: Int,
)
