#!/usr/bin/env bash
# Builds and runs the host tests of the OpenXR layer's pure logic (xrlayer/controller_allocation.h). Needs a C++17 compiler; no Android NDK.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
"${CXX:-c++}" -std=c++17 -Wall -Wextra -o "$out/controller_allocation_test" "$root/xrlayer/test/controller_allocation_test.cpp"
"$out/controller_allocation_test"

# The way the layer gives a game's bindings to the runtime, tried against a fake runtime. Needs the OpenXR headers (build_xr_layer.sh fetches them once); skipped without.
headers="${XDG_CACHE_HOME:-$HOME/.cache}/gameport/openxr-release-1.1.63/include"
if [[ -d "$headers" ]]; then
  "${CXX:-c++}" -std=c++17 -Wall -Wno-unused-function -Wno-unused-variable -I "$root/xrlayer/test/stubs" -I "$headers" -o "$out/suggest_tolerant_test" "$root/xrlayer/test/suggest_tolerant_test.cpp"
  "$out/suggest_tolerant_test"
else
  echo "suggest_tolerant_test skipped: the OpenXR headers are not in $headers (run scripts/build_xr_layer.sh --syntax-only once)"
fi
