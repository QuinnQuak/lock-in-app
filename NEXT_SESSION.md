# Next Session Kickoff

> Read this first. Then, if you need more depth: `CONTEXT.md` (why/product intent), `ARCHITECTURE.md` (how/tech stack/codebase map), `PROGRESS.md` (detailed status log). This file is just the fast-start summary — it doesn't replace those. Also read `CLAUDE.md` for standing working agreements (address the user as Quinn; keep every Markdown doc current after each goal).

## Where things stand
Stages 0–4 complete, plus onboarding. **Stage 5 (Social Feed & Gamification) is mid-flight — steps 1–5 done and verified, and step 6's first part done and verified:** activity backbone, Feed screen, kudos, streaks + Profile, achievements, and now the "ALARM SOUNDING" header state. Steps 1–5 are committed (`0195042`); the "ALARM SOUNDING" work is committed (`0f5e25a`). Tree is clean.

## What's next
**Stage 5, step 6 — the remaining two parts of the polish pass.** ✅ Done: the **"ALARM SOUNDING"** header state — Home now shows a coral "ALARM SOUNDING" (not the misleading green "LOCK-IN ACTIVE") whenever the sticky alarm is actually sounding, via a new `LockInMonitor.alarmSounding` flag published by the service. Still to do: (1) an **in-app nudge** if the user declined notifications during onboarding (break alerts + the foreground-service notification silently don't appear otherwise); (2) a **visual pass** over the new Feed / Profile / Achievements screens. After those, Stage 5 is complete.

## Stage 5 scope (decided 2026-07-15) — see `CONTEXT.md`
Friend-wide auto-posting feed; streak = a day with a lock-in ≥ a per-user, friend-visible `streakMinMinutes` (default 30); feed items show break count; single kudos per person; achievements derived on the fly. **Achievements roster (decided 2026-07-15, built in step 5):** First Lock-In, Getting Consistent (10), Half-Century (50), Deep Work (single 2h session), Ten Hours In (10h total), Week Warrior (7-day run), Flawless Week (7 consecutive break-free days) — all monotonic (longest-run-ever, not current streak) so they never un-earn. All built at portfolio depth.

## Test fixtures (see `ARCHITECTURE.md` for full detail)
- Emulator is signed in as `mutebreaker@lockin.test` / `MuteTest2026`, with 3 backdated 30-min sessions faking a 🔥3 streak.
- `feedtester@lockin.test` / `FeedTest2026` is a mutual friend of mutebreaker with a posted 25-min activity — the friend-feed / kudos fixture.

## Loose ends worth folding in opportunistically (not blockers)
- 2-min group alarm cap: logic-reviewed only, never runtime-verified with a real wait.
- `currentForegroundApp()`'s 1-hour lookback window is a stopgap; correct fix is querying from session start.
- No in-app nudge if the user declines notifications during onboarding (targeted by step 6's polish pass).

## Working agreements (see `CONTEXT.md` / `CLAUDE.md` for full text)
- Address the user as **Quinn** in every response.
- After each goal, update **all** affected Markdown docs (`PROGRESS.md`, `NEXT_SESSION.md`, `CONTEXT.md`, `ARCHITECTURE.md`) — don't leave any stale.
- Explain tradeoffs on consequential/irreversible decisions; stay terse for routine work.
- Verify every feature live on the emulator (screenshots/dumpsys/logcat/REST), not just compiled.
- Strip temporary debug logging after verification; multi-round clarifying questions for ambiguous UI/UX.
- Move step by step — don't jump ahead of what's proven.
