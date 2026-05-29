package com.mimeo.android.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimeo.android.model.ContentSummaryOut
import com.mimeo.android.model.ContentSummaryState
import com.mimeo.android.model.ReaderSummaryState
import com.mimeo.android.model.canRefreshOutdatedSummary
import com.mimeo.android.model.canRequestGeneration
import com.mimeo.android.model.contentSummaryFailureMessage
import com.mimeo.android.model.contentSummaryFailureReasonFromCode
import com.mimeo.android.model.normalizedState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarySheet(
    state: ReaderSummaryState,
    itemId: Int,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onGenerate: (force: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val loading = state is ReaderSummaryState.Loading && state.itemId == itemId
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onRefresh,
                    enabled = !loading,
                ) {
                    Text(if (loading) "Checking" else "Recheck")
                }
            }
            HorizontalDivider()
            SummarySheetBody(
                state = state,
                itemId = itemId,
                onRefresh = onRefresh,
                onGenerate = onGenerate,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun SummarySheetBody(
    state: ReaderSummaryState,
    itemId: Int,
    onRefresh: () -> Unit,
    onGenerate: (force: Boolean) -> Unit,
) {
    when (state) {
        ReaderSummaryState.Idle -> SummaryUnavailableMessage()
        is ReaderSummaryState.Loading -> {
            val previous = state.previous
            if (previous != null && state.itemId == itemId) {
                SummaryContent(
                    summary = previous,
                    loading = true,
                    onRefresh = onRefresh,
                    onGenerate = onGenerate,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("Loading summary...")
                }
            }
        }
        is ReaderSummaryState.Ready -> {
            if (state.itemId == itemId) {
                SummaryContent(
                    summary = state.summary,
                    loading = false,
                    onRefresh = onRefresh,
                    onGenerate = onGenerate,
                )
            } else {
                SummaryUnavailableMessage()
            }
        }
        is ReaderSummaryState.Error -> {
            if (state.itemId == itemId) {
                SummaryMessage(
                    title = "Summary unavailable",
                    body = state.message,
                )
            } else {
                SummaryUnavailableMessage()
            }
        }
    }
}

@Composable
private fun SummaryContent(
    summary: ContentSummaryOut,
    loading: Boolean,
    onRefresh: () -> Unit,
    onGenerate: (force: Boolean) -> Unit,
) {
    when (summary.normalizedState()) {
        ContentSummaryState.READY -> {
            SummaryText(summary)
            if (summary.canRefreshOutdatedSummary()) {
                SummaryMessage(
                    title = summaryOutdatedTitle(),
                    body = summaryOutdatedBody(),
                )
                SummarySecondaryButton("Refresh summary", loading) { onGenerate(true) }
            }
        }
        ContentSummaryState.STALE -> {
            SummaryMessage(
                title = "Summary may be out of date",
                body = "This summary was generated before the latest readable text was saved.",
            )
            SummaryText(summary)
            if (summary.canRequestGeneration()) {
                SummaryPrimaryButton("Generate updated summary", loading) { onGenerate(false) }
            }
        }
        ContentSummaryState.MISSING -> {
            val canRequest = summary.canRequestGeneration()
            SummaryMessage(
                title = if (canRequest) "No summary yet" else "Summary unavailable",
                body = if (canRequest) {
                    "Generate a summary from the server when you want one."
                } else {
                    summaryUnavailableBody(summary)
                },
            )
            if (canRequest) {
                SummaryPrimaryButton("Generate summary", loading) { onGenerate(false) }
            }
        }
        ContentSummaryState.PENDING -> {
            SummaryMessage(
                title = "Generating summary",
                body = "The server is generating this summary. Tap Check status to see if it's ready.",
            )
            SummarySecondaryButton("Check status", loading, onRefresh)
        }
        ContentSummaryState.FAILED -> {
            SummaryMessage(
                title = "Summary failed",
                body = summaryFailedBody(summary),
            )
            if (summary.canRequestGeneration()) {
                SummaryPrimaryButton("Retry", loading) { onGenerate(true) }
            }
        }
        ContentSummaryState.UNKNOWN -> {
            SummaryMessage(
                title = "Summary unavailable",
                body = "This summary state is not supported by this app version.",
            )
        }
    }
}

@Composable
private fun SummaryText(summary: ContentSummaryOut) {
    Text(
        text = summaryDisclaimerText(summary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontStyle = FontStyle.Italic,
    )
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = summary.summaryText.orEmpty().ifBlank { "Summary text is not available." },
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .heightIn(min = 96.dp, max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        )
    }
    SummaryMetadata(summary)
}

@Composable
private fun SummaryMetadata(summary: ContentSummaryOut) {
    val parts = summaryMetadataParts(summary)
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString("  |  "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun summaryUnavailableBody(summary: ContentSummaryOut): String {
    val reason = contentSummaryFailureReasonFromCode(summary.failureReason)
    return reason?.let(::contentSummaryFailureMessage)
        ?: "Summaries are not available for this item right now."
}

internal fun summaryFailedBody(summary: ContentSummaryOut): String {
    val reason = contentSummaryFailureReasonFromCode(summary.failureReason)
    return reason?.let(::contentSummaryFailureMessage)
        ?: "The last summary attempt did not finish. Try again when the server is available."
}

internal fun summaryOutdatedTitle(): String = "Summary uses an older prompt"

internal fun summaryOutdatedBody(): String =
    "A newer summary prompt is available. Tap Refresh to regenerate this summary."

internal fun summaryDisclaimerText(summary: ContentSummaryOut): String {
    return summary.disclaimer
        ?.takeIf { it.isNotBlank() }
        ?: "AI-generated summary. Verify important details."
}

internal fun summaryMetadataParts(summary: ContentSummaryOut): List<String> {
    val generated = summary.generatedAt?.takeIf { it.isNotBlank() }?.let { "Generated $it" }
    val provider = summary.provider?.takeIf { it.isNotBlank() }
    val model = summary.model?.takeIf { it.isNotBlank() }
    val modelPart = when {
        provider != null && model != null -> "Model $provider/$model"
        provider != null -> "Provider $provider"
        else -> null
    }
    return listOfNotNull(generated, modelPart)
}

@Composable
private fun SummaryMessage(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryUnavailableMessage() {
    SummaryMessage(
        title = "Summary unavailable",
        body = "Open a readable item to view or request its summary.",
    )
}

@Composable
private fun SummaryPrimaryButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !loading,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(label)
    }
}

@Composable
private fun SummarySecondaryButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !loading,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(label)
    }
}
