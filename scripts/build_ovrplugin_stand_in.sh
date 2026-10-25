#!/usr/bin/env bash
# Compiles the stand-in for Meta's OVRPlugin (see ovrplugin/ovrplugin_stand_in.c) with the Android NDK and stages it as an asset of the app, from where the patcher
# injects it into the games that need it.
#
# Usage: ANDROID_NDK=<ndk> scripts/build_ovrplugin_stand_in.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
ndk="${ANDROID_NDK:?set ANDROID_NDK to an Android NDK folder}"
source_file="$root/ovrplugin/ovrplugin_stand_in.c"
out="$root/core/patch/src/main/assets/ovrplugin/arm64-v8a"
bin="$(ls -d "$ndk"/toolchains/llvm/prebuilt/*/bin | head -1)"

mkdir -p "$out"
"$bin/aarch64-linux-android29-clang" -shared -fPIC -O2 -Wl,-z,max-page-size=16384 -o "$out/libOVRPlugin.so" "$source_file"
"$bin/llvm-strip" --strip-unneeded "$out/libOVRPlugin.so"
"$bin/llvm-nm" -D --defined-only "$out/libOVRPlugin.so"
ls -la "$out/libOVRPlugin.so"
