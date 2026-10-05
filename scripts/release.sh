#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="luads/byd-auto-seat-preset"
SIGNING_CONFIG="${SEAT_SIGNING_CONFIG:-$HOME/.config/seat-presets/signing.env}"
if [ -f "$SIGNING_CONFIG" ]; then source "$SIGNING_CONFIG"; fi
: "${SEAT_SIGNING_STORE:?Set the private release keystore path}"
: "${SEAT_SIGNING_STORE_PASSWORD:?Set the keystore password}"
: "${SEAT_SIGNING_ALIAS:?Set the key alias}"
: "${SEAT_SIGNING_KEY_PASSWORD:?Set the key password}"
[ -f "$SEAT_SIGNING_STORE" ] || { echo "Release keystore is missing"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "Commit the source before releasing"; exit 1; }
PRIVATE_LABELS="${SEAT_PRIVATE_LABELS_FILE:-$HOME/.config/seat-presets/private-labels.txt}"
if [ -s "$PRIVATE_LABELS" ] && git grep -n -F -f "$PRIVATE_LABELS" -- app docs README.md scripts; then
  echo "Remove personal fixture labels before publication"; exit 1
fi
if git grep -n -E 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' -- app docs README.md; then
  echo "Remove private key material before publication"; exit 1
fi
[ -z "$(git ls-files '*.jks' '*.keystore' '.env' '*.apk')" ] || { echo "Remove private signing/config/build files from git"; exit 1; }
[ "$(gh repo view "$REPO" --json visibility -q .visibility)" = PUBLIC ] || { echo "This release path expects a public repository"; exit 1; }
git fetch origin main
HEAD_SHA="$(git rev-parse HEAD)"
[ "$HEAD_SHA" = "$(git rev-parse origin/main)" ] || { echo "Push this exact source to main before releasing"; exit 1; }
VN="$(sed -nE 's/.*versionName = .*\?: "([^"]+)".*/\1/p' app/build.gradle.kts | head -1)"
VC="$(sed -nE 's/.*versionCode = .*\?: ([0-9]+).*/\1/p' app/build.gradle.kts | head -1)"
[ -n "$VN" ] && [ -n "$VC" ] || { echo "Version fields are missing"; exit 1; }
if gh release view "v$VN" --repo "$REPO" >/dev/null 2>&1; then echo "Version already exists; bump both version fields"; exit 1; fi
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
./gradlew test lintDemoRelease lintLiveRelease assembleDemoRelease assembleLiveRelease
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
for FLAVOR in demo live; do
  SRC="app/build/outputs/apk/$FLAVOR/release/app-$FLAVOR-release.apk"
  [ -f "$SRC" ] || { echo "Signed release APK missing for $FLAVOR"; exit 1; }
  cp "$SRC" "$OUT/byd-auto-seat-preset-$FLAVOR-$VN-$VC.apk"
done
gh release create "v$VN" "$OUT"/*.apk --repo "$REPO" --target "$HEAD_SHA" --title "v$VN · pre-alpha" --prerelease \
  --notes-file "${SEAT_RELEASE_NOTES:-docs/RELEASE-0.1.10.md}"
