#!/usr/bin/env bash
# Checks that the patcher still does to real shaderpacks what the old one did.
#   check-real-packs.sh <dir-of-zips> [old-ref]
# Builds the old patcher from old-ref (default upstream/dev) into a temp dir, takes the new one from
# the ovvar-client mod's jar (which is the patcher too), runs both over every zip in the directory (on copies, the packs are not
# touched) and diffs the outputs, ignoring ovvar-patch.properties. Packs are never committed.
set -euo pipefail
packs=${1:?usage: check-real-packs.sh <dir-of-zips> [old-ref]}
ref=${2:-upstream/dev}
root=$(git rev-parse --show-toplevel)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# the old jar: sources and resources as of the old ref, compiled by hand
mkdir -p "$work/old/src" "$work/old/classes"
git -C "$root" archive "$ref" mods/ovvar/tools/shaderpatcher/src | tar -x -C "$work/old/src"
git -C "$root" show "$ref:mods/ovvar/src/main/resources/assets/ovvar/shaders/include/ovvar.glsl" > "$work/old/classes/ovvar.glsl"
for f in rift_common void_rift nether_rift; do
	git -C "$root" show "$ref:mods/metacraft-shaders/src/main/resources/assets/metacraft/shaders/include/$f.glsl" > "$work/old/classes/$f.glsl"
done
javac --release 11 -d "$work/old/classes" $(find "$work/old/src" -name '*.java')
printf 'Main-Class: metacraft.ovvar.shaderpatcher.Patcher\n' > "$work/old/manifest"
jar cfm "$work/old.jar" "$work/old/manifest" -C "$work/old/classes" .

# the new one
(cd "$root" && ./gradlew --offline -q :mods:ovvar-client:jar)
new=$(ls "$root"/mods/ovvar-client/build/libs/ovvar-client-*.jar | grep -v sources | head -1)

status=0
for zip in "$packs"/*.zip; do
	name=$(basename "$zip" .zip)
	for v in old new; do
		mkdir -p "$work/$v/in" "$work/$v/out/$name"
		cp "$zip" "$work/$v/in/$name.zip"
		jar=$work/old.jar; [ "$v" = new ] && jar=$new
		java -jar "$jar" "$work/$v/in/$name.zip" > "$work/$v/$name.log" 2>&1 || true
		if [ -f "$work/$v/in/$name+ovvar.zip" ]; then
			unzip -q -o "$work/$v/in/$name+ovvar.zip" -d "$work/$v/out/$name"
		fi
		rm -f "$work/$v/out/$name/ovvar-patch.properties"
	done
	if diff -r -q "$work/old/out/$name" "$work/new/out/$name" > /dev/null; then
		echo "same:  $name ($(find "$work/new/out/$name" -type f | wc -l | tr -d ' ') files$([ -f "$work/new/in/$name+ovvar.zip" ] || echo ', not patched'))"
	else
		echo "DIFF:  $name"
		diff -r -q "$work/old/out/$name" "$work/new/out/$name" || true
		status=1
	fi
done
exit $status
