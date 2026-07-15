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
- **Decided (2026-07-15):** in a **group** lock-in the alarm is *sticky* — it keeps sounding after the breaker returns to a compliant app, and stops only on group approval or the 2-min cap. Solo is unchanged (returning to compliance silences it). This isn't polish: without it the mute-approval mechanic can't exist at all, because opening Lock-In to ask for a mute counts as compliant and would silence the alarm on the way in. It also delivers the "hard-to-silence" alarm the core mechanic calls for. Muting stops the *sound only* — the BREAK state, the group's red dot, and the break count all stand.

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
Gamified but focused — clean structure (**Notion**) with an encouraging, non-childish tone. Avoid childish or overly "mascot-heavy" gamification; lean toward clean typography and subtle reward/streak mechanics rather than loud badges or cartoonish UI.

- **Decided (2026-07-15):** the palette moved from the original soft lavender/sage/coral pastels to a **warm, energetic scheme** (amber primary, green momentum, warm-cream canvas, red alert) — the "lock in and get things done" energy over "calm and soothing." Navigation moved to a **bottom `NavigationBar`** (Home · Feed · Friends · Groups · Profile), with Allowlist + Sign Out nested under Profile. (Implemented palette/typography/nav specifics are in `ARCHITECTURE.md`.)

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
- **Stage 5 — Social Feed & Gamification:** Post-session achievements, streaks, kudos/reactions, and a full visual polish pass (the warm/energetic redesign — see Design Direction).
- **Stage 6 — Anti-Cheat Hardening:** Adversarial pass on Stage 1's detection logic — force-close, disabling battery optimization exceptions, airplane mode, etc.
- **Stage 7 — Polish & Portfolio Packaging:** Onboarding flow, README, demo video/screenshots for portfolio presentation.

## Open Design Questions
- ~~**Onboarding & Permission Priming**~~ — **Built (2026-07-15).** A 5-step priming flow now precedes the OS dialogs, framing both asks in product terms and walking the user through the Usage Access settings list by name. See `PROGRESS.md` and `ARCHITECTURE.md`. The one judgment call worth recording: **notification permission was folded into the same flow** rather than left as an ad-hoc prompt on session start. Break alerts *are* the group mechanic, so a cold deny at the Start button silently kills the social layer with no explanation; priming it alongside Usage Access frames it as "this is how you hear about your friends." **Backstopped in Stage 5 step 6 (2026-07-15):** if the user still declines (or later revokes), Home shows a dismissible nudge with a one-tap route to notification settings — a decline stays a real choice, but no longer a silent, unrecoverable one.

## Stage 5 Decisions (2026-07-15)
Scope confirmed with the user before building, then implemented steps 1–4 (see `PROGRESS.md`):
- **Feed model:** friend-wide and automatic — every completed lock-in (solo + group) posts to a friend-visible `users/{uid}/activity` stream. The raw `sessions` log stays private; the feed is a separate purpose-built stream, fanned out on read (no Cloud Functions). Closest to the "Strava for focus" framing.
- **Streak rule:** a day counts if you complete a lock-in of at least your personal `streakMinMinutes`. The threshold is **customizable but friend-visible** (default 30 min) — the same transparency principle as the allowlist: you can't secretly lower it to farm streaks.
- **Feed shows break count** (e.g. "50m · 2 breaks") — on-theme with the no-honor-system trust layer, rather than hiding slips.
- **Kudos:** a single kudos per person per post (a like/Strava-kudos), not an emoji palette. You can't kudos your own post (enforced in rules).
- **Achievements:** derived on the fly from session history (no awarded/stored docs) — simplest, no new write path. **Roster confirmed with the user (2026-07-15):** 7 tiers — First Lock-In, Getting Consistent (10 sessions), Half-Century (50), Deep Work (a single 2h session), Ten Hours In (10h all-time), Week Warrior (7-day run), Flawless Week. **Flawless Week** was chosen as *any 7 consecutive break-free days in history* (not the current streak), so it stays earned once reached; likewise Week Warrior tracks the longest run ever, not the live streak. Every milestone is monotonic — an achievement never un-earns.
- **Depth:** all five pieces (feed, streaks, achievements, kudos, polish) built shallow-but-complete at portfolio depth.

## Working Agreement for the Agent
- **Address the user as Quinn in every response** (also recorded in `CLAUDE.md`).
- **Keep the docs current:** after each goal/step, update every affected Markdown file — `PROGRESS.md`, `NEXT_SESSION.md`, `CONTEXT.md`, `ARCHITECTURE.md` — not just some.
- Move stage by stage; don't jump ahead to social/gamification features before Stage 1's detection core is proven reliable.
- Flag tradeoffs explicitly rather than silently picking one — the developer wants to understand *why*, not just receive a decision. (Exception: for routine day-to-day work the user has since asked for terse responses — see memory `feedback_terse_no_unsolicited_code`. Reserve full tradeoff explanations for genuinely consequential/irreversible decisions.)
- Assume no prior Android/Kotlin/Firebase knowledge — explain unfamiliar platform concepts briefly as they come up, the way you would to a strong programmer who is new to this specific ecosystem.
