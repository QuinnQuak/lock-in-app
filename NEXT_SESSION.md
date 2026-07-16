# Next Session Kickoff

> **Read this first on a fresh session, then resume at the RESUME POINT below.** For depth if the step
> needs it: `STAGE7_PLAN.md` (active-stage spec), `PROGRESS.md` (status log), `ARCHITECTURE.md` (how),
> `CONTEXT.md` (why), `CLAUDE.md` (working agreements). Completed stages 0–6 live in
> `docs/archive/STAGES_0-6.md`. This file is the fast-start summary — don't recap finished work here.

## Where things stand
Stages 0–6 complete and emulator-verified (full detail in `docs/archive/STAGES_0-6.md`; one-line
summaries in `PROGRESS.md`). Solo core, accounts/sync, friends, group lock-ins reworked into
Discord-style servers + live lobbies, social feed + gamification, and the Bubblegum cute-redesign +
mascot economy are all shipped. **Stage 7 (Anti-Cheat Hardening) is in progress — steps 1–3 of 4 done.**

## What's next — RESUME POINT (Stage 7 step 4 — verify 2-min cap + correct the airplane-mode record)
**Stage 7 steps 1 + 2 + 3 are DONE and emulator-verified** — step 1 (fail-closed detection) `7d710b3`,
step 2 (void force-closed sessions) `3a91f94`, step 3 (block Stop while a group alarm sounds) `ec0bd09`.
- Step 1: a screen-on unknown foreground is a BREAK; a deliberate Usage-Access revocation alarms on the
  first tick; `currentForegroundApp` lookback widened to the session start.
- Step 2: the service writes a 1s **heartbeat** to session prefs; `MainActivity.onResume` reconciles a
  phantom (force-closed) session via `isStale()` (active + heartbeat >10s old) — voids it with **no
  credit** and shows a one-time red Home "interrupted" banner.
- Step 3: in a **group** session with `LockInMonitor.alarmSounding`, the Home "Stop Lock-In" is disabled
  with a red caption (clears on group approval or the 2-min cap); `PressableButton` gained an `enabled` param.

**Resume at Stage 7 step 4** — full turnkey spec in `STAGE7_PLAN.md` Step 4. Two parts, verify each on
the emulator first:
1. **Verify the 2-min cap:** temp-lower `MAX_ALARM_DURATION_MILLIS` (`LockInService.kt:36`) to ~20s,
   trigger a group break, confirm the alarm auto-silences at ~20s with `capped=true muteGranted=false`
   in logs, then **restore the constant**.
2. **Correct the airplane-mode record:** local detection + the solo alarm are connectivity-independent
   (only the *group reporting* layer needs Firestore) — check whether a queued BREAK `liveStatus` write
   flushes on reconnect, then verify/rewrite the airplane-mode limitation in the docs.

Then **Stage 8 — Polish & Portfolio Packaging** (onboarding docs, README, demo video/screenshots).

## Test fixtures (full detail in `ARCHITECTURE.md`)
- Emulator signed in as `mutebreaker@lockin.test` / `MuteTest2026` (3 backdated 30-min sessions fake a 🔥3 streak).
- `feedtester@lockin.test` / `FeedTest2026` — a mutual friend of mutebreaker with a posted 25-min activity (friend-feed / kudos fixture).
- **`Chat Test` group** (id `r1hs2AriiJhQYBTLVsvF`, members mutebreaker + feedtester, `muteApprovalCount 1`) — the two-party chat / lobby / mute-approval fixture; second party driven over REST via **`tools/fb.py`** (see `tools/README.md`).
- mutebreaker's **allowlist** has Chrome (`com.android.chrome`), Clock (`com.google.android.deskclock`), Settings (`com.android.settings`) — Stage 7 step-1 fixtures.
- **Harness:** `tools/fb.py` (Firebase REST two-party driver) + `tools/adb-helpers.sh` (scoped emulator checks) live in the repo now — use them instead of re-deriving one-liners. Env/adb gotchas: `docs/archive/GOTCHAS.md` (e.g. a one-emulator lobby needs a REST-hosted live member; a wedged qemu DNS proxy after airplane-mode needs an emulator-process restart).

## Working agreements (full text in `CLAUDE.md` / `CONTEXT.md`)
- Address the user as **Quinn** in every response.
- After each goal, update the affected Markdown docs by **surgical append/edit** (only what that step touched); archive completed stages to `docs/archive/`. End any doc-update response with the confirmation banner.
- Explain tradeoffs on consequential/irreversible decisions; stay terse for routine work.
- Verify every feature live on the emulator (screenshots/dumpsys/logcat/REST), not just compiled; strip temporary debug logging after.
- Multi-round clarifying questions for ambiguous UI/UX; move step by step — don't jump ahead of what's proven.
- Stage 7 decisions were locked with Quinn 2026-07-15/16 — do **not** re-litigate. Don't `git push` without an explicit ask (remote stays paused).
