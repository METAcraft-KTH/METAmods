#!/usr/bin/env bash
# Builds a server's set and the dist jar twice from clean; fails if any jar's or datapack's bytes differ.
# A deploy uploads the jars and datapacks whose sha256 changed, so unchanged source must give identical ones.
# Usage: .github/scripts/check-reproducible.sh [server]    (default: test)
set -euo pipefail
cd "$(dirname "$0")/../.."
server="${1:-test}"
first="$(mktemp -d)"
trap 'rm -rf "$first"' EXIT

build() {
  ./gradlew --quiet --no-build-cache clean
  ./gradlew --quiet --no-build-cache deploySet -Pserver="$server" :dist:jar
}

build
cp "build/deploy/$server/manifest.json" "$first/manifest.json"
[ ! -f "build/deploy/$server/datapacks.json" ] || cp "build/deploy/$server/datapacks.json" "$first/datapacks.json"
(cd dist/build/libs && shasum -a 256 metacraft-*.jar) > "$first/dist.sha256"

build
if ! diff -u "$first/manifest.json" "build/deploy/$server/manifest.json"; then
  echo "FAIL: jars in $server's set differ between two builds of the same source (sha256 lines above)" >&2
  exit 1
fi
if [ -f "$first/datapacks.json" ] || [ -f "build/deploy/$server/datapacks.json" ]; then
  if ! diff -u "$first/datapacks.json" "build/deploy/$server/datapacks.json"; then
    echo "FAIL: datapacks in $server's set differ between two builds of the same source" >&2
    exit 1
  fi
fi
if ! (cd dist/build/libs && shasum -a 256 -c "$first/dist.sha256"); then
  echo "FAIL: the dist jar (jar-in-jar) differs between two builds of the same source" >&2
  exit 1
fi
echo "OK: $server's set ($(jq length "build/deploy/$server/manifest.json") jars) and the dist jar are byte-identical across two clean builds"
