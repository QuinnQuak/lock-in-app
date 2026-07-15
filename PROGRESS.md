# Progress Handover

> Living status doc only. For product intent/rationale see `CONTEXT.md`; for tech stack, data model, and codebase structure see `ARCHITECTURE.md`. Update this file after each meaningful milestone.

## Status: Stages 0–4 complete and verified, plus the onboarding/permission-priming deliverable

Every item below was checked live on the `Medium_Phone` emulator (screenshots, `dumpsys`, logcat, or direct REST calls against deployed rules), not just compiled.

### Stage 0 — Environment ✅
Kotlin + Jetpack Compose scaffold, git initialized, hello-world verified running.

### Stage 1 — Solo Lock-In Core ✅
All 4 deliverables done: foreground-app + screen-on/off detection, personal allowlist, session start/stop (real foreground `Service`), break detection + alarm. Visual design (soft lavender/sage/coral, Quicksand) applied app-wide.

### Stage 2 — Accounts & Cloud Sync ✅
Firebase Auth (email/password, required sign-in) + Firestore. Profile doc on sign-up, allowlist two-way sync, session history recording. Verified end-to-end on-device and via REST against deployed rules.

### Stage 3 — Friends & Visible Allowlists ✅
Friend requests (search by email, send/accept/decline), symmetric friendships, friend-visible allowlists. Verified with two real accounts alternating sign-in on one emulator: search → send → accept → symmetric friendship both sides → allowlist visible both directions → non-friend correctly denied (403 via direct REST test).

**Real bug found and fixed:** a `LazyColumn` key collision crash from leftover request state after an interrupted accept — see `ARCHITECTURE.md`'s Key Decisions & Gotchas for the general pattern.

### Stage 4 — Group Lock-Ins ✅ (commits `7a707c1`→`05e85e6`)
- ✅ Group data model + create/join (owner-managed membership picked from friends at creation — no accept-step join flow, a documented scope simplification).
- ✅ Real-time group session state sync — live compliance pushed to Firestore per member, shown as a colored-dot list on Home during an active group session.
- ✅ Break alerts — mocked (no Blaze billing for real FCM/Cloud Functions): a local notification fires via the same live listener when a groupmate breaks. Verified: cold app launch with an existing BREAK doc correctly raised a notification.
- ✅ Alarm cap — auto-silences after 2 min regardless of group response. Implemented and logic-reviewed; not runtime-verified (would need a real 2-minute wait).
- ✅ **Mute-approval flow** (`MuteRequestStore.kt`): the breaker asks the group to silence their alarm, and it goes quiet once `muteApprovalCount` *other* members approve. Muting stops the sound only — the BREAK state, the red dot, and `breakCount` all stand.
- ✅ **Sticky alarm in group sessions** — a prerequisite discovered while building the above, not a nice-to-have: the alarm used to track live compliance, so a breaker opening Lock-In to request a mute became "compliant" and silenced their own alarm on the way in, making approval unreachable. See `CONTEXT.md`'s 2026-07-15 decision and `ARCHITECTURE.md`'s Key Decisions.

**Verified on the emulator end-to-end** with a real second account (`muteapprover@lockin.test`) driven over the Firestore REST API, since a two-party flow can't be exercised by one signed-in emulator:
- Break fires → alarm sounds → breaker returns to Lock-In → **alarm keeps sounding** (the sticky behavior).
- "Ask the group to mute my alarm" writes the request; Home shows `Waiting on the group — 0/1 approved`.
- The approver's approval lands → the breaker's device silences the alarm within ~1s, via mute grant and *not* the 2-min cap (confirmed in logs: `muteGranted=true capped=false`).
- **A breaker self-approving is rejected by the rules (403)** — otherwise one person could clear the room's threshold alone.
- A second break afterwards sounds normally and is *not* silenced by the now-stale approval (the `breakId` guard).

### Onboarding & Permission Priming ✅
The long-open deliverable from `CONTEXT.md`, now built (`OnboardingScreen.kt`, `OnboardingStore.kt`). Five steps: welcome → why Usage Access (with an honest note that Lock-In reads *which* app is open, never what's inside) → a walkthrough naming exactly what the Android settings list will look like and what to toggle → notification priming → confirmation. Replaces the old single-card `PermissionPrompt`, which explained the ask in one sentence and then dropped the user into an unlabelled list of every app on the phone.

Notification permission moved here from its old ad-hoc prompt on the Start button — see `CONTEXT.md` for why.

**Verified end-to-end on the emulator** (fresh install, brand-new account):
- All five steps render; the step-dot indicator tracks position.
- "Open Settings" fires `ACTION_USAGE_ACCESS_SETTINGS` (confirmed in logcat); granting the permission and returning **auto-advances off the grant step** via the `onResume` re-check, with no user action.
- The notification dialog now arrives *after* the framing card rather than cold; allowing it lands on the confirmation step.
- Home then shows "CURRENTLY OPEN: Lock-In" — the grant genuinely works, not just the UI state.
- Restarting the app does **not** replay onboarding (flag persisted to `SharedPreferences`).
- Revoking Usage Access re-enters the flow **at the grant step**, not back at the welcome.

### Stage 5 — Social Feed & Gamification 🚧 (in progress)
**Scope decided with the user (2026-07-15):** friend-wide feed (every completed lock-in auto-posts to all friends); streak = a day with a lock-in reaching a **per-user, friend-visible** `streakMinMinutes` (default 30, mirrors the allowlist-transparency rule); feed items **show break count** (on-theme with the no-honor-system trust layer); **single kudos** per person per item; achievements **derived on the fly**; build all five pieces (feed, streaks, achievements, kudos, polish) at portfolio depth. Build order: activity backbone → feed → kudos → streak + profile setting → achievements → polish.

Key design call: raw `users/{uid}/sessions` stays **private**; the feed reads a separate, purpose-built friend-visible `users/{uid}/activity` stream. No Cloud Functions, so the feed **fans out on read** (query each friend's stream, merge client-side). `streakAtPost` is stamped by the poster because a friend can't compute a streak from the poster's private sessions.

**Step 1 — activity backbone ✅ (verified on emulator).** New `users/{uid}/activity` + `kudos` rules deployed; `ActivityStore.recordActivityToCloud()` posts on session teardown alongside the private session write; `streakMinMinutes: 30` default on sign-up; `groupName` denormalized through `LockInSessionStore` so a non-member friend can still show the group name. Verified: a solo and a group lock-in each wrote one activity doc with the right shape (`type` SOLO/GROUP, duration, `breakCount`, timestamps, `streakAtPost: 0` until step 4; group doc carried `groupId` + `groupName: "Mute Test"`). Unauthenticated read → 403. Friend-read of activity is deferred to step 3's positive test (reuses the Stage-3-verified `isFriendOf()` gate). `streakAtPost` stays 0 until step 4 wires the streak.
> Test note: `displayName` reads "Someone" for the REST-created test accounts (their Firebase Auth profile has no display name); real app sign-ups set it via `updateProfile` (`AuthScreen.kt:78`), same as the existing group live-status path.

**Step 2 — Feed screen ✅ (verified on emulator).** `ActivityStore.fetchFeed()` fans out on read (own uid + friends, one-time parallel `get()` per author, individual failures tolerated, merged newest-first); `FeedScreen.kt` renders each item as a soft card (name/"You", "Locked in {dur} · {breaks}", group name for group sessions, relative time, 🔥streak when > 0), with loading/empty states; wired as a `Screen.Feed` sub-screen with a "Feed" entry on Home. Verified: both of the viewer's own docs rendered, sorted newest-first, the group doc showed "with Mute Test", break counts and relative times correct.
> Deferred to step 3: positive test of a *friend's* post appearing (needs a friend-with-activity pair — same harness step 3's kudos test needs). Self-path already exercises the full merge/sort/render; friend read reuses the Stage-3-verified `isFriendOf()` gate.

**Step 3 — Kudos ✅ (verified on emulator).** `KudosStore.kt` (give/remove/live-listen, one doc per reactor at `activity/{eventId}/kudos/{reactorUid}`); each feed card owns a kudos listener (bounded by on-screen rows) and a heart toggle — filled/count when reacted, hidden on own posts (read-only tally only once someone reacts). Set up a durable friend pair to close the deferred friend-visibility test (see test accounts in `ARCHITECTURE.md`). Verified end-to-end: **the friend's post now shows in the feed** ("Feed Tester · 25m · 1 break"); give → heart fills, "1 kudos", kudos doc created server-side (positive friend-kudos rule test); remove → doc deleted (0 server-side); own posts show no control; **self-kudos rejected by rules (403)** — the `reactorUid != uid` guard.

**Step 4 — Streaks + Profile ✅ (verified on emulator).** `ProfileStore.kt` computes the current streak on the fly from own `sessions` (consecutive local-days with a lock-in ≥ `streakMinMinutes`; today doesn't break it until it ends), using `Calendar` not `java.time` (minSdk 24, no desugaring — see `ARCHITECTURE.md`). `ProfileScreen.kt`: name, 🔥 streak hero (🌱 at 0), and a −/+ stepper for the friend-visible `streakMinMinutes` (clamped 5–120). `LockInService` teardown now stamps the real `streakAtPost` — a streak fetch (folding in the just-finished session) runs on Firestore's own threads and posts with streak 0 if it fails, never dropping the feed event. Verified with 3 backdated 30-min sessions: streak shows **🔥 3**; raising the goal to 35 min drops it to **0** and persists server-side, lowering back to 30 restores **3**; a fresh solo lock-in posted an activity doc with `streakAtPost=3` (REST-confirmed) that renders 🔥3 in the feed. Also fixed a blank-`displayName` fallback (now `"You"`, not an empty gap).
> Finding: mutebreaker's `users/{uid}` doc never existed (REST-created in Stage 4 without a profile doc) — the SDK `.get()` returns a non-existent snapshot as *success*, so streak still computed on the default threshold. The `setStreakMinMinutes` merge-write is what first created the doc (partial: `streakMinMinutes` only). Not a bug; a note about that incomplete test fixture.

**Step 5 — Achievements ✅ (verified on emulator).** `AchievementsStore.kt`: a pure `computeAchievements()` derives 7 tiered milestones from own `sessions` (no persisted "earned" flag — same on-the-fly philosophy as the streak), plus a `fetchAchievements()` that mirrors `fetchStreakInfo`'s read shape and falls back to "all locked" on any read failure. The roster: **First Lock-In** (≥1 session), **Getting Consistent** (10), **Half-Century** (50), **Deep Work** (a single session ≥2h), **Ten Hours In** (≥10h all-time), **Week Warrior** (7 consecutive qualifying days), **Flawless Week** (7 consecutive break-free qualifying days). `ProfileScreen.kt` gained a scrollable earned/locked 2-col grid below the streak goal (earned = full-color card + "Earned"; locked = dimmed emoji + progress like "7 / 10"), an "N of 7 earned" tally, and re-fetches achievements when the threshold stepper changes. **Key design call:** the two streak-shaped milestones use the *longest run ever* (`longestConsecutiveDays()`), not the live current streak, so an achievement never un-earns — decoupled from the Profile hero, which still shows the live streak. "Flawless Week" was chosen (over "current 7-day streak, break-free") as *any* 7 historical consecutive break-free days, i.e. monotonic like the rest. Verified with the mutebreaker fixture (7 sessions across a 3-day streak): First Lock-In earned; Getting Consistent 7/10, Half-Century 7/50, Deep Work 30/120 min, Ten Hours In 1.6/10 h, Week Warrior 3/7, Flawless Week 3/7 — all internally consistent. Raising the goal to 35 min live-dropped Week Warrior and Flawless Week to 0/7 (30-min days no longer qualify) while count/total/max milestones held; lowering back to 30 restored them. No new debug logging (only the permanent `Log.w` error fallbacks).

**Step 6 — polish pass 🚧 (in progress).** The final step of Stage 5, three parts:
- ✅ **"ALARM SOUNDING" header state (verified on emulator).** New `LockInMonitor.alarmSounding: StateFlow<Boolean>`, published each tick by `LockInService` from the settled `alarmActive` flag; the Home header reads it and shows a coral **"ALARM SOUNDING"** (plus a red Stop button) whenever the alarm is actually making noise, instead of the misleading green "LOCK-IN ACTIVE". Chose an explicit flag over deriving `breakId > 0 && !isBreak` in the UI — same reason `breakId` was surfaced for the mute flow: honest reflection of ground truth, robust to state-machine changes. Precedence is BREAK → ALARM SOUNDING → LOCK-IN ACTIVE, so an in-break breaker still reads "BREAK DETECTED". Verified: group session → opened Settings (break) → alarm fires → returned to Lock-In (COMPLIANT again, green group dot, "CURRENTLY OPEN: Lock-In") → **header correctly read coral "ALARM SOUNDING"**, the exact case that previously showed green. Stopping the session cleared the flag via `LockInMonitor.reset()`. No debug logging added.
- ⬜ In-app nudge if the user declined notifications during onboarding.
- ✅ **Visual redesign (verified on emulator).** At the user's request (2026-07-15) the app moved off the original lavender/sage/coral pastels to a **warm/energetic palette** (amber `#F57C1F` primary, green `#2BB673` secondary, warm-cream `#FBF7F2` canvas, red `#E8455F` alert; light + dark) and off the Home-screen `TextButton` list to a **bottom `NavigationBar`** (Home · Feed · Friends · Groups · Profile). Allowlist + Sign Out are now a Settings block under Profile; `material-icons-extended` added for the tab icons. **Bug found and fixed during review:** there was no `BackHandler`, so hardware/gesture Back finished the activity from *every* screen (a bottom-tab app should send Back from a secondary tab → Home, and from nested Allowlist → Profile). Added two conditional `BackHandler`s; verified live: Feed→Back→Home, Profile→Allowlist→Back→Profile, Home→Back→exit. Palette + nav render verified across Home/Profile/Allowlist/Feed. No debug logging added.

## Known, Currently-Live Limitations
Same spirit as `CONTEXT.md`'s documented loopholes — real gaps, not oversights, as of this commit:
- Airplane mode defeats detection.
- Force-stopping the app leaves a phantom "active" session in the UI until manually stopped.
- Opening Lock-In itself always counts as compliant — the alarm can be silenced just by switching back to the app without actually returning to focus.
- Break alerts only fire while the observing device's own app process is alive (mock, not real FCM — see `ARCHITECTURE.md`).
- Declining notifications during onboarding is a real choice with no second ask: break alerts *and* the foreground-service notification silently don't appear. The session still runs and detection still works, so this is degraded rather than broken — but there's currently no in-app nudge to reconsider.
- `currentForegroundApp()`'s 1-hour lookback window is a stopgap, not the technically correct fix.
- **Pressing "Stop Lock-In" silences a sticky alarm**, since the service tears down with it. So the alarm is now hard to silence *within* a session, but ending the session is still a free escape hatch. Natural Stage 6 (Anti-Cheat Hardening) target.

## What's Next
1. **Stage 5, step 6 — one part left.** "ALARM SOUNDING" header ✅ and the visual redesign ✅ are both done and verified. Still to do: an **in-app nudge** if the user declined notifications during onboarding. After that, Stage 5 is complete.
2. Two Stage-4 loose ends worth folding into a later stage: the 2-min alarm cap is still only logic-reviewed (never runtime-waited), and `currentForegroundApp()`'s lookback window should query from session start.
3. Stage 5 steps 1–5 committed as `0195042`; step 6's "ALARM SOUNDING" work committed as `0f5e25a`; the visual redesign + back-nav fix committed as `3fcd7b5` (`Theme.kt`, `MainActivity.kt`, `ProfileScreen.kt`, `build.gradle.kts`, `libs.versions.toml` + docs).
