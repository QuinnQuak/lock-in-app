# CLAUDE.md — Lock-In project working agreements

Standing instructions for this project. Read alongside `CONTEXT.md` (why), `ARCHITECTURE.md` (how), `PROGRESS.md` (status), and `NEXT_SESSION.md` (fast start).

## Always
- **Address the user as Quinn in every response.**
- **After each goal/step, update every affected Markdown doc** — `PROGRESS.md`, `NEXT_SESSION.md`, `CONTEXT.md`, and `ARCHITECTURE.md`. Don't leave any of them stale; don't update only some.
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
