#!/usr/bin/env bash
# adb-helpers.sh — scoped emulator helpers for Lock-In verification.
#
# Why: raw `logcat`/`dumpsys`/`screencap` dump huge output into the session. These wrappers
# produce TARGETED output (faster to run, a fraction of the tokens) and bake in the emulator
# gotchas learned the hard way (see ARCHITECTURE.md / docs/archive/GOTCHAS.md):
#   - device paths need MSYS_NO_PATHCONV=1 (Git Bash rewrites /sdcard/... otherwise)
#   - screencap-to-file + pull, never `exec-out screencap -p > file` (PowerShell/redirect corrupts it)
#   - Usage Access revoke/grant is `appops set ... android:get_usage_stats ignore|allow`
#
# Usage:  source tools/adb-helpers.sh   then call the functions below.
#   li_shot [name]         -> screenshot to scratchpad (or ./ ) as <name>.png
#   li_audio               -> is the alarm sounding? (USAGE_ALARM MediaPlayer state)
#   li_prefs               -> dump the session SharedPreferences (active/heartbeat/group/lobby)
#   li_log [tag]           -> tail app logcat, optionally filtered to a tag
#   li_revoke_usage        -> revoke Usage Access (simulate mid-session revocation)
#   li_grant_usage         -> re-grant Usage Access
#   li_fg                  -> current foreground activity/package

export MSYS_NO_PATHCONV=1
ADB="${ADB:-C:/Users/quakj/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
PKG="${PKG:-com.example.lockin}"
OUT_DIR="${OUT_DIR:-.}"

li_shot() {
  local name="${1:-shot}"
  "$ADB" shell screencap -p /sdcard/_lishot.png
  "$ADB" pull /sdcard/_lishot.png "$OUT_DIR/$name.png" >/dev/null
  "$ADB" shell rm /sdcard/_lishot.png
  echo "$OUT_DIR/$name.png"
}

li_audio() {
  # Non-empty output => the alarm is actively playing.
  "$ADB" shell dumpsys audio | grep -iE "USAGE_ALARM|MediaPlayer.*state:started" || echo "(no alarm playing)"
}

li_prefs() {
  # Session prefs written by LockInSessionStore. Adjust the file name if it changes.
  "$ADB" shell run-as "$PKG" cat "/data/data/$PKG/shared_prefs/lockin_session_prefs.xml" 2>/dev/null \
    || echo "(session prefs not found — check SESSION_PREFS_NAME in LockInSessionStore.kt)"
}

li_log() {
  local tag="$1"
  "$ADB" logcat -d -t 200 | grep -i "${tag:-lockin}"
}

li_revoke_usage() {
  "$ADB" shell appops set "$PKG" android:get_usage_stats ignore && echo "Usage Access REVOKED for $PKG"
}

li_grant_usage() {
  "$ADB" shell appops set "$PKG" android:get_usage_stats allow && echo "Usage Access GRANTED for $PKG"
}

li_fg() {
  "$ADB" shell dumpsys activity activities | grep -iE "mResumedActivity|topResumedActivity"
}
