#!/bin/sh
# Fetch the torrent engine OpenTV bundles (TorrServer, GPL-3.0) into jniLibs.
#
# The binary is 64 MB and deliberately not committed to git; both CI workflows run this
# script before assembling, and a fresh clone runs it once. It is idempotent: when the
# file is already present and matches the pin below, it does nothing.
#
# Upgrading the engine: change ENGINE_VERSION and ENGINE_SHA256 here *and* the table in
# docs/TORRENTS.md, then delete the file so this fetches the new one. The SHA-256 is the
# upstream release asset byte for byte; a mismatch refuses the build rather than using
# an unverified binary.

set -eu

ENGINE_VERSION="MatriX.147"
ENGINE_ASSET="TorrServer-android-arm7"
ENGINE_SHA256="8cb44454ff201bd902a9c454e6f046d4fb25937a3b628270d0c7b48f1cc7b94c"
DEST="app/src/main/jniLibs/armeabi-v7a/libtorrserver.so"
URL="https://github.com/YouROK/TorrServer/releases/download/${ENGINE_VERSION}/${ENGINE_ASSET}"

if [ -f "$DEST" ] && echo "$ENGINE_SHA256  $DEST" | sha256sum -c --status - 2>/dev/null; then
  echo "TorrServer $ENGINE_VERSION already present at $DEST (SHA-256 verified)."
  exit 0
fi

mkdir -p "$(dirname "$DEST")"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

echo "Fetching TorrServer $ENGINE_VERSION from $URL"
curl -fsSL --retry 3 -o "$TMP" "$URL"

if ! echo "$ENGINE_SHA256  $TMP" | sha256sum -c --status - 2>/dev/null; then
  echo "error: SHA-256 mismatch for the downloaded engine; refusing to use it" >&2
  exit 1
fi

mv "$TMP" "$DEST"
echo "Fetched into $DEST (SHA-256 verified)."
