# Mimeo Android - Codex Instructions

**Read `AGENTS.md` first.** It is the shared policy owner for this repository and
it is self-contained: project boundary, cross-repository authority, worktree and
WSL rules, backend-target resolution, build and test commands, reporting
defaults, source precedence, coordination channel, and the full ticket lifecycle
(preflight, implementation discipline, PR/open report, merge authority, merge
trigger, deployment assessment, post-merge closeout) all live there and apply to
Codex unchanged — including the rules that used to be written as if they only
bound Claude.

This file adds only Codex-harness differences and reusable prompt scaffolding. It
deliberately does not restate shared policy, and it may not weaken `AGENTS.md`.

**Precedence**: `CODEX_PROMPTS.md` is authoritative for Codex-specific behaviour
in this repo; `CLAUDE.md` is authoritative for Claude-specific behaviour;
`AGENTS.md` is authoritative for everything shared. Where this file appears to
conflict with `AGENTS.md` on a shared rule, `AGENTS.md` wins and this file is the
bug.

## Codex session behaviour

- A Codex session usually starts from a pasted ticket prompt rather than from an
  accumulated conversation. That prompt is the ticket; it is not repository
  policy, and it does not override `AGENTS.md`. Read `AGENTS.md` at session start
  even when the prompt looks complete.
- A pasted prompt may carry stale facts copied from an older ticket — a
  hostname, a model choice, a file path, a branch name. Resolve those from the
  repository at execution time: the runtime target from the resolver named in
  `AGENTS.md §Backend-dependent verification`, model routing from Mimeo's
  canonical routing documents and the live picker, priorities from `ROADMAP.md`.
- One ticket is one session. Do not continue a previous ticket's work in a
  session opened for a new one.

## Prompt preamble for Codex tickets

A ticket prompt for this repository should open with the orientation below, so
the session reads policy from the repository rather than inheriting it from the
prompt author's memory. Fill the placeholders; add nothing that restates a rule
`AGENTS.md` already owns.

```text
Repository: Mimeo-Android (Android client only; backend lives in the sibling
Mimeo repository and is read-only to you unless the operator says otherwise).

Read AGENTS.md in full before starting. It owns the shared rules: project
boundary, cross-repository authority, worktree isolation, Windows/WSL test
boundary, backend-target resolution, build and test gates, failing-gate
handling, reporting, merge authority and closeout. Read CODEX_PROMPTS.md for
Codex-specific behaviour. Do not take repository policy from this prompt.

Working directory: <assigned isolated checkout or worktree>
Branch: <branch>, based on <base>
Ticket: <intended outcome, settled decisions, hard scope boundaries, invariants,
required evidence, stop conditions, minimum closeout report>
Remote backend checks: <not required | required, and why acceptance depends on
the authoritative runtime>
```

No further Codex-only constraint is recorded for this repository. Add one here
only when a current, reproducible Codex limitation justifies it, and name the
limitation — not a remembered one from an older ticket.
