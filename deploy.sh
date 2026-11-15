#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

GRADLE_FILE=app/build.gradle.kts
CUR_CODE=$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$GRADLE_FILE")
CUR_NAME=$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$GRADLE_FILE")

[[ ${1:-} =~ ^v?([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || { echo "usage: $0 <major.minor.patch>   (current: $CUR_NAME)" >&2; exit 1; }
NAME="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.${BASH_REMATCH[3]}"
CODE=$(( 10#${BASH_REMATCH[1]} * 10000 + 10#${BASH_REMATCH[2]} * 100 + 10#${BASH_REMATCH[3]} ))   # 1.2.3 -> 10203
(( CODE > CUR_CODE )) && [ "$(printf '%s\n' "$CUR_NAME" "$NAME" | sort -V | tail -n 1)" = "$NAME" ] || { echo "error: $NAME (versionCode $CODE) is not above $CUR_NAME ($CUR_CODE)" >&2; exit 1; }
TAG="v$NAME"
CHANGELOG="fastlane/metadata/android/en-US/changelogs/$CODE.txt"

git log --no-merges --format=%s "$(git describe --tags --abbrev=0)..HEAD" > "$CHANGELOG"
sed -i -E \
    -e "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+/\1$CODE/" \
    -e "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*)\"[^\"]+\"/\1\"$NAME\"/" \
    "$GRADLE_FILE"

git add "$CHANGELOG"
git commit -q -m "release: $TAG" -- "$GRADLE_FILE" "$CHANGELOG"
git tag -a "$TAG" -F "$CHANGELOG"
echo "tagged $TAG  versionCode $CODE  commit $(git rev-parse HEAD)"
