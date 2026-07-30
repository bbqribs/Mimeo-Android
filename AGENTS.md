# Mimeo Android - Shared Agent Rules

## Multi-agent workflow (Codex + Claude + humans)
- **Model routing**: Use Mimeo's canonical documents — the [routing policy](https://github.com/bbqribs/Mimeo/blob/master/docs/AI_MODEL_ROUTING_POLICY.md) for durable principles, the [model inventory](https://github.com/bbqribs/Mimeo/blob/master/docs/AI_MODEL_INVENTORY.md) for current dated model/plan/effort facts, and the [performance ledger](https://github.com/bbqribs/Mimeo/blob/master/docs/AI_MODEL_PERFORMANCE_LEDGER.md) for evidenced outcomes — plus the live model picker and usage/headroom information. This repository deliberately keeps no model inventory of its own; do not add one here.
  - **Do not assign models primarily by repository.** Choose the model and harness that best fit the task's capability, risk, tool-access, and execution requirements. Treat repository familiarity and historical ownership as secondary considerations, not capability substitutes.
  - **If the sibling Mimeo checkout is unavailable**, use the operator's explicit assignment and the live model picker. Do not infer the current model choice from old ticket text, prose in this repo, or a dated worked example.
- **Single-writer per PR/branch**: Exactly one agent may push commits to a given PR branch. No tag-team pushing.
- **Merge authority**: Claude and Codex may merge pull requests only when explicitly instructed by the operator. Without explicit merge instruction, stop after opening or updating the PR and report `not merged; awaiting operator approval`. Owning the ticket or branch is not a merge entitlement. See `§Merge authority (canonical)` below.
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

## Avoid redundant validation
- Do not rerun a build, test, lint, smoke, or deployment check when a trustworthy passing result already covers the same relevant source tree and configuration. Reuse and report that result instead.
- A merge commit does not by itself require rerunning checks when it introduces no content changes relative to the already-tested PR head. Prefer the completed PR checks and any automatic post-merge CI runs.
- Rerun validation only when the relevant inputs changed, a required result is missing, failed, cancelled, stale, or otherwise unreliable, the merge introduced conflict-resolution changes, or the ticket/operator explicitly requires a fresh run.
- Distinguish validation executed in the current closeout from earlier or CI validation being reused; report the source commit or workflow run when useful.
- This permits reuse; it never weakens a stricter requirement stated by a ticket or by repository policy.

Closeout reports must distinguish three failure kinds rather than collapsing them into "tests failed":

- **code or test failure** — the change is wrong;
- **environment-caused test failure** — the change is fine, the local environment is not (name the artifact or condition);
- **CI infrastructure unavailability** — including jobs that never started because of billing, spending limits, or a platform fault. A job that never ran is not a passing job and is not a failing test.

## Source precedence

Before proposing or starting the next ticket, inspect this repository's current `ROADMAP.md` and its recent merged work. Then resolve conflicts in this order:

- `ROADMAP.md` owns current sequencing and priority.
- Accepted contracts and decision documents own settled behaviour. Backend/API contracts are owned by the Mimeo repository; do not assume an unmerged contract.
- Current code and tests own shipped implementation reality.
- Older conversations, summaries, and external stable-reference files supply context but do not override fresher repository evidence.

State explicitly which of these a claim rests on when they disagree, and distinguish among shipped, active, planned, newly proposed, and trigger-gated work.

## Ticket construction

**Tickets must be decision-complete, not implementation-complete.**

A ticket should state the intended outcome; settled product, security, or architectural decisions; dangerous shortcuts and prohibited approaches; hard scope boundaries; load-bearing invariants; required evidence and gates; meaningful stop conditions; and the minimum closeout report.

A ticket should normally delegate call-site and dependency discovery, exact file selection, internal implementation design, routine refactoring choices, expanded test-matrix construction, and identification of incidental documentation changes. Prescribe implementation detail only when it captures a decision or hazard the agent cannot safely infer — for this repository, that includes the known-fragile areas (Smart Queue drag/reorder geometry, `pointerInput` consolidation, cold-launch behaviour).

Token efficiency is **total completion cost** — exploration, tool use, rework, verification, and review — not prompt length. There is no word limit: a ticket should be no longer than necessary to preserve decisions and prevent predictable mistakes.

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

### Merge authority (canonical)

Claude and Codex may merge pull requests **only when explicitly instructed by the operator**. Without an explicit merge instruction, the agent must stop after opening or updating the PR and report `not merged; awaiting operator approval`.

This is the single authoritative merge rule for all agents in this repository. It replaces the earlier rule that let the agent owning the ticket or branch merge its own PR. Owning a ticket or branch confers no merge entitlement, and no ticket template or workflow shortcut confers one — an instruction to open a PR is not an instruction to merge it.

The **one-merge-at-a-time-per-repository** rule is preserved and unchanged: only one merge operation may happen at a time across both Mimeo and Mimeo-Android.

Merging when explicitly instructed remains ordinary work, not an escalation. The rule constrains the default, not the operator.

This matches `AGENTS.md §Merge authority (canonical)` in the Mimeo repository, so both repositories hold the same merge rule.

### Merge trigger

When the operator says "merge this", "merged", "I merged it", "I've merged", or any equivalent, immediately run the post-merge closeout for that PR/ticket. Do not wait for a separate closeout ticket or prompt. Do not repeat manual verification steps unless explicitly requested. Report: final canonical branch SHA, PR merge state, clean tracked tree, untracked summary, and test/build results.

### Post-merge closeout

Canonical branch: `main`.

1. Sync `main`.
2. Confirm: final SHA, `git status -sb`, PR merge state via `gh pr view <PR>`, tracked tree clean, untracked files summarized.
3. For Android-only PRs: include the existing Gradle gate summary without rerunning passing gates unless the redundant-validation rules require it. Run remote backend checks only if the PR touched backend contracts or runtime.
4. If runtime deploy/sync was in scope: runtime sync result, smoke result, remote git checkout state if relevant.
5. Never say "merged" unless `gh pr view` confirms state is `MERGED`.
