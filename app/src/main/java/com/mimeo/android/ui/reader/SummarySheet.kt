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
import androidx.compose.material3.Button
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
import com.mimeo.android.model.ContentSummaryOut
import com.mimeo.android.model.ContentSummaryState
import com.mimeo.android.model.ReaderSummaryState
import com.mimeo.android.model.canRequestGeneration
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
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
            HorizontalDivider()
            SummarySheetBody(
                state = state,
                itemId = itemId,
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
    onGenerate: (force: Boolean) -> Unit,
) {
    when (state) {
        ReaderSummaryState.Idle -> SummaryUnavailableMessage()
        is ReaderSummaryState.Loading -> {
            val previous = state.previous
            if (previous != null && state.itemId == itemId) {
                SummaryContent(summary = previous, loading = true, onGenerate = onGenerate)
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
                SummaryContent(summary = state.summary, loading = false, onGenerate = onGenerate)
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
    onGenerate: (force: Boolean) -> Unit,
) {
    when (summary.normalizedState()) {
        ContentSummaryState.READY -> {
            SummaryText(summary)
        }
        ContentSummaryState.STALE -> {
            SummaryMessage(
                title = "Summary may be out of date",
                body = "This summary was generated for an older version of the item.",
            )
            SummaryText(summary)
            if (summary.canRequestGeneration()) {
                SummaryPrimaryButton("Generate new summary", loading) { onGenerate(false) }
            }
        }
        ContentSummaryState.MISSING -> {
            SummaryMessage(
                title = "No summary yet",
                body = if (summary.canRequestGeneration()) {
                    "Generate a summary from the server when you want one."
                } else {
                    "Summaries are not available for this item right now."
                },
            )
            if (summary.canRequestGeneration()) {
                SummaryPrimaryButton("Generate Summary", loading) { onGenerate(false) }
            }
        }
        ContentSummaryState.PENDING -> {
            SummaryMessage(
                title = "Generating summary",
                body = "The server is working on this summary. Refresh to check again.",
            )
        }
        ContentSummaryState.FAILED -> {
            SummaryMessage(
                title = "Summary failed",
                body = summary.failureReason?.takeIf { it.isNotBlank() }
                    ?.let { "The last summary attempt did not finish: $it." }
                    ?: "The last summary attempt did not finish.",
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
    summary.disclaimer?.takeIf { it.isNotBlank() }?.let { disclaimer ->
        Text(
            text = disclaimer,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
        )
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = summary.summaryText.orEmpty().ifBlank { "Summary text is not available." },
            style = MaterialTheme.typography.bodyLarge,
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
    val parts = listOfNotNull(
        summary.generatedAt?.takeIf { it.isNotBlank() }?.let { "Generated $it" },
        summary.model?.takeIf { it.isNotBlank() },
        summary.promptVersion?.takeIf { it.isNotBlank() },
    )
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" • "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
