# Next Session Kickoff

> **Read this first on a fresh session, then resume at the RESUME POINT below.** For depth if the step
> needs it: `STAGE7_PLAN.md` (active-stage spec), `PROGRESS.md` (status log), `ARCHITECTURE.md` (how),
> `CONTEXT.md` (why), `CLAUDE.md` (working agreements). Completed stages 0–6 live in
> `docs/archive/STAGES_0-6.md`. This file is the fast-start summary — don't recap finished work here.

## Where things stand
Stages 0–6 complete and emulator-verified (full detail in `docs/archive/STAGES_0-6.md`; one-line
summaries in `PROGRESS.md`). Solo core, accounts/sync, friends, group lock-ins reworked into
Discord-style servers + live lobbies, social feed + gamification, and the Bubblegum cute-redesign +
mascot economy are all shipped. **Stage 7 (Anti-Cheat Hardening) COMPLETE.** **Stage 8 (Social
Refinement — Groups & Friends) COMPLETE + deployed & emulator-verified (2026-07-16).** Next is Stage 9.

## What's next — RESUME POINT (Stage 9 — Polish & Portfolio Packaging IN PROGRESS; polish + stability, recruiter audience, no plan doc)

**Stage 9 scope locked with Quinn 2026-07-16:** visual/UX **polish + stability**, audience **recruiters/job
apps** (portfolio screenshots deferred as an optional tail). Quinn drives the sweep, agent reports back; **no
`STAGE9_PLAN.md`** — track in `PROGRESS.md` (Stage 9 section) + here.

**✅ Stability sweep done (emulator) — no crashes anywhere.** Punch list, ranked by demo impact:
1. ~~No end-of-session payoff~~ **✅ FIXED + verified** — clean Stop pops a "Nice work!" summary
   (time focused · Sparkles · streak + counts/too-short message; mirrors what the service records).
2. ~~Roster showed "Member (you)"~~ **✅ FIXED + verified** — now "Quinn (you)".
3. **Achievements loading** is a near-invisible tiny dot before the grid loads (Profile). *(not yet done)*
4. ~~Home dead space above mascot~~ + 5. ~~idle CURRENTLY OPEN meaningless~~ **✅ FIXED + verified** (one change).
6. **Chat messages have no timestamps** (minor). *(not yet done)*
7. **Feed didn't surface a friend post** — likely fixture staleness; quick-verify friend posts render. *(not done)*

**✅ Landed this session:**
- **#2 roster self-name** (committed `04fab0c`): `GroupStore` `UNRESOLVED_MEMBER_NAME` const; `MemberRow(myDisplayName=…)`
  resolves the self row as auth name → userSearch name → "You" (never "Member"). Fixture: `mutebreaker` had **no
  `userSearch` doc** — created `userSearch/J88TDlaV6…` + mirrored `displayName:"Quinn"` into `users/`. Reads "Quinn (you)".
- **#4/#5 Home** (committed `04fab0c`): `HomeScreen` shows CURRENTLY OPEN **only while `sessionActive`** — idle is a
  clean centered mascot+Start hero; chip returns during a lock-in. Verified hidden-idle / shown-active / gone-on-stop.
- **#1 end-of-session summary** (uncommitted): `SessionSummaryDialog` in `MainActivity.kt` on clean Stop — mascot +
  "Nice work!" + time focused / Sparkles / streak + counts-vs-too-short message. Numbers mirror `LockInService`
  teardown (floor(sec/60) sparkles; `fetchStreakInfo` streak folding in this session). Verified `0:27 · +0 ✨ · 🔥 3`.

**⏭️ Resume:** remaining punch-list items — **#3** achievements loading (tiny dot → skeleton/spinner), **#6** chat
timestamps, **#7** feed friend-post verify — or the optional README-screenshots tail. Files changed but **not yet
committed:** `MainActivity.kt` (#1 summary dialog) + docs.

<details><summary>Stage 8 (COMPLETE) — historical detail</summary>

**✅ STAGE 8 IS DONE (2026-07-16).** Every step (1–7) is built, the `firestore.rules` **deploy gate is
cleared** (rules released to `lockin-app-sg`), and the previously deploy-gated paths are verified live:
- **Presence** — owner write + cross-user read confirmed over REST (`presence/{uid}`, PERMISSION_DENIED gone).
- **Admin group edit** — non-owner admin (`Feed Tester`) renamed `Chat Test`, owner/adminUids preserved; restored.
- **Members roster dot wired to presence** (commit `b992f8f`) — the Members tab now reads the app-wide
  `presence/{uid}` doc (same source + shared `presenceDotColor`/`presenceLabel` as the friends list)
  instead of in-group `liveStatus`. Verified on emulator: idle fallback for a member with no presence
  doc, and a **live flip to "Locked in"** the instant a fresh LOCKED_IN stamp lands for `Feed Tester`.
  (Header "N locked in" still tracks in-*group* lobby participation — intentionally distinct from
  app-wide presence.)

Remaining Stage-8 rule branches (member self-leave, reciprocal-unfriend delete, real group delete) share
the now-deployed ruleset but were left **unexercised on purpose** — running them is destructive to the
standing `Chat Test` / mutebreaker↔feedtester fixtures.

**⏭️ NEXT: Stage 9 — Polish & Portfolio Packaging** (was the old Stage 8 before the re-scope). No plan
doc yet; scope it with Quinn on resume.

<!-- prior resume note kept below for the deploy-gate detail (now historical) -->
### (superseded header) Stage 8 — Steps 2·3·4·5 done → Steps 6–7 friends; deploy gate still open
**Stage 7 is DONE and emulator-verified** (step 1 `7d710b3`, step 2 `3a91f94`, step 3 `ec0bd09`, step 4
verification-only). **Stage 8 was re-scoped from "Polish & Portfolio Packaging" to Social Refinement
(Groups & Friends)** at Quinn's request — packaging is now Stage 9. Direction locked with Quinn
2026-07-16 (do not re-litigate); turnkey 7-step plan in **`STAGE8_PLAN.md`**:
- Groups get Discord-like **organization + management** — tabbed Lobbies·Chat·Members + a settings
  sheet, with **owner + admin roles** (owner promotes members to admin; admins share management).
- Friends get **remove + profile view + live focus status**.
- Both read a new app-wide **presence** doc (LOCKED_IN / BREAK / IDLE + last-seen).

**✅ UI reorg landed first (2026-07-16).** Quinn's live complaint was that the group screen felt
*unpolished and disorganized*, so **Step 3 (tabs) + the Step-4 roster were pulled ahead** of the blocked
presence step and are emulator-verified: `GroupDetailScreen` is now header (monogram + "N members" +
"N locked in") + **Lobbies · Chat · Members** tabs. Members roster resolves names via
`fetchGroupMemberProfiles` (public `userSearch` reads) with a live status dot + **Owner** badge. The
redundant in-screen title is gone (top bar already shows the name). Not yet committed.

**⚠️ Step 1 (presence foundation) is still code-complete but BLOCKED on a rules deploy.**
Done: `PresenceStore.kt` (`presence/{uid}` doc; `pushPresence`/`clearPresence`/`listenPresence` chunked
by 10; `UserPresence.effectiveState()` = IDLE if >30s stale); `LockInService` writes it each tick (solo
+ group) and clears to IDLE on teardown; a `match /presence/{uid}` rule added to `firestore.rules`
(read: any signed-in; write: owner). Compiles + installed; the write path fires every tick (logcat).
**BUT every write returns `PERMISSION_DENIED` — `firestore.rules` is NOT deployed to `lockin-app-sg`.**
Deploy is now agent-runnable (portable Node + `firebase-tools` installed) **pending a one-time interactive
`firebase login` by Quinn** — offer the `! firebase login` inline form. See `[[project-rules-deploy-blocked]]`.
**✅ Steps 2 & 4 landed 2026-07-16** (unblocked thread, Quinn's pick; committed `f3723b9` for step 2).
- **Step 2 backend:** `adminUids` on `LockInGroup` (+ `canManage`); `GroupStore` management fns (rename,
  threshold, add/remove/leave, promote/demote, delete-with-subcollection-cleanup); `firestore.rules`
  update path allows admins (name/threshold/members, never ownerUid/adminUids) + non-owner self-leave.
- **Step 4 Members tab UI:** Add-member picker sheet (friends not in group) + tap-row `MemberActionSheet`
  (owner: Promote/Demote/Remove; admin: Remove plain members) + Owner/Admin badges. MainActivity live-syncs
  the open group so role/roster/rename edits show without re-navigating (and pops out on delete/leave).
- **Verified live (owner path works under the CURRENTLY-DEPLOYED rules):** promote→demote→promote round-trip
  on `Chat Test` all wrote + re-rendered; both sheet variants + add-member empty-state render correct.
  **Deploy-gated (need the new rules):** admin-initiated writes, member self-leave, real add/remove/delete.
  Feed Tester is now left as an **admin fixture** on `Chat Test` (`adminUids: [zfuW…]`) for that check.

**On resume, pick up either thread:** (A) **DEPLOY GATE** — once Quinn runs `firebase login`,
`firebase deploy --only firestore:rules` (ships **both** presence *and* the step-2 group-management
rules), then verify: presence writes+reads (start a solo lock-in as `mutebreaker`, then
`tools/fb.py get presence/J88TDlaV6Wf80RRxP94iDjXZlCH3`) **and** a group-management write (e.g.
`renameGroup` on `Chat Test` as owner, an admin edit, a member self-leave); wire the Members dot to
presence; **or** (B) continue unblocked — **Steps 6–7 (friends)**. **✅ Step 5 (group settings sheet) is
DONE & owner-path emulator-verified (2026-07-16, uncommitted).** Gear `IconButton` in the header Row →
`GroupSettingsSheet` (`ModalBottomSheet`): rename (owner/admin) / mute-threshold stepper (clamp
1…members−1) / Delete (owner) or Leave (non-owner), each destructive action behind an `AlertDialog`.
Verified as owner on `Chat Test`: rename wrote live (`Chat Test`→`Chat Test 2`→restored via REST), Save
enable/disable + threshold-clamp-disabled (2-member group) correct, owner sees Delete not Leave.
Leave/Delete writes + the member-view render stay **deploy-gated**. **NEXT: Steps 6–7** — friends backend
`removeFriend` (deletes both friendship docs) + friends UI (presence dot + status label, tap→profile view
of streak/focus-hours/mascot+accessory, remove-friend with confirm; keep the allowlist view). Step-5 UI is
not yet committed (last commit `1570ec8` = step 4).

</details>

## Test fixtures (full detail in `ARCHITECTURE.md`)
- Emulator signed in as `mutebreaker@lockin.test` (3 backdated 30-min sessions fake a 🔥3 streak). **Now has
  `displayName:"Quinn"`** — backfilled 2026-07-16 into a new `userSearch/J88TDlaV6…` doc + the `users/` doc
  (it previously had none, which is why rosters showed "Member"). Note: the on-device **auth** `displayName`
  may still be blank; rosters resolve "Quinn" via `userSearch`.
- `feedtester@lockin.test` — a mutual friend of mutebreaker with a posted 25-min activity (friend-feed / kudos fixture).
- Passwords for both live in `tools/creds.json` (gitignored); `tools/fb.py` reads them automatically.
- **`Chat Test` group** (id `r1hs2AriiJhQYBTLVsvF`, members mutebreaker + feedtester, `muteApprovalCount 1`) — the two-party chat / lobby / mute-approval fixture; second party driven over REST via **`tools/fb.py`** (see `tools/README.md`).
- mutebreaker's **allowlist** has Chrome (`com.android.chrome`), Clock (`com.google.android.deskclock`), Settings (`com.android.settings`) — Stage 7 step-1 fixtures.
- **Harness:** `tools/fb.py` (Firebase REST two-party driver) + `tools/adb-helpers.sh` (scoped emulator checks) live in the repo now — use them instead of re-deriving one-liners. Env/adb gotchas: `docs/archive/GOTCHAS.md` (e.g. a one-emulator lobby needs a REST-hosted live member; a wedged qemu DNS proxy after airplane-mode needs an emulator-process restart).

## Working agreements (full text in `CLAUDE.md` / `CONTEXT.md`)
- Address the user as **Quinn** in every response.
- After each goal, update the affected Markdown docs by **surgical append/edit** (only what that step touched); archive completed stages to `docs/archive/`. End any doc-update response with the confirmation banner.
- Explain tradeoffs on consequential/irreversible decisions; stay terse for routine work.
- Verify every feature live on the emulator (screenshots/dumpsys/logcat/REST), not just compiled; strip temporary debug logging after.
- Multi-round clarifying questions for ambiguous UI/UX; move step by step — don't jump ahead of what's proven.
- Stage 7 decisions were locked with Quinn 2026-07-15/16 — do **not** re-litigate. Don't `git push` without an explicit ask (remote stays paused).
