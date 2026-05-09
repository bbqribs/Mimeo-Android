# Bluesky Browse / Discover UX — Decision Note

**Status:** Decision-only. No code, schema, endpoint, or ROADMAP changes authorized.
**Scope:** Web + Android browse/discover/sources surfaces.
**Authority predecessors:** `BLUESKY_BROWSE_SURFACES_V1_PLAN.md`, `BLUESKY_AUTHENTICATED_READ_V1_PLAN.md`, `SMART_PLAYLISTS_V1_CONTRACT_PLAN.md`.

## Core invariants (carry through every decision below)

- **Browse** = harvested Mimeo items only. Mimeo DB is the only source of truth.
- **Discover** = live Bluesky catalogue only.
- **Sources** = configured Mimeo harvest sources.
- **Smart playlists / Up Next** = explicit handoff only. No automation.
- Android must not call Bluesky directly. Backend `/bluesky/*` only.
- No Bluesky writes, DMs, notifications, likes, reposts, follows, mutes, blocks.
- Adding a source must not auto-harvest.
- Harvesting must not auto-mutate Up Next.
- Live Discover results must not visually blur with harvested Mimeo items.

---

## 1. Primary surfaces

| Surface | Role |
|---|---|
| **Web Browse** (`/bluesky/browse`) | Read-only browse over harvested Bluesky items in the Mimeo DB. Filter, pin, hand off to Up Next or smart playlist. |
| **Web Discover** (`/bluesky/discover`) | Live, ephemeral catalogue of the connected Bluesky account's lists, follows, and (later) saved feeds. Preview live; explicitly add as a (default-disabled) source. |
| **Web Sources** (`/bluesky/sources`) | Browse-shaped wrapper over configured `BlueskySource` rows. Enable/disable, rename, schedule, manual run, link out to Browse-filtered-by-source. |
| **Android Browse** (`BlueskyBrowseScreen`) | Backend-backed read of harvested items. Pin/unpin, filter, send to Up Next. No Bluesky calls, no source mutation. |
| **Android Settings → Bluesky status** | Connection state + read-only source diagnostics. Connect/disconnect lives here. No browse content, no live catalogue. |

## 2. Surface boundaries

| Surface | For | Must not | Source of truth |
|---|---|---|---|
| Web Browse | Reading harvested items, filtering, pinning, explicit handoff | Call Bluesky; mutate sources; auto-mutate Up Next | Mimeo DB |
| Web Discover | Live catalogue + live preview + explicit "add as disabled source" | Persist preview content; auto-harvest; surface harvested items inline | Live Bluesky |
| Web Sources | Manage configured sources; navigate to per-source browse | Show live Bluesky content; auto-pin; auto-create smart playlists | Mimeo DB |
| Android Browse | Read-only harvested browse + pin + handoff | Call Bluesky directly; manage sources; show live results | Mimeo DB (via backend) |
| Android Settings Bluesky | Connection + diagnostics | Browse content; live catalogue; source CRUD | Mimeo DB (via backend) |

## 3. Navigation and naming

**Recommendation: keep the three-tab IA but rename for clarity.**

Web top-level under `/bluesky/`:

```
[ Browse ]   [ Discover ]   [ Sources ]
   ^harvested   ^live          ^config
```

Order: **Browse → Discover → Sources.** Browse first because it is the daily-use, no-network surface; Discover second because it produces sources; Sources last because it is configuration.

- "Browse" stays. Subtitle in page header: *Harvested from Bluesky*.
- "Discover" stays. Subtitle: *Live from Bluesky*. A persistent "Live" pill on the tab itself.
- "Sources" stays. Subtitle: *Configured harvest sources*.

Rejected alternatives:
- *Feed / Find / Manage* — cute, but loses the Bluesky-native vocabulary the user already understands.
- Collapsing Sources into Discover — violates the "adding a source is an explicit action" invariant.
- Collapsing Browse into a global inbox filter chip — loses the Bluesky-shaped landing surface that the v1 plan exists to create.

## 4. Visual distinction

Four object classes must be visibly different at a glance.

| Class | Treatment |
|---|---|
| **Live Bluesky catalogue results** (Discover) | Tinted card background (cool/blue tone). "Live" pill top-right. Relative-time stamp `Refreshed 12s ago` + Refresh button. No "Save" or "Up Next" inline actions — only Preview and Add as source. |
| **Configured harvest sources** (Sources, pinned strip) | Neutral card. Source-type icon (timeline / list / author). Last-harvested-at, next-harvest-at, enabled/disabled chip. Action: Run, Pin, Open in Browse. |
| **Harvested Mimeo articles** (Browse) | Standard Mimeo article row grammar — identical to inbox rows. Provenance line: `From @handle · 3h ago · via <source label>`. Bluesky badge in the metadata line (not as a card border). |
| **Smart playlist handoff** | Standard smart-playlist row chrome from existing Library; entered via explicit "Save as smart playlist" or "Use as Up Next" actions on a Browse filter. Never auto-rendered as a Bluesky tab. |

Hard rules:
- Live and harvested **never share a list**. Discover and Browse are different routes; cross-linking is a navigation, not an embed.
- Tinted-background convention is only used for live data. Do not tint harvested rows for any reason.
- The page `<h1>` says either "Browse harvested" or "Discover (live)" verbatim; the address bar reflects this.

## 5. Pinning

**Decision:** keep the existing `bluesky_browse_pins` model. Pins are pinned `BlueskySource` rows, per user, ordered.

- **Web:** horizontal pinned-source strip at the top of Browse. Each card shows source avatar/icon, name, last-saved count, last-harvested-at. Tap → Browse filtered to `source_id`. Pin/unpin from Sources row overflow ("Pin to Browse"). Reorder via "Manage pinned sources" sheet. Strip hidden when empty. Soft cap 12.
- **Android:** mirror the strip at top of Browse. Pin/unpin via long-press / overflow on a source-filtered Browse view. Same `/bluesky/browse/pins` endpoints.
- **Source pins vs smart-playlist pins:** different object classes, different stores, different UI homes. Source pins live in Bluesky Browse only. Smart-playlist pins live in Library. They are not unified and should not visually echo each other beyond shared row grammar.
- **Effect on Library / Up Next:** none. Pinning a source does **not** pin a corresponding smart playlist in Library, does **not** create a smart playlist, and does **not** seed Up Next. Pinning is a Browse-only ordering primitive.

## 6. Android strategy

Android exposes **now**:

- Harvested-item browse (already shipped — keep).
- Pinned sources strip (already shipped — keep, parity with web).
- Source filters: by `source_id`, by author handle, by 24h / 7d / 30d window.
- Send-to-Up-Next and Save-to-playlist row actions (reuse existing item actions).
- Bluesky status + connect/disconnect in Settings (already shipped).

Android **defers**:

- **Discover (live catalogue) — deferred.** Web-only until web Discover is shipped, stable, and instrumented. Rationale: Android architecture forbids direct Bluesky calls, and proxying live catalogue + preview through the backend doubles the contract surface for marginal value while web is still settling.
- **Source management — deferred.** No create/edit/delete/schedule UI on Android. Settings continues to show read-only diagnostics. Rationale: source CRUD is low-frequency, configuration-shaped, and best done on web where Discover lives next door.

## 7. Standard copy

Use these labels and help strings verbatim across web and Android. Sentence case throughout.

| Slot | Copy |
|---|---|
| Browse tab subtitle | **Browse harvested** — Items already saved from your Bluesky sources. |
| Discover tab subtitle | **Discover live from Bluesky** — Live catalogue. Nothing here is saved yet. |
| Add-as-source CTA | **Add as source** (button). Help: *Adds this to your sources, disabled by default. No items are harvested until you run it.* |
| Preview affordance | **Preview** (button). Help: *Preview does not save anything to Mimeo.* |
| Harvest run confirm | **Run harvest now**. Help: *Harvest does not add to Up Next.* |
| Up Next handoff | **Use as Up Next** (row/filter action). Help: *Replaces your current Up Next with these items. This is the only way these items reach Up Next.* |
| Empty Browse | *No Bluesky-harvested items yet. Add a source on Discover, then run a harvest.* |
| Empty Discover (no connection) | *Connect a Bluesky account to discover your lists, feeds, and follows. Public author-feed sources work without a connection.* |
| Discover stale-cache banner | *Couldn't refresh — showing last cached version.* |

## 8. Next implementation tickets

Order matters; each ticket is small and contract-safe.

### T1. Browse / Discover / Sources copy + visual-distinction pass (web)
- **Repo:** Mimeo backend (templates) + frontend.
- **Goal:** Land the §4 visual rules and §7 standard copy on the three already-shipped web surfaces.
- **Scope:** Tab subtitles; "Live" pill on Discover; tinted live-card background; provenance line on Browse rows; empty/failure copy; page `<h1>` strings.
- **Gates:** No new endpoints. No data-model changes. Existing tests green. Per-user isolation unchanged.
- **Stop conditions:** If any copy change would require a new endpoint or schema field, stop and re-scope.

### T2. Pinned-source strip parity check (web ↔ Android)
- **Repo:** Mimeo backend + Mimeo-Android.
- **Goal:** Confirm web and Android pinned-source strips render identically against the same `/bluesky/browse/pins` payload, with the §5 card grammar.
- **Scope:** Web pinned strip at top of Browse; Android pinned strip parity; "Manage pinned sources" sheet on web; long-press pin/unpin on Android.
- **Gates:** No endpoint changes. Reuses existing `/bluesky/browse/pins` set. No effect on Library or Up Next.
- **Stop conditions:** Stop if parity requires a new endpoint shape; record the gap and re-scope.

### T3. Discover "add as disabled source" hardening (web)
- **Repo:** Mimeo backend.
- **Goal:** Guarantee that Discover-originated source creation lands disabled, with no harvest run side effect, and idempotent on re-add.
- **Scope:** `POST /bluesky/catalogue/sources` enforces `enabled=false` default; idempotent on `(user_id, source_type, actor)`; Discover row flips to "Already a source" with link to Sources entry.
- **Gates:** No write/chat/notification lexicons touched. No auto-harvest. No Up Next mutation. Test: adding from Discover never enqueues a run.
- **Stop conditions:** Stop if idempotency requires a schema change beyond an additive index.

## 9. Deferred work (explicit non-goals for this pass)

- Saved feeds / feed generators (`savedFeedsPrefV2`, `getFeed` integration). Defer until risk R1 is validated against current Bluesky docs.
- Android Discover (live catalogue surface).
- Android source management (create/edit/delete/schedule).
- Auto-created smart playlists (per-source or global "Bluesky · last 24h").
- Up Next automation of any kind. Up Next mutation stays an explicit user action.
- Global Bluesky search.
- Media / image / gallery work beyond what the harvester already captures.
- Mirroring Bluesky-side saved-feeds order or pin order.
- Live home-timeline preview (use "Open in Bluesky" deep link until usage data justifies the surface).
- Starter packs.

## 10. Context-pack vs repo mismatches to flag (not guess)

These are stated by the context pack but cannot be confirmed from this design pass alone. Record rather than assume:

- The pack states `/bluesky/discover` is shipped (CURRENT_STATE §9) and that Discover is the live catalogue surface. Confirm the shipped page already renders the "Live" pill and live/harvested separation per §4 before T1 lands; if not, T1 closes the gap.
- The pack states Android pin add/remove is shipped (`ANDROID_STATE` §6). Confirm Android renders the pinned-source strip — not just the underlying pin endpoints — before T2 closes as parity-only; if Android lacks the strip UI, T2 expands to add it.
- The pack lists `POST /bluesky/catalogue/sources` (`API_SURFACE`) but does not state the default-disabled invariant. T3 is written to enforce it; if the endpoint already enforces it, T3 narrows to test coverage.

If any of the above conflict with the live repo at ticket-open time, update this doc rather than the ticket scope, and re-decide.
