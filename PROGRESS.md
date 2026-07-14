# Progress Handover

> This is a living status doc, not a design doc — for the "what and why" of the project, see `PROJECT_BRIEF.md`. This file exists so a fresh Claude Code session (or you) can get oriented in one read, without depending on memory carrying over across sessions. Update it after each meaningful milestone.

## Status: Stages 0, 1, and 2 complete and verified

### Stage 2 — Accounts & Cloud Sync (complete)
- **Firebase project:** `lockin-app-sg` (display name "Lock-In"), owned by quakjunehao@gmail.com, Firestore in `asia-southeast1`. Config lives in `app/google-services.json` — **gitignored**, re-download via Firebase console → Project settings → Your apps if missing.
- **Auth:** email/password (required sign-in model — app opens to `AuthScreen` until signed in). Sign-up collects a display name and writes a `users/{uid}` profile doc to Firestore (`UserProfileStore.kt`). Verified on-emulator end-to-end: sign-up → profile doc visible in Firestore, sign-out → auth screen, sign-in → Home.
- **Security rules:** owner-only rules deployed from `firestore.rules` (never used test mode — DB was created with closed rules and immediately got real ones). Deploy changes with `firebase deploy --only firestore:rules`.
- **Allowlist sync (`AllowlistSync.kt`):** Firestore (`users/{uid}.allowlist`) is the source of truth; SharedPreferences stays the synchronous local cache the service polls. Toggles write both; a snapshot listener (owned by MainActivity via `DisposableEffect(signedIn)`) pulls remote changes into the cache. Verified both directions on-device (remote REST edit reached SharedPreferences in seconds). Last-write-wins conflicts, fine for one device.
- **Session history (`SessionHistoryStore.kt`):** on service teardown, one doc per session under `users/{uid}/sessions` — startedAt/endedAt, durationSeconds, breakCount (transition-edge counted in `LockInService`). uid + start time are captured at service *start* because stop-flow clears both stores before `onDestroy` runs. Verified: a 43s session with one deliberate break recorded `breakCount: 1`. Force-stopped sessions are never recorded (known loophole, Stage 6).
- **Tooling note:** Firebase CLI runs via a portable Node install at `%LOCALAPPDATA%\node-portable\node-v20.18.2-win-x64\firebase.cmd` (logged in as the owner account). No global Node/npm on this machine.

## Earlier stages: Stage 0 and Stage 1 complete and verified

Every item below was checked live on the `Medium_Phone` emulator (screenshots, `dumpsys`, or logcat depending on what a screenshot couldn't show), not just compiled.

### Stage 0 — Environment
Kotlin + Jetpack Compose scaffold, git initialized, hello-world verified running.

### Stage 1 — Solo Lock-In Core (all 4 deliverables done)
1. **Foreground-app + screen-on/off detection** — `UsageStatsManager` polling (`UsageAccess.kt`) + a runtime-registered `BroadcastReceiver` for the protected `SCREEN_ON`/`SCREEN_OFF` broadcasts (`ScreenStateReceiver.kt`). Note: `currentForegroundApp()` uses a 1-hour lookback window, which is a stopgap — the technically correct fix is to query from the session's start time instead of any fixed window (not yet done).
2. **Personal allowlist** (`AllowlistScreen.kt`, `AllowlistStore.kt`, `InstalledApps.kt`) — real installed apps with icons, toggleable, persisted via `SharedPreferences`.
3. **Session start/stop** (`LockInService.kt`, `LockInSessionStore.kt`) — a real foreground `Service`, not just an Activity toggle, so monitoring survives backgrounding.
4. **Break detection + alarm** (`ComplianceMonitor.kt`) — compliance evaluated every 1s (screen off, own app, or allowlisted app = compliant; else break); on break, a looping `MediaPlayer` alarm + vibration starts, confirmed via `dumpsys audio` (not just internal state).

### Visual design
Custom `LockInTheme` (`Theme.kt`): soft lavender/sage/coral pastel palette (light + dark), Quicksand variable font app-wide, `Scaffold` + top bar with spring-based screen transitions, springy button press feedback. Chosen direction: "soft & tactile," explicitly not a mascot/character (checked against the brief's "avoid childish" guidance).

## Known, deliberately accepted limitations
Same spirit as the brief's own documented loopholes — real gaps, not oversights:
- Airplane mode defeats detection (brief's original)
- Force-stopping the app isn't prevented, and leaves a **phantom "active" session** in the UI (growing timer, no actual service running) until the user manually stops it
- Opening Lock-In itself always counts as compliant (intentional, so checking your session doesn't punish you) — but this means the alarm can be silenced just by switching back to the app without actually returning to focus

## What's next
**Stage 3 — Friends & visible allowlists** (friend request system; allowlists become friend-readable, which means loosening the owner-only Firestore rules deliberately). Still open from the brief itself and not yet addressed:
- The onboarding/permission-priming screen (explicitly called out as a real design deliverable)
- The Stage 4 open question: does an unresponsive group need a grace period / max alarm duration?

## Codebase map
All Kotlin under `app/src/main/java/com/example/lockin/`:
- `MainActivity.kt` — screens (Permission prompt, Home, Allowlist) + navigation shell
- `Theme.kt` — colors, typography, `LockInTheme`
- `UsageAccess.kt` — Usage Access permission check + foreground-app polling
- `InstalledApps.kt` — queries launchable apps for the allowlist
- `AllowlistStore.kt` / `AllowlistScreen.kt` — allowlist persistence + UI
- `LockInSessionStore.kt` — session active/start-time persistence + start/stop helpers
- `LockInService.kt` — foreground service: notification, screen-state receiver, compliance polling loop, alarm
- `ScreenStateReceiver.kt` — wraps `SCREEN_ON`/`SCREEN_OFF` broadcast registration
- `ComplianceMonitor.kt` — compliance model + `LockInMonitor` shared state (Service writes, UI observes)
- `AuthScreen.kt` — email/password sign-in/sign-up UI (no success callback; MainActivity observes auth state)
- `UserProfileStore.kt` — writes the Firestore `users/{uid}` profile doc on sign-up
- `AllowlistSync.kt` — two-way allowlist sync (push on toggle, snapshot listener pulls into SharedPreferences)
- `SessionHistoryStore.kt` — writes one `users/{uid}/sessions` doc per finished session

Firebase config files at the project root: `firestore.rules` (owner-only rules), `firebase.json`, `.firebaserc` (default project `lockin-app-sg`).

**Renamed (2026-07-14):** package is now `com.example.lockin`, app displays as "Lock-In" everywhere (launcher, notification, top bar). Previously `com.example.firstproject` / "First Project," the leftover Android Studio scaffold name. Verified with a clean build + fresh install after uninstalling the old package (different `applicationId` = a distinct app identity to Android).
