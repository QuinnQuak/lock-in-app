# Next Session Kickoff

> Read this first. Then, if you need more depth: `CONTEXT.md` (why/product intent), `ARCHITECTURE.md` (how/tech stack/codebase map), `PROGRESS.md` (detailed status log). This file is just the fast-start summary — it doesn't replace those. Also read `CLAUDE.md` for standing working agreements (address the user as Quinn; keep every Markdown doc current after each goal).

## Where things stand
Stages 0–5 complete + onboarding. **The group lock-in feature was reworked (2026-07-15) from a
passive roster into Discord-style servers + live lobbies** — a group is now a persistent server with
**group chat**, and a **lobby** is an ephemeral live room inside it (multiple at once; each
**Concurrent** = own clocks, or **Shared** = one synced auto-ending round). All live group-session UI
moved into the group room; Home is solo-only + an "Open group room" deep-link. New files
`ChatStore.kt`, `LobbyStore.kt`, `GroupDetailScreen.kt`; new `groups/{id}/messages` + `lobbies` +
a `lobbyId` field on liveStatus/mute docs. Built + emulator-verified in four steps (chat, concurrent
lobbies + relocated mute UI, shared-mode auto-stop, dead-lobby cleanup) — see `PROGRESS.md`.
Committed as `9b8279f` (hash recorded in a follow-up docs commit `35e30c7`). A GitHub remote was
also added (`origin` → `QuinnQuak/lock-in-app`, public) and pushed once — but **don't push again
without an explicit ask**; Quinn asked to pause pushing and stay local for now. Keep committing
locally as usual.

## What's next — RESUME POINT (Stage 7 step 3 — block Stop while a group alarm sounds)
**Stage 7 steps 1 + 2 are DONE and emulator-verified** — step 1 (fail-closed detection) `7d710b3`,
step 2 (void force-closed sessions) `3a91f94`. Step 1: a screen-on unknown foreground is a BREAK, a
deliberate Usage-Access revocation alarms on the first tick, lookback widened to session start. Step 2:
the service writes a 1s **heartbeat** to session prefs; `MainActivity.onResume` reconciles a phantom
(force-closed) session via `isStale()` (active + heartbeat >10s old) — voids it with **no credit** and
shows a one-time red Home "interrupted" banner; a `START_STICKY` `voided` path in `LockInService`
handles an OS auto-restart. Verified: force-stop→relaunch voids + banner + REST-unchanged counts, while
a Home-button background stays active (heartbeat keeps beating). Full build+verify log in `PROGRESS.md`.
**Resume at Stage 7 step 3** — see the `STAGE7_PLAN.md` Step 3 spec: in `SessionControls`
(`MainActivity.kt`), while in a group session (`session.groupId != null`) **and**
`LockInMonitor.alarmSounding`, disable Stop and explain (clears on group approval or the 2-min cap).
Needs the two-party REST harness. Step 4 (verify 2-min cap + correct the airplane-mode record) follows.
*Test setup note:* mutebreaker's allowlist has Chrome, Clock, and Settings (step-1 fixtures).


**Stage 6 — Cute Redesign & Mascot Economy is COMPLETE (all 6 steps).** Same-day design redecision
superseding the amber/green palette that had *just* shipped in Stage 5 step 6: a "Bubblegum"
pink/orange palette + light/dark + a curated theme picker (Bubblegum/Peach/Berry/Sunset),
Fredoka/Nunito typography, a reactive mascot ("blob buddy"), a trophy-case unlock per achievement
tier, and a Shop bought with the passively-earned **Sparkles** currency (1/min locked in). Full spec
in `CONTEXT.md`'s Design Direction; full build log in `PROGRESS.md`.

- Steps 1–4 (palette + typography, theme picker, mascot moods, Sparkles accrual/display) committed
  `fda078c`, `0b41eaa`, `112deee`, `8d63e56`.
- **Steps 5+6 (Trophy Case + Shop) built and verified together** — new `MascotWardrobe.kt` +
  `MascotWardrobeStore.kt`; accessories are **emoji at a HEAD/FACE/NECK slot** overlaid on the blob
  (not Canvas-drawn); the equipped accessory is hoisted via a `LocalEquippedAccessory`
  `CompositionLocal` so **every** mascot wears it; `equippedAccessory` (string) + `ownedAccessories`
  (array) ride the `users/{uid}` doc alongside `sparkles` (**no new rule**); trophy ownership is
  derived from achievements, only Shop purchases persist; purchases use a **Firestore transaction**
  (verify balance → decrement + `arrayUnion`). Verified: equip propagates to Home+Profile mascots and
  across a cold relaunch; a Flower buy dropped ✨100→85, REST-confirmed `owned:[FLOWER]`,
  `equipped:FLOWER`. Commit hash: see `PROGRESS.md` "What's Next".

**Stage 7 — Anti-Cheat Hardening is IN PROGRESS (steps 1 + 2 of 4 done).** The adversarial pass on
Stage 1's detection core, fully scoped in `STAGE7_PLAN.md` (decisions made with Quinn 2026-07-15/16, do
NOT re-litigate). Its spine: detection *was* **fail-open** (`evaluateCompliance` treated an unknown
foreground as COMPLIANT — revoke Usage Access mid-session → permanent compliance), now flipped
**fail-closed** with a widened lookback + grace debounce so the flip is safe.
- **Step 1 — fail-closed detection ✅ DONE (committed `7d710b3`, emulator-verified).** Three edits
  (drop the null clause in `evaluateCompliance`; widen `currentForegroundApp`'s lookback to the session
  start; cause-distinguishing grace gate in the `LockInService` loop — revocation alarms on tick 1,
  transient unknowns wait `UNKNOWN_GRACE_TICKS=4`). Verified: no regression on allowlisted/own apps,
  screen-off still compliant, **revoking Usage Access mid-session fires the alarm on the first tick and
  stays broken even when the app is reopened**, rapid app-switching raises no false alarm. Full log in
  `PROGRESS.md`; rationale in `ARCHITECTURE.md`.
- **Step 2 — void force-closed sessions ✅ DONE (committed `3a91f94`, emulator-verified).** The service
  writes a 1s **heartbeat** to session prefs; `MainActivity.onResume` reconciles via `isStale()` (active
  + heartbeat >10s old) → voids the phantom with **no credit** + a one-time red Home "interrupted"
  banner. Voiding is free (record helpers only run in `onDestroy`, which a force-stop skips); a
  `START_STICKY` `voided` path in `LockInService` handles an OS auto-restart with a dark window.
  Verified: force-stop→relaunch voids the phantom + shows the banner + **REST-confirmed unchanged
  sparkles/session/activity counts**, while a Home-button background keeps the heartbeat fresh so the
  session stays active (not voided). Full log in `PROGRESS.md`; rationale in `ARCHITECTURE.md`.
- **Steps 3–4 remaining** (execute in order, verify each on the emulator first): **3** block Stop while
  a group alarm sounds (`SessionControls`, bounded by the 2-min cap); **4** verify the 2-min cap
  (temp-lower it) + correct the airplane-mode record. Turnkey specs + live-verify steps in `STAGE7_PLAN.md`.

**Stage 8 — Polish & Portfolio Packaging** (was Stage 7): onboarding flow docs, README, demo video/screenshots.

Stage 5 step 6 recap (all done, now superseded visually by Stage 6 above but still the live build): the **"ALARM SOUNDING"** header state (Home shows red "ALARM SOUNDING" via `LockInMonitor.alarmSounding` instead of a misleading green "LOCK-IN ACTIVE"); the **visual redesign** (amber/green/warm-cream palette, bottom `NavigationBar`, Allowlist + Sign Out under Profile, `BackHandler`s); and the **notification nudge** (dismissible Home banner when notifications are off on API 33+, deep-links to app notification settings, `onResume` re-check via `MainActivity.notificationsGranted`).

## Stage 5 scope (decided 2026-07-15) — see `CONTEXT.md`
Friend-wide auto-posting feed; streak = a day with a lock-in ≥ a per-user, friend-visible `streakMinMinutes` (default 30); feed items show break count; single kudos per person; achievements derived on the fly. **Achievements roster (decided 2026-07-15, built in step 5):** First Lock-In, Getting Consistent (10), Half-Century (50), Deep Work (single 2h session), Ten Hours In (10h total), Week Warrior (7-day run), Flawless Week (7 consecutive break-free days) — all monotonic (longest-run-ever, not current streak) so they never un-earn. All built at portfolio depth.

## Stage 6 scope (decided 2026-07-15) — see `CONTEXT.md`'s Design Direction
- **Bubblegum palette:** pink primary (`#FF4F8B`/`#FF6FA3` light/dark), orange secondary, cherry-red alert (distinct hue from primary so it doesn't read as a normal button), blush/deep-plum background — full 4-role light+dark table in `CONTEXT.md`.
- **Theme picker:** Bubblegum (default), Peach, Berry, Sunset — each a light+dark pair, user-selectable (likely Profile).
- **Typography:** Fredoka (500–700, headers/hero numbers/buttons/nav) + Nunito (400–700, body/chat/feed rows), replacing Quicksand; corner radii bumped app-wide.
- **Mascot "blob buddy":** reactive companion, appears everywhere (Home hero, Profile, session status, loading states) — idle/breathing while compliant, happy bounce+sparkle on completion, droop+tears on break, sleeping "zzz" when idle; recolors to the active theme.
- **Mascot accessories:** trophy case (1 signature accessory per achievement tier, auto-granted, never purchasable — reuses the existing 7-tier achievement system) + a broader Shop bought with **Sparkles** (new currency, 1/minute locked in, solo or group).
- **Build order (decided 2026-07-15) — ALL DONE:** 1) palette + typography swap ✅ done, 2) theme picker (Peach/Berry/Sunset) ✅ done, 3) mascot "blob buddy" static states ✅ done (all 4 moods verified, committed `112deee`), 4) Sparkles currency (accrual + display only) ✅ done (`8d63e56`), 5) trophy case ✅ done (auto-granted, ownership derived from `AchievementsStore`), 6) Shop ✅ done (spend Sparkles via a transaction, reuses the trophy case's single equip slot + inventory plumbing). Stage 6 complete. Full rationale in `CONTEXT.md`.

## Test fixtures (see `ARCHITECTURE.md` for full detail)
- Emulator is signed in as `mutebreaker@lockin.test` / `MuteTest2026`, with 3 backdated 30-min sessions faking a 🔥3 streak.
- `feedtester@lockin.test` / `FeedTest2026` is a mutual friend of mutebreaker with a posted 25-min activity — the friend-feed / kudos fixture.
- **`Chat Test` group** (id `r1hs2AriiJhQYBTLVsvF`, members mutebreaker + feedtester, `muteApprovalCount 1`) — the fixture for chat / lobby / mute-approval tests; second party driven over REST. Reusable Python REST helpers live in the job's tmp dir (`fb.py` reads the API key without printing it).
- mutebreaker's **allowlist** now contains Chrome (`com.android.chrome`), Clock (`com.google.android.deskclock`), and Settings (`com.android.settings`) — added as Stage 7 step-1 fixtures.

## Loose ends worth folding into Stage 7 (not blockers)
- 2-min group alarm cap: logic-reviewed only, never runtime-verified with a real wait. → Stage 7 **step 4**.
- ~~`currentForegroundApp()`'s 1-hour lookback window is a stopgap~~ → ✅ addressed in Stage 7 step 1 (`7d710b3`): widened to `min(sessionStartMillis, now − 1h)`.

## Working agreements (see `CONTEXT.md` / `CLAUDE.md` for full text)
- Address the user as **Quinn** in every response.
- After each goal, update **all** affected Markdown docs (`PROGRESS.md`, `NEXT_SESSION.md`, `CONTEXT.md`, `ARCHITECTURE.md`) — don't leave any stale.
- Explain tradeoffs on consequential/irreversible decisions; stay terse for routine work.
- Verify every feature live on the emulator (screenshots/dumpsys/logcat/REST), not just compiled.
- Strip temporary debug logging after verification; multi-round clarifying questions for ambiguous UI/UX.
- Move step by step — don't jump ahead of what's proven.
