# CLAUDE.md — Lock-In project working agreements

Standing instructions for this project. Read alongside `CONTEXT.md` (why), `ARCHITECTURE.md` (how), `PROGRESS.md` (status), and `NEXT_SESSION.md` (fast start).

## On a fresh session (start here)
- **When Quinn opens a new/reset session — especially with just "continue" or "next step" — read `NEXT_SESSION.md` first and resume at its `## What's next — RESUME POINT`.** That doc is the fast-start anchor; it names the current step and links the deeper docs. Only read `PROGRESS.md`/`STAGE7_PLAN.md`/etc. if the resume point calls for that depth. Don't re-read the whole doc set on every reset.

## Always
- **Address the user as Quinn in every response.**
- **After each goal/step, update every affected Markdown doc** — but only the ones that goal actually touched, and don't leave a touched doc stale. Not every step touches all four.
- **Update by surgical append/edit, not by rewriting the whole file.** To keep context lean, prefer a targeted `Edit` (append a status line, flip a checkbox, add one bullet) over reading a doc end-to-end and rewriting it. Only read the specific section you're changing. The big docs (`PROGRESS.md`, `ARCHITECTURE.md`) are append-heavy logs — treat them that way.
- **Keep the live docs lean; archive completed work.** Finished stages live in `docs/archive/` (e.g. `docs/archive/STAGES_0-6.md`), not in `PROGRESS.md`. When a stage completes, move its narrative to the archive and leave a one-line summary + pointer behind. `PROGRESS.md` should carry only the *active* stage in full.
- **Whenever you update any Markdown doc, end that response with a big, obvious confirmation banner** so it can't be missed — e.g.

  > ## ✅ 📝 MARKDOWN DOCS UPDATED
  > Files touched: `<list the docs you changed>`

  Always list exactly which docs changed. Show this every single time docs are updated, no exceptions.

## Also (carried from CONTEXT.md)
- Explain tradeoffs on consequential/irreversible decisions; stay terse for routine work.
- Verify every feature live on the emulator (screenshots / dumpsys / logcat / REST), not just that it compiles.
- Use temporary debug logging to verify Android behavior, then strip it before finishing.
- Multi-round clarifying questions for ambiguous UI/UX asks.
- Move step by step — don't build ahead of what's been proven.
