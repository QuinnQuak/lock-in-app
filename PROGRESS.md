# Progress Handover

> Living status doc only. For product intent/rationale see `CONTEXT.md`; for tech stack, data model, and codebase structure see `ARCHITECTURE.md`. **Completed stages 0–6 live in `docs/archive/STAGES_0-6.md`** — this file keeps only the active stage in full. Update this file after each meaningful milestone; append rather than rewrite.

## Status: Stages 0–8 complete (see archive / below). **Stage 8 (Social Refinement — Groups & Friends) COMPLETE + deployed & emulator-verified 2026-07-16** (rules shipped; presence, admin edit, and Members-dot-to-presence all verified live). **Stage 9 (Polish & Portfolio Packaging) in progress** — polish + stability, recruiter audience, no plan doc (tracked in the Stage 9 section below). Stability sweep done (no crashes); end-of-session summary (#1), roster self-name (#2), and Home idle-layout / CURRENTLY-OPEN (#4/#5) fixes landed + emulator-verified.

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

## Stage 7 — Anti-Cheat Hardening ✅ (COMPLETE — turnkey plan archived at `docs/archive/STAGE7_PLAN.md`)
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

## Stage 8 — Social Refinement (Groups & Friends) ✅ (COMPLETE — turnkey plan archived at `docs/archive/STAGE8_PLAN.md`)
Re-scoped from "Polish & Portfolio Packaging" (now Stage 9) at Quinn's request: the group + friends features feel
incomplete/under-organized. Direction locked with Quinn 2026-07-16 — groups get Discord-like **organization +
management** (tabbed Lobbies·Chat·Members + a settings sheet; **owner + admin roles**), friends get **remove +
profile view + live focus status**, and both read a new app-wide **presence** doc. 7 steps in `docs/archive/STAGE8_PLAN.md`.

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

**Step 4 — Members tab controls ✅ (owner-path emulator-verified; admin-path deploy-gated; 2026-07-16).**
Built on the step-3 roster: an **Add member** row (owner/admin only) opening `AddMemberSheet` — a
`ModalBottomSheet` picker of friends *not* already in the group (empty-state "All your friends are already
in this group" when none); a tap-a-member `MemberActionSheet` with role-aware actions (**owner**: Promote↔Demote
+ Remove; **admin**: Remove plain members only; owner + self rows aren't tappable — self-leave is step 5);
and **Owner**/**Admin** `RoleBadge`s driven by `adminUids`. `MainActivity` now live-syncs the open
`selectedGroup` off `listenMyGroups` so role/roster/rename edits reflect without re-navigating, and pops back
to the group list if the group is deleted or we leave/are removed. **Verified on the emulator as owner
(mutebreaker):** promote→demote→promote on `Chat Test` each wrote (confirmed via REST `adminUids`) and the
badge re-rendered live; both action-sheet variants + the add-member empty-state render correctly. Owner-path
writes succeed under the *currently-deployed* rules (owner-any-update); **admin-initiated writes, member
self-leave, and real add/remove/delete stay deploy-gated** with steps 1–2. Left Feed Tester as an admin
fixture (`adminUids: [zfuW…]`) on `Chat Test` for the post-deploy admin-path check.

**Step 5 — Group settings sheet ✅ (owner-path emulator-verified; leave/delete deploy-gated; 2026-07-16).**
Pure UI in `GroupDetailScreen.kt`, no backend/rules work (all fns already in `GroupStore`). A gear
`IconButton` (`Icons.Rounded.Settings`) at the trailing edge of the header Row opens `GroupSettingsSheet`
(`ModalBottomSheet`), available to everyone: **rename** (owner/admin — `OutlinedTextField` + Save, gated
`enabled = draft.trim() != group.name`), a **mute-approval stepper** (owner/admin, clamped `1 … members−1`
per `approvalsFor`), and one destructive row — **Delete group** for the owner, **Leave group** for any
non-owner member — each behind an `AlertDialog` confirm (pattern from `ProfileScreen.kt`). No owner-transfer
UI (owner simply can't leave, per locked direction); on success the sheet closes and `MainActivity`'s
live-sync auto-pops the screen when I'm removed / the group vanishes. **Verified on the emulator as owner
(mutebreaker) on `Chat Test`:** rename wrote live (`Chat Test`→`Chat Test 2`, confirmed via REST, then
restored); Save enable/disable gating correct; the threshold stepper's −/+ both correctly disabled at the
1…1 clamp of a 2-member group; owner sees **Delete**, not Leave. **Deploy-gated** (same rules blocker as
steps 1–2): member/admin **Leave** and owner **Delete** writes, and the member-view render (non-owner).

**Steps 6–7 — Friends: remove + profile view + presence ✅ (emulator-verified; presence & reciprocal-delete
deploy-gated; 2026-07-16).** *Step 6 (backend):* `removeFriend(myUid, friendUid)` in `FriendsStore.kt` fires
two **independent** deletes (not a batch — a batch is atomic, so a denied reciprocal delete would roll back
my own-side delete). My own `users/{me}/friends/{friend}` doc deletes under current rules; the reciprocal
`users/{friend}/friends/{me}` needs the new `firestore.rules` clause — `friends/{friendUid}` `delete` now
allows `auth.uid == uid || auth.uid == friendUid`, i.e. either party can end a friendship (symmetric with the
reciprocal *create* on accept). *Step 7 (UI, `FriendsScreen.kt`):* each friend row is now presence dot + name +
status label, fed by `listenPresence` over the friend-uid set (re-subscribed on roster change). Tapping a row
opens `FriendProfileSheet` (`ModalBottomSheet`): mascot wearing the friend's equipped accessory (mood from
presence) + a status dot; a **Streak** stat (🔥, read from their latest `activity` doc's `streakAtPost` —
their `sessions` log is owner-only, so the poster stamps it) and **Focus hours** (summed `durationSeconds`
over their readable `activity`, so recent-capped); the **allowlist** (moved here out of the old inline
row-expander); and **Remove friend** behind an `AlertDialog` confirm. **Verified on the emulator (mutebreaker →
`Feed Tester`):** row shows a grey dot + "Idle"; the sheet renders the sleeping mascot, 🔥0 (their `streakAtPost`),
**0.4h** (their one 25-min activity = 1500 s), "No allowlisted apps", and the remove-confirm dialog (cancelled
to preserve the fixture). **Deploy-gated:** live presence dots (presence reads are denied until the rules ship,
so every friend reads Idle for now) and the reciprocal-side unfriend delete. **⏭️ Stage 8 feature work (steps
1–7) is now complete — only the rules deploy + its gated verifications remain to close the stage.**

**✅ STAGE 8 COMPLETE — deploy gate cleared + Members dot wired (2026-07-16, commit `b992f8f`).**
`firestore.rules` released to `lockin-app-sg` (`firebase deploy --only firestore:rules`; already-up-to-date
compile + release, so a prior session had shipped it). Deploy-gated paths now verified live via `tools/fb.py`:
**presence** write (owner) + cross-user read (feedtester reads mutebreaker) — the old `PERMISSION_DENIED` is
gone; **admin group edit** — `Feed Tester` (non-owner admin) renamed `Chat Test` with `ownerUid`/`adminUids`
preserved, then restored. **Members roster dot re-wired to presence:** the Members tab now reads the app-wide
`presence/{uid}` doc (listener keyed on `group.memberUids`; shared `presenceDotColor`/`presenceLabel` extracted
to `internal` in `FriendsScreen.kt` so the group + friends rosters can't drift) instead of in-group
`liveStatus`. Emulator-verified on `Chat Test` Members tab: a member with no presence doc shows grey + "Idle",
and `Feed Tester` **flipped live to "Locked in"** the moment a fresh LOCKED_IN stamp was written over REST. The
header "N locked in" still reflects in-*group* lobby participation — intentionally distinct from app-wide
presence. **Left unexercised on purpose (destructive to standing fixtures):** member self-leave, reciprocal
unfriend delete, real group delete — all share the now-deployed ruleset. **⏭️ NEXT: Stage 9 (Polish &
Portfolio Packaging)** — no plan doc yet; scope with Quinn.

## Stage 9 — Polish & Portfolio Packaging ✅ COMPLETE (2026-07-16; no plan doc — tracked here per Quinn 2026-07-16)
Scope locked with Quinn 2026-07-16: **visual/UX polish + stability**, audience **recruiters/job apps** (portfolio
screenshots deferred as an optional tail, now done). Quinn drives, agent reports back — no `STAGE9_PLAN.md`.

**Stability sweep ✅ (2026-07-16, emulator).** Drove every surface — Home, Feed, Friends, Groups list, GroupDetail
(Lobbies·Chat·Members), Profile (incl. achievements grid), friend profile sheet, chat, and a full solo lock-in
start→stop. **No crashes, no PERMISSION_DENIED surfaced to UI, no stuck spinners.** Punch list (ranked by demo
impact): 1) no end-of-session summary/payoff; 2) roster showed "Member (you)"; 3) achievements loading is a tiny
near-invisible dot; 4) Home dead space above mascot; 5) idle "CURRENTLY OPEN" is meaningless; 6) chat has no
timestamps; 7) feed didn't surface a friend post (fixture staleness, not a bug). Quinn picked **#2 + #4/#5** first.

**Fix: roster self-name (#2) ✅ (emulator-verified).** Root cause: the current user (`mutebreaker`) had **no
`userSearch` doc at all** (404) — an old fixture predating the userSearch signup flow — so `fetchGroupMemberProfiles`
fell back to the `"Member"` placeholder. Two-part fix: (a) **code hardening** — `GroupStore` "Member" fallback
promoted to `UNRESOLVED_MEMBER_NAME`; `MemberRow` now takes `myDisplayName` (auth `displayName`) and for the self
row prefers auth name → resolved userSearch name → `"You"`, so a user with a missing/blank name never sees
"Member (you)" again. (b) **fixture backfill** — created `userSearch/J88TDlaV6…` + mirrored `displayName:"Quinn"`
into the `users/` doc (name chosen by Quinn). Roster now reads **"Quinn (you)"**; other users also resolve the name.

**Fix: Home idle layout + CURRENTLY OPEN (#4/#5) ✅ (emulator-verified).** One change closes both: `HomeScreen` now
renders the "CURRENTLY OPEN" foreground-app chip **only while a lock-in is active** (`sessionActive` polled 1s
alongside `foregroundApp`). Idle Home is now a clean, centered mascot + Start hero (the chip was pulling the
centered block up, causing the asymmetric dead space); during a lock-in the chip returns where it's meaningful
(confirms the allowlisted app in focus). Verified: hidden idle, restored on start, gone again on stop.

**Fix: end-of-session summary (#1) ✅ (emulator-verified).** A clean "Stop Lock-In" now pops a celebratory
`SessionSummaryDialog` (happy mascot + "Nice work!") in `MainActivity.kt` — replacing the silent drop-to-Home.
Shows **time focused**, **Sparkles earned** (`floor(sec/60)`), and a **streak line** with a "counts toward your
streak" vs "lock in {threshold} min to count" sub-message. Numbers mirror what `LockInService` records on
teardown: sparkles via the same floor rule; streak via `fetchStreakInfo(uid, start, sec)` (folds in this very
session if it clears the user's threshold, same call the feed post uses). Streak fields load async — the dialog
shows the known numbers instantly and fills the streak in when the read returns (guarded so a dismiss-before-read
can't re-show it). Only on a **clean** finish — an alert-state stop (break/alarm) shows no summary. Verified live:
a 27s solo stop showed `0:27 · +0 ✨ · 🔥 3 day streak · "Lock in 30 min to count…"`, Done dismissed to idle Home.

**Fix: achievements loading skeleton (#3) ✅ (emulator-verified).** The Profile achievements grid used to flash a
near-invisible 24dp `CircularProgressIndicator` while the milestone read was in flight. Replaced with
`AchievementGridSkeleton` (`ProfileScreen.kt`) — a placeholder that mirrors the real 2-column layout (fixed-height
`116.dp` rounded cells, last odd cell half-width) with a gentle `rememberInfiniteTransition` alpha pulse (0.35↔0.75,
800ms reverse), so the section reads as "loading" and swaps into the real grid with no layout jump. Cell count is
`ACHIEVEMENT_COUNT = computeAchievements(emptyList(), 1).size`, derived from the pure builder so it never drifts from
the real grid. Verified live via a temporary 5s fetch delay (since stripped): skeleton rendered 7 placeholder cells,
then swapped cleanly into "2 of 7 earned" (First Lock-In / Getting Consistent) in the same slots.

**Feature: in-app dark mode ✅ (emulator-verified, full P1–P4).** Scoped with Quinn 2026-07-16 (chose "full in-app
toggle + sweep"). Key finding on investigation: **the dark palettes already existed** — `Theme.kt` had complete
`darkColorScheme`s for all four skins and `LockInTheme` already applied them, but only when the *OS* was dark
(`isSystemInDarkTheme()`), with no in-app control and never verified on-device. Work done:
- **P1 mode plumbing:** new `ThemeMode { LIGHT, DARK, SYSTEM }`; `LockInTheme(theme, mode)` resolves `dark` =
  DARK / !LIGHT / OS-when-SYSTEM. `ThemeStore` gains `loadThemeMode`/`saveThemeMode` (defaults SYSTEM, so existing
  installs keep prior behavior); `MainActivity` holds `appThemeMode` state, loads on create, saves on change.
- **P2 picker UI:** `ThemeModeSelector` segmented Light/Dark/System pill in `ProfileScreen` under a new "Appearance"
  header, above the skin swatches. Skin swatches now resolve their preview `dark` from the *active* scheme's
  background luminance (not `isSystemInDarkTheme()`), so previews track a forced mode.
- **P3 system bars:** `SideEffect` in `LockInTheme` sets `isAppearanceLight{Status,Navigation}Bars = !dark` via
  `WindowCompat`, so the status/nav icons stay legible when the background flips.
- **P4 verification sweep (emulator, forced Dark, Bubblegum skin):** drove Profile (+selector), Home (idle **and**
  active lock-in), Feed, Friends, Groups list, GroupDetail (Lobbies + Chat tabs), and the **session-summary dialog**
  — all legible, correct contrast, status-bar icons white on dark. Verified Light↔Dark↔System round-trip + the
  status bar flipping back to dark icons on return to light. Reset the fixture to SYSTEM after.

**Fix: chat timestamps (#6) ✅ (emulator-verified).** Group chat messages showed no time. `GroupMessage` already
carried `createdAtMillis` (server timestamp, with a local-echo fallback to now) — it just wasn't rendered.
`MessageBubble` (`GroupDetailScreen.kt`) now shows a locale-aware short clock time under each bubble via
`DateFormat.getTimeFormat(context)` (respects the device's 12/24h setting), `remember`ed on `createdAtMillis`,
in `labelSmall`/`onSurfaceVariant`, aligned to the sender's side. Verified live: "hello" → `8:01 AM` (right),
"live ping from feedtester" → `12:05 PM` (left).

**Verify: feed friend post (#7) ✅ (emulator-verified — NOT a bug).** Suspected the feed wasn't surfacing friend
activity; it is. `feedtester`'s single activity doc (25m SOLO, 1 break, `startedAt` 2026-07-15T02:04Z) renders
correctly as a **"Feed Tester · Locked in 25m · 1 break · Yesterday"** card with a kudos heart — it just sorts
*below* mutebreaker's many more-recent self-posts (feed is newest-first, fanned out over self + friends in
`fetchFeed`). The earlier "didn't surface" read was top-of-feed only. No code change; `fetchFeed` + `FeedScreen`
verified over REST (`users/zfuW…/activity`) and on the emulator. **Stage 9 punch list is fully cleared.**

**Portfolio packaging tail ✅ (2026-07-16) — the last Stage 9 item; Stage 9 (and the build) now COMPLETE.**
Decisions locked with Quinn: **detailed** README, **paired light+dark** gallery, **product-forward** positioning
(anti-cheat as supporting depth), **include a demo clip**. Delivered:
- **`README.md` (new, the centerpiece):** one-line pitch + demo GIF hero → problem → product-forward feature
  tour (lock-in + mascot, fail-closed anti-cheat, Discord-style groups, social feed, Sparkles economy, dark
  mode) → paired light/dark gallery → a "How the anti-cheat works" code+prose section (with an honest PoC
  false-positive/rooted-device caveat, per `CONTEXT.md`) → tech-stack table → `ARCHITECTURE.md`/`CONTEXT.md`
  pointers → build/run notes (bring-your-own Firebase + `google-services.json`).
- **`docs/screenshots/` (new):** clean paired light+dark set — Home idle, Home active, Feed, Friends, Group
  Chat, Group Members (Owner/Admin roles), Profile top/mid/bottom (streak · achievements · mascot economy),
  plus a `break-detected` shot. Captured on the emulator via `tools/adb-helpers.sh`.
- **`docs/demo.gif` (new):** idle → Start → LOCK-IN ACTIVE → BREAK DETECTED (mascot panics, red) → stopped.
  Built from a screenshot sequence with Pillow (no ffmpeg on this machine), ~380px wide, ~150 KB.
- **Fixture tidy:** deleted ~11 sub-minute throwaway `activity` docs from `mutebreaker`'s feed so the Feed
  gallery reads as real sessions (2–7 min, group + solo, with breaks) not test noise. Emulator theme restored
  to Dark after captures. *Not committed — `git push` still paused pending an explicit ask.*

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
1. **Stage 7 is COMPLETE** — all four steps done and verified (step 1 `7d710b3`; step 2 `3a91f94`; step 3 `ec0bd09`; step 4 verification-only, this commit) — see the Stage 7 section above. **Stage 8 was re-scoped from Polish & Portfolio Packaging to Social Refinement (Groups & Friends)** at Quinn's request (packaging → Stage 9); plan in `docs/archive/STAGE8_PLAN.md`, resume point in `NEXT_SESSION.md`. **Landed 2026-07-16 (uncommitted):** the `GroupDetail` **tab reorg** (Lobbies · Chat · Members + monogram header) and the Members **roster** (`fetchGroupMemberProfiles` via public `userSearch`, live status dot + Owner badge) — pulled ahead of the still-deploy-blocked presence step to fix the "disorganized" complaint; emulator-verified all three tabs. *Two-party REST harness for group flows:* `Chat Test` group `r1hs2AriiJhQYBTLVsvF`, `feedtester` over REST — a group session is startable solo by opening a CONCURRENT lobby in-app (you're the live member, so it survives dead-lobby cleanup); no REST-hosted member needed for that path.
2. Loose ends folded into Stage 7: `currentForegroundApp()`'s lookback ✅ addressed in step 1 (widened to session start for >1h sessions); the 2-min alarm cap ✅ runtime-verified in step 4 (`capped=true muteGranted=false` at a temp-lowered cap).
3. **GitHub remote (2026-07-15):** `origin` → `QuinnQuak/lock-in-app` (public), all history pushed once. Quinn then asked to pause pushing and stay local for now — commit as usual, don't `git push` again without an explicit ask. See `ARCHITECTURE.md`'s Source Control section.
4. Older commit trail (Stages 5–6) recorded in `docs/archive/STAGES_0-6.md`.
