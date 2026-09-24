#!/usr/bin/env bash
# Dev-only: watch the felling animation in the real client. Usage: dev/capture/run.sh [mc-version] [out-dir]
# env: SCENE=oak|spruce|forest (default oak), VIEW=fp|side (default fp), DROPS=0|1, FRAMES (default 110), WARMUP (default 40)
# Builds the pack, lets the test server create a flat world with it, adds the capture scene (dev/capture/scene),
# plays it off-screen via ../ClientCapture and writes frames plus dev/capture/sheet.png.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
VER=${1:-26.3}
SCENE=${SCENE:-oak}
VIEW=${VIEW:-fp}
OUT=${2:-$ROOT/dev/capture/frames/$SCENE-$VIEW}
WORK=$ROOT/dev/capture/.work/$SCENE-$VIEW
mkdir -p "$WORK"
# build the scene on the server first, so the saved world already has settled lighting when the client loads it
printf "forceload add -48 -48 80 80\ngamerule max_block_modifications 10000000\n!tick 40\nfunction tscene:build_%s\nfunction tscene:view_%s\n!tick 60\n" "$SCENE" "$VIEW" > "$WORK/build.txt"
[ -n "${DROPS:-}" ] && echo "scoreboard players set #drops timber.config $DROPS" >> "$WORK/build.txt"
# EXTRA_PACKS is split on spaces and the workspace path has one, so pass the scene relative to the pack root
(cd "$ROOT" && EXTRA_PACKS=dev/capture/scene dev/test/run.sh "$VER" "$WORK/server" explore "$WORK/build.txt") > "$WORK/server.log" 2>&1
"$ROOT/../ClientCapture/run.sh" "$VER" "$WORK/server/world" "$OUT" "${WARMUP:-40}" "${FRAMES:-110}"
python3 "$ROOT/dev/capture/sheet.py" "$OUT" "$ROOT/dev/capture/sheet-$SCENE-$VIEW.png" 0 60 0 0 1280 720 0.25
