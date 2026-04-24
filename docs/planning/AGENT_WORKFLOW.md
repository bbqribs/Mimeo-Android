# Agent Workflow (Planning + Transition)

**Status:** Active workflow guardrails  
**Last updated:** 2026-04-24  
**Product-model anchor:** `docs/planning/PRODUCT_MODEL_POST_REDESIGN.md`

## Purpose

Keep planning, implementation, and git/PR housekeeping as separate lanes so
sessions stay predictable across Claude, Codex, GPT-5.5, and human operators.

## Source-of-truth layout

- `docs/planning/` is source-of-truth planning space, not scratch space.
- `docs/planning/PRODUCT_MODEL_POST_REDESIGN.md` is the current product-model
  anchor for post-redesign decisions and lane sequencing.
- `docs/planning/AGENT_WORKFLOW.md` defines session split and git/PR rules.
- `docs/planning/PROJECT_HANDOFF.md` captures the current handoff state and
  next lanes.

## Split-ticket workflow rule (normative)

- High-effort sessions perform design/content/code/test and stop before git
  when the ticket says to stop before git.
- Low-effort sessions perform git/PR housekeeping (stage, commit, push, PR).
- Do not switch models inside a long high-effort session merely to do minor
  git housekeeping; finish the assigned high-effort scope first.
- Use a fresh low-effort session for git/PR housekeeping after high-effort
  work is complete.

## Branch and push rule (normative)

- Low-effort housekeeping sessions must create a branch and open a PR unless
  direct-push authorization is explicitly stated by the operator.
- If the session starts on `main`, create a new branch before committing.
- Single-writer-per-PR/branch remains in force.
- No rebases or force-pushes by agents; additive commits only.

## Local ignore rule for docs (normative)

- `docs/` and `docs/planning/` must not be treated as locally ignored scratch
  space.
- If `.git/info/exclude` contains `docs/` or `docs/planning/`, report it.
- Resolve by either:
  - removing the local ignore entry, or
  - using an explicit one-time workaround only with operator acknowledgement.

## Minimal execution checklists

### High-effort session checklist

1. Confirm ticket scope and stop conditions.
2. Implement planning/design/code/test scope only.
3. Stop before git if required by ticket instructions.
4. Hand off to low-effort lane for git/PR work.

### Low-effort session checklist

1. Confirm branch is not `main`; create branch if needed.
2. Stage only ticket-scoped files.
3. Commit with repo-style message.
4. Push branch and open PR.
5. Stop before merge unless explicitly instructed to merge.
