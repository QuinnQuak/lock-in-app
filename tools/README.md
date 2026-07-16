# tools/ — verification harness

Reusable tooling for verifying Lock-In on the emulator, kept in the repo so it survives session
resets (it used to be rebuilt from scratch each session). Nothing here ships in the app.

## `fb.py` — Firebase Auth + Firestore REST helper
Drives the **second party** in two-party flows (friend requests, kudos, group mute-approval) that a
single signed-in emulator can't exercise. Reads the web API key + project id from
`app/google-services.json` at runtime — no secrets committed.

```bash
python tools/fb.py whoami --as mutebreaker@lockin.test
python tools/fb.py get    users/<uid> --as mutebreaker@lockin.test
python tools/fb.py patch  users/<uid> --as mutebreaker@lockin.test --set sparkles=88 streakMinMinutes=30
python tools/fb.py delete groups/<gid>/lobbies/<lid> --as feedtester@lockin.test
python tools/fb.py query  users/<uid>/activity --as mutebreaker@lockin.test --order-by endedAt --desc --limit 5
```
- **`patch` is surgical by default** — only the fields named in `--set` are written, the rest of the
  doc is untouched (a maskless PATCH once wiped a `users/` doc to a single field). Override the mask
  with `--mask a b c` if you ever need to.
- `--set` infers types: `sparkles=88` → int, `flag=true` → bool, `x=null` → null. Force a string with
  quotes: `--set 'name="42"'`.
- Passwords resolve from `tools/creds.json` (gitignored) → `--password` → `FB_PASSWORD` env var.

## `adb-helpers.sh` — scoped emulator checks
Targeted wrappers so verification produces small output instead of full `logcat`/`dumpsys` dumps.
```bash
source tools/adb-helpers.sh
li_shot home        # screenshot -> ./home.png (screencap + pull, redirect-safe)
li_audio            # is the alarm sounding? (USAGE_ALARM MediaPlayer state)
li_prefs            # session prefs: active / heartbeat / group_id / lobby_id
li_log LockInService  # tail app logcat filtered to a tag
li_revoke_usage     # appops revoke Usage Access (simulate mid-session revocation)
li_grant_usage      # re-grant
li_fg               # current foreground activity/package
```
Override `ADB`/`PKG`/`OUT_DIR` via env if paths differ.

## `creds.json` (gitignored — create locally)
Test-account passwords for `fb.py`. Emulator-only throwaways. Shape:
```json
{ "mutebreaker@lockin.test": "<password>", "feedtester@lockin.test": "<password>" }
```

## Standing fixtures (detail in `ARCHITECTURE.md`)
- `mutebreaker@lockin.test` — the emulator's signed-in account (backdated sessions fake a 🔥3 streak).
- `feedtester@lockin.test` — a mutual friend of mutebreaker (friend-feed / kudos fixture).
- **`Chat Test` group** `r1hs2AriiJhQYBTLVsvF` (members mutebreaker + feedtester, `muteApprovalCount 1`)
  — the two-party chat / lobby / mute-approval fixture. A one-emulator lobby needs a REST-hosted live
  member to survive dead-lobby cleanup: have `fb.py` write the lobby doc **and** that party's tagged
  `liveStatus`, then join in-app.
