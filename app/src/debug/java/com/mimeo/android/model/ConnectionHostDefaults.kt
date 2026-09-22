package com.mimeo.android.model

import com.mimeo.android.BuildConfig

// Debug-only developer presets. The remote URL is injected from the sibling Mimeo repository's
// runtime-target.json. If that source is unavailable or invalid, the remote preset is omitted.
// These values are compiled ONLY into debug builds (src/debug source set). The release counterpart
// lives in src/release/.../ConnectionHostDefaults.kt and remains host-neutral.
internal const val DEVELOPER_PRESETS_AVAILABLE = true

// Labelled in the UI as a local-development example; it is not an authoritative runtime target.
internal const val DEFAULT_LAN_HOST = "192.168.68.124:8000"
internal val DEFAULT_REMOTE_BASE_URL = BuildConfig.DEBUG_REMOTE_BASE_URL
internal val REMOTE_DEVELOPER_PRESET_AVAILABLE = DEFAULT_REMOTE_BASE_URL.isNotBlank()
