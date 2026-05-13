package com.mimeo.android.ui.bluesky

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction

@Composable
internal fun BlueskyHandleField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Account by handle",
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text("alice.bsky.social") },
        singleLine = true,
        enabled = enabled,
        trailingIcon = if (onSearch != null) {
            {
                IconButton(onClick = onSearch, enabled = enabled) {
                    Icon(Icons.Default.Search, contentDescription = "Scan account")
                }
            }
        } else {
            null
        },
        keyboardOptions = if (onSearch != null) KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
        keyboardActions = if (onSearch != null) KeyboardActions(onSearch = { onSearch() }) else KeyboardActions.Default,
    )
}

@Composable
internal fun BlueskyListUriField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("List URL") },
        placeholder = { Text("https://bsky.app/profile/.../lists/...") },
        singleLine = true,
        enabled = enabled,
        trailingIcon = if (onSearch != null) {
            {
                IconButton(onClick = onSearch, enabled = enabled) {
                    Icon(Icons.Default.Search, contentDescription = "Scan list")
                }
            }
        } else {
            null
        },
        keyboardOptions = if (onSearch != null) KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
        keyboardActions = if (onSearch != null) KeyboardActions(onSearch = { onSearch() }) else KeyboardActions.Default,
    )
}
