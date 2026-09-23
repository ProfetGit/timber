#!/usr/bin/env bash
# Usage: dev/test/run.sh <mc-version> [workdir] [harness args...]
# Boots a headless server from the local ModrinthApp jar with the built pack and runs TimberTest.
set -euo pipefail

VER=${1:?usage: run.sh <mc-version> [workdir] [args...]}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
WORK=${2:-$ROOT/dev/test/.work/$VER}
shift $(( $# >= 2 ? 2 : 1 ))
META=${MODRINTH_META:-$HOME/.local/share/ModrinthApp/meta}

VDIR=$(ls -d "$META"/versions/"$VER"-* 2>/dev/null | head -1)
[ -n "$VDIR" ] || { echo "no jar for $VER under $META/versions" >&2; exit 2; }
JAR="$VDIR/$(basename "$VDIR").jar"

CP=$(python3 - "$VDIR/$(basename "$VDIR").json" "$META/libraries" <<'EOF'
import json, os, sys
spec, libroot = sys.argv[1], sys.argv[2]
out = []
for lib in json.load(open(spec))["libraries"]:
    parts = lib["name"].split(":")
    if len(parts) != 3 or parts[0] in ("net.fabricmc", "org.ow2.asm") or parts[1].startswith("lwjgl"):
        continue
    g, a, v = parts
    p = os.path.join(libroot, *g.split("."), a, v, f"{a}-{v}.jar")
    if os.path.exists(p):
        out.append(p)
print(":".join(out))
EOF
)

python3 "$ROOT/dev/build.py" >/dev/null
ZIP=$(ls "$ROOT"/dist/Timber-*.zip | head -1)

rm -rf "$WORK"
mkdir -p "$WORK/world/datapacks" "$WORK/classes"
cp "$ZIP" "$WORK/world/datapacks/"
for extra in ${EXTRA_PACKS:-}; do cp -r "$extra" "$WORK/world/datapacks/"; done
echo "eula=true" > "$WORK/eula.txt"
cat > "$WORK/server.properties" <<'EOF'
level-type=minecraft\:flat
generate-structures=false
online-mode=false
server-ip=127.0.0.1
server-port=25577
spawn-protection=0
view-distance=3
simulation-distance=3
difficulty=peaceful
sync-chunk-writes=false
enable-query=false
enable-rcon=false
EOF

javac -nowarn -cp "$JAR:$CP" -d "$WORK/classes" "$ROOT/dev/test/TimberTest.java"
cd "$WORK"
set +e
java -Xmx2G ${JAVA_OPTS:-} --add-opens java.base/java.lang=ALL-UNNAMED -cp "$WORK/classes:$JAR:$CP" TimberTest "$@" 2>&1 | tee "$WORK/harness.log" | grep -oE '\[(PASS|FAIL|INFO|EXPLORE)\].*|SUMMARY.*|  - .*|[A-Za-z.]*Exception.*'
STATUS=${PIPESTATUS[0]}
set -e

echo "--- server log warnings/errors (test-env noise filtered) ---"
grep -E '/(WARN|ERROR)\]|Command execution stopped' "$WORK/logs/latest.log" | grep -v 'STDOUT' \
  | grep -vE 'OFFLINE/INSECURE|authenticate usernames|online-mode|hackers to connect|No key layers in MapLike' || echo "none"
exit $STATUS
