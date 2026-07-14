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

## Known, Currently-Live Limitations
Same spirit as `CONTEXT.md`'s documented loopholes — real gaps, not oversights, as of this commit:
- Airplane mode defeats detection.
- Force-stopping the app leaves a phantom "active" session in the UI until manually stopped.
- Opening Lock-In itself always counts as compliant — the alarm can be silenced just by switching back to the app without actually returning to focus.
- Break alerts only fire while the observing device's own app process is alive (mock, not real FCM — see `ARCHITECTURE.md`).
- Declining notifications during onboarding is a real choice with no second ask: break alerts *and* the foreground-service notification silently don't appear. The session still runs and detection still works, so this is degraded rather than broken — but there's currently no in-app nudge to reconsider.
- `currentForegroundApp()`'s 1-hour lookback window is a stopgap, not the technically correct fix.
- **Pressing "Stop Lock-In" silences a sticky alarm**, since the service tears down with it. So the alarm is now hard to silence *within* a session, but ending the session is still a free escape hatch. Natural Stage 6 (Anti-Cheat Hardening) target.
- The Home header still reads "LOCK-IN ACTIVE" (green) while a sticky alarm is blaring, because the header tracks live compliance and the breaker is technically compliant again. Honest, but visually confusing — worth an "ALARM SOUNDING" state in Stage 5's polish pass.

## What's Next
1. **Stage 5 — Social Feed & Gamification.**
2. Two Stage-4 loose ends worth folding into a later stage: the 2-min alarm cap is still only logic-reviewed (never runtime-waited), and `currentForegroundApp()`'s lookback window should query from session start.
