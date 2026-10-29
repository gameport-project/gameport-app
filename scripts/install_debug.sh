#!/usr/bin/env bash
# Asks a test build of GamePort on the device what it knows about its installs, and drives them.
# A test build only: the released app has no such receiver. See docs/DEVELOPMENT.md.
#
# Usage: scripts/install_debug.sh <state|perm|log|files|install|cancel|discard> [steam-app-id]
set -euo pipefail

cmd="${1:-state}"
app_id="${2:-0}"
receiver="app.gameport.dev/app.gameport.debug.DebugBridgeReceiver"

# The answer goes to logcat, so the marker tells this run's lines from an earlier one's.
adb logcat -c 2>/dev/null || true
adb shell am broadcast -n "$receiver" --es cmd "$cmd" --ei appId "$app_id" > /dev/null
# The app answers on its own thread; a moment is enough for anything but a library that is still loading.
sleep 2
adb logcat -d -s GamePortDebug -v raw
