# Mimeo Android - Codex Instructions

## Multi-agent workflow (Codex + Claude + humans)
- **Lane model**: Claude is the primary implementer for Mimeo-Android. Codex is secondary in this repo unless explicitly assigned an Android ticket.
- **Default scope**: In Mimeo-Android, Codex defaults to read-only validation/support unless explicitly assigned implementation work.
- **Single-writer per PR/branch**: Exactly one agent may push commits to a given PR branch. No tag-team pushing.
- **Merge authority**: The agent owning the ticket/branch may merge its PR.
- **Serialized merges**: Only one merge operation may happen at a time across BOTH repos (Mimeo + Mimeo-Android).
- **No history rewrites**: No rebases or force-pushes by agents. Additive commits only.
- **Contract-change flag**: Any PR changing backend/API semantics must be labeled "CONTRACT CHANGE"; dependent Android work must not assume the change until merged.
- **Local safety**: If agents share a machine, do not share a working directory; avoid stash workflows; tracked local modifications => STOP.
- **Precedence**: `AGENTS.md` is authoritative for Codex behavior in this repo. `CODEX_PROMPTS.md` should remain in sync.

## Project context (when assigned implementation)
Mimeo Android is the mobile client for the Mimeo "read later" system.

## Backend connection (when assigned implementation)
- **Emulator**: Use `baseUrl=http://10.0.2.2:8000`
- **Physical device (LAN)**: Use `baseUrl=http://<your-PC-LAN-IP>:8000` or `https://` if TLS is configured.
- **Auth**: Prefer per-device tokens over the legacy shared `API_TOKEN`.

## Conditional remote-backend verification
- Android tickets remain Android-first by default.
- Run remote backend checks only when the Android work touched backend behavior/contracts, or explicitly depends on backend changes.
- In backend-dependent cases, verify against `http://100.84.13.10:8000` (not `127.0.0.1`).
- If backend deployment verification is needed, run Mimeo repo scripts (not Android-local scripts):
  - quick sync: `powershell -ExecutionPolicy Bypass -File C:\Users\brend\Documents\Coding\Mimeo\scripts\stage2-runtime-sync.ps1 -Action Install`
  - full sync when quick sync is insufficient: `powershell -ExecutionPolicy Bypass -File C:\Users\brend\Documents\Coding\Mimeo\scripts\stage2-runtime-sync.ps1 -Action InstallFull`
- Reference: `C:\Users\brend\Documents\Coding\Mimeo\docs\REMOTE_RUNTIME_VERIFICATION_PROTOCOL.md`

## Operator reporting default
- Always report explicitly: what changed, what passed, what failed, and exact next step if blocked.
- Always include plain-English manual verification steps.
- Include copyable command blocks when commands are relevant.

## Build and test (when assigned implementation)
```bash
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

## Context hygiene
- Treat one ticket as one working session.
- Start a fresh session when switching tickets.
- Do not rely on stale chat history for repo policy; rely on this file, the repo, and the active ticket.

## Current focus
See `ROADMAP.md` for active priorities.

## Related repo
Backend + extension + scripts: `C:\Users\brend\Documents\Coding\Mimeo`
