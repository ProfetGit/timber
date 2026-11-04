import sys
from pathlib import Path
FB = Path.home() / ".claude/skills/pack-showcase/scripts/flashback"; sys.path[:0] = [str(FB), str(FB.parent)]
import showcase as S, cam
from recipes import Reel
ROOT = Path.home() / "Projects/Minecraft Datapacks/media/Showcase"; S.set_root(ROOT)
tag = sys.argv[1]
reel = Reel(ROOT / f"out/{tag}/{tag}.replay.zip", ROOT / f"out/{tag}/reshoot")
c, f = cam.orbit((-146.0, 90.0, -132.0), r=20, h=3, az=(-60, 120), ticks=reel.span(), fov=60)
reel.video("orbit", c, f, size=(1920, 1080), fps=60)
print({k: ("ok" if v["ok"] else v["error"]) for k, v in reel.run().items()})
