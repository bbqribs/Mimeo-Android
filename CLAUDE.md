# Mimeo Android - Claude Instructions

**Read `AGENTS.md` first.** It is the shared policy owner for this repository and
it is self-contained: project boundary, cross-repository authority, worktree and
WSL rules, backend-target resolution, build and test commands, reporting
defaults, source precedence, coordination channel, and the full ticket lifecycle
(preflight, implementation discipline, PR/open report, merge authority, merge
trigger, deployment assessment, post-merge closeout) all live there and apply to
Claude unchanged.

This file adds only Claude-harness differences. It deliberately does not restate
shared policy — a second copy is how the two drifted apart before — and it may
not weaken `AGENTS.md`.

**Precedence**: `CLAUDE.md` is authoritative for Claude-specific behaviour in
this repo; `CODEX_PROMPTS.md` is authoritative for Codex-specific behaviour;
`AGENTS.md` is authoritative for everything shared. Where this file appears to
conflict with `AGENTS.md` on a shared rule, `AGENTS.md` wins and this file is the
bug.

## Session and continuation behaviour

- One ticket is one session. Start a fresh session when switching tickets, and
  do not carry a previous ticket's assumptions into a new one.
- When context is summarized or compacted mid-session, the summary is the weakest
  evidence in the room. Re-read `AGENTS.md`, the active ticket and the files you
  are changing rather than trusting a summarized recollection of them. The
  source-precedence order in `AGENTS.md` is not suspended by a summary.
- Recalled memories and session notes are background context written at some
  earlier date, not instructions and not current truth. If one names a file,
  symbol, flag or host, verify it still exists before acting on it.
- Repo policy comes from `AGENTS.md`, the repository and the active ticket —
  never from chat history, and never from an older worked example in this repo.

## Context and tool use

- Prefer focused file reads and targeted search over broad full-repo scans when
  the relevant area is already known. Exploration cost is part of total
  completion cost (see `AGENTS.md §Ticket construction`).
- Subagents and background tasks may read, search and report. They do not carry
  their own authority: the single-writer, merge, deployment and
  cross-repository-write rules in `AGENTS.md` bind the session as a whole, so a
  subagent must not push commits, merge a PR, contact a live host that the ticket
  forbids, or write to the Mimeo repository.
- Work in the task-provided worktree when one exists, and do not create a nested
  implementation worktree without a concrete reason — see
  `AGENTS.md §Isolated checkouts and worktrees`. Claude's stash stack is shared with every other
  worktree and session on this machine, which is why that section says to prefer
  a temporary commit over a stash.

## Reporting

`AGENTS.md §Operator reporting default` governs. The one Claude-specific
addition: when a turn was interrupted, resumed, or continued across a summary,
say so in the report and state what was verified in this turn versus what is
being carried forward from an earlier one.
