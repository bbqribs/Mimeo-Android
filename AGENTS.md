# Mimeo Android - Shared Agent Rules

## Multi-agent workflow (Codex + Claude + humans)
- **Lane model**: Claude is the primary implementer for Mimeo-Android. Codex is secondary in this repo unless explicitly assigned an Android ticket.
- **Default scope**: In Mimeo-Android, Codex defaults to read-only validation/support unless explicitly assigned implementation work.
- **Single-writer per PR/branch**: Exactly one agent may push commits to a given PR branch. No tag-team pushing.
- **Merge authority**: The agent owning the ticket/branch may merge its PR.
- **Serialized merges**: Only one merge operation may happen at a time across BOTH repos (Mimeo + Mimeo-Android).
- **No history rewrites**: No rebases or force-pushes by agents. Additive commits only.
- **Contract-change flag**: Any PR changing backend/API semantics must be labeled "CONTRACT CHANGE"; dependent Android work must not assume the change until merged.
- **Local safety**: If agents share a machine, do not share a working directory; avoid stash workflows; tracked local modifications => STOP.
- **Precedence**: `AGENTS.md` is authoritative for shared lifecycle hygiene and workflow rules for all agents in this repo. `CLAUDE.md` is authoritative for Claude-specific behavior. `CODEX_PROMPTS.md` is authoritative for Codex-specific behavior. Tool-specific docs may add stricter rules but must not weaken `AGENTS.md`.

## Project context (when assigned implementation)
Mimeo Android is the mobile client for the Mimeo "read later" system.

## Backend connection (when assigned implementation)
- **Emulator**: Use `baseUrl=http://10.0.2.2:8000`
- **Physical device (LAN)**: Use `baseUrl=http://<your-PC-LAN-IP>:8000` or `https://` if TLS is configured.
- **Auth**: Prefer per-device tokens over the legacy shared `API_TOKEN`.

## Conditional remote-backend verification
- Android tickets remain Android-first by default.
- Run remote backend checks only when the Android work touched backend behavior/contracts, or explicitly depends on backend changes.
- In backend-dependent cases, verify against `https://beh-august2015.taildacac5.ts.net/` (not `127.0.0.1`). The raw Tailscale IP (`http://100.84.13.10:8000`) is legacy/fallback only.
- If backend deployment verification is needed, run Mimeo repo scripts (not Android-local scripts):
  - quick sync: `powershell -ExecutionPolicy Bypass -File C:\Users\brend\Documents\Coding\Mimeo\scripts\stage2-runtime-sync.ps1 -Action Install`
  - full sync when quick sync is insufficient: `powershell -ExecutionPolicy Bypass -File C:\Users\brend\Documents\Coding\Mimeo\scripts\stage2-runtime-sync.ps1 -Action InstallFull`
- Reference: `C:\Users\brend\Documents\Coding\Mimeo\docs\REMOTE_RUNTIME_VERIFICATION_PROTOCOL.md`

## Operator reporting default
- Always report explicitly: what changed, what passed, what failed, and exact next step if blocked.
- Always include plain-English manual verification steps.
- Include copyable command blocks when commands are relevant.
- Post-merge report: After a ticket is merged, provide a concise full summary of delivered scope, changed files, and test/build results; omit manual verification steps unless explicitly requested.

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

## Ticket lifecycle hygiene

### Preflight

At session start, report:
- current branch
- current SHA (`git rev-parse HEAD`)
- upstream tracking + ahead/behind count
- `git status -sb`
- tracked diff summary (files changed, nature)
- untracked file summary

Stop before work if any condition is true:
- tracked modifications exist outside expected files for this ticket
- current branch belongs to another agent or ticket
- local branch is behind or ahead unexpectedly
- checkout is not on the requested base branch
- sensitive-looking untracked files would be touched by this work

Never stash, reset, clean, delete, overwrite, or move files without explicit operator instruction.

### Implementation discipline

Before editing, declare expected files. Keep all changes inside declared scope.
No broad formatting passes, no dependency upgrades unless explicitly requested.
No tag-team commits on another agent's branch.
Never print secrets, tokens, .env values, cookies, browser profiles, or backup contents.

### PR / open report

Before awaiting merge approval, report:
- branch name and PR URL
- commit SHA
- changed files
- tests run and results
- tests skipped and reason
- manual verification steps
- explicit statement: `not merged; awaiting operator approval`

### Merge trigger

When the operator says "merge this", "merged", "I merged it", "I've merged", or any equivalent, immediately run the post-merge closeout for that PR/ticket. Do not wait for a separate closeout ticket or prompt. Do not repeat manual verification steps unless explicitly requested. Report: final canonical branch SHA, PR merge state, clean tracked tree, untracked summary, and test/build results.

### Post-merge closeout

Canonical branch: `main`.

1. Sync `main`.
2. Confirm: final SHA, `git status -sb`, PR merge state via `gh pr view <PR>`, tracked tree clean, untracked files summarized.
3. For Android-only PRs: include Gradle gate summary. Run remote backend checks only if the PR touched backend contracts or runtime.
4. If runtime deploy/sync was in scope: runtime sync result, smoke result, remote git checkout state if relevant.
5. Never say "merged" unless `gh pr view` confirms state is `MERGED`.
