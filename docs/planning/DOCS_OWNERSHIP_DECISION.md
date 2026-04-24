# Docs Ownership Decision (Pre-Relocation)

**Date:** 2026-04-24  
**Status:** Decision recorded (planning/governance only)  
**Scope:** Ownership decision only. No file moves in this ticket.

## Decision Summary

Use a **split-by-ownership model** with a single canonical source per topic:

- **Cross-repo product/planning canon lives in Mimeo**:
  `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\`
- **Android-specific execution/spec docs live in Mimeo-Android**:
  `C:\Users\brend\Documents\Coding\Mimeo-Android\docs\`
- Repos reference each other with pointers; do not duplicate source-of-truth
  planning docs across both repos.

This corresponds to option **(3) split by ownership**, with explicit canonical
placement per doc type.

## Why This Decision

- Cross-repo product planning (Android + backend + web implications) should be
  owned where backend contract and deployment sequencing are anchored.
- Android implementation and UI behavior specs should stay close to Android
  code and Android ticket execution.
- This avoids duplicated planning authorities and reduces drift risk.

## Ownership Matrix

| Doc category | Canonical repo | Notes |
|---|---|---|
| Cross-repo product model and forward planning | Mimeo (`docs/planning/`) | One canonical doc set; Android keeps pointer docs only. |
| Cross-repo contract-change planning (backend semantics, ingest contracts) | Mimeo (`docs/planning/` + contract docs) | Android may reference, not mirror. |
| Android implementation specs and behavior contracts (`ANDROID_*`, player/UI contracts) | Mimeo-Android (`docs/`) | Stays with Android execution lane. |
| Android workflow/handoff docs for agent execution in this repo | Mimeo-Android (`docs/planning/`) | Repo-local process guardrails remain local. |
| Backend runbooks/ops and deployment protocol | Mimeo | Keep backend operations with backend repo. |

## Current Docs Reviewed In This Ticket

- `docs/planning/AGENT_WORKFLOW.md`
- `docs/planning/PROJECT_HANDOFF.md`
- `docs/planning/PRODUCT_MODEL_POST_REDESIGN.md`
- `docs/planning/DOCS_INVENTORY_AND_RELOCATION_PLAN.md`

## What Stays Android-Specific (Mimeo-Android)

- `docs/planning/AGENT_WORKFLOW.md` (repo-local execution workflow)
- `docs/planning/PROJECT_HANDOFF.md` (repo-local handoff state)
- Android spec/contract docs in `docs/` (for example `ANDROID_*`)
- `ROADMAP.md` as Android execution backlog

## What Stays Mimeo/Backend-Specific (Mimeo)

- Backend runbooks/deployment procedures
- Backend API contract planning and cross-repo rollout sequencing
- Cross-repo product planning canon moving forward

## What Can Be Moved Later

- `docs/planning/PRODUCT_MODEL_POST_REDESIGN.md` to
  `Mimeo/docs/planning/` as canonical cross-repo product planning, with
  Android pointer retained.
- Potentially `docs/REDESIGN_V2_PLAN.md` and
  `docs/REDESIGN_V2_DECISION_SNAPSHOT.md` in a separate relocation ticket,
  once full reference rewrites are prepared.

## What Should Not Be Moved

- Android implementation/spec docs (`docs/ANDROID_*`, Android UI/behavior
  contracts).
- Repo-local workflow docs used to run Android tickets
  (`docs/planning/AGENT_WORKFLOW.md`, `docs/planning/PROJECT_HANDOFF.md`).

## Relocation Guardrails (Follow-up Ticket)

- No duplicate source-of-truth docs across repos.
- Move canon first, then replace old location with a short pointer/tombstone.
- Update references in one pass across both repos.
- Keep this ticket decision-only; execute moves separately.
