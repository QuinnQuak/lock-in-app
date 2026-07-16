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

**Group model — decided 2026-07-15 (supersedes the earlier "passive group"):** a group is a
persistent, Discord-like **"server"** — a standing roster (owner invites friends) with its own
**group chat**. You don't lock in "as a group" implicitly; instead a member opens an ephemeral
**lobby** *inside* the group — a live room others hop into as they arrive. **Multiple lobbies can run
at once** (like several voice channels). Each lobby picks a **mode**: **Concurrent** (everyone locked
in together, each on their own open-ended clock) or **Shared** (one synced round with a set length —
everyone starts now and ends together, a "match"). Solo lock-ins stay on Home; all live group-session
UI (member presence, the mute-approval flow) lives in the group room. Came out of explicit clarifying
rounds with the user, consistent with prior design collaboration.

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
- **✅ Stage 7 step 3 (`ec0bd09`):** the last easy way around the sticky group alarm — hitting **"Stop Lock-In"** (which tears down the service and silences the alarm with it) — is now blocked in group sessions: Stop is disabled while the alarm sounds, clearing only on group approval or the 2-min cap. This makes the "hard-to-silence" alarm genuinely hard to silence, not one button-press away. Solo is unaffected (its alarm self-clears on refocus); mid-alarm sign-out is a knowingly-kept residual (Stage 8 candidate).

## Known Loopholes / Deliberately Deferred Problems
Surfaced during design review and consciously deferred rather than accidentally missed — worth remembering as "known limitations of a proof of concept," not oversights. (See `PROGRESS.md` for which are currently live in the build.)

- **Airplane mode / no connectivity:** Many blocked-category apps (games, cached social feeds, offline media, notes, texts) still work fully offline. Airplane mode doesn't stop the underlying behavior. *Refined by Stage 7:* it does **not** defeat the *local* detection + solo alarm (`UsageStatsManager` is a local query) — only the *group reporting* layer (liveStatus/mute/alerts, which need Firestore). Stage 7 step 4 will verify + document this precisely; the group's ability to *see* a member go dark is deferred to Stage 8 (decision 3).
- **Permission revocation mid-session:** ✅ **Closed by Stage 7 step 1 (`7d710b3`).** Detection is now fail-closed: revoking Usage Access mid-session makes the foreground query return `null`, which the service attributes to the revocation and escalates to a **BREAK + alarm on the first tick** (no free pass). Previously the group was merely *informed* monitoring stopped with no enforcement; now the breaker's own alarm fires. (Force-stop is a related shape — ✅ **closed by Stage 7 step 2 (`3a91f94`)**: a per-tick heartbeat goes stale when the service dies, and the app voids the phantom session on next entry — no streak/Sparkles/feed credit, flagged as interrupted. A rooted device still defeats any client — stated plainly, not hidden.)
- **False positives in detection:** Occasional misdetection (falsely flagging a break) is an accepted risk for MVP. Frame explicitly as a known PoC limitation in any demo/README/portfolio presentation — don't gloss over it.

## Platform Decision
**Android-first, native.** Rationale:
1. Android's `UsageStatsManager` / Accessibility Service APIs allow real foreground-app detection and stronger background monitoring than iOS's more restrictive Screen Time / Family Controls / DeviceActivity framework.
2. The developer has no Apple hardware or Mac available for iOS testing/building, and iOS Simulator is known to be unreliable for Screen Time / background activity APIs — physical device testing is effectively required for iOS.
3. iOS is a possible future platform post-validation. The social/backend layer should be architected to not be Android-locked (Firebase backend is platform-agnostic) so iOS can be added as a second native client later without a backend rebuild.

## Design Direction
**Cute, bold, and character-driven.** Chunky rounded typography, a candy pink/orange palette, and a reactive mascot companion — playful and personality-forward, not clean/minimal.

**Superseded (2026-07-15):** the earlier direction ("clean structure inspired by Notion... avoid childish or mascot-heavy gamification... lean toward clean typography over loud badges or cartoonish UI") is retired at the user's explicit request, not softened — mascot-heavy and cartoonish *is* the new direction now, not a tension to manage against a professional baseline. This also supersedes the amber/green "warm, energetic" palette decided earlier the same day (2026-07-15) — that one shipped first, then was itself replaced by Bubblegum the same day. **Stage 6 steps 1–2 (palette + typography, theme picker) are now built** — see `ARCHITECTURE.md`'s Visual Design; the rest of this redesign (mascot, Sparkles economy) is decided but not yet built.

- **Palette — "Bubblegum" (decided 2026-07-15):** pink is the primary brand color, orange secondary. Rethought as one cohesive four-color set rather than reusing the old functional colors as-is — streak/momentum uses **orange** (ties to the existing 🔥 streak emoji) instead of green, and break/alert uses a **cherry red** distinct enough in hue from primary pink not to be confused with a normal button.

  | Role | Light | Dark |
  |---|---|---|
  | Primary (Pink) | `#FF4F8B` | `#FF6FA3` |
  | Secondary (Orange) | `#FF9142` | `#FFA766` |
  | Alert/Break (Cherry Red) | `#E63950` | `#FF5C72` |
  | Background | `#FFF3F6` (blush) | `#241620` (deep plum) |
  | Surface/cards | `#FFFAFB` | `#331D2C` |

- **Theme picker (new, decided 2026-07-15):** beyond light/dark, a curated set of accent skins (not a fully open picker, to keep the system cohesive and avoid an unbounded accessibility/QA surface) — **Bubblegum** (default, pink primary/orange secondary), **Peach** (orange primary/pink secondary, swapped emphasis), **Berry** (deeper magenta-pink primary/coral secondary), **Sunset** (red-orange forward, hotter/punchier). Each has its own light + dark pair. User-selectable, likely from Profile.

- **Typography (decided 2026-07-15):** replaces Quicksand. **Fredoka** (weights 500–700) for headers, hero numbers, buttons, and nav labels — bold, chunky, high-personality. **Nunito** (weights 400–700) for body text, chat, and feed/list rows, where Fredoka's weight would hurt legibility at density. Both Google Fonts, OFL-licensed, variable fonts (same licensing shape as the outgoing Quicksand). Corner radii bumped up app-wide to match the rounder letterforms.

- **Mascot — "blob buddy" (new, decided 2026-07-15):** a reactive companion character, not just a static profile icon. Simple round/teardrop blob, big expressive eyes, tiny stub limbs; recolors to match the user's active theme. Reacts to app state: idle/breathing loop while compliant, a happy bounce + sparkle on a completed lock-in, a droop-and-tears animation on a break, sleeping with "zzz" when there's no active session. Appears **everywhere** — Home hero, Profile, session status views, loading states — not scoped to one screen.

- **Mascot customization & the "Sparkles" economy (new, decided 2026-07-15):** the mascot supports equippable accessories (hats, glasses, etc.), unlocked two ways:
  - **Trophy case** — one signature accessory per existing achievement tier (7 total, see Stage 5 Decisions below), auto-granted on earning the achievement, never purchasable. Reuses the already-built achievement system instead of inventing new unlock logic.
  - **Shop** — a broader, lower-stakes set of cosmetics bought with **Sparkles**, a new currency earned passively at **1 Sparkle per minute locked in** (solo or group). Chosen over a flat per-session award or a streak bonus because it ties the currency directly to the core mechanic (time actually spent locked in) rather than a separate economy that needs its own balancing.

This is large enough to get its own line in the Staged Build Plan below rather than folding silently into "polish" — see the new Stage 6.

- **Build order (decided 2026-07-15):** 1) palette + typography swap ✅ **built same day** (`Theme.kt` Bubblegum colors, Fredoka/Nunito, corner radii — pure visual, no new data model); 2) theme picker ✅ **built same day** (Peach/Berry/Sunset + persisted selection, builds on step 1's token structure); 3) mascot "blob buddy" ✅ **built same day** (`Mascot.kt`, Canvas-drawn, static app-state reactivity only — idle/happy/droop/sleeping, no economy yet — wired into Home/Profile/session status/loading; all four moods verified on the emulator, BREAK caught via the sticky group alarm since a solo break self-clears on refocus; committed `112deee`); 4) Sparkles currency ✅ **built** (1/min-locked-in accrual awarded at session teardown via `FieldValue.increment` on `users/{uid}.sparkles`, display only, no spending; a `✨ N Sparkle(s)` pill on Profile; verified on the emulator — a real ~96s session seeded the absent field to 1, and the pill rendered plural/singular correctly; committed `8d63e56`); 5) trophy case ✅ **built** (the 7 achievement tiers mapped 1:1 to auto-granted accessories, ownership derived on the fly from `AchievementsStore` — no persisted "earned" flag, same philosophy as the streak/achievement math); 6) Shop ✅ **built** (spend Sparkles on 6 cosmetics via a Firestore transaction, reusing the single equip slot + inventory plumbing the trophy case established) — steps 5+6 built and verified together (equip propagates to every mascot, purchase decrements atomically + persists across relaunch), Stage 6 now complete. **Implementation call (2026-07-15):** accessories are **emoji overlaid on the blob at a slot** (HEAD/FACE/NECK), not hand-drawn Canvas shapes — matches the existing mood-emoji overlays and keeps a 13-item catalog cheap; the tradeoff is emoji don't recolor with the theme like the blob does. The equipped accessory is hoisted at the composition root via a `CompositionLocal` so every `Mascot()` wears it without threading a param (same idea as the theme). Rationale: visual foundation before the character before the economy; within the economy, the free/automatic unlock (trophy case) before the one needing a purchase flow (Shop), so the accessory/equip system gets validated cheaply before Shop adds transaction complexity on top of it. Confirmed with Quinn before building.

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
- **Stage 6 — Cute Redesign & Mascot Economy ✅ complete:** Full visual redesign (Bubblegum pink/orange palette + theme picker, Fredoka/Nunito typography) and a reactive mascot companion with equippable accessories, unlocked via the existing achievements (trophy case) and a new passively-earned Sparkles currency (Shop). Full decided spec is in Design Direction above; all 6 steps built and emulator-verified.
- **Stage 7 — Anti-Cheat Hardening 🚧 (in progress):** Adversarial pass on Stage 1's detection logic — force-close, revoking Usage Access, airplane mode, etc. Turnkey execution plan in `STAGE7_PLAN.md` (decisions made with Quinn 2026-07-15/16; spine is flipping detection from fail-open to fail-closed). **Step 1 (fail-closed detection) ✅ done + emulator-verified (`7d710b3`); Step 2 (void force-closed sessions) ✅ done + emulator-verified (`3a91f94`)** — a per-tick heartbeat + reconcile-on-app-entry voids a phantom force-closed session with no credit and flags it as interrupted. Steps 3–4 remaining (block Stop during a group alarm, verify the 2-min cap + correct the airplane-mode record).
- **Stage 8 — Polish & Portfolio Packaging:** Onboarding flow, README, demo video/screenshots for portfolio presentation.

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
- **Commit locally as usual, but don't `git push` unless explicitly asked again (2026-07-15).** A GitHub remote (`origin` → `QuinnQuak/lock-in-app`) was added and pushed to once, then Quinn asked to pause pushing and stay local for now. Don't treat that earlier approval as standing — wait for a fresh ask each time. See `ARCHITECTURE.md`'s Source Control section.
