#!/usr/bin/env bash
# Fails if a binary shipped in the app's assets still carries a home-folder path (see sanitize_paths.sh).
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
found=0
while IFS= read -r file; do
  hits="$(strings -a "$file" | grep -E '/(Users|home)/[A-Za-z0-9._-]+/' | grep -v -E '/(Users|home)/_+/' | head -3 || true)"
  if [[ -n "$hits" ]]; then
    echo "home-folder path in ${file#$root/}:" >&2
    echo "$hits" >&2
    found=1
  fi
done < <(find "$root/core/patch/src/main/assets" -type f \( -name '*.so' -o -name '*.dex' \))
exit $found
