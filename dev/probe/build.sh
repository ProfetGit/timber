#!/usr/bin/env bash
# M1 exit probe (dev only, never shipped): build.sh [mc-version] -> timberprobe.jar. A copy of the 1.3.6 lab probe
# (packs/Timber/dev/lab/probe/mod) with wall-clock stamps, chat cues, frame capture and a results.json.
set -euo pipefail
VER=${1:-26.3}
HERE=$(cd "$(dirname "$0")" && pwd)
META=${MODRINTH_META:-$HOME/.local/share/ModrinthApp/meta}
VDIR=$(ls -d "$META"/versions/"$VER"-* | head -1)
CP="$VDIR/$(basename "$VDIR").jar:$(python3 "$HERE/../../../../tools/ClientCapture/classpath.py" "$VDIR/$(basename "$VDIR").json" "$META/libraries")"
rm -rf "$HERE/build" && mkdir -p "$HERE/build"
(cd "$HERE/src" && javac -nowarn --release 21 -cp "$CP" -d "$HERE/build" $(find . -name '*.java'))
cp "$HERE/fabric.mod.json" "$HERE/timberprobe.mixins.json" "$HERE/build/"
(cd "$HERE/build" && jar --create --file "$HERE/timberprobe.jar" .)
echo "built $HERE/timberprobe.jar"
