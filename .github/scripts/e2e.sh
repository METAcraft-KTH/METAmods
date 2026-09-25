#!/usr/bin/env bash
# End-to-end deploy test: an SFTP server (atmoz/sftp in Docker) whose mods/ is bind-mounted
# from build/e2e/server/mods, the real deployServer task, and autodeploy run over the same
# directory as a server restart would. No real server is touched.
# Usage: .github/scripts/e2e.sh <path to autodeploy.jar>
# Needs: docker, java, jq, python3, shasum, and build/deploy/test/ (./gradlew deploySet -Pserver=test).
set -euo pipefail
cd "$(dirname "$0")/../.."
[ $# -eq 1 ] || { echo "usage: $0 <autodeploy.jar>" >&2; exit 2; }
AUTODEPLOY_JAR="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
E2E=build/e2e
SERVER_DIR="$E2E/server"
SET1=build/deploy/test
PORT=2222
export AUTODEPLOY_PASSWORD=e2e-password

fail() { printf 'E2E FAIL: %b\n' "$*" >&2; exit 1; }

make_mod_jar() { # <jar> <mod id>
  python3 - "$1" "$2" <<'EOF'
import json, sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": sys.argv[2], "version": "1.0.0"}))
EOF
}

deploy() { # <set dir>
  ./gradlew --quiet deployServer -Pserver=test -PdeployDir="$1" -PserversFile="$E2E/servers.json"
}

restart() { # what the server's startup does before loading mods
  (cd "$SERVER_DIR" && java -jar "$AUTODEPLOY_JAR")
}

update_jars() { find "$SERVER_DIR/mods/update" -maxdepth 1 -name '*.jar' | wc -l | tr -d ' '; }

assert_mods_match() { # <set dir>: mods/ holds exactly the set's jars, plus the hand-managed one
  local expected actual f want got
  expected=$( (jq -r '.[].file' "$1/manifest.json"; echo fabric-api-fake.jar) | sort)
  actual=$(cd "$SERVER_DIR/mods" && ls -1 -- *.jar | sort)
  [ "$expected" = "$actual" ] || fail "mods/ differs from $1:\n$(diff <(echo "$expected") <(echo "$actual") || true)"
  for f in $(jq -r '.[].file' "$1/manifest.json"); do
    want=$(jq -r --arg f "$f" '.[] | select(.file == $f) | .sha256' "$1/manifest.json")
    got=$(shasum -a 256 "$SERVER_DIR/mods/$f" | cut -d' ' -f1)
    [ "$want" = "$got" ] || fail "mods/$f has sha256 $got, expected $want"
  done
  [ ! -e "$SERVER_DIR/mods/update/remove.txt" ] || fail "autodeploy left remove.txt behind"
}

derive_set() { # <from> <to>
  rm -rf "$2"; cp -R "$1" "$2"
}

[ -f "$SET1/manifest.json" ] || fail "no $SET1/manifest.json; run ./gradlew deploySet -Pserver=test first"

echo "0. the test set holds every listed mod and metacraft-lib"
for id in $(sed -e 's/^#.*//' -e 's/[[:space:]]#.*//' deploy/test.txt | awk 'NF { print ($1 == "external" ? $2 : $1) }') metacraft-lib; do
  jq -e --arg id "$id" 'has($id)' "$SET1/manifest.json" >/dev/null || fail "$id is missing from $SET1/manifest.json"
done

rm -rf "$E2E"
mkdir -p "$SERVER_DIR/mods/update"
make_mod_jar "$SERVER_DIR/mods/metacraft-1.0.0.jar" metacraft
make_mod_jar "$SERVER_DIR/mods/fabric-api-fake.jar" fabric-api
cat > "$E2E/servers.json" <<EOF
{"branches": {}, "servers": {"test": {"host": "127.0.0.1", "port": $PORT, "user": "deploy"}}}
EOF

docker rm -f e2e-sftp >/dev/null 2>&1 || true
docker run -d --name e2e-sftp -p "$PORT:22" -v "$PWD/$SERVER_DIR/mods:/home/deploy/mods" \
  atmoz/sftp:alpine "deploy:$AUTODEPLOY_PASSWORD:$(id -u):$(id -g)" >/dev/null
trap 'docker rm -f e2e-sftp >/dev/null 2>&1 || true' EXIT
for _ in $(seq 1 60); do
  docker logs e2e-sftp 2>&1 | grep -q 'Server listening' && break
  sleep 1
done
docker logs e2e-sftp 2>&1 | grep -q 'Server listening' || fail "the SFTP container did not start:\n$(docker logs e2e-sftp 2>&1)"

echo "1. a server without .autodeploy-version refuses and uploads nothing"
if out=$(deploy "$SET1" 2>&1); then fail "the deploy succeeded without a marker"; fi
grep -q "update autodeploy.jar on test first" <<<"$out" || fail "wrong refusal:\n$out"
[ "$(update_jars)" = 0 ] || fail "jars were uploaded despite the refusal"
[ ! -e "$SERVER_DIR/mods/metacraft-deploy.json" ] || fail "a manifest was written despite the refusal"

echo "2. first deploy: every jar uploaded, the bundle deleted at the restart"
restart
[ -f "$SERVER_DIR/mods/update/.autodeploy-version" ] || fail "autodeploy wrote no .autodeploy-version (is it FabricModsUpdate 1.1+?)"
deploy "$SET1"
[ "$(update_jars)" = "$(jq length "$SET1/manifest.json")" ] || fail "the first deploy did not upload every jar"
[ "$(cat "$SERVER_DIR/mods/update/remove.txt")" = metacraft ] || fail "remove.txt should be exactly 'metacraft'"
cmp -s <(jq -S . "$SET1/manifest.json") <(jq -S . "$SERVER_DIR/mods/metacraft-deploy.json") || fail "server manifest differs from the set's"
restart
assert_mods_match "$SET1"

echo "3. the same set again uploads nothing"
deploy "$SET1"
[ "$(update_jars)" = 0 ] || fail "an unchanged set uploaded jars"
[ "$(cat "$SERVER_DIR/mods/update/remove.txt")" = metacraft ] || fail "every deploy should list metacraft in remove.txt"

echo "4. one changed mod uploads one jar"
SET2="$E2E/set2"; derive_set "$SET1" "$SET2"
changed=faster-minecarts
file=$(jq -r --arg id "$changed" '.[$id].file' "$SET2/manifest.json")
python3 - "$SET2/$file" <<'EOF'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "a") as z:
    z.writestr("e2e-changed.txt", "changed")
EOF
sha=$(shasum -a 256 "$SET2/$file" | cut -d' ' -f1)
jq --arg id "$changed" --arg sha "$sha" '.[$id].sha256 = $sha' "$SET2/manifest.json" > "$SET2/manifest.tmp"
mv "$SET2/manifest.tmp" "$SET2/manifest.json"
deploy "$SET2"
[ "$(update_jars)" = 1 ] || fail "expected exactly one upload, got $(update_jars)"
[ -f "$SERVER_DIR/mods/update/$file" ] || fail "$file was not the upload"
restart
assert_mods_match "$SET2"

echo "5. a mod taken off the list is deleted"
SET3="$E2E/set3"; derive_set "$SET2" "$SET3"
removed=portal-opening
rm "$SET3/$(jq -r --arg id "$removed" '.[$id].file' "$SET3/manifest.json")"
jq --arg id "$removed" 'del(.[$id])' "$SET3/manifest.json" > "$SET3/manifest.tmp"
mv "$SET3/manifest.tmp" "$SET3/manifest.json"
deploy "$SET3"
[ "$(cat "$SERVER_DIR/mods/update/remove.txt")" = "$(printf 'metacraft\n%s' "$removed")" ] || fail "remove.txt should be exactly metacraft and $removed"
[ "$(update_jars)" = 0 ] || fail "a removal uploaded jars"
restart
assert_mods_match "$SET3"

echo "6. rolling back to the first set restores it"
deploy "$SET1"
[ "$(update_jars)" = 2 ] || fail "the rollback should upload 2 jars, got $(update_jars)"
restart
assert_mods_match "$SET1"

echo "E2E OK"
