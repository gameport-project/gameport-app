#!/usr/bin/env bash
# Compiles the in-game hook (hook/src) to a dex and stages it as an asset of the app, from
# where the patcher injects it into games. Needs an Android SDK with a platform and build-tools.
#
# Usage: ANDROID_HOME=<sdk> scripts/build_hook.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
sdk="${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
platform="$(ls -d "$sdk"/platforms/android-* | sort -V | tail -1)"
tools="$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)"
out="$root/core/patch/src/main/assets/hook"
work="$(mktemp -d)"

mkdir -p "$out"
# Tests (src/test) are not part of the dex; main and android sources are.
javac -source 8 -target 8 -classpath "$platform/android.jar" -d "$work/classes" \
  $(find "$root/hook/src/main" "$root/hook/src/android" -name '*.java') 2>&1 | grep -v "warning: \[options\]\|^1 warning" || true
[[ -d "$work/classes" ]] || { echo "the hook did not compile" >&2; exit 1; }
"$tools/d8" --min-api 29 --lib "$platform/android.jar" --output "$work" $(find "$work/classes" -name '*.class')
cp "$work/classes.dex" "$out/classes.dex"
ls -la "$out/classes.dex"
