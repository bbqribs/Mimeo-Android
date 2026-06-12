package com.mimeo.android.model

// Debug-only developer presets. These carry the developer's real backend host identity so
// the emulator/dev workflow keeps working out of the box. They are compiled ONLY into debug
// builds (src/debug source set) and must never leak into release defaults, preset chips,
// strings, or BuildConfig. The release counterpart lives in src/release/.../ConnectionHostDefaults.kt.
internal const val DEVELOPER_PRESETS_AVAILABLE = true

internal const val DEFAULT_LAN_HOST = "192.168.68.124:8000"
internal const val DEFAULT_REMOTE_HTTPS_HOST = "beh-august2015.taildacac5.ts.net"
internal const val DEFAULT_REMOTE_HTTP_FALLBACK_HOST = "100.84.13.10:8000"
