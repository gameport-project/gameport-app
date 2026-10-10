#!/usr/bin/env bash
# Builds and runs the host tests of the OpenXR layer's pure logic (xrlayer/controller_allocation.h). Needs a C++17 compiler; no Android NDK.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
"${CXX:-c++}" -std=c++17 -Wall -Wextra -o "$out/controller_allocation_test" "$root/xrlayer/test/controller_allocation_test.cpp"
"$out/controller_allocation_test"
