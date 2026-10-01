#!/usr/bin/env bash
# Replaces the home-folder part of build paths left inside compiled files (assertion messages carry the
# path of the source file they were built from) by underscores of the same length, so a binary made on
# someone's machine does not carry their user name. The binary stays valid: only text changes.
#
# Usage: scripts/sanitize_paths.sh <file>...
set -euo pipefail
for file in "$@"; do
  perl -0777 -pi -e 's{/(?:Users|home)/[A-Za-z0-9._-]+/}{ "/" . ("_" x (length($&) - 2)) . "/" }ge' "$file"
done
