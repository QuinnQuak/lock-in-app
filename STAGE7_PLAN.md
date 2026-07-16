# Stage 7 — Anti-Cheat Hardening (execution plan)

> The adversarial pass on Stage 1's detection core. This is the turnkey build plan: decisions are
> already made (with Quinn, 2026-07-15), each step is independently demoable, and every step names
> the real files/lines it touches and how to verify it live. Read `PROGRESS.md` for status,
> `ARCHITECTURE.md` for the codebase map, `CONTEXT.md` for product intent. **Move step by step; verify
> each on the emulator before the next.**

## The core finding that reframes Stage 7
Detection is **fail-open**. `evaluateCompliance` (`ComplianceMonitor.kt:59`) treats
`foregroundApp == null` as **COMPLIANT**, and `currentForegroundApp` (`UsageAccess.kt:24`) returns
`null` the instant Usage Access is revoked (empty `queryEvents`). Net effect today: **revoke the
permission mid-session → permanent compliance → browse anything, no alarm.** Force-close and airplane
mode are variants of the same shape — "the eyes went dark and nothing reacts." Flipping this default
to **fail-closed** is the spine of Stage 7; everything else hangs off it.

## Decisions (made with Quinn, 2026-07-15) — do NOT re-litigate
1. **Unknown foreground → BREAK** (fail-closed), after a short grace so a one-tick polling blip
   doesn't false-alarm. Not a new user-facing state — it's a normal break. (Rejected: a distinct
   "compromised" state; revocation-only detection.)
2. **Force-closed session → voided, no credit.** Killing the app yields no completed lock-in (no
   streak / Sparkles / feed), and the phantom session is surfaced as "interrupted." (Rejected: partial
   credit; silent cleanup.)
3. **Group "going dark" surfacing is DEFERRED to Stage 8+.** Stage 7 stays scoped to the solo/local
   detection core. Airplane-mode's *social* half (letting the group see a member drop off) is out of
   scope; we only correct the record on what airplane mode actually does (Step 4).
4. **In a group session, Stop is blocked while the alarm sounds.** It clears only via group approval
   or the 2-min cap (bounded wait). Solo is unaffected (its alarm self-clears on refocus). (Rejected:
   allow-with-a-bail-record; leave as-is.)

## Non-goals (explicit — don't reopen)
- **Accessibility Service.** `CONTEXT.md` deliberately chose `UsageStatsManager` (lower onboarding
  friction, lower Play-Store risk). Do not switch to Accessibility for real-time foreground events.
- **Real FCM / server-side enforcement / Cloud Functions.** No Blaze plan. Enforcement stays
  client + rules, same as the rest of the app.
- **Group going-dark surfacing** (deferred, decision 3).
- **Root / OS-level tamper.** A rooted user can defeat any client; out of PoC scope. State it plainly
  in the portfolio writeup rather than pretending otherwise.

---

## Step 1 — Fail-closed detection *(highest value, solo-verifiable — do first)*
**Goal:** an unknown foreground stops counting as compliant. Revoking Usage Access mid-session, or a
sustained "no foreground data" while the screen is on, becomes a real BREAK (alarm fires).

**Files:** `ComplianceMonitor.kt` (evaluateCompliance), `UsageAccess.kt` (lookback + a revoked-vs-empty
distinction), `LockInService.kt` (grace debounce in the loop).

**Approach — distinguish the *cause* of a null, don't blanket-alarm:**
- **Screen off → still compliant.** Legitimate and independent of foreground. Keep the `!isScreenOn`
  branch in `evaluateCompliance:59` exactly as-is.
- **Permission revoked** (`!hasUsageAccessPermission(this)`, `UsageAccess.kt:13`): unambiguous and
  deliberate — you don't accidentally revoke a system permission. Escalate to BREAK fast (short or
  zero grace), ideally with a clear reason surfaced ("monitoring was turned off").
- **Screen on, permission granted, but no foreground app found:** could be a transient/lookback
  artifact → apply a **grace debounce** (~3–5s of *continuous* unknown-while-screen-on) before
  escalating to BREAK, so a single-tick `null` never edge-triggers the break at
  `LockInService.kt:172-181`.
- Keep `evaluateCompliance` a **pure function**; the grace/debounce state (consecutive-unknown tick
  counter) lives in the service loop where the other edge-triggered state already is.

**Fold in the lookback loose end here** (it directly reduces false "unknown"):
`currentForegroundApp` uses a fixed 1-hour window (`UsageAccess.kt:30`). **The careful fix WIDENS,
never narrows:** query from `min(sessionStartMillis, now − 1h)`. A naive "query from session start"
would *narrow* the window for any <1h session and reintroduce the exact `null` the 1h window was
papering over (an app already foreground *before* the session, never re-foregrounded, emits no
`MOVE_TO_FOREGROUND` in `[start, now]`). Pass `sessionStartMillis` (already captured at
`LockInService.kt:78`) into `currentForegroundApp` and take the earlier of it and `now − 1h`.

**Key design call:** fail-closed is the anti-cheat-honest default, but its risk is false-positive
breaks. The two mitigations above (widened lookback + grace debounce) are what make it safe — build
both, not just the flip. A hair-trigger fail-closed that alarms on every polling gap would be worse
than the hole it fixes.

### Implementation spec (decided 2026-07-16 — turnkey, execute as-is)
Verified against the live code. **Four small edits, no new files, no new enum, no new UI state, no
new security rule; `MainActivity` untouched.** The whole flip is: stop the pure function calling a
screen-on `null` "compliant," and gate the *transient* case in the loop. Nothing roundabout.

**The one insight that keeps it minimal:** the old `foregroundApp == null` compliant clause was
*doubly* covering screen-off — but screen-off is *already* compliant via `!isScreenOn` (the loop
passes `foregroundApp = null` **and** `isScreenOn = false` when the screen is off). So we don't need a
"cause" enum threaded through `evaluateCompliance`; we just **delete the null clause** and screen-off
stays compliant for free, while screen-on + null becomes a break. Cause-distinction (revoked vs.
transient blip) lives entirely in the loop, where the other edge-triggered state already is.

**Edit 1 — `ComplianceMonitor.kt`, `evaluateCompliance` (delete one line).** Drop `foregroundApp ==
null ||` from the `isCompliant` expression (currently `ComplianceMonitor.kt:60`). Result:
```kotlin
    // Fail-closed: an unknown foreground (null) while the screen is ON is not
    // compliant. Screen-off stays compliant via !isScreenOn, so dropping the old
    // `foregroundApp == null` clause costs nothing there but closes the "revoke
    // Usage Access mid-session → permanent compliance" hole. The service loop
    // applies a grace debounce so a one-tick null can't edge-trigger a break.
    val isCompliant = !isScreenOn ||
        foregroundApp == ownPackageName ||
        allowlist.contains(foregroundApp)
```
(`foregroundApp == ownPackageName` and `allowlist.contains(...)` are both false for `null`, so a
screen-on `null` now yields BREAK. Pure function stays pure.)

**Edit 2 — `UsageAccess.kt`, `currentForegroundApp` (widen the lookback, backward-compatibly).** Add a
**defaulted** param so the Home-display caller (`MainActivity.kt:408`) needs no change:
```kotlin
fun currentForegroundApp(context: Context, sessionStartMillis: Long = 0L): String? {
    ...
    val endTime = System.currentTimeMillis()
    val oneHourAgo = endTime - TimeUnit.HOURS.toMillis(1)
    // Widen to the session start only when the session is older than an hour (an
    // app foregrounded before the 1h window would age out and report a false
    // null). Never NARROWS below 1h: a <1h session, or no session (0L, the Home
    // caller), keeps the 1h floor.
    val startTime = if (sessionStartMillis in 1 until oneHourAgo) sessionStartMillis else oneHourAgo
    val events = usageStatsManager.queryEvents(startTime, endTime)
    ...
}
```

**Edit 3 — `LockInService.kt`, one field + one constant.** Beside `wasInBreak` (`:57`):
```kotlin
private var unknownTicks = 0   // consecutive screen-on + unknown-foreground ticks (grace debounce)
```
Beside `MAX_ALARM_DURATION_MILLIS` (`:36`):
```kotlin
// A screen-on "unknown foreground" (null) must persist this many 1s ticks
// before it escalates to BREAK, so a single-tick lookback blip during a fast
// app-switch can't false-alarm. Deliberate permission revocation bypasses it.
private const val UNKNOWN_GRACE_TICKS = 4
```

**Edit 4 — `LockInService.kt`, the loop (`:160-163`).** Pass the session start into the lookback, and
compute `status` through the grace gate:
```kotlin
    val foregroundApp = if (isScreenOn) currentForegroundApp(this@LockInService, sessionStartMillis) else null
    val allowlist = loadAllowlist(this@LockInService)

    // Distinguish the *cause* of a screen-on null before deciding.
    val unknownWhileOn = isScreenOn && foregroundApp == null
    unknownTicks = if (unknownWhileOn) unknownTicks + 1 else 0
    // Only pay for the AppOps check when there's actually an unknown to explain.
    val permissionRevoked = unknownWhileOn && !hasUsageAccessPermission(this@LockInService)

    val rawStatus = evaluateCompliance(packageName, isScreenOn, foregroundApp, allowlist)
    // Hold compliant through a *transient* unknown (grace) — but never for a
    // deliberate revocation, which escalates on the first tick.
    val status = if (unknownWhileOn && !permissionRevoked && unknownTicks < UNKNOWN_GRACE_TICKS) {
        ComplianceStatus(ComplianceState.COMPLIANT, foregroundApp)
    } else {
        rawStatus
    }
    LockInMonitor.update(status)
```
Everything downstream (`inBreak`, alarm, liveStatus push, `setAlarmSounding`) already reads `status`
unchanged — a group revocation-break flows through the existing sticky-alarm path for free, a solo one
through the self-clearing path. `unknownTicks` needs no manual reset: the service is a fresh instance
per session, and the per-tick `else 0` clears it the moment a known foreground returns.

**How the two mitigations compose (why it's safe, not hair-trigger):** the widened lookback (Edit 2)
removes the *legitimate* screen-on nulls (an app foregrounded before the window), so a sustained null
now almost always means revocation or a real query gap; the grace (Edit 4) absorbs the *residual*
one-to-three-tick blip. Revocation is the one unambiguous case, so it skips grace and fires in ~1s;
a generic unknown waits ~4s. No user-facing "compromised" state — it's a normal BREAK (decision 1).

**Verify (solo, no second party needed):**
- Start a solo session on an allowlisted app → compliant (no regression).
- Screen off → still compliant.
- **Revoke Usage Access in system settings mid-session → BREAK + alarm within the grace window.**
- Normal fast app-switch between allowlisted apps → no false alarm (grace holds).
- (Temp `Log.d` the unknown-tick counter + cause to confirm the debounce path, then strip it.)

**Demo line:** "Turning off the app's own permission mid-session sets off the alarm instead of
silently granting a free pass."

---

## Step 2 — Force-closed session is voided ✅ DONE (`3a91f94`, emulator-verified)
**Goal:** kill the phantom "active" session left after a force-stop, and give it **no credit**.

> **Built as specified below.** Heartbeat (`KEY_LAST_HEARTBEAT` + `writeHeartbeat` in
> `LockInSessionStore.kt`, written each tick by `LockInService`), `isStale()` (active + heartbeat
> >`SESSION_STALE_THRESHOLD_MILLIS` 10s), `MainActivity.onResume` reconcile → void + one-time red
> `InterruptedBanner`, and a `START_STICKY` `voided` path in `LockInService` (onCreate detects an
> already-stale restart; onStartCommand tears down; onDestroy early-returns past recording *and* the
> receiver-unregister). Verified on the emulator: force-stop→relaunch voids the phantom + shows the
> banner + REST-confirmed unchanged `sparkles/sessions/activity`; a Home-button background stays active
> (heartbeat fresh). Full log in `PROGRESS.md`.

**Files:** `LockInSessionStore.kt` (add a heartbeat field), `LockInService.kt` (write heartbeat each
tick; staleness check in `onCreate` for START_STICKY restarts), `MainActivity.kt` (reconcile on
launch/`onResume`).

**Approach:**
- **Heartbeat:** add `KEY_LAST_HEARTBEAT` to the session prefs
  (`LockInSessionStore.kt:7-13`); the service writes `now` every tick (the loop already runs every
  1s at `LockInService.kt:230`). Cheap, and it's the ground-truth "is the service alive" signal —
  cleaner than the deprecated `getRunningServices`.
- **Reconcile on app entry** (`MainActivity` launch / `onResume`): if prefs say `isActive` **and**
  `now − lastHeartbeat > staleThreshold` (~10s), the service is dead (force-stop / crash / OS kill).
  → clear the phantom session and surface a one-time "Your last lock-in was interrupted and didn't
  count."
- **Voiding is naturally free:** history/activity/Sparkles are all written in
  `LockInService.onDestroy` (`LockInService.kt:114-147`), which **never runs on a force-stop** — so
  nothing was recorded. "Void it" is therefore just: clear the phantom prefs, do **not**
  retroactively record anything, message the user. Be careful the reconciliation path does not call
  any of the `recordSessionToCloud` / `awardSparkles` / `recordActivityToCloud` helpers.
- **START_STICKY belt-and-suspenders:** Android may auto-restart the service after a kill. In
  `onCreate`, if the loaded session's `lastHeartbeat` is already stale (a monitoring gap happened),
  treat it as interrupted and void rather than silently resuming a session that had a dark window.
- **Don't false-void a legit background:** a foreground service keeps beating while the app is merely
  backgrounded (Home button), so its heartbeat stays fresh — only a real process death goes stale.
  The ~10s threshold must comfortably clear a 1s tick cadence.

**Verify (solo):**
- Start session → `adb shell am force-stop com.example.lockin` → relaunch → phantom session gone,
  "interrupted, didn't count" shown, **REST-confirm no new `sessions` / `activity` doc and `sparkles`
  unchanged**.
- Background via Home button (not force-stop) → return → session **still active**, not voided
  (heartbeat fresh).

**Demo line:** "Force-quitting to escape monitoring doesn't bank the session — it's voided and
flagged, not silently completed."

---

## Step 3 — Block Stop while the alarm sounds (group) *(needs the two-party REST harness)*
**Goal:** close the "Stop Lock-In silences the sticky alarm" escape hatch in group sessions.

**Files:** `MainActivity.kt` (`SessionControls` — the solo/Home Stop control).

**Approach:**
- While in a **group** session (`session.groupId != null`) **and** `LockInMonitor.alarmSounding` is
  true (`ComplianceMonitor.kt:31`), disable the Stop button and show why: "Alarm is active — it
  clears when your group approves or after the 2-minute cap."
- **Bounded by design:** the 2-min cap (`MAX_ALARM_DURATION_MILLIS`, `LockInService.kt:36`)
  guarantees `alarmSounding` flips false within 2 minutes, so the user is never trapped. This is why
  decision 4 is safe — the block has a hard ceiling.
- **Solo unaffected:** a solo alarm self-clears the moment the user refocuses an allowlisted app
  (`LockInService.kt:190-192`), so Stop is never meaningfully blocked solo.

**Residual hatch to document (not fix this step):** sign-out tears the service down regardless
(auth cleared → `onDestroy`), so it bypasses the Stop block. Note it as a known residual; a full fix
(gating sign-out mid-alarm) is Stage 8 polish, not core.

**Verify (group — reuse `Chat Test` fixture `r1hs2AriiJhQYBTLVsvF`, second party over REST):**
- Open a Concurrent lobby → break → sticky alarm → **Stop is disabled**.
- `feedtester` approves over REST (or wait out the 2-min cap) → alarm clears → **Stop re-enables**.
- Confirm the cap path re-enables Stop too (see Step 4's temp-lowered cap trick).

**Demo line:** "In a group, you can't just hit Stop to kill the alarm — the group approves it or it
caps out, exactly as designed."

---

## Step 4 — Close the loose ends: verify the 2-min cap + correct the airplane-mode record ✅ DONE (emulator-verified, verification-only — no code kept)
**Goal:** convert two "logic-reviewed / vaguely-stated" items into verified, accurately-documented
facts. Mostly verification + doc correction, minimal new code.

> **Both verified.** Temp scaffolding (20s cap + a one-line `Stage7Cap` log) added, used, and fully
> reverted (`git diff` clean, restored build reinstalled). **4a:** group break's sticky alarm
> auto-silenced at the temp cap *without* approval — `CAP HIT capped=true muteGranted=false
> elapsedMs=20287`, `dumpsys audio` showed the `USAGE_ALARM` MediaPlayer `event:stopped` + `releasing
> player` at that instant, and Home re-enabled Stop (cap clears the Step-3 block). **4b:** airplane ON →
> break → alarm `state:started` **while offline** (local detection is connectivity-independent); REST
> (from host) still `COMPLIANT` (BREAK write queued, not sent); airplane OFF → REST `state: BREAK`
> (queued write flushed on reconnect). So airplane mode *delays* group reporting, not defeats it —
> Firestore offline persistence is on by default. Docs corrected in `PROGRESS.md` + `CONTEXT.md` +
> `ARCHITECTURE.md` + `docs/archive/STAGES_0-6.md`. Full log in `PROGRESS.md`.

**4a — 2-min alarm cap.** The docs are inconsistent (Stage 4 says "logic-reviewed, not runtime
verified"; the lobby-rework Step 2 note says it fired once in an earlier pass). Nail it down:
temporarily set `MAX_ALARM_DURATION_MILLIS` (`LockInService.kt:36`) to ~20s, trigger a group break,
confirm the alarm auto-silences at ~20s with `muteGranted=false capped=true` in logs, then **restore
the constant**. Update every doc that still calls the cap "logic-reviewed only."

**4b — airplane mode, stated accurately.** Today `PROGRESS.md`/`CONTEXT.md` flatly say "airplane mode
defeats detection," which is imprecise. Verify and record the truth:
- **Local detection + alarm are connectivity-independent** — `UsageStatsManager` is a local query, so
  airplane mode does **not** defeat the *solo* alarm. It only defeats the *group reporting* layer
  (liveStatus push, mute approvals, break alerts need Firestore).
- **Firestore offline persistence:** verify whether a BREAK `liveStatus` write **queues offline and
  flushes on reconnect** (group session → airplane on → break → local alarm fires → airplane off →
  REST-confirm the queued write lands). If it does, airplane mode is a *delay*, not an escape, as long
  as the process survives — and if the process is *also* killed, Step 2 voids it locally (the group
  still won't know until the Stage 8 going-dark work — decision 3).
- Rewrite the airplane-mode limitation line to this precise version in `PROGRESS.md` + `CONTEXT.md`.

**Verify:** covered inline above (cap timing in logs; queued-write REST check).

---

## Suggested order & why
1 → 2 → 3 → 4. Step 1 is the highest-value, most-novel, and fully solo-verifiable — validate the
risky core first (matches Stage 1's "validate before building on top"). Step 2 is independent and
also solo-verifiable. Step 3 needs the group REST harness. Step 4 is verification/cleanup, last.
Each step is committable on its own and leaves the app runnable. **All four steps done: 1 (`7d710b3`),
2 (`3a91f94`), 3 (`ec0bd09`), 4 (verification-only). Stage 7 COMPLETE — next is Stage 8.**

## Residual limitations we knowingly keep (portfolio honesty)
- Sign-out ends a session, bypassing the Step 3 Stop block (Stage 8 candidate).
- Airplane mode still *delays* group reporting; the group can't *see* a member go dark until Stage 8
  (decision 3).
- Dwelling in the Lock-In app itself counts as compliant (opening the app = compliant). It isn't a
  distracting app, so this is acceptable — document, don't fix.
- A rooted device / OS-level tamper defeats any client-side scheme. State it, don't hide it.

## Test fixtures (unchanged — see `ARCHITECTURE.md`)
- Emulator signed in as `mutebreaker@lockin.test` (🔥3 fixture, `sparkles:85`).
- `feedtester@lockin.test` — mutual friend, driven over REST for two-party flows.
- `Chat Test` group `r1hs2AriiJhQYBTLVsvF` (mutebreaker + feedtester, `muteApprovalCount 1`) — the
  lobby / mute-approval fixture. REST harness: `tools/fb.py` (passwords in gitignored `tools/creds.json`).

