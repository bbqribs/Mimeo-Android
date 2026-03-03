package com.mimeo.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.FolderSummary

@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<FolderSummary>,
    assignedFolderId: Int?,
    onDismiss: () -> Unit,
    onSelectFolder: (Int?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to folder...") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (folders.isEmpty()) {
                    Text(
                        text = "No folders yet. Create one first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = assignedFolderId != null,
                        onClick = { onSelectFolder(null) },
                    ) {
                        Text("Remove from folder")
                    }
                    folders.forEach { folder ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectFolder(folder.id) },
                        ) {
                            Text(
                                text = if (folder.id == assignedFolderId) {
                                    "${folder.name} (Current)"
                                } else {
                                    folder.name
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
