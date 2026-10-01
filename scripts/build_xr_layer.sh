#!/usr/bin/env bash
# Compiles GamePort's OpenXR layer with the Android NDK and stages it as an asset of the app, from
# where the patcher injects it into games. The OpenXR headers (Apache-2.0) are fetched once from
# Khronos into a cache folder and are not stored in this repository.
#
# Usage: ANDROID_NDK=<ndk> scripts/build_xr_layer.sh [--syntax-only]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
ndk="${ANDROID_NDK:?set ANDROID_NDK to an Android NDK folder}"
version="release-1.1.63"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/gameport/openxr-$version"
source_file="$root/xrlayer/gameport_xr_layer.cpp"
out="$root/core/patch/src/main/assets/xrlayer/arm64-v8a"
clang="$(ls -d "$ndk"/toolchains/llvm/prebuilt/*/bin | head -1)/aarch64-linux-android29-clang++"

if [[ ! -d "$cache/include" ]]; then
  mkdir -p "$cache"
  curl -sL "https://github.com/KhronosGroup/OpenXR-SDK/archive/refs/tags/$version.tar.gz" | tar xz -C "$cache" --strip-components=1
fi

flags=(-std=c++17 -fPIC -O2 -fvisibility=hidden -I"$cache/include" -Wall -Wno-unused-function)
if [[ "${1:-}" == "--syntax-only" ]]; then
  "$clang" "${flags[@]}" -fsyntax-only "$source_file"
  echo "syntax OK"
  exit 0
fi

mkdir -p "$out"
"$clang" "${flags[@]}" -shared -static-libstdc++ -Wl,--no-undefined -Wl,-z,max-page-size=16384 \
  -o "$out/libXrApiLayer_gameport.so" "$source_file" -llog
"$(dirname "$clang")/llvm-strip" --strip-unneeded "$out/libXrApiLayer_gameport.so"
ls -la "$out/libXrApiLayer_gameport.so"
