# Next Session Kickoff

> Read this first. Then, if you need more depth: `CONTEXT.md` (why/product intent), `ARCHITECTURE.md` (how/tech stack/codebase map), `PROGRESS.md` (detailed status log). This file is just the fast-start summary — it doesn't replace those. Also read `CLAUDE.md` for standing working agreements (address the user as Quinn; keep every Markdown doc current after each goal).

## Where things stand
Stages 0–5 complete + onboarding. **The group lock-in feature was reworked (2026-07-15) from a
passive roster into Discord-style servers + live lobbies** — a group is now a persistent server with
**group chat**, and a **lobby** is an ephemeral live room inside it (multiple at once; each
**Concurrent** = own clocks, or **Shared** = one synced auto-ending round). All live group-session UI
moved into the group room; Home is solo-only + an "Open group room" deep-link. New files
`ChatStore.kt`, `LobbyStore.kt`, `GroupDetailScreen.kt`; new `groups/{id}/messages` + `lobbies` +
a `lobbyId` field on liveStatus/mute docs. Built + emulator-verified in four steps (chat, concurrent
lobbies + relocated mute UI, shared-mode auto-stop, dead-lobby cleanup) — see `PROGRESS.md`.
Committed as `9b8279f`.

## What's next
**Stage 6 — Cute Redesign & Mascot Economy** (renumbered 2026-07-15 — this used to be Anti-Cheat
Hardening; that's now Stage 7). Same-day design redecision, superseding the amber/green palette that
had *just* shipped in Stage 5 step 6: a "Bubblegum" pink/orange palette + light/dark + a curated
theme picker (Bubblegum/Peach/Berry/Sunset), Fredoka/Nunito typography, and a reactive mascot
("blob buddy") with equippable accessories — a trophy-case unlock per achievement tier, plus a
broader Shop bought with a new passively-earned **Sparkles** currency (1/minute locked in). Full
spec is in `CONTEXT.md`'s Design Direction; nothing in this stage is built yet.

**Stage 7 — Anti-Cheat Hardening** (was Stage 6). The adversarial pass on Stage 1's detection core: the phantom "active" session left by a force-close, airplane mode defeating detection, revoking Usage Access mid-session, and the "Stop Lock-In silences a sticky alarm" free escape hatch. Fold in the two loose ends below.

**Stage 8 — Polish & Portfolio Packaging** (was Stage 7): onboarding flow docs, README, demo video/screenshots.

Stage 5 step 6 recap (all done, now superseded visually by Stage 6 above but still the live build): the **"ALARM SOUNDING"** header state (Home shows red "ALARM SOUNDING" via `LockInMonitor.alarmSounding` instead of a misleading green "LOCK-IN ACTIVE"); the **visual redesign** (amber/green/warm-cream palette, bottom `NavigationBar`, Allowlist + Sign Out under Profile, `BackHandler`s); and the **notification nudge** (dismissible Home banner when notifications are off on API 33+, deep-links to app notification settings, `onResume` re-check via `MainActivity.notificationsGranted`).

## Stage 5 scope (decided 2026-07-15) — see `CONTEXT.md`
Friend-wide auto-posting feed; streak = a day with a lock-in ≥ a per-user, friend-visible `streakMinMinutes` (default 30); feed items show break count; single kudos per person; achievements derived on the fly. **Achievements roster (decided 2026-07-15, built in step 5):** First Lock-In, Getting Consistent (10), Half-Century (50), Deep Work (single 2h session), Ten Hours In (10h total), Week Warrior (7-day run), Flawless Week (7 consecutive break-free days) — all monotonic (longest-run-ever, not current streak) so they never un-earn. All built at portfolio depth.

## Stage 6 scope (decided 2026-07-15) — see `CONTEXT.md`'s Design Direction
- **Bubblegum palette:** pink primary (`#FF4F8B`/`#FF6FA3` light/dark), orange secondary, cherry-red alert (distinct hue from primary so it doesn't read as a normal button), blush/deep-plum background — full 4-role light+dark table in `CONTEXT.md`.
- **Theme picker:** Bubblegum (default), Peach, Berry, Sunset — each a light+dark pair, user-selectable (likely Profile).
- **Typography:** Fredoka (500–700, headers/hero numbers/buttons/nav) + Nunito (400–700, body/chat/feed rows), replacing Quicksand; corner radii bumped app-wide.
- **Mascot "blob buddy":** reactive companion, appears everywhere (Home hero, Profile, session status, loading states) — idle/breathing while compliant, happy bounce+sparkle on completion, droop+tears on break, sleeping "zzz" when idle; recolors to the active theme.
- **Mascot accessories:** trophy case (1 signature accessory per achievement tier, auto-granted, never purchasable — reuses the existing 7-tier achievement system) + a broader Shop bought with **Sparkles** (new currency, 1/minute locked in, solo or group).
- Build order not yet decided with Quinn — first thing to nail down when picking this stage up.

## Test fixtures (see `ARCHITECTURE.md` for full detail)
- Emulator is signed in as `mutebreaker@lockin.test` / `MuteTest2026`, with 3 backdated 30-min sessions faking a 🔥3 streak.
- `feedtester@lockin.test` / `FeedTest2026` is a mutual friend of mutebreaker with a posted 25-min activity — the friend-feed / kudos fixture.
- **`Chat Test` group** (id `r1hs2AriiJhQYBTLVsvF`, members mutebreaker + feedtester, `muteApprovalCount 1`) — the fixture for chat / lobby / mute-approval tests; second party driven over REST. Reusable Python REST helpers live in the job's tmp dir (`fb.py` reads the API key without printing it).

## Loose ends worth folding into Stage 7 (not blockers)
- 2-min group alarm cap: logic-reviewed only, never runtime-verified with a real wait.
- `currentForegroundApp()`'s 1-hour lookback window is a stopgap; correct fix is querying from session start.

## Working agreements (see `CONTEXT.md` / `CLAUDE.md` for full text)
- Address the user as **Quinn** in every response.
- After each goal, update **all** affected Markdown docs (`PROGRESS.md`, `NEXT_SESSION.md`, `CONTEXT.md`, `ARCHITECTURE.md`) — don't leave any stale.
- Explain tradeoffs on consequential/irreversible decisions; stay terse for routine work.
- Verify every feature live on the emulator (screenshots/dumpsys/logcat/REST), not just compiled.
- Strip temporary debug logging after verification; multi-round clarifying questions for ambiguous UI/UX.
- Move step by step — don't jump ahead of what's proven.
