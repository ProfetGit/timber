"""A/B: the Host drawing the server's displays (case m1_ab) vs the mesh (m1), same spot: pops at the chop window."""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import exit as ex
W = ex.WORK
END = {'oak': 466_339, 'birch': 416_120, 'spruce': 440_326}
for case in sys.argv[1:]:
    host = W / f'mp-{case}' / 'out-host'
    tr = json.loads((host / 'trees.json').read_text())
    for t in ('oak', 'birch', 'spruce'):
        walls = {int(r[0]): int(r[1]) for r in ex.rows(host / f'frames_{t}.tsv')}
        c = tr[t]['chop_us']
        win = ex.chop_window(walls, c - 100_000, c + END[t])
        a = ex.flicker(host / 'frames' / f'mp_{t}')
        w = ex.flicker(host / 'frames' / f'mp_{t}', win)
        print(case, t, 'window', win, 'chop pops', w['pops'], [(p['frame'], p['px']) for p in w.get('top', [])][:4], '| whole clip', a['pops'], [(p['frame'], p['px']) for p in a.get('top', [])][:3])
