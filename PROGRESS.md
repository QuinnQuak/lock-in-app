# Progress Handover

> Living status doc only. For product intent/rationale see `CONTEXT.md`; for tech stack, data model, and codebase structure see `ARCHITECTURE.md`. Update this file after each meaningful milestone.

## Status: Stages 0–3 complete and verified; Stage 4 mostly done

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

### Stage 4 — Group Lock-Ins (in progress, commits `7a707c1`→`5ff1201`)
- ✅ Group data model + create/join (owner-managed membership picked from friends at creation — no accept-step join flow, a documented scope simplification).
- ✅ Real-time group session state sync — live compliance pushed to Firestore per member, shown as a colored-dot list on Home during an active group session.
- ✅ Break alerts — mocked (no Blaze billing for real FCM/Cloud Functions): a local notification fires via the same live listener when a groupmate breaks. Verified: cold app launch with an existing BREAK doc correctly raised a notification.
- ✅ Alarm cap — auto-silences after 2 min regardless of group response. Implemented and logic-reviewed; not runtime-verified (would need a real 2-minute wait).
- ❌ **Not yet built: mute-approval flow.** The room `muteApprovalCount` setting exists on the group doc, but nothing reads or acts on it yet — a breaker's alarm can't currently be muted by group approval at all.

## Known, Currently-Live Limitations
Same spirit as `CONTEXT.md`'s documented loopholes — real gaps, not oversights, as of this commit:
- Airplane mode defeats detection.
- Force-stopping the app leaves a phantom "active" session in the UI until manually stopped.
- Opening Lock-In itself always counts as compliant — the alarm can be silenced just by switching back to the app without actually returning to focus.
- Break alerts only fire while the observing device's own app process is alive (mock, not real FCM — see `ARCHITECTURE.md`).
- `currentForegroundApp()`'s 1-hour lookback window is a stopgap, not the technically correct fix.

## What's Next
1. **Mute-approval flow** to finish Stage 4 (a breaker's alarm-mute request needs N group approvals per the room's `muteApprovalCount`).
2. **Onboarding/permission-priming screen** — still open from `CONTEXT.md`, a named real deliverable, not yet started.
3. After Stage 4: Stage 5 (Social Feed & Gamification).
