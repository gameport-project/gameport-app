#!/usr/bin/env bash
# Dumps the headset's recent logcat to a file (default: ./logcat.txt at the repo root, git-ignored)
# so it can be read without pasting. Usage: scripts/capture_logs.sh [output-file]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
out="${1:-$root/logcat.txt}"

adb logcat -d -v time > "$out"
echo "Wrote $(wc -l < "$out") lines to $out"
