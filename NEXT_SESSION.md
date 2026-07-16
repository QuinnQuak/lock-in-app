# Next Session Kickoff

> **Read this first on a fresh session, then resume at the RESUME POINT below.** For depth if the step
> needs it: `STAGE7_PLAN.md` (active-stage spec), `PROGRESS.md` (status log), `ARCHITECTURE.md` (how),
> `CONTEXT.md` (why), `CLAUDE.md` (working agreements). Completed stages 0–6 live in
> `docs/archive/STAGES_0-6.md`. This file is the fast-start summary — don't recap finished work here.

## Where things stand
Stages 0–6 complete and emulator-verified (full detail in `docs/archive/STAGES_0-6.md`; one-line
summaries in `PROGRESS.md`). Solo core, accounts/sync, friends, group lock-ins reworked into
Discord-style servers + live lobbies, social feed + gamification, and the Bubblegum cute-redesign +
mascot economy are all shipped. **Stage 7 (Anti-Cheat Hardening) is COMPLETE — all 4 steps done + emulator-verified.**

## What's next — RESUME POINT (Stage 8 — Polish & Portfolio Packaging)
**Stage 7 is DONE and emulator-verified** — step 1 (fail-closed detection) `7d710b3`, step 2 (void
force-closed sessions) `3a91f94`, step 3 (block Stop while a group alarm sounds) `ec0bd09`, step 4
(verify 2-min cap + correct airplane-mode record) verification-only, committed alongside these docs.
- Step 1: a screen-on unknown foreground is a BREAK; a deliberate Usage-Access revocation alarms on the
  first tick; `currentForegroundApp` lookback widened to the session start.
- Step 2: the service writes a 1s **heartbeat** to session prefs; `MainActivity.onResume` reconciles a
  phantom (force-closed) session via `isStale()` (active + heartbeat >10s old) — voids it with **no
  credit** and shows a one-time red Home "interrupted" banner.
- Step 3: in a **group** session with `LockInMonitor.alarmSounding`, the Home "Stop Lock-In" is disabled
  with a red caption (clears on group approval or the 2-min cap); `PressableButton` gained an `enabled` param.
- Step 4: the 2-min alarm cap is now **runtime-verified** (`capped=true muteGranted=false` at a
  temp-lowered cap, then restored); airplane mode only **delays** group reporting — local detection +
  alarm are connectivity-independent, and a BREAK `liveStatus` write queues offline and flushes on
  reconnect (Firestore offline persistence). No code kept from step 4; docs corrected.

**Resume at Stage 8 — Polish & Portfolio Packaging** (onboarding docs, README, demo video/screenshots).
No turnkey plan doc exists for it yet — scope it with Quinn first (multi-round clarifying questions for
anything ambiguous), then move step by step. The Stage 7 residual limitations to state honestly in the
portfolio writeup are listed in `STAGE7_PLAN.md` ("Residual limitations we knowingly keep").

## Test fixtures (full detail in `ARCHITECTURE.md`)
- Emulator signed in as `mutebreaker@lockin.test` (3 backdated 30-min sessions fake a 🔥3 streak).
- `feedtester@lockin.test` — a mutual friend of mutebreaker with a posted 25-min activity (friend-feed / kudos fixture).
- Passwords for both live in `tools/creds.json` (gitignored); `tools/fb.py` reads them automatically.
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
