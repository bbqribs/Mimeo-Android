package com.mimeo.android.ui.bluesky

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.BlueskyCandidateSourceSelection
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active
import com.mimeo.android.util.bluesky.normalizeBlueskyHandleInput
import com.mimeo.android.util.bluesky.parseBlueskyListIdentifierInput

private enum class BlueskyScanMode { Handle, ListUrl }

@Composable
internal fun BlueskyCandidateInputSection(
    loading: Boolean,
    onScan: (BlueskyCandidateSourceSelection) -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    var scanMode by remember { mutableStateOf(BlueskyScanMode.Handle) }
    // Per-mode drafts so toggling Handle <-> List URL does not discard typed text.
    var handleDraft by remember { mutableStateOf("") }
    var listDraft by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    val submitHandle = {
        val handle = normalizeBlueskyHandleInput(handleDraft)
        if (handle != null) {
            inputError = null
            onScan(BlueskyCandidateSourceSelection("account", "@$handle", actor = handle))
        } else {
            inputError = "Handle is required."
        }
    }
    val submitList = {
        val parsed = parseBlueskyListIdentifierInput(listDraft)
        if (parsed.ok) {
            inputError = null
            onScan(BlueskyCandidateSourceSelection("list_feed", "Bluesky list", uri = parsed.uri))
        } else {
            inputError = parsed.error
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = scanMode == BlueskyScanMode.Handle,
                onClick = {
                    scanMode = BlueskyScanMode.Handle
                    inputError = null
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                enabled = !loading,
            ) {
                Text("Handle")
            }
            SegmentedButton(
                selected = scanMode == BlueskyScanMode.ListUrl,
                onClick = {
                    scanMode = BlueskyScanMode.ListUrl
                    inputError = null
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = !loading,
            ) {
                Text("List URL")
            }
        }
        when (scanMode) {
            BlueskyScanMode.Handle -> BlueskyHandleField(
                value = handleDraft,
                onValueChange = { handleDraft = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                onSearch = submitHandle,
            )
            BlueskyScanMode.ListUrl -> BlueskyListUriField(
                value = listDraft,
                onValueChange = { listDraft = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                onSearch = submitList,
            )
        }
        if (!inputError.isNullOrBlank()) {
            Text(
                inputError.orEmpty(),
                color = if (isV1) mColors.danger else MaterialTheme.colorScheme.error,
                style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
            )
        }
    }
}
