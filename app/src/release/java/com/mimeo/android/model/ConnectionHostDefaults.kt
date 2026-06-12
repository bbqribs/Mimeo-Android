package com.mimeo.android.model

// Release neutral defaults. No personal backend host identity ships in release builds:
// developer presets are unavailable, and the host placeholders are generic templates the
// user replaces with their own server URL via manual entry. The debug counterpart with the
// real developer presets lives in src/debug/.../ConnectionHostDefaults.kt.
internal const val DEVELOPER_PRESETS_AVAILABLE = false

internal const val DEFAULT_LAN_HOST = "<LAN-IP>:8000"
internal const val DEFAULT_REMOTE_HTTPS_HOST = "<machine>.<tailnet>.ts.net"
internal const val DEFAULT_REMOTE_HTTP_FALLBACK_HOST = "<tailscale-ip>:8000"
