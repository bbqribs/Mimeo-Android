package com.mimeo.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared loading / error / empty pane (T-C). Renders, in priority order: a centered
 * spinner while [loading], an error message with an optional Retry action when
 * [error] is set, an [emptyMessage] when [empty] and nothing else applies, and
 * otherwise [content].
 *
 * Error-copy convention for callers: sentence case; prefer "Couldn't <do the thing>.
 * Try again." for generic/transient failures; skip "Please" unless the message gives
 * a specific instruction to follow (e.g. "Session expired. Please sign in again.").
 */
@Composable
fun LoadStatePane(
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    empty: Boolean = false,
    emptyMessage: String? = null,
    content: @Composable () -> Unit = {},
) {
    when {
        loading -> Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        error != null -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
        empty && emptyMessage != null -> Text(
            text = emptyMessage,
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> content()
    }
}
