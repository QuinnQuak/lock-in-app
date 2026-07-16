# Stage 8 — Social Refinement (Groups & Friends)

> Active-stage spec. Inserted ahead of portfolio packaging (now **Stage 9**) at Quinn's request:
> the group + friends features feel incomplete and under-organized. Goal — make groups feel
> Discord-like in **organization + management**, and give friends real presence + management.
> Read alongside `NEXT_SESSION.md` (resume point), `PROGRESS.md` (log), `ARCHITECTURE.md` (how).

## Direction locked with Quinn (2026-07-16)
- **Groups: management + polish first** — keep the single chat, but reorganize `GroupDetail` into
  tabs (Lobbies · Chat · Members) with a real header, and add group settings. **Owner + admin roles**:
  the owner can promote members to admin; admins share management rights (rename, threshold, add/remove
  members). Only the owner deletes. Any member can leave; the owner must transfer or delete first.
- **Friends: remove + profile view + focus status** — remove a friend (both sides), tap a friend to
  see their stats (streak / focus hours / mascot+accessory), and show live focus status.
- **Presence: per-user `presence/{uid}` doc** — state (LOCKED_IN / BREAK / IDLE) + displayName +
  last-seen, written by `LockInService`, read by both Friends and group headers. Stale (>30s) ⇒ idle.

## Steps (build → emulator-verify → surgical doc update, in order)
1. **Presence foundation** — `PresenceStore.kt`: `presence/{uid}` doc, `pushPresence`/`clearPresence`
   (IDLE), `listenPresence(uids)`. `LockInService` writes it each 1s tick (COMPLIANT→LOCKED_IN,
   BREAK→BREAK) beside the heartbeat, and clears to IDLE on teardown. Verify via REST/logcat.
2. ✅ **Group model + roles + management backend** — DONE (code-complete; write paths deploy-blocked
   like step 1). `adminUids: List<String>` added to `LockInGroup` (+ listener read + `canManage(uid)`
   helper = owner ∪ admins). `GroupStore`: `renameGroup`, `setMuteThreshold`, `addMembers` (arrayUnion),
   `removeMember` / `leaveGroup` (arrayRemove from memberUids **and** adminUids), `promoteAdmin` /
   `demoteAdmin` (arrayUnion/Remove on adminUids), `deleteGroup` (batch-cleans lobbies · messages ·
   liveStatus · muteRequests · muteApprovals, then the parent). No UI yet. **`firestore.rules` updated:**
   owner = full control; admin may edit name/threshold/memberUids but never ownerUid/adminUids;
   non-owner member may only self-remove (leave). ⚠️ **Rules NOT deployed** — verify these + presence
   together after Quinn's one-time `firebase login`.
3. ✅ **`GroupDetail` reorg into tabs** — DONE, emulator-verified (reordered ahead of the blocked
   presence step to fix Quinn's "disorganized" complaint first). Lobbies · Chat · Members tabs; header =
   monogram + "N members" + "N locked in" (live count from `memberStatuses`; swaps to presence later).
   Existing lobby + chat blocks moved under their tabs unchanged. Redundant in-screen title dropped (the
   top bar already carries the group name).
4. ✅ **Members tab** — DONE (owner-path emulator-verified; admin-path deploy-gated). Roster names via
   `fetchGroupMemberProfiles`; live status dot + **Owner**/**Admin** role badges. **Add member** row
   (owner/admin only) → `AddMemberSheet` picker of friends not already in the group (empty-state when none).
   Tap an actionable member → `MemberActionSheet` (ModalBottomSheet): owner sees Promote/Demote + Remove;
   admin sees Remove for plain members only; owner + self rows aren't tappable (self-leave is step 5).
   MainActivity now live-syncs the open `selectedGroup` off `listenMyGroups` (roster/role/rename edits
   reflect immediately; pops to the list on delete/leave). *Still TODO:* swap the idle dot to true
   presence (step 1, deploy-gated).
5. **Settings sheet** — rename, mute-threshold stepper, leave (member/admin), delete (owner-only),
   with confirms on destructive actions.
6. **Friends backend** — `removeFriend` (deletes both friendship docs).
7. **Friends UI** — presence dot + status label per friend, tap → profile view (streak / focus hours /
   mascot+accessory), remove-friend with confirm. Keep the allowlist view.

## Residual / out of scope (state honestly later)
- No multi-channel per group (single chat kept); no server rail. Deferred unless Quinn revisits.
- Presence is best-effort (service-driven, last-seen staleness); no dedicated realtime presence infra.
