#!/usr/bin/env bash
# Keeps the patch generation honest: it must be raised whenever the binaries injected into games
# (Steamworks shim, in-game hook, OpenXR layer) changed since the previous release.
#
# `core/patch/patch-generation.lock` records, for the last release, the generation and the checksum of
# each injected binary. Without that file (before the first release) the check passes.
#
# Usage: scripts/check_patch_generation.sh            check (what CI runs on a release tag)
#        scripts/check_patch_generation.sh --update   record the current state (when preparing a release)
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
lock="$root/core/patch/patch-generation.lock"
assets="$root/core/patch/src/main/assets"
generation="$(sed -n 's/.*const val GENERATION = \([0-9]*\).*/\1/p' "$root/core/patch/src/main/kotlin/app/gameport/core/patch/PatchVersioning.kt")"
[[ -n "$generation" ]] || { echo "could not read PatchVersioning.GENERATION" >&2; exit 1; }

sums() {
  (cd "$assets" && for f in shim/arm64-v8a/libsteamclient.so hook/classes.dex xrlayer/arm64-v8a/libXrApiLayer_gameport.so xrloader/arm64-v8a/libopenxr_loader.so; do
    printf '%s  %s\n' "$(shasum -a 256 "$f" | cut -d' ' -f1)" "$f"
  done)
}

if [[ "${1:-}" == "--update" ]]; then
  { echo "generation=$generation"; sums; } > "$lock"
  echo "recorded generation $generation in ${lock#$root/}"
  exit 0
fi

if [[ ! -f "$lock" ]]; then
  echo "no previous release recorded: nothing to compare"
  exit 0
fi

recorded_generation="$(sed -n 's/^generation=//p' "$lock")"
if [[ "$(sums)" == "$(sed 1d "$lock")" ]]; then
  echo "injected binaries unchanged since generation $recorded_generation"
elif [[ "$generation" -gt "$recorded_generation" ]]; then
  echo "injected binaries changed and the generation was raised ($recorded_generation -> $generation): run --update to record it"
else
  echo "the injected binaries changed since generation $recorded_generation but PatchVersioning.GENERATION was not raised" >&2
  exit 1
fi
