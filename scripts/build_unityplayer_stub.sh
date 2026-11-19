#!/usr/bin/env bash
# Compiles the Unity player stand-in (core/patch/src/dexstub) to a dex and prints it in base64, the form in which UnrealUnityPlayerStubPatch holds it.
#
# Usage: ANDROID_HOME=<sdk> scripts/build_unityplayer_stub.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
sdk="${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
platform="$(ls -d "$sdk"/platforms/android-* | sort -V | tail -1)"
tools="$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)"
work="$(mktemp -d)"

javac -source 8 -target 8 -classpath "$platform/android.jar" -d "$work/classes" $(find "$root/core/patch/src/dexstub" -name '*.java') >&2 2>&1 || true
"$tools/d8" --min-api 29 --lib "$platform/android.jar" --output "$work" $(find "$work/classes" -name '*.class') >&2
echo "bytes: $(wc -c < "$work/classes.dex")" >&2
base64 < "$work/classes.dex" | tr -d '\n'
echo
