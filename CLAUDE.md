# Mimeo Android - Claude Instructions

## Multi-agent concurrency policy (Codex + Claude + humans)

- **Parallelism allowed**: Codex and Claude may both implement tickets, including code changes, in either repo.
- **Single-writer per PR/branch**: Exactly one agent may push commits to a given PR branch. No tag-team pushing.
- **Merge authority**: Only Codex may merge PRs (or perform the final "merge step" for the session).
- **Serialized merges**: Only one merge operation may happen at a time across BOTH repos (Mimeo + Mimeo-Android).
- **No history rewrites**: No rebases or force-pushes by agents. Additive commits only.
- **Contract-change flag**: Any PR that changes backend/API semantics must be labeled "CONTRACT CHANGE" and coordinated; dependent client work must not assume the new contract until merged.
- **Local safety**: If agents share a machine, do not share a working directory; avoid stash-based workflows; tracked local modifications => STOP.
- **Precedence**: CLAUDE.md is authoritative for Claude behavior; CODEX_PROMPTS.md is authoritative for Codex behavior.

## Project context

Mimeo Android is the mobile client for the Mimeo "read later" system. It communicates with a backend API running on a local or LAN-accessible server.

## Backend connection

- **Emulator**: Use `baseUrl=http://10.0.2.2:8000`
- **Physical device (LAN)**: Use `baseUrl=http://<your-PC-LAN-IP>:8000` or `https://` if TLS is configured
- **Per-device tokens**: Prefer creating device tokens over sharing the legacy `API_TOKEN`

## Build and test

```bash
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

## Related repo

Backend + extension + scripts: `C:\Users\brend\Documents\Coding\Mimeo`

Backend contract changes that affect Android (API additions, response format changes, playback endpoints) will be labeled "CONTRACT CHANGE" in the backend repo.
