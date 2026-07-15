# Next Session Kickoff

> Read this first. Then, if you need more depth: `CONTEXT.md` (why/product intent), `ARCHITECTURE.md` (how/tech stack/codebase map), `PROGRESS.md` (detailed status log). This file is just the fast-start summary — it doesn't replace those. Also read `CLAUDE.md` for standing working agreements (address the user as Quinn; keep every Markdown doc current after each goal).

## Where things stand
Stages 0–4 complete, plus onboarding. **Stage 5 (Social Feed & Gamification) is DONE and verified** — all six steps: activity backbone, Feed screen, kudos, streaks + Profile, achievements, and the full step-6 polish pass (the "ALARM SOUNDING" header state, the **visual redesign** — warm/energetic palette + bottom-bar navigation — and the **in-app notification nudge**). Steps 1–5 committed (`0195042`); "ALARM SOUNDING" committed (`0f5e25a`); the visual redesign + back-nav fix committed (`3fcd7b5`). **The notification nudge is not yet committed** (`MainActivity.kt`, `OnboardingStore.kt` + docs).

## What's next
**Stage 6 — Anti-Cheat Hardening.** The adversarial pass on Stage 1's detection core: the phantom "active" session left by a force-close, airplane mode defeating detection, revoking Usage Access mid-session, and the "Stop Lock-In silences a sticky alarm" free escape hatch. Fold in the two Stage-4 loose ends here too (see below). First, though — the notification nudge still needs committing.

Step 6 recap (all done): the **"ALARM SOUNDING"** header state (Home shows red "ALARM SOUNDING" via `LockInMonitor.alarmSounding` instead of a misleading green "LOCK-IN ACTIVE"); the **visual redesign** (amber/green/warm-cream palette, bottom `NavigationBar`, Allowlist + Sign Out under Profile, `BackHandler`s); and the **notification nudge** (dismissible Home banner when notifications are off on API 33+, deep-links to app notification settings, `onResume` re-check via `MainActivity.notificationsGranted`).

## Stage 5 scope (decided 2026-07-15) — see `CONTEXT.md`
Friend-wide auto-posting feed; streak = a day with a lock-in ≥ a per-user, friend-visible `streakMinMinutes` (default 30); feed items show break count; single kudos per person; achievements derived on the fly. **Achievements roster (decided 2026-07-15, built in step 5):** First Lock-In, Getting Consistent (10), Half-Century (50), Deep Work (single 2h session), Ten Hours In (10h total), Week Warrior (7-day run), Flawless Week (7 consecutive break-free days) — all monotonic (longest-run-ever, not current streak) so they never un-earn. All built at portfolio depth.

## Test fixtures (see `ARCHITECTURE.md` for full detail)
- Emulator is signed in as `mutebreaker@lockin.test` / `MuteTest2026`, with 3 backdated 30-min sessions faking a 🔥3 streak.
- `feedtester@lockin.test` / `FeedTest2026` is a mutual friend of mutebreaker with a posted 25-min activity — the friend-feed / kudos fixture.

## Loose ends worth folding into Stage 6 (not blockers)
- 2-min group alarm cap: logic-reviewed only, never runtime-verified with a real wait.
- `currentForegroundApp()`'s 1-hour lookback window is a stopgap; correct fix is querying from session start.

## Working agreements (see `CONTEXT.md` / `CLAUDE.md` for full text)
- Address the user as **Quinn** in every response.
- After each goal, update **all** affected Markdown docs (`PROGRESS.md`, `NEXT_SESSION.md`, `CONTEXT.md`, `ARCHITECTURE.md`) — don't leave any stale.
- Explain tradeoffs on consequential/irreversible decisions; stay terse for routine work.
- Verify every feature live on the emulator (screenshots/dumpsys/logcat/REST), not just compiled.
- Strip temporary debug logging after verification; multi-round clarifying questions for ambiguous UI/UX.
- Move step by step — don't jump ahead of what's proven.
