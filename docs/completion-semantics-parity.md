# Completion Semantics Parity

Web and Android completion toggles should match the backend's canonical completion behavior.

## Canonical backend fields

- Completion truth is derived from `last_read_percent`.
- An item is done when `last_read_percent == 100`.
- `resume_read_percent` is the reader resume position and may be lower than canonical progress.

Evidence:

- `C:\Users\brend\Documents\Coding\Mimeo\DECISIONS.md`
- `C:\Users\brend\Documents\Coding\Mimeo\docs\PROGRESS_MODEL.md`
- `C:\Users\brend\Documents\Coding\Mimeo\backend\app\api\items.py`

## Web behavior

- Reader `Mark done`: `POST /items/{item_id}/done?auto_archive=0`
  - no JSON body
  - backend helper: `mark_item_done(article)`
  - effect: `last_read_percent = 100`; preserve existing `resume_read_percent` if present
- Reader `Mark not done`: `POST /items/{item_id}/reset`
  - no JSON body
  - backend helper: `reset_item_done_state(article, fallback_percent=97)`
  - effect: `last_read_percent = 97`; `resume_read_percent = min(existing_resume, 97)` or `97` if no resume exists

The inbox buttons route through `/inbox/{item_id}/done` and `/inbox/{item_id}/reset`, which delegate to the same item helpers.

## Android behavior

- Completion toggle uses the same backend routes:
  - `POST /items/{item_id}/done?auto_archive=0`
  - `POST /items/{item_id}/reset`
- Android no longer uses `POST /items/{item_id}/progress` for the completion toggle.
- Regular progress updates still use `POST /items/{item_id}/progress`.

## Invariants

- `done => last_read_percent = 100`
- `mark not done => last_read_percent = 97`
- Completion toggle must preserve or clamp resume semantics rather than inventing a separate Android-only meaning
