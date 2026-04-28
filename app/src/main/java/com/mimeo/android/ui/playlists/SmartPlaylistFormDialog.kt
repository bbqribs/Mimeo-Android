package com.mimeo.android.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.SmartPlaylistDetail
import com.mimeo.android.model.SmartPlaylistFilterDefinition
import com.mimeo.android.model.SmartPlaylistSummary
import com.mimeo.android.model.SmartPlaylistWriteRequest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal data class SmartPlaylistFormState(
    val name: String = "",
    val keyword: String = "",
    val sourceLabels: String = "",
    val domains: String = "",
    val captureKinds: String = "",
    val savedAfter: String = "",
    val savedBefore: String = "",
    val dateWindow: String = "",
    val includeArchived: String = "false",
    val favoritesOnly: Boolean = false,
    val readStatus: String = "any",
    val sort: String = "saved_desc",
) {
    fun toRequest(): SmartPlaylistWriteRequest =
        SmartPlaylistWriteRequest(
            name = name.trim(),
            filterDefinition = SmartPlaylistFilterDefinition(
                keyword = keyword.trim().ifBlank { null },
                sourceLabels = sourceLabels.csvValues().ifEmpty { null },
                domains = domains.csvValues().ifEmpty { null },
                captureKinds = captureKinds.csvValues().ifEmpty { null },
                savedAfter = savedAfter.trim().ifBlank { null },
                savedBefore = savedBefore.trim().ifBlank { null },
                dateWindow = dateWindow.ifBlank { null },
                includeArchived = includeArchived,
                favoritesOnly = favoritesOnly,
                readStatus = readStatus,
            ),
            sort = sort,
        )

    companion object {
        fun fromDetail(detail: SmartPlaylistDetail): SmartPlaylistFormState {
            val filter = detail.filterDefinition
            return SmartPlaylistFormState(
                name = detail.name,
                keyword = filter.stringValue("keyword").orEmpty(),
                sourceLabels = filter.stringList("source_labels").joinToString(", "),
                domains = filter.stringList("domains").joinToString(", "),
                captureKinds = filter.stringList("capture_kinds").joinToString(", "),
                savedAfter = filter.stringValue("saved_after").orEmpty(),
                savedBefore = filter.stringValue("saved_before").orEmpty(),
                dateWindow = filter.stringValue("date_window").orEmpty(),
                includeArchived = filter.stringValue("include_archived") ?: "false",
                favoritesOnly = filter.booleanValue("favorites_only") == true,
                readStatus = filter.stringValue("read_status") ?: "any",
                sort = detail.sort,
            )
        }
    }
}

@Composable
internal fun SmartPlaylistFormDialog(
    title: String,
    initialState: SmartPlaylistFormState = SmartPlaylistFormState(),
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSubmit: suspend (SmartPlaylistWriteRequest) -> Result<SmartPlaylistSummary>,
    onSaved: (SmartPlaylistSummary) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var form by remember(initialState) { mutableStateOf(initialState) }
    var saving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!saving) onDismiss()
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = {
                        form = form.copy(name = it)
                        errorText = null
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = errorText != null && form.name.isBlank(),
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.keyword,
                    onValueChange = { form = form.copy(keyword = it) },
                    label = { Text("Keyword") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.sourceLabels,
                    onValueChange = { form = form.copy(sourceLabels = it) },
                    label = { Text("Source labels") },
                    placeholder = { Text("Comma-separated") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.domains,
                    onValueChange = { form = form.copy(domains = it) },
                    label = { Text("Domains") },
                    placeholder = { Text("example.com, news.example") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.captureKinds,
                    onValueChange = { form = form.copy(captureKinds = it) },
                    label = { Text("Capture kinds") },
                    placeholder = { Text("link, manual_text") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.savedAfter,
                    onValueChange = { form = form.copy(savedAfter = it) },
                    label = { Text("Saved after") },
                    placeholder = { Text("YYYY-MM-DD or ISO time") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.savedBefore,
                    onValueChange = { form = form.copy(savedBefore = it) },
                    label = { Text("Saved before") },
                    placeholder = { Text("YYYY-MM-DD or ISO time") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceField(
                    label = "Date window",
                    value = form.dateWindow,
                    choices = listOf(
                        "" to "Any time",
                        "last_24h" to "Last 24 hours",
                        "last_7d" to "Last 7 days",
                        "last_30d" to "Last 30 days",
                        "last_90d" to "Last 90 days",
                    ),
                    enabled = !saving,
                    onValueChange = { form = form.copy(dateWindow = it) },
                )
                ChoiceField(
                    label = "Archived",
                    value = form.includeArchived,
                    choices = listOf(
                        "false" to "Exclude archived",
                        "true" to "Include archived",
                        "only" to "Archived only",
                    ),
                    enabled = !saving,
                    onValueChange = { form = form.copy(includeArchived = it) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = form.favoritesOnly,
                        onCheckedChange = { form = form.copy(favoritesOnly = it) },
                        enabled = !saving,
                    )
                    Text("Favorites only", style = MaterialTheme.typography.bodyMedium)
                }
                ChoiceField(
                    label = "Read status",
                    value = form.readStatus,
                    choices = listOf(
                        "any" to "Any",
                        "unread" to "Unread",
                        "in_progress" to "In progress",
                        "done" to "Done",
                    ),
                    enabled = !saving,
                    onValueChange = { form = form.copy(readStatus = it) },
                )
                ChoiceField(
                    label = "Sort",
                    value = form.sort,
                    choices = listOf(
                        "saved_desc" to "Newest saved first",
                        "saved_asc" to "Oldest saved first",
                    ),
                    enabled = !saving,
                    onValueChange = { form = form.copy(sort = it) },
                )
                errorText?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    if (form.name.isBlank()) {
                        errorText = "Smart playlist name is required."
                        return@TextButton
                    }
                    saving = true
                    errorText = null
                    scope.launch {
                        onSubmit(form.toRequest())
                            .onSuccess { onSaved(it) }
                            .onFailure { error ->
                                errorText = error.message ?: "Couldn't save smart playlist."
                            }
                        saving = false
                    }
                },
            ) {
                Text(if (saving) "Saving..." else confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !saving,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ChoiceField(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.first == value }?.second ?: value.ifBlank { "Any" }
    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            enabled = enabled,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label)
                Text(
                    text = selectedLabel,
                    modifier = Modifier.padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            choices.forEach { (choiceValue, choiceLabel) ->
                DropdownMenuItem(
                    text = { Text(choiceLabel) },
                    onClick = {
                        expanded = false
                        onValueChange(choiceValue)
                    },
                )
            }
        }
    }
}

private fun String.csvValues(): List<String> =
    split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

private fun JsonObject.stringValue(key: String): String? =
    (get(key) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun JsonObject.booleanValue(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.stringList(key: String): List<String> =
    (get(key) as? JsonArray)
        ?.mapNotNull { it.stringContentOrNull() }
        .orEmpty()

private fun JsonElement.stringContentOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
