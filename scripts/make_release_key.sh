#!/usr/bin/env bash
# Creates, once, the key that signs GamePort releases, with a random password, and prepares the four values
# the release workflow reads from the repository secrets. Run it on your own machine: the key and its
# password stay in the folder it creates, never in the project and never printed.
#
# Usage: scripts/make_release_key.sh [folder]      (default folder: ~/gameport-signing)
set -euo pipefail

dir="${1:-$HOME/gameport-signing}"
keystore="$dir/gameport-release.jks"
[[ -e "$keystore" ]] && { echo "There is already a key in $dir: it is left as it is." >&2; exit 1; }
command -v openssl >/dev/null || { echo "openssl is needed." >&2; exit 1; }
command -v keytool >/dev/null || { echo "keytool (from a JDK) is needed; set JAVA_HOME or put it on the PATH." >&2; exit 1; }

mkdir -p "$dir" && chmod 700 "$dir"
# 48 random hexadecimal characters.
password="$(openssl rand -hex 24)"
# Only a project name is written into the certificate, which everyone can read inside the APK.
keytool -genkeypair -keystore "$keystore" -alias gameport -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$password" -keypass "$password" -dname "CN=GamePort, O=GamePort Project" >/dev/null 2>&1

base64 < "$keystore" | tr -d '\n' > "$dir/KEYSTORE_BASE64.txt"
printf '%s' "$password" > "$dir/KEYSTORE_PASSWORD.txt"
printf '%s' "$password" > "$dir/KEY_PASSWORD.txt"
printf '%s' "gameport" > "$dir/KEY_ALIAS.txt"
chmod 600 "$dir"/*

echo "Key created in $dir"
echo
echo "Create these four secrets in the repository (Settings > Secrets and variables > Actions),"
echo "each one with the content of the file of the same name:"
echo "  KEYSTORE_BASE64    <- $dir/KEYSTORE_BASE64.txt"
echo "  KEYSTORE_PASSWORD  <- $dir/KEYSTORE_PASSWORD.txt"
echo "  KEY_ALIAS          <- $dir/KEY_ALIAS.txt"
echo "  KEY_PASSWORD       <- $dir/KEY_PASSWORD.txt"
echo
echo "Keep the folder (a password manager, a backup): without the key, installed copies cannot be updated."
echo
echo "The key's public fingerprint, to show in the README so people can check the APK:"
keytool -list -keystore "$keystore" -storepass "$password" 2>/dev/null | grep -i -E "fingerprint|empreinte|SHA" || true
