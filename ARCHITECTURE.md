# Architecture: Social Lock-In App

> The "how." For product intent/rationale see `CONTEXT.md`; for current build status see `PROGRESS.md`.

## Tech Stack
- **Language/Framework:** Kotlin, native Android (Jetpack Compose, not the classic View/XML system), no cross-platform framework.
- **Backend:** Firebase — Firestore (profiles, allowlists, session history, group state), Firebase Auth (email/password), no Firebase Cloud Messaging in use (see Break Alerts below).
- **Foreground app / usage detection:** `UsageStatsManager` polling (not Accessibility Service — see Key Decisions).
- **Build:** AGP 9.2.1 has Kotlin compilation built directly into the Android Gradle plugin — do **not** add the separate `org.jetbrains.kotlin.android` plugin, it conflicts (`Cannot add extension with name 'kotlin'`).

## Firebase Project
- Project `lockin-app-sg`, display name "Lock-In", owner `quakjunehao@gmail.com`, Firestore in `asia-southeast1`.
- Console: https://console.firebase.google.com/project/lockin-app-sg
- Config: `app/google-services.json` — **gitignored**, re-download via console → Project settings → Your apps if missing.
- CLI: portable Node + firebase-tools at `%LOCALAPPDATA%\node-portable\node-v20.18.2-win-x64\firebase.cmd` (logged in as the owner). No global Node/npm on this machine; the standalone `firebase.tools` instant binary is broken on this machine (firepit welcome.js JSON crash) — use the portable-Node install instead.
- Dev/test accounts: `testuser@lockin.test` / `testuser2@lockin.test` (throwaway, trivial passwords, emulator-only). Stage 4's mute-approval test added `mutebreaker@lockin.test` / `muteapprover@lockin.test` (password `MuteTest2026`), plus a `Mute Test` group with `muteApprovalCount: 1` — the second member is driven purely over the Firestore REST API, which is how a two-party flow gets tested on a single emulator.

## Firestore Data Model
```
users/{uid}                          — displayName, email, createdAt, allowlist (array<string>)
  /sessions/{sessionId}               — startedAt, endedAt, durationSeconds, breakCount (owner-only, not friend-visible)
  /friends/{friendUid}                — displayName, since (symmetric friendship marker)

userSearch/{uid}                     — emailLower, displayName (public directory for friend search)

friendRequests/{fromUid}_{toUid}     — fromUid, toUid, fromDisplayName, createdAt
                                        (doc existence alone = pending state; no status field)

groups/{groupId}                     — name, ownerUid, memberUids (array), muteApprovalCount, createdAt
  /liveStatus/{uid}                  — displayName, state (COMPLIANT|BREAK), updatedAt
  /muteRequests/{breakerUid}         — displayName, breakId, createdAt
                                        (doc existence alone = pending state, as with friendRequests)
  /muteApprovals/{breakerUid}_{approverUid}
                                     — breakerUid, approverUid, breakId, createdAt
```

`breakId` is the millisecond of the COMPLIANT→BREAK transition it belongs to. An approval only
counts against a matching `breakId`, so leftover docs from an earlier break can never silence a
later one — the cleanup on break-end is best-effort and correctness doesn't depend on it running.

### Security rules design (`firestore.rules`)
- **Owner-only baseline**, loosened deliberately per stage:
  - `users/{uid}`: owner read/write always; a **friend** may also read (`isFriendOf()`, an `exists()` check on `users/{uid}/friends/{request.auth.uid}`) — this is what makes the allowlist friend-visible.
  - `groups/{groupId}`: readable by members only; only the owner can create/update/delete the group doc itself.
  - `groups/{groupId}/liveStatus/{uid}`: any member can read; each member can only write their own doc, gated by an `exists()`/`get()` check that `request.auth.uid` is in the group's `memberUids`.
  - `groups/{groupId}/muteRequests/{uid}`: same shape as `liveStatus` — only the breaker writes their own request, any member reads it.
  - `groups/{groupId}/muteApprovals/{breakerUid}_{approverUid}`: any member reads; only the approver can create their own approval, and **never for themselves** (`approverUid != breakerUid`) — without that clause a breaker could self-approve past the room's threshold and the whole mechanic collapses. Either party may delete (the breaker cleans up when the break ends).
  - `userSearch/{uid}`: readable by any signed-in user (needed for email search), writable only by the owner.
- **No Cloud Functions anywhere** (would require the paid Blaze plan). Two patterns make trust-sensitive multi-party writes work with pure client + rules:
  1. **Friend accept, no status field:** a `friendRequests/{fromUid}_{toUid}` doc's mere *existence* is its pending state. Accepting batch-creates both `users/{uid}/friends/{friendUid}` docs (each gated by "the other party's doc create is allowed if a pending request between us still exists"), then deletes the request as a separate cleanup step. **Why not one atomic batch for accept+cleanup:** Firestore evaluates a batch's security rules against the *pre-batch* state — a status flip (or delete) earlier in the same batch is not visible to a rule check on a later write in that same batch. This is a general gotcha for any future batched-write + rules design in this app, not just this one feature.
  2. **Group membership:** owner-managed only (owner picks members from friends at creation or adds later) — no join/accept flow, sidesteps needing a non-owner to write to the group doc at all.

## Key Architectural Decisions & Gotchas
- **`UsageStatsManager` over Accessibility Service:** lower onboarding friction (plain "Usage Access" toggle vs. Accessibility's scary system warning), lower Play Store policy risk, simpler first implementation. Accepted tradeoff: polling-interval latency could let a very brief app-switch slip through.
- **`currentForegroundApp()` uses a 1-hour lookback window** (stopgap) — an app sitting in the foreground longer than the lookback with no new switch event would otherwise wrongly report `null`. Technically correct fix (not yet done): query from the active session's start time instead of a fixed window.
- **`LockInService` (foreground service) captures `ownerUid`, `groupId`, and `sessionStartMillis` in `onCreate`, not `onDestroy`:** `stopLockInSession()` clears the session store *before* `onDestroy` runs, and sign-out clears auth right after — reading them at teardown gets nothing.
- **The alarm is a separate state from compliance, and is sticky in group sessions.** Originally the alarm simply tracked live compliance, which made the group mute-approval flow *unbuildable*: this app's own package counts as compliant, so a breaker opening Lock-In to ask for a mute silenced their own alarm on the way in — nobody would ever request a mute, and no groupmate would ever see one. Now a break starts an alarm "episode" that outlives the break itself: in a **group** session it stops only on group approval or the 2-minute cap; in a **solo** session returning to a compliant app still stops it (per `CONTEXT.md`, solo users manage their own alarm). The BREAK state, the group's red dot, and `breakCount` all follow live compliance regardless — the group can forgive the noise, not the record.
- **Break-count and alarm-cap logic are edge-triggered:** `breakCount` increments and `alarmStartMillis` resets only on the COMPLIANT→BREAK transition, not every tick, so a single break doesn't get counted/re-timed repeatedly. Alarm auto-silences 2 minutes after `alarmStartMillis` regardless of group response (BREAK state itself is unaffected, just the sound) — logic-reviewed, not runtime-verified (would need a real 2-minute wait).
- **Compose `LazyColumn` keys must be unique across the *entire* list, not per `items()` block.** Bit us once: `FriendsScreen.kt` had two `items()` blocks (incoming requests, friends) both keyed by raw uid; a leftover friendRequest whose sender was already a friend (from an interrupted accept) caused `IllegalArgumentException: Key "..." was already used` the instant the screen rendered. Fixed with prefixed keys (`"request-$uid"` / `"friend-$uid"`) plus a self-heal that quietly deletes any request whose sender is already a friend. Check for this class of bug in any future screen with multiple `items()` blocks in one `LazyColumn`.
- **Break alerts are a mock, not real FCM:** true push (wakes a fully force-closed app) needs a Cloud Function + Blaze billing, which this project isn't using. Instead, `MockBreakNotifier.kt` reuses the same live Firestore listener from group session sync — when a groupmate's `liveStatus` transitions into BREAK, this device raises a local notification. Documented limitation: only fires while *this* device's app process is alive, unlike real FCM.
- **Onboarding is gated on `!usageAccessGranted || !onboardingComplete`, two separate conditions.** The Usage Access grant happens in the *system settings app*, so `MainActivity.onResume()` is the only place the app can learn it landed — that's what lets the flow auto-advance off its grant step. The persisted `onboardingComplete` flag (`OnboardingStore.kt`) is a *separate* condition because without it, the moment Usage Access is granted the gate would fall through to Home and skip the remaining steps (notifications, the confirmation) entirely. Revoking Usage Access later re-enters the flow, and it starts on the grant step rather than replaying the welcome.
- **Android 11+ package-visibility:** `queryIntentActivities` (used to list installed apps for the allowlist) silently returns empty/filtered results unless the manifest declares a `<queries>` block for `MAIN`/`LAUNCHER` intents.
- **Protected system broadcasts** (`ACTION_SCREEN_ON`/`OFF`) are only deliverable to a runtime-registered receiver (`Context.registerReceiver`), not a manifest-declared one — see `ScreenStateReceiver.kt`.
- **Force-stopping the app leaves a phantom "active" session** in the UI (growing timer, no actual service running) until the user manually stops it — session state is `SharedPreferences`-backed and doesn't know the service died. Deferred to Stage 6 (documented loophole, not a bug).
- **`adb shell input text` mangles `!` and other shell metacharacters** — a test password containing `!` types as something else and the sign-in silently fails with "email or password is incorrect." Use alphanumeric-only passwords for anything driven through `adb input`.
- **`adb exec-out screencap -p > file` gets corrupted by PowerShell 5.1 redirection** — use `adb shell screencap -p /sdcard/s.png` + `adb pull` instead when scripting emulator screenshots.
- **Git Bash mangles device paths in `adb` commands** — MSYS path translation rewrites `/sdcard/s.png` into a Windows path, so `adb shell screencap -p /sdcard/s.png` fails with a usage error that looks like a bad flag. Prefix with `MSYS_NO_PATHCONV=1`.
- **Driving the emulator's soft keyboard from `adb` is a trap.** `input tap` at fixed coordinates races the IME: the keyboard shifts the layout, swallows taps meant for buttons underneath it, and stray taps land on keys and corrupt the field you just typed. `input keyevent 111` (ESC) does *not* reliably dismiss it, and a long run of `keyevent 67` (backspace) overflows into back-navigation and can throw you out of the app entirely. Two things that actually work: drive forms with **key events only** (fields auto-focus, `keyevent 61` = TAB between them, `66` = ENTER), or create test accounts **out-of-band via the Firebase Auth REST API** (`accounts:signUp` with the web API key from `google-services.json`) and write their `users/`+`userSearch/` docs over the Firestore REST API — the app's own sign-up path is the only thing that creates those, so skipping the UI means creating them yourself.
- **`UID` is a readonly variable in bash** — `UID=$(...)` silently fails and leaves the shell's own uid in place, which then gets interpolated into REST URLs and yields a confusing `403 PERMISSION_DENIED` against a path that doesn't exist. Use a different name.
- **PowerShell here-strings with embedded double quotes inside git commit messages can break argument parsing** — avoid double-quoted phrases in `git commit -m @'...'@` bodies; rephrase without quotes.

## Visual Design (implemented)
- `Theme.kt`: custom `LockInTheme` — soft lavender/sage/coral pastel palette (light + dark variants), chosen over Material3's default purple to match the Finch/Notion direction.
- Typography: **Quicksand** variable font (`app/src/main/res/font/quicksand.ttf`, SIL OFL, bundled from `google/fonts`), applied app-wide via a custom Material3 `Typography`; `FontVariation.weight(...)` derives Medium/SemiBold/Bold from the single variable-font file.
- Motion: `Scaffold` + top bar with spring-based `AnimatedContent` screen transitions; springy button press-scale via `animateFloatAsState` + `spring()`.
- Direction explicitly chosen and confirmed with the user: "soft & tactile," not illustrated accents or a mascot/character — checked against `CONTEXT.md`'s "avoid childish/mascot-heavy" guidance rather than assumed.

## Codebase Map
All Kotlin under `app/src/main/java/com/example/lockin/`:

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Screens (Auth gate, Onboarding gate, Home, Allowlist, Friends, Groups) + navigation shell; owns `AuthStateListener`, allowlist snapshot-listener, and group break-watcher lifecycles |
| `OnboardingScreen.kt` | 5-step permission-priming flow: welcome → why Usage Access → Settings walkthrough → notification priming → done |
| `OnboardingStore.kt` | Persists the onboarding-complete flag; notification-permission checks |
| `Theme.kt` | Colors, typography, `LockInTheme` |
| `UsageAccess.kt` | Usage Access permission check + foreground-app polling |
| `InstalledApps.kt` | Queries launchable apps for the allowlist |
| `AllowlistStore.kt` / `AllowlistScreen.kt` | Allowlist local persistence (SharedPreferences) + UI |
| `AllowlistSync.kt` | Two-way allowlist sync: Firestore is source of truth, toggle pushes, snapshot listener pulls into SharedPreferences |
| `LockInSessionStore.kt` | Session active/start-time/groupId persistence + start/stop helpers |
| `LockInService.kt` | Foreground service: notification, screen-state receiver, compliance polling loop, alarm (+ cap), group live-status push, session-history write on teardown |
| `ScreenStateReceiver.kt` | Wraps `SCREEN_ON`/`SCREEN_OFF` broadcast registration |
| `ComplianceMonitor.kt` | Compliance model + `LockInMonitor` shared state (Service writes, UI observes) |
| `AuthScreen.kt` | Email/password sign-in/sign-up UI (no success callback; MainActivity observes auth state) |
| `UserProfileStore.kt` | Writes `users/{uid}` profile doc + `userSearch/{uid}` entry on sign-up |
| `SessionHistoryStore.kt` | Writes one `users/{uid}/sessions` doc per finished session |
| `FriendRequestStore.kt` | Email search, send/accept/decline friend requests |
| `FriendsStore.kt` | Friends-list listener, one-time fetch of a friend's allowlist |
| `FriendsScreen.kt` | Add-friend, incoming requests, friends list with expandable allowlist view |
| `GroupStore.kt` | Create group, listen to "my groups" |
| `GroupsScreen.kt` | Create-group panel (name, friend picker, mute-approval threshold), groups list |
| `GroupSessionStore.kt` | Push/clear/listen to a group's live per-member compliance status |
| `MuteRequestStore.kt` | Group mute-approval flow: request/approve/clear + listeners for both collections |
| `MockBreakNotifier.kt` | Watches groupmates' live status for breaks, raises a local "push-like" notification |

Firebase config at project root: `firestore.rules`, `firebase.json`, `.firebaserc` (default project `lockin-app-sg`).

**App identity:** package `com.example.lockin`, displays as "Lock-In" everywhere (launcher, notification, top bar). Renamed from the Android Studio scaffold defaults (`com.example.firstproject` / "First Project") early in Stage 0/1 — a distinct `applicationId` means a distinct app identity to Android, so any future rename needs a clean uninstall + reinstall.
