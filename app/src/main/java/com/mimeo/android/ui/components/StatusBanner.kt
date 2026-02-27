package com.mimeo.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
                if (onRetry != null) {
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 32.dp),
                    ) {
                        Text("R", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onDiagnostics != null) {
                    IconButton(
                        onClick = onDiagnostics,
                        modifier = Modifier.heightIn(min = 32.dp),
                    ) {
                        Text("i", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!detail.isNullOrBlank()) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.heightIn(min = 32.dp),
                    ) {
                        Text(if (expanded) "^" else "v", style = MaterialTheme.typography.labelSmall)
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
