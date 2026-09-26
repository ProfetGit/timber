#!/usr/bin/env bash
# Dev-only display probe for the Timber lab: build.sh [mc-version] -> timberprobe.jar
set -euo pipefail
VER=${1:-26.3}
HERE=$(cd "$(dirname "$0")" && pwd)
META=${MODRINTH_META:-$HOME/.local/share/ModrinthApp/meta}
VDIR=$(ls -d "$META"/versions/"$VER"-* | head -1)
CP="$VDIR/$(basename "$VDIR").jar:$(python3 "$HERE/../../../../../ClientCapture/classpath.py" "$VDIR/$(basename "$VDIR").json" "$META/libraries")"
rm -rf "$HERE/build" && mkdir -p "$HERE/build"
(cd "$HERE/src" && javac -nowarn --release 21 -cp "$CP" -d "$HERE/build" $(find . -name '*.java'))
cp "$HERE/fabric.mod.json" "$HERE/timberprobe.mixins.json" "$HERE/build/"
(cd "$HERE/build" && jar --create --file "$HERE/timberprobe.jar" .)
echo "built $HERE/timberprobe.jar"
