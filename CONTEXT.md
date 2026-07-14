# Context: Social Lock-In App (working title)

> The "why" and product intent. For current build status see `PROGRESS.md`; for tech stack, data model, and codebase structure see `ARCHITECTURE.md`.

## Context for the Agent
Solo portfolio project built by a CS student with a C/C++ background who is new to Android/Kotlin and Firebase. Explanations should assume strong general programming fundamentals but no prior mobile-specific experience. Prioritize teaching moments alongside code, not just code dumps.

## Core Problem
Digital minimalism and focus habits have no verified social accountability layer. Unlike fitness (Strava) or reading (Goodreads), there's no trusted way to track and share "I stayed off my phone" — existing solutions rely on the honor system or manual screenshots, which are easy to fake and don't build real social pressure.

## Target Users
Students and young professionals. The primary growth/retention mechanic is **peer pressure via live, social accountability** — not solo habit tracking with social features bolted on afterward.

## Core Mechanic: The "Lock-In"
A lock-in is an auto-detected, tamper-resistant focus session, either solo or in a group.

**Compliance rules during an active lock-in:**
- Screen off → compliant
- Screen on + foreground app is in the user's **personal allowlist** → compliant
- Screen on + foreground app is NOT in the allowlist (e.g., games, social media, messaging) → **BREAK**

**On break:**
- An obnoxiously loud, hard-to-silence alarm triggers on the breaker's own device
- All other members of that lock-in group receive an instant break alert (e.g., "Sarah just broke her lock-in 💀")

**Anti-cheat philosophy:** No honor system, anywhere. Every compliance state must be system-detected, not self-reported.

**Allowlist transparency rule:** Each user's allowlist is customizable per-user, but is **visible to their friends/group members**. This prevents users from secretly whitelisting distracting apps under false pretenses — the allowlist itself is part of the social trust layer.

**Alarm muting rule (solo vs. group):**
- **Solo lock-in:** the user manages their own alarm/rules — no group gatekeeping needed.
- **Group lock-in:** muting the breaker's alarm requires approval from other room members — a form of "herd behavior" enforcement. The **approval threshold is a customizable room setting** (e.g., "4 of 8 members must approve" or unanimous, or a single approver), set by whoever creates/configures the room. This makes strictness a group choice rather than a hardcoded rule, and naturally self-limits: if the group doesn't bother approving a mute request, that's the group's peer pressure choosing to lapse, not a system bug.
- **Decided (2026-07-15):** additionally, max alarm duration — the alarm auto-stops after a fixed cap (2 min) regardless of group response, so an unresponsive group at 3am doesn't mean a genuine multi-minute alarm.

## Known Loopholes / Deliberately Deferred Problems
Surfaced during design review and consciously deferred rather than accidentally missed — worth remembering as "known limitations of a proof of concept," not oversights. (See `PROGRESS.md` for which are currently live in the build.)

- **Airplane mode / no connectivity:** Many blocked-category apps (games, cached social feeds, offline media, notes, texts) still work fully offline. Airplane mode doesn't stop the underlying behavior — it stops the app's ability to detect/report it. Deferred past MVP; revisit in Stage 6 (Anti-Cheat Hardening).
- **Permission revocation mid-session:** If a user revokes Usage Access / Accessibility Service permission or force-stops the app during a lock-in, the group is informed that monitoring stopped, but no further enforcement action is taken. Intentional MVP-scope decision — the system "surfaces the disable event but doesn't prevent it." Be prepared to state this distinction clearly if asked whether the app is truly cheat-proof.
- **False positives in detection:** Occasional misdetection (falsely flagging a break) is an accepted risk for MVP. Frame explicitly as a known PoC limitation in any demo/README/portfolio presentation — don't gloss over it.

## Platform Decision
**Android-first, native.** Rationale:
1. Android's `UsageStatsManager` / Accessibility Service APIs allow real foreground-app detection and stronger background monitoring than iOS's more restrictive Screen Time / Family Controls / DeviceActivity framework.
2. The developer has no Apple hardware or Mac available for iOS testing/building, and iOS Simulator is known to be unreliable for Screen Time / background activity APIs — physical device testing is effectively required for iOS.
3. iOS is a possible future platform post-validation. The social/backend layer should be architected to not be Android-locked (Firebase backend is platform-agnostic) so iOS can be added as a second native client later without a backend rebuild.

## Design Direction
Gamified but calming — a blend of **Finch** (soft pastel warmth, encouraging tone) and **Notion** (clean structure, minimalism). Avoid childish or overly "mascot-heavy" gamification; lean toward soft color palettes, clean typography, and subtle reward/streak mechanics rather than loud badges or cartoonish UI. (Implemented palette/typography specifics are in `ARCHITECTURE.md`.)

## MVP Feature Scope (in priority order)
1. **Group lock-in sessions** — create/join, live session state, break detection triggers alarm + group alert
2. **Screen-off / foreground-app auto-detection** — the trust/anti-cheat backbone; must work reliably in the background
3. **App allowlist system** — user-customizable, friend-visible, distinguishes "safe" apps (e.g., Spotify, study apps) from blocked ones (games, social, messaging)

Explicitly deferred past MVP: broader social feed, streaks/kudos, gamified profile polish (later stages).

## Staged Build Plan
> Each stage should end in something runnable/demoable — avoid multi-stage detours before there's something to test. Current status of each stage is tracked in `PROGRESS.md`.

- **Stage 0 — Environment & Fundamentals:** Android Studio setup, Kotlin basics, minimal "hello world" app(s).
- **Stage 1 — Solo Lock-In Core (no social/Firebase yet):** Local-only mechanic — start/stop a session, detect screen on/off and foreground app via `UsageStatsManager`, personal allowlist, background break-detection + alarm. **Highest-risk, most novel stage — validate before building anything on top of it.**
- **Stage 2 — Accounts & Cloud Sync:** Firebase Auth + Firestore. Persist user profiles, allowlists, and session history tied to a real account.
- **Stage 3 — Friends & Visible Allowlists:** Friend request system; allowlists become visible to friends.
- **Stage 4 — Group Lock-Ins:** Create/join group sessions, sync session state in real time via Firestore, alarm (local) + break alert (group) on a break.
- **Stage 5 — Social Feed & Gamification:** Post-session achievements, streaks, kudos/reactions, full pastel Finch/Notion visual polish.
- **Stage 6 — Anti-Cheat Hardening:** Adversarial pass on Stage 1's detection logic — force-close, disabling battery optimization exceptions, airplane mode, etc.
- **Stage 7 — Polish & Portfolio Packaging:** Onboarding flow, README, demo video/screenshots for portfolio presentation.

## Open Design Questions
- **Onboarding & Permission Priming:** Android's Usage Access permission prompt is a scary, deeply-buried system setting most users have never touched, and asking for it cold at signup risks losing users before they experience the core loop. Plan a **permission-priming screen** (shown before the OS system dialog) that frames the ask in product terms — e.g., "we need this to keep your friends honest." Treat as a real design deliverable, not an afterthought. **Not yet built** as of Stage 4.

## Working Agreement for the Agent
- Move stage by stage; don't jump ahead to social/gamification features before Stage 1's detection core is proven reliable.
- Flag tradeoffs explicitly rather than silently picking one — the developer wants to understand *why*, not just receive a decision. (Exception: for routine day-to-day work the user has since asked for terse responses — see memory `feedback_terse_no_unsolicited_code`. Reserve full tradeoff explanations for genuinely consequential/irreversible decisions.)
- Assume no prior Android/Kotlin/Firebase knowledge — explain unfamiliar platform concepts briefly as they come up, the way you would to a strong programmer who is new to this specific ecosystem.
