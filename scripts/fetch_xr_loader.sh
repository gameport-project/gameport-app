#!/usr/bin/env bash
# Stages Khronos' own OpenXR loader (Apache-2.0 or MIT) as an asset of the app. The patcher puts it in a game
# whose own loader is too old to find the device's runtime (see XrLoaderPatch). The library comes from Khronos'
# release on Maven Central and its checksum is checked against the one Maven publishes.
#
# Usage: scripts/fetch_xr_loader.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
version="1.1.63"
base="https://repo1.maven.org/maven2/org/khronos/openxr/openxr_loader_for_android/$version/openxr_loader_for_android-$version.aar"
out="$root/core/patch/src/main/assets/xrloader/arm64-v8a"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

curl -sSf -o "$work/loader.aar" "$base"
expected="$(curl -sSf "$base.sha1")"
actual="$(shasum "$work/loader.aar" | cut -d' ' -f1)"
[[ "$expected" == "$actual" ]] || { echo "checksum mismatch: $actual, expected $expected" >&2; exit 1; }

mkdir -p "$out"
unzip -p "$work/loader.aar" jni/arm64-v8a/libopenxr_loader.so > "$out/libopenxr_loader.so"
echo "staged OpenXR loader $version ($(wc -c < "$out/libopenxr_loader.so" | tr -d ' ') bytes)"
