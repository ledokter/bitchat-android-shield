#!/usr/bin/env bash
# Publish a chatbit update: ./tools/chatbit-release.sh 2.1.1 ["release notes"]
#
# 1. bumps versionName / versionCode in app/build.gradle.kts
# 2. builds the universal release APK
# 3. signs it with the chatbit release key (kept outside the repository)
# 4. checks the signature matches the certificate pinned in gradle.properties
# 5. builds the Play bundle (dist/chatbit-X.Y.Z.aab) signed with the upload key, for the Play Console
# 6. commits, tags vX.Y.Z, pushes and creates the GitHub release with chatbit-universal.apk
#
# Phones fetch https://github.com/<repo>/releases/latest/download/chatbit-universal.apk through Tor,
# verify the signature, then share the update offline (hotspot mini-site, Bluetooth, Quick Share).
set -euo pipefail

VERSION="${1:-}"
NOTES="${2:-chatbit $VERSION}"
REPO="ledokter/chatbit"
KEY_DIR="${CHATBIT_KEY_DIR:-$HOME/.chatbit-keys}"
KEYSTORE="$KEY_DIR/chatbit-release.jks"
PASSWORD_FILE="$KEY_DIR/password"
UPLOAD_KEYSTORE="$KEY_DIR/chatbit-upload.jks"
UPLOAD_PASSWORD_FILE="$KEY_DIR/upload-password"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_FILE="$ROOT/app/build.gradle.kts"
DIST="$ROOT/dist"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"

die() { echo "✗ $*" >&2; exit 1; }

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "usage: $0 X.Y.Z [notes]"
[[ -f "$KEYSTORE" && -f "$PASSWORD_FILE" ]] || die "release key not found in $KEY_DIR"
[[ -f "$UPLOAD_KEYSTORE" && -f "$UPLOAD_PASSWORD_FILE" ]] || die "Play upload key not found in $KEY_DIR"
command -v jarsigner >/dev/null || die "jarsigner (JDK) is required"
command -v gh >/dev/null || die "gh (GitHub CLI) is required"
cd "$ROOT"
[[ -z "$(git status --porcelain)" ]] || die "working tree not clean: commit or stash first"
git rev-parse "v$VERSION" >/dev/null 2>&1 && die "tag v$VERSION already exists"

OLD_NAME="$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' "$GRADLE_FILE" | head -1)"
OLD_CODE="$(sed -n 's/^ *versionCode = \([0-9]*\)/\1/p' "$GRADLE_FILE" | head -1)"
NEW_CODE=$((OLD_CODE + 1))
echo "→ $OLD_NAME ($OLD_CODE)  ⇒  $VERSION ($NEW_CODE)"
sed -i "s/^\( *\)versionCode = $OLD_CODE\$/\1versionCode = $NEW_CODE/; s/^\( *\)versionName = \"$OLD_NAME\"\$/\1versionName = \"$VERSION\"/" "$GRADLE_FILE"

echo "→ building the universal release APK and the Play bundle"
./gradlew -q :app:assembleRelease :app:bundleRelease
UNSIGNED="$(ls app/build/outputs/apk/release/*universal*release*.apk | head -1)"
[[ -f "$UNSIGNED" ]] || die "universal release APK not found"

mkdir -p "$DIST"
ALIGNED="$DIST/chatbit-universal-aligned.apk"
OUT="$DIST/chatbit-universal.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED" "$ALIGNED"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias chatbit \
    --ks-pass "file:$PASSWORD_FILE" --out "$OUT" "$ALIGNED"  # key password = store password (apksigner would read a 2nd line)
rm -f "$ALIGNED" "$OUT.idsig"

echo "→ verifying the signature"
SIGNED_SHA="$("$BUILD_TOOLS/apksigner" verify --print-certs "$OUT" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)"
PINNED_SHA="$(sed -n 's/^BITCHAT_GITHUB_RELEASE_CERT_SHA256=//p' gradle.properties | tr -d ':' | tr 'A-F' 'a-f')"
[[ -n "$SIGNED_SHA" && "$SIGNED_SHA" == "$PINNED_SHA" ]] || die "signature $SIGNED_SHA does not match the pinned $PINNED_SHA"
echo "  ✓ signed by the chatbit release key ($SIGNED_SHA)"

echo "→ signing the Play bundle with the upload key"
AAB="$DIST/chatbit-$VERSION.aab"
cp app/build/outputs/bundle/release/app-release.aab "$AAB"
jarsigner -quiet -keystore "$UPLOAD_KEYSTORE" -storepass:file "$UPLOAD_PASSWORD_FILE" -keypass:file "$UPLOAD_PASSWORD_FILE" \
    -sigalg SHA256withRSA -digestalg SHA-256 "$AAB" upload
jarsigner -verify -strict "$AAB" >/dev/null || die "Play bundle signature check failed"
echo "  ✓ $AAB (upload it in Play Console → Release → Production or Testing)"

echo "→ commit, tag and publish v$VERSION"
git add "$GRADLE_FILE"
git commit -q -m "chore(release): chatbit $VERSION"
git tag -a "v$VERSION" -m "chatbit $VERSION"
git push -q origin HEAD "v$VERSION"
gh release create "v$VERSION" "$OUT#chatbit-universal.apk" --repo "$REPO" --title "chatbit $VERSION" --notes "$NOTES"
echo "✓ chatbit $VERSION published: https://github.com/$REPO/releases/tag/v$VERSION"
