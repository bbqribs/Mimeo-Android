package com.mimeo.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun StatusBanner(
    stateLabel: String,
    summary: String,
    detail: String? = null,
    onRetry: (() -> Unit)? = null,
    onDiagnostics: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(stateLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.heightIn(min = 28.dp),
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (onRetry != null) {
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.heightIn(min = 28.dp),
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (onDiagnostics != null) {
                        TextButton(
                            onClick = onDiagnostics,
                            modifier = Modifier.heightIn(min = 28.dp),
                        ) {
                            Text("Diag", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (!detail.isNullOrBlank()) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.heightIn(min = 28.dp),
                        ) {
                            Text(if (expanded) "Less" else "More", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (expanded && !detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
