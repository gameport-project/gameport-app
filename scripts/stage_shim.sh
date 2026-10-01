#!/usr/bin/env bash
# Copies the compiled Steamworks shim into the GamePort app as a packaged asset, stripped of debug
# symbols. GamePort injects this file into the games it patches; it never runs inside GamePort itself.
# Build the shim first with ../gameport-steamworks-shim/android/build_shim.sh when its sources changed.
#
# Usage: ANDROID_NDK=<ndk> scripts/stage_shim.sh [path/to/libsteamclient.so]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
source_lib="${1:-/tmp/gp-shim-build/lib/libsteamclient.so}"
target_dir="$root/core/patch/src/main/assets/shim/arm64-v8a"
ndk="${ANDROID_NDK:?set ANDROID_NDK to an Android NDK folder}"
strip_tool="$(ls -d "$ndk"/toolchains/llvm/prebuilt/*/bin | head -1)/llvm-strip"

[[ -f "$source_lib" ]] || { echo "shim not found: $source_lib (build it first)" >&2; exit 1; }

mkdir -p "$target_dir"
cp "$source_lib" "$target_dir/libsteamclient.so"
"$strip_tool" --strip-unneeded "$target_dir/libsteamclient.so"
# Build paths left in the binary (assertion messages) would carry the builder's user name.
"$(dirname "$0")/sanitize_paths.sh" "$target_dir/libsteamclient.so"
ls -la "$target_dir/libsteamclient.so"
