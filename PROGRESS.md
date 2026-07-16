# Progress Handover

> Living status doc only. For product intent/rationale see `CONTEXT.md`; for tech stack, data model, and codebase structure see `ARCHITECTURE.md`. **Completed stages 0–6 live in `docs/archive/STAGES_0-6.md`** — this file keeps only the active stage in full. Update this file after each meaningful milestone; append rather than rewrite.

## Status: Stages 0–7 complete (see archive / below). **Stage 8 re-scoped to Social Refinement (Groups & Friends)** at Quinn's request — portfolio packaging pushed to Stage 9. Stage 8 in progress: UI reorg (tabs+roster) done; Steps 1 (presence) & 2 (group roles/management backend) code-complete, both blocked on a `firestore.rules` deploy. Turnkey plan in `STAGE8_PLAN.md`.

Everything was checked live on the `Medium_Phone` emulator (screenshots, `dumpsys`, logcat, or direct REST calls against deployed rules), not just compiled.

## Completed stages 0–6 — summary (full detail in `docs/archive/STAGES_0-6.md`)
- **Stage 0 — Environment ✅** Kotlin + Compose scaffold, git, hello-world verified.
- **Stage 1 — Solo Lock-In Core ✅** Foreground-app + screen detection, allowlist, session start/stop (foreground `Service`), break detection + alarm.
- **Stage 2 — Accounts & Cloud Sync ✅** Firebase Auth + Firestore; profile doc, allowlist two-way sync, session history.
- **Stage 3 — Friends & Visible Allowlists ✅** Friend requests, symmetric friendships, friend-visible allowlists. (`LazyColumn` key-collision bug found + fixed — see `ARCHITECTURE.md`.)
- **Stage 4 — Group Lock-Ins ✅** Group model, real-time compliance sync, mocked break alerts, alarm cap, mute-approval flow, sticky group alarm.
- **Onboarding & Permission Priming ✅** 5-step Usage-Access/notification priming flow.
- **Stage 5 — Social Feed & Gamification ✅** Friend-visible `activity` feed (fan-out on read), kudos, on-the-fly streaks + `streakMinMinutes` goal, 7 derived achievements, polish pass ("ALARM SOUNDING" header, notification nudge, warm-palette redesign — later superseded).
- **Group Lobbies Rework ✅** Discord-style servers + ephemeral lobbies (Concurrent/Shared), group chat; live group-session UI moved off Home into the group room.
- **Stage 6 — Cute Redesign & Mascot Economy ✅** Bubblegum palette + Fredoka/Nunito, 4-theme picker, Canvas mascot "blob buddy" (4 moods), Sparkles currency (1/min), trophy case + Shop (transaction-guarded purchases).

## Project infrastructure (2026-07-16, no app-code change)
Context-efficiency pass on the project itself (not a feature): completed stages 0–6 moved to `docs/archive/STAGES_0-6.md`; env/adb/REST gotchas moved to `docs/archive/GOTCHAS.md`; PROGRESS/NEXT_SESSION/CONTEXT slimmed; CLAUDE.md now mandates surgical append-edits + a "read NEXT_SESSION.md on a fresh session" resume rule. **Verification harness now lives in the repo at `tools/`** (`fb.py` Firebase REST two-party driver — smoke-tested; `adb-helpers.sh` scoped emulator checks; gitignored `creds.json`; see `tools/README.md`) instead of being rebuilt each session. Gradle build cache + parallel enabled. Memory seeded (empty before). `/fewer-permission-prompts` still pending — must be run by Quinn (blocked for the agent).

## Stage 7 — Anti-Cheat Hardening 🚧 (in progress — turnkey plan in `STAGE7_PLAN.md`)
The adversarial pass on Stage 1's detection core. Four independently-demoable steps (decisions locked with Quinn 2026-07-15/16, do not re-litigate). Spine: flip detection from **fail-open** to **fail-closed**, made safe by a widened lookback + grace debounce.

**Step 1 — Fail-closed detection ✅ (verified on emulator, committed `7d710b3`).** An unknown foreground (`null`) while the screen is ON is no longer compliant. Three edits: `ComplianceMonitor.evaluateCompliance` dropped its `foregroundApp == null` compliant clause (screen-off stays compliant via `!isScreenOn`); `UsageAccess.currentForegroundApp` gained a defaulted `sessionStartMillis` param and widens its lookback to `min(sessionStartMillis, now − 1h)` — extends earlier for >1h sessions, never narrows below the 1h floor; `LockInService`'s loop distinguishes the *cause* of a screen-on null — a deliberate permission revocation (`!hasUsageAccessPermission`) escalates to BREAK on the **first tick**, any other unknown is held compliant through a grace debounce (`UNKNOWN_GRACE_TICKS = 4`) so a one-tick blip can't false-alarm. No new state/UI/rule; `MainActivity` untouched. See `ARCHITECTURE.md`'s Key Decisions for the full rationale (why widen-not-narrow, why cause-distinction lives in the loop).

**Verified live on the emulator** (solo, signed in as `mutebreaker`; temp `Log.d` of the tick/cause added during the run, then stripped before commit). Allowlisted Chrome + Clock + Settings added via the app's Allowlist UI as fixtures. Four cases, all via logcat + `dumpsys audio`:
- **Regression:** own app + allowlisted Chrome → COMPLIANT throughout, `ticks=0`, foreground detected cleanly (widened lookback working).
- **Screen off** (`mWakefulness=Asleep`): `fg=null` but `unknownOn=false` (screen-off path) → COMPLIANT — a screen-off null does *not* fail closed.
- **Revoke Usage Access mid-session** (`appops set … android:get_usage_stats ignore`): the **first tick** logged `revoked=true state=BREAK` and the alarm fired immediately (`USAGE_ALARM state:started`), bypassing grace as designed; it **stayed BREAK even when Lock-In was reopened** (the old fail-open code would have shown COMPLIANT here). Re-granting reset `unknownTicks` to 0 and the solo alarm self-cleared on refocus (fail-closed doesn't get stuck); the app also correctly re-entered onboarding at the grant step on revocation.
- **Rapid allowlisted app-switch** (Chrome↔Clock ×4): every tick resolved a real foreground, `unknownOn=false`, no false alarm.
> *Fixture note:* mutebreaker's allowlist now contains `com.android.chrome`, `com.google.android.deskclock`, `com.android.settings` (added for this test), and the verification's compliant ~3½-min solo session banked one more SOLO session/activity doc + a few Sparkles — realistic, below every achievement threshold.

**Step 2 — Force-closed session voided ✅ (verified on emulator, committed `3a91f94`).** A force-stop skips `onDestroy` (where history/activity/Sparkles are written), leaving a phantom "active" session in the UI that could be silently completed later. Now the service writes a **heartbeat** (`writeHeartbeat`, `LockInSessionStore.kt`) to session prefs every 1s tick; on app entry `MainActivity.onResume` calls `loadSession(this).isStale()` — active **and** heartbeat older than `SESSION_STALE_THRESHOLD_MILLIS` (10s) — and if stale **voids** the phantom: `stopLockInSession` (a no-op `stopService` on the already-dead service, so no recording path runs) + a one-time red Home banner ("Last lock-in was interrupted … didn't count — no streak, Sparkles, or feed post"). Voiding is free because none of the record helpers live on the reconcile path. A `START_STICKY` belt-and-suspenders in `LockInService.onCreate` handles an OS auto-restart that finds an already-stale heartbeat: a `voided` flag skips receiver/monitoring setup, `onStartCommand` tears the service back down (`START_NOT_STICKY`), and `onDestroy` early-returns past the recording block (and past the receiver-unregister, which would throw since nothing was registered). Heartbeat is **seeded at start** so a just-started session isn't seen as stale before tick 1.

**Verified live on the emulator** (solo, signed in as `mutebreaker`; REST baseline `sparkles:88, sessions:15, activity:11`). Confirmed via `run-as` prefs reads + `dumpsys`/screenshots + REST:
- **Heartbeat advances** each tick while active (`session_last_heartbeat` bumped ~every 1s, `session_active=true`).
- **Force-stop → relaunch:** `adb shell am force-stop` froze the heartbeat with prefs still `active=true`; after a 12s wait + relaunch, the phantom was **gone** (Home back to Start/sleeping mascot), the **interrupted banner showed**, prefs were cleared to inactive, and **REST confirmed `sparkles:88, sessions:15, activity:11` — unchanged (no credit)**. Banner dismisses and doesn't reappear on relaunch (one-time flag).
- **Background via Home (not force-stop):** heartbeat **stayed fresh** (advanced ~15s while backgrounded — the foreground service keeps beating), so returning showed the session **still active at 0:33 with no banner** — a legit background is correctly *not* voided.
No debug logging added (verified entirely via prefs/REST/screenshots).

**Step 3 — Block Stop while a group alarm sounds ✅ (verified on emulator, committed `ec0bd09`).** The
"Pressing Stop silences a sticky alarm" escape hatch is closed for **group** sessions. In `SessionControls`
(`MainActivity.kt`), while `session.groupId != null` **and** `LockInMonitor.alarmSounding` is true, the Home
"Stop Lock-In" button is disabled and a red caption explains why ("Alarm is active — it clears when your group
approves or after the 2-minute cap"). `PressableButton` gained an `enabled` param (defaults true) forwarded to
the underlying `Button`. Bounded by `MAX_ALARM_DURATION_MILLIS` (2 min) so the user is never trapped; **solo is
unaffected** because its alarm self-clears on refocus (the block only ever engages when `groupId != null`).

**Verified live on the emulator** (two-party, `Chat Test` group `r1hs2AriiJhQYBTLVsvF`, `feedtester` driven over
REST). Because a one-emulator lobby is cleaned up as "dead" without a live member, feedtester **hosted** the lobby
over REST (a lobby doc + a feedtester `liveStatus` tagged with the lobbyId) so it persisted with a "Join" button;
mutebreaker joined in-app (`onJoin → startLockInSession`, prefs confirmed `session_active=true`,
`group_id=r1hs2AriiJhQYBTLVsvF`, `lobby_id=s7step3lobby`). Then:
- **Break → sticky alarm → Stop disabled.** Pressing Home (launcher not allowlisted) fired the break;
  `dumpsys audio` showed `USAGE_ALARM …MediaPlayer state:started`; returning to Home showed red "ALARM SOUNDING",
  the **greyed-out disabled Stop button**, and the red caption. Tapping the disabled Stop was a **no-op** (prefs
  still `session_active=true`), confirming the block holds.
- **Cap path re-enables Stop.** Left the alarm running; at the 2-min cap the alarm auto-silenced, the header
  flipped green "LOCK-IN ACTIVE", **Stop re-enabled**, and the caption vanished.
- **Mute-approval path re-enables Stop (primary).** Triggered a fresh break, tapped "Ask the group to mute my
  alarm" (room showed "Waiting on the group — 0/1 approved"), then feedtester approved over REST (matching
  `breakId`/`lobbyId`); ~40s later (well under the 2-min cap, so it was the approval, not the cap) the alarm
  silenced, Home re-enabled Stop, caption gone. Stopping the now-enabled session cleared prefs to inactive.
No debug logging added (verified via `dumpsys audio` + prefs + screenshots). *Environment note:* the emulator's
qemu DNS proxy had wedged after an airplane-mode toggle (app couldn't reach Firestore realtime though REST from
the host worked); fixed by killing + relaunching the emulator process (a guest `adb reboot` was not enough).
*Fixture note:* the ~7-min group test session (with breaks) was recorded on Stop, so mutebreaker now reads
`sparkles:95, sessions:17, activity:13` — realistic, below every achievement threshold; all group subcollections
(lobbies/liveStatus/muteRequests/muteApprovals) were cleaned back to empty over REST.

**Step 4 — Verify the 2-min cap + correct the airplane-mode record ✅ (verified on emulator, no code change kept).**
Both loose ends nailed down empirically; temp scaffolding (a 20s cap + a one-line `Stage7Cap` log) was added, used, and
fully reverted (`git diff` clean, restored build reinstalled). Verified via the two-party fixture (`Chat Test`
`r1hs2AriiJhQYBTLVsvF`, group CONCURRENT lobby started in-app).
- **4a — 2-min cap runtime-verified.** With the cap temp-lowered to 20s, a group break's sticky alarm auto-silenced at
  the cap **without** approval: `Stage7Cap: CAP HIT capped=true muteGranted=false elapsedMs=20287`, and `dumpsys audio`
  showed the `USAGE_ALARM` MediaPlayer `event:stopped` + `releasing player` at that exact instant (sounded ~19s then
  cut). Home then showed **LOCK-IN ACTIVE** with **Stop re-enabled** (no red caption) — confirming the cap path clears
  the Step-3 block. Constant restored to `2 * 60 * 1000L`.
- **4b — airplane mode is a *delay*, not an escape.** Group session active (REST liveStatus = `COMPLIANT`). Airplane
  ON → Home (break): the `USAGE_ALARM` MediaPlayer was `state:started` **while offline** — local detection + alarm are
  connectivity-independent (`UsageStatsManager` is a local query). REST (read from the host, not the offline emulator)
  still showed `COMPLIANT` — the BREAK `liveStatus` write was **queued locally, not sent**. Airplane OFF → after
  reconnect REST showed `state: BREAK` — the queued write **flushed on reconnect**. So airplane mode only *delays*
  group reporting (Firestore offline persistence is on by default; the code already relies on it); the group can't
  *see* the member go dark during the outage (that surfacing is the Stage-8 going-dark work, decision 3), and if the
  process is *also* killed, Step 2 voids the session locally.
No debug logging kept (temp `Stage7Cap` log stripped; verified via `dumpsys audio` + prefs + REST + screenshots).

**Stage 7 COMPLETE** — all four steps done and emulator-verified (step 1 `7d710b3`, step 2 `3a91f94`, step 3 `ec0bd09`,
step 4 verification-only).

## Stage 8 — Social Refinement (Groups & Friends) 🚧 (turnkey plan in `STAGE8_PLAN.md`)
Re-scoped from "Polish & Portfolio Packaging" (now Stage 9) at Quinn's request: the group + friends features feel
incomplete/under-organized. Direction locked with Quinn 2026-07-16 — groups get Discord-like **organization +
management** (tabbed Lobbies·Chat·Members + a settings sheet; **owner + admin roles**), friends get **remove +
profile view + live focus status**, and both read a new app-wide **presence** doc. 7 steps in `STAGE8_PLAN.md`.

**Step 1 — Presence foundation 🚧 (code-complete, NOT yet emulator-verified — blocked on a rules deploy).**
New `PresenceStore.kt`: a top-level `presence/{uid}` doc (`state` LOCKED_IN/BREAK/IDLE + `displayName` +
`lastSeenMillis`), modeled on the group `liveStatus` store but app-wide so solo sessions and friends-without-a-shared-group
also report. `pushPresence`/`clearPresence(→IDLE)`, and `listenPresence(uids)` that chunks by 10 (Firestore `in`-query cap)
and merges into a `Map<uid, UserPresence>`; `UserPresence.effectiveState()` treats a >30s-stale stamp as IDLE.
`LockInService` writes presence every 1s tick (solo *and* group, mapping COMPLIANT→LOCKED_IN) and clears to IDLE on
teardown. Added a `match /presence/{uid}` rule (read: any signed-in — group headers read co-members who may not be
friends; write: owner only — same posture as `userSearch`). Compiles clean, installed. **Verified the write path
fires every tick** via logcat, but every write currently returns `PERMISSION_DENIED` because **`firestore.rules` has
not been deployed** to `lockin-app-sg`. Deploy tooling set up 2026-07-16: the machine had no Firebase CLI/Node, so a
**portable Node v20.18.2 + npm `firebase-tools` v15.24.0** was installed (`C:\Users\quakj\node-v20.18.2-win-x64`, on
user PATH; the self-contained firepit binary was tried first and abandoned — welcome-greeting JSON crash). Deploy is
now agent-runnable (`firebase deploy --only firestore:rules`) **pending a one-time interactive `firebase login` by
Quinn**. Round-trip verification + downstream steps stay blocked until the rules are live. See `[[project-rules-deploy-blocked]]`.

**Step 2 — Group model + roles + management backend ✅ (code-complete, deploy-blocked like step 1; 2026-07-16).**
`LockInGroup` gains `adminUids: List<String>` (listener reads it; `canManage(uid)` = owner ∪ admins). New
`GroupStore` write paths (no UI yet): `renameGroup`, `setMuteThreshold`, `addMembers` (arrayUnion), `removeMember`
& `leaveGroup` (arrayRemove from memberUids **and** adminUids so a departing/removed admin doesn't linger),
`promoteAdmin`/`demoteAdmin` (arrayUnion/Remove on adminUids), and `deleteGroup` (batch-deletes the known
subcollections — lobbies · messages · liveStatus · muteRequests · muteApprovals — then the parent doc; stragglers
are orphaned but unreachable). `firestore.rules` `update` rule rewritten: **owner** = full control; **admin** may
edit name/threshold/memberUids but the write is rejected if it touches `ownerUid` or `adminUids` (promotion &
ownership stay owner-only, so an admin can't self-perpetuate or seize the group); **any non-owner member** may only
self-remove (leave) — the rule pins every other field to its prior value. `delete` stays owner-only. Compiles clean.
**Write paths NOT yet verified — same deploy gate as step 1** (verify a rename/admin-edit/self-leave alongside the
presence round-trip once the rules ship).

## Known, Currently-Live Limitations
Same spirit as `CONTEXT.md`'s documented loopholes — real gaps, not oversights, as of this commit:
- Airplane mode only **delays** group reporting — it doesn't defeat detection (✅ verified, Stage 7 step 4). **Local detection + the solo alarm are connectivity-independent** (`UsageStatsManager` is a local query — the alarm was confirmed sounding while offline). The *group reporting* layer (liveStatus/mute/alerts) needs Firestore, but a BREAK `liveStatus` write **queues offline and flushes on reconnect** (Firestore offline persistence, on by default) — so as long as the process survives, the break reaches the group once connectivity returns; if the process is also killed, Step 2 voids it locally. The group's ability to *see* a member go dark *during* an outage is the deferred Stage-8 going-dark work (decision 3).
- Opening Lock-In itself always counts as compliant — the alarm can be silenced just by switching back to the app without actually returning to focus. (Acceptable: Lock-In isn't a distracting app — document, don't fix.)
- Break alerts only fire while the observing device's own app process is alive (mock, not real FCM — see `ARCHITECTURE.md`).
- Declining notifications during onboarding silences break alerts *and* the foreground-service notification. The session still runs and detection still works, so this is degraded rather than broken — and Home now surfaces a dismissible nudge (with a one-tap route to settings) to reconsider. Still a real choice: dismiss it and the degraded state stands until manually re-enabled.
- **Pressing "Stop Lock-In" silences a sticky alarm**, since the service tears down with it. ✅ **Closed for group sessions by Stage 7 step 3 (`ec0bd09`):** in a group session, Stop is disabled while the alarm sounds (clears on group approval or the 2-min cap). Two residuals knowingly kept: **solo** sessions still allow Stop (their alarm self-clears on refocus, so there's nothing to trap), and **sign-out** still tears the service down regardless of the block (a Stage 8 polish candidate).

**Closed by Stage 7 step 1 (`7d710b3`):** revoking Usage Access mid-session no longer grants permanent compliance (now fail-closed → BREAK on the first tick); and `currentForegroundApp()`'s fixed 1-hour lookback is no longer a stopgap — it widens to the session start for >1h sessions.

**Closed by Stage 7 step 2 (`3a91f94`):** force-stopping the app no longer leaves a silently-completable phantom session — a stale heartbeat is reconciled away on app entry (voided, no credit, flagged as interrupted).

**Closed for group sessions by Stage 7 step 3 (`ec0bd09`):** in a group session, "Stop Lock-In" is disabled while the alarm sounds, so a breaker can't silence the sticky alarm by ending the session (bounded by the 2-min cap). Solo Stop and mid-alarm sign-out remain intentional residuals (see Known Limitations).

## What's Next
1. **Stage 7 is COMPLETE** — all four steps done and verified (step 1 `7d710b3`; step 2 `3a91f94`; step 3 `ec0bd09`; step 4 verification-only, this commit) — see the Stage 7 section above. **Stage 8 was re-scoped from Polish & Portfolio Packaging to Social Refinement (Groups & Friends)** at Quinn's request (packaging → Stage 9); plan in `STAGE8_PLAN.md`, resume point in `NEXT_SESSION.md`. **Landed 2026-07-16 (uncommitted):** the `GroupDetail` **tab reorg** (Lobbies · Chat · Members + monogram header) and the Members **roster** (`fetchGroupMemberProfiles` via public `userSearch`, live status dot + Owner badge) — pulled ahead of the still-deploy-blocked presence step to fix the "disorganized" complaint; emulator-verified all three tabs. *Two-party REST harness for group flows:* `Chat Test` group `r1hs2AriiJhQYBTLVsvF`, `feedtester` over REST — a group session is startable solo by opening a CONCURRENT lobby in-app (you're the live member, so it survives dead-lobby cleanup); no REST-hosted member needed for that path.
2. Loose ends folded into Stage 7: `currentForegroundApp()`'s lookback ✅ addressed in step 1 (widened to session start for >1h sessions); the 2-min alarm cap ✅ runtime-verified in step 4 (`capped=true muteGranted=false` at a temp-lowered cap).
3. **GitHub remote (2026-07-15):** `origin` → `QuinnQuak/lock-in-app` (public), all history pushed once. Quinn then asked to pause pushing and stay local for now — commit as usual, don't `git push` again without an explicit ask. See `ARCHITECTURE.md`'s Source Control section.
4. Older commit trail (Stages 5–6) recorded in `docs/archive/STAGES_0-6.md`.
