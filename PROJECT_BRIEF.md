# Project Brief: Social Lock-In App (working title)

## Context for the Agent
This is a solo portfolio project built by a CS student with a C/C++ background who is new to Android/Kotlin and Firebase. Explanations should assume strong general programming fundamentals but no prior mobile-specific experience. Prioritize teaching moments alongside code, not just code dumps.

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
- All other members of that lock-in group receive an instant push notification (e.g., "Sarah just broke her lock-in 💀")

**Anti-cheat philosophy:** No honor system, anywhere. Every compliance state must be system-detected, not self-reported.

**Allowlist transparency rule:** Each user's allowlist is customizable per-user, but is **visible to their friends/group members**. This prevents users from secretly whitelisting distracting apps under false pretenses — the allowlist itself is part of the social trust layer.

**Alarm muting rule (solo vs. group):**
- **Solo lock-in:** the user manages their own alarm/rules — no group gatekeeping needed.
- **Group lock-in:** muting the breaker's alarm requires approval from other room members — a form of "herd behavior" enforcement. The **approval threshold is a customizable room setting** (e.g., "4 of 8 members must approve" or unanimous, or a single approver), set by whoever creates/configures the room. This makes strictness a group choice rather than a hardcoded rule, and naturally self-limits: if the group doesn't bother approving a mute request, that's the group's peer pressure choosing to lapse, not a system bug.
- **Decided (2026-07-15):** max alarm duration, auto-stops after a fixed cap (2 min) regardless of group response.

## Known Loopholes / Deliberately Deferred Problems
These were surfaced during design review and consciously deferred rather than accidentally missed — worth remembering as "known limitations of a proof of concept," not oversights:

- **Airplane mode / no connectivity:** Many blocked-category apps (games, cached social feeds, offline media, notes, texts) still work fully offline. Airplane mode doesn't stop the underlying behavior — it stops the app's ability to detect/report it. This is a real, easily discoverable loophole, not just an edge case. Deferred past MVP; revisit in Stage 6 (Anti-Cheat Hardening).
- **Permission revocation mid-session:** If a user revokes Usage Access / Accessibility Service permission or force-stops the app during a lock-in, the group is informed that monitoring stopped, but no further enforcement action is taken. This is an intentional MVP-scope decision: the system is "not fully unfakeable," it "surfaces the disable event but doesn't prevent it." Be prepared to state this distinction clearly if asked whether the app is truly cheat-proof.
- **False positives in detection:** Since this is a proof-of-concept, occasional misdetection (falsely flagging a break) is an accepted risk for MVP. This should be explicitly framed as a known PoC limitation in any demo, README, or portfolio presentation — not glossed over.

## Platform Decision
**Android-first, native.** Rationale:
1. Android's `UsageStatsManager` / Accessibility Service APIs allow real foreground-app detection and stronger background monitoring than iOS's more restrictive Screen Time / Family Controls / DeviceActivity framework.
2. The developer has no Apple hardware or Mac available for iOS testing/building, and iOS Simulator is known to be unreliable for Screen Time / background activity APIs — physical device testing is effectively required for iOS.
3. iOS is a possible future platform post-validation, once there's justification to acquire test hardware or bring on an iOS-capable collaborator. The social/backend layer should be architected to not be Android-locked (e.g., Firebase backend is platform-agnostic) so iOS can be added as a second native client later without a backend rebuild.

## Design Direction
Gamified but calming — a blend of **Finch** (soft pastel warmth, encouraging tone) and **Notion** (clean structure, minimalism). Avoid childish or overly "mascot-heavy" gamification; lean toward soft color palettes, clean typography, and subtle reward/streak mechanics rather than loud badges or cartoonish UI.

## Tech Stack
- **Language/Framework:** Kotlin, native Android (Android Studio)
- **Backend:** Firebase
  - Firestore — user profiles, allowlists, session history, group session state
  - Firebase Auth — login/signup
  - Firebase Cloud Messaging (FCM) — real-time break-alert push notifications to group members
- **Foreground app / usage detection:** `UsageStatsManager` and/or Accessibility Service (final choice TBD — needs a tradeoff discussion in Stage 1 around permission friction, reliability, and detection latency)
- **No cross-platform framework** (React Native/Flutter) — going fully native Kotlin since this is Android-only for now, and native is a stronger portfolio/resume signal for a solo project.

## MVP Feature Scope (in priority order)
1. **Group lock-in sessions** — create/join, live session state, break detection triggers alarm + group push notification
2. **Screen-off / foreground-app auto-detection** — the trust/anti-cheat backbone; must work reliably in the background
3. **App allowlist system** — user-customizable, friend-visible, distinguishes "safe" apps (e.g., Spotify, study apps) from blocked ones (games, social, messaging)

Explicitly deferred past MVP: broader social feed, streaks/kudos, gamified profile polish (these come in later stages, see below).

## Staged Build Plan
> Each stage should end in something runnable/demoable — avoid multi-stage detours before there's something to test.

- **Stage 0 — Environment & Fundamentals:** Android Studio setup, Kotlin basics, minimal "hello world" app(s) to get comfortable with Android lifecycle/layouts before touching project-specific code.
- **Stage 1 — Solo Lock-In Core (no social/Firebase yet):** Local-only mechanic. Start/stop a lock-in session, detect screen on/off and foreground app via `UsageStatsManager`, implement personal allowlist, get background break-detection + alarm working reliably. **This is the highest-risk, most novel stage — validate this before building anything on top of it.**
- **Stage 2 — Accounts & Cloud Sync:** Firebase Auth + Firestore. Persist user profiles, allowlists, and session history tied to a real account.
- **Stage 3 — Friends & Visible Allowlists:** Friend request system; allowlists become visible to friends.
- **Stage 4 — Group Lock-Ins:** Create/join group sessions, sync session state in real time via Firestore, wire FCM so a break triggers alarm (local) + push notification (group).
- **Stage 5 — Social Feed & Gamification:** Post-session achievements, streaks, kudos/reactions, full pastel Finch/Notion visual polish.
- **Stage 6 — Anti-Cheat Hardening:** Adversarial pass on Stage 1's detection logic — force-close, disabling battery optimization exceptions, airplane mode, etc.
- **Stage 7 — Polish & Portfolio Packaging:** Onboarding flow, README, demo video/screenshots for portfolio presentation.

## Open Design Question: Onboarding & Permission Priming
Android's Usage Access permission prompt is a scary, deeply-buried system setting that most users have never touched, and asking for it cold at signup risks losing users before they experience the core loop. Plan to design a **permission-priming screen** (shown before the OS system dialog) that frames the ask in product terms — e.g., "we need this to keep your friends honest" — rather than relying on ad hoc explanation at build time. This should be treated as a real design deliverable in Stage 1 or 2, not an afterthought.

## Working Agreement for the Agent
- Move stage by stage; don't jump ahead to social/gamification features before Stage 1's detection core is proven reliable.
- Flag tradeoffs explicitly rather than silently picking one (e.g., `UsageStatsManager` vs Accessibility Service) — the developer wants to understand *why*, not just receive a decision.
- Assume no prior Android/Kotlin/Firebase knowledge — explain unfamiliar platform concepts briefly as they come up, the way you would to a strong programmer who is new to this specific ecosystem.
