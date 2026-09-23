// Timber icon scene + animation (pack-icon-animation skill). Run inside Blockbench (free format project):
//   eval(require('fs').readFileSync('<this file>', 'utf8')); window.VM = VM; VM.loadTextures()   // then, in a later call:
//   VM.build(); VM.animate(); VM.camera()                                                        // then:
//   VM.render(first, last)                                                                       // 1600px frames -> frames/
// The tree lives in `yaw` (rotated 45 deg, so west = left face, south = right face). The chopped bottom log (log_0) sits
// directly in `yaw` and shatters; the rest hangs, then `sink` drops it one block. Inside `sink`, `lean` tips the tree back
// about its east bottom edge and `fall` topples it toward -x (screen left-front) about its west bottom edge.
// Screen space: the `screen` group is tilted to face the orthographic camera, so inside it x = right, y = up, z = toward camera.
var VM = (function () {
  const fs = require('fs');
  const DIR = '/home/emppu/Projects/Minecraft Datapacks/Timber/dev/icon/';
  const TEX = DIR + 'sprites/';
  const FPS = 25, DT = 1 / FPS, LEN = 3.2;
  const CAM_POS = [0, 60, 104], CAM_TARGET = [0, 16, 0], CAM_PAN = [-14, 7.5, 0], CAM_ZOOM = 0.25;
  const PITCH = -Math.atan2(CAM_POS[1] - CAM_TARGET[1], CAM_POS[2] - CAM_TARGET[2]) * 180 / Math.PI;
  const O = new THREE.Vector3(...CAM_TARGET);
  const RAD = Math.PI / 180;
  const Z_ITEMS = 40, Z_FX = 50, Z_AXE = 30, Z_DEBRIS = 35;

  const LOGS = [0, 1, 2];
  const CROWN = [];
  for (const x of [-1, 0, 1]) for (const z of [-1, 0, 1]) CROWN.push([x, 3, z]);
  CROWN.push([0, 4, 0], [1, 4, 0], [-1, 4, 0], [0, 4, 1], [0, 4, -1]);
  const leafAt = (x, y, z) => CROWN.some(c => c[0] === x && c[1] === y && c[2] === z);

  const T = {
    windStart: 0.20, cocked: 0.44, mid: 0.48, impact: 0.52, stopEnd: 0.60,
    brk: 0.64, drop: 0.72, land: 0.80,                 // log_0 shatters, the tree hangs, then drops one block
    leanStart: 0.84, leanPeak: 0.92, fallStart: 1.04, slam: 1.40,
    poof: 1.88, launch: 1.92,
    pickup: [2.56, 2.62, 2.68, 2.74],                  // log_0's drop (landed at the break), the 2 poof logs, the apple
    respawn: [2.72, 2.78, 2.84], crown: 2.92,
    glint: 2.96,
  };
  const P = {
    restRot: -18, cockedRot: -40, impactRot: 25, rest: [3, 4, 0], cocked: [1, 8, 0],
    lean: -7, landing: 70, fallPow: 2.2,
    bounce: [[9, 5], [3.5, 4]],
    // drops: 3 mini logs (yaw space, landing slots on the ground) + 1 apple (screen space); log 0 pops out at the break
    logSlots: [[-8, 30], [-18, 10], [-34, 13]], appleSlot: [-52, 6],
    apex: [14, 13, 11, 12], flight: [8, 8, 8, 8],
    dropBounce: [[3.5, 4], [1, 2]],
  };

  const q = t => Math.round(t * FPS) / FPS;
  const worldToScreen = w => new THREE.Vector3(...w).sub(O).applyEuler(new THREE.Euler(-PITCH * RAD, 0, 0)).add(O);
  const yawToWorld = a => new THREE.Vector3(...a).applyEuler(new THREE.Euler(0, 45 * RAD, 0));
  const yawToScreen = a => worldToScreen(yawToWorld(a).toArray());
  // a point of the upright tree (yaw space) after it has dropped one block and `fall` has turned by `deg` about the west
  // bottom edge of log_1
  const fallen = (p, deg) => {
    const a = deg * RAD, x = p[0] + 8, y = p[1] - 16;
    return [-8 + x * Math.cos(a) - y * Math.sin(a), x * Math.sin(a) + y * Math.cos(a), p[2]];
  };

  const tex = {};
  function loadTextures() {
    Texture.all.slice().forEach(t => t.remove(true));
    for (const f of fs.readdirSync(TEX).filter(f => f.endsWith('.png') && !/^(bg|banner)_/.test(f))) {
      const url = 'data:image/png;base64,' + fs.readFileSync(TEX + f).toString('base64');
      tex[f.slice(0, -4)] = new Texture({ name: f }).fromDataURL(url).add(false);
    }
    return Object.keys(tex).join(',');
  }
  function ensureTex() {
    if (!Object.keys(tex).length) Texture.all.forEach(t => { tex[t.name.replace('.png', '')] = t; });
  }
  function pixels(name) {
    const t = tex[name], img = t.img;
    const cv = document.createElement('canvas');
    cv.width = img.naturalWidth; cv.height = img.naturalHeight;
    const ctx = cv.getContext('2d'); ctx.drawImage(img, 0, 0);
    const d = ctx.getImageData(0, 0, cv.width, cv.height).data, out = [];
    for (let y = 0; y < cv.height; y++) for (let x = 0; x < cv.width; x++) if (d[(y * cv.width + x) * 4 + 3] > 127) out.push([x, y]);
    return { px: out, w: cv.width, h: cv.height };
  }

  function group(name, origin, parent, rotation) {
    const g = new Group({ name, origin, rotation: rotation || [0, 0, 0] });
    g.addTo(parent); g.init();
    return g;
  }
  const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
  function cube(name, from, to, parent, faceTex, opts) {
    const c = new Cube(Object.assign({ name, from, to, box_uv: false }, opts || {}));
    c.addTo(parent); c.init();
    for (const f of FACES) {
      const spec = faceTex[f] || faceTex.all;
      if (spec) c.faces[f].extend({ texture: tex[spec[0]].uuid, uv: spec[1] });
      else c.faces[f].extend({ texture: null });
    }
    return c;
  }
  function plane(name, centre, size, parent, texName, uvSize, mirror) {
    const [x, y, z] = centre, h = size / 2;
    const uv = mirror ? [uvSize, 0, 0, uvSize] : [0, 0, uvSize, uvSize];
    return cube(name, [x - h, y - h, z], [x + h, y + h, z], parent, { south: [texName, uv] });
  }
  // one cube per opaque sprite pixel; (px,py) = sprite pixel placed at `at`, pixel size s. UVs are in 16-unit project space.
  function extrude(prefix, texName, pivotPx, at, s, parent) {
    const { px, w, h } = pixels(texName), ku = 16 / w, kv = 16 / h;
    for (const [x, y] of px) {
      const x0 = at[0] + (x - pivotPx[0]) * s, y0 = at[1] + (pivotPx[1] - y - 1) * s;
      cube(prefix, [x0, y0, at[2] - s / 2], [x0 + s, y0 + s, at[2] + s / 2], parent, { all: [texName, [x * ku, y * kv, (x + 1) * ku, (y + 1) * kv]] });
    }
  }

  const FULL = [0, 0, 16, 16];
  const LOG_FACES = {
    west: ['log_side_left', FULL], south: ['log_side_right', FULL], east: ['log_side', FULL],
    north: ['log_side', FULL], up: ['log_side', FULL], down: ['log_side_right', FULL],
  };
  // east turns up when the tree lies down, so it carries the top-lit texture; `up` turns west, which the caps cover
  const LEAF_FACES = {
    west: ['leaves_left', FULL], south: ['leaves_right', FULL], east: ['leaves', FULL],
    north: ['leaves', FULL], up: ['leaves', FULL], down: ['leaves_right', FULL],
  };
  const MINI = {
    up: ['mini_log_top', FULL], down: ['mini_log_top', FULL], west: ['mini_log_side_left', FULL],
    south: ['mini_log_side_right', FULL], east: ['mini_log_side', FULL], north: ['mini_log_side', FULL],
  };

  const G = {};
  function build() {
    ensureTex();
    Animation.all.slice().forEach(a => a.remove(false));
    Outliner.root.slice().forEach(n => n.remove(false));

    G.yaw = group('yaw', [0, 0, 0], undefined, [0, 45, 0]);
    G.sink = group('sink', [0, 16, 0], G.yaw);
    G.lean = group('lean', [8, 16, 0], G.sink);
    G.fall = group('fall', [-8, 16, 0], G.lean);
    LOGS.forEach(i => {
      const g = G['log_' + i] = group('log_' + i, [0, 16 * i, 0], i ? G.fall : G.yaw);
      cube('log_block_' + i, [-8, 16 * i, -8], [8, 16 * i + 16, 8], g, LOG_FACES);
    });
    G.flash = group('flash', [0, 8, 0], G.log_0);
    cube('flash_cube', [-8, 0, -8], [8, 16, 8], G.flash, { all: ['hit_flash', FULL] }, { inflate: 0.35 });
    G.crown = group('crown', [0, 48, 0], G.fall);
    CROWN.forEach(([x, y, z], k) => {
      cube('leaf_' + k, [x * 16 - 8, y * 16, z * 16 - 8], [x * 16 + 8, y * 16 + 16, z * 16 + 8], G.crown, LEAF_FACES);
    });
    // fallen-state shading for exposed leaf tops (they face west once the tree lies down)
    G.caps = group('caps', [0, 48, 0], G.crown);
    CROWN.filter(([x, y, z]) => !leafAt(x, y + 1, z)).forEach(([x, y, z], k) => {
      const top = y * 16 + 16.05;
      cube('cap_' + k, [x * 16 - 8, top, z * 16 - 8], [x * 16 + 8, top, z * 16 + 8], G.caps, { up: ['leaves_left', FULL] });
    });

    P.logSlots.forEach(([sx, sz], i) => {
      const d = G['drop_' + i] = group('drop_' + i, [sx, 0, sz], G.yaw);
      const sp = G['drop_spin_' + i] = group('drop_spin_' + i, [sx, MINI_S / 2, sz], d);
      const h = MINI_S / 2;
      cube('mini_log_' + i, [sx - h, 0, sz - h], [sx + h, MINI_S, sz + h], sp, MINI);
    });

    G.screen = group('screen', O.toArray(), undefined, [PITCH, 0, 0]);
    const L = layout();

    G.axe = group('axe', [L.grip.x, L.grip.y, Z_AXE], G.screen);
    extrude('axe', 'axe_item', AXE_GRIP, [L.grip.x, L.grip.y, Z_AXE], AXE_PX, G.axe);
    G.smear = group('smear', [L.grip.x, L.grip.y, Z_AXE - 2], G.screen);
    const S = SMEAR_R * 16 / 15.6;
    cube('smear_plane', [L.grip.x - S, L.grip.y, Z_AXE - 2], [L.grip.x, L.grip.y + S, Z_AXE - 2], G.smear, { south: ['fx_smear', [16, 0, 0, 16]] });
    G.glint = group('glint', [L.glint.x, L.glint.y, Z_FX + 10], G.screen);
    plane('glint_plane', [L.glint.x, L.glint.y, Z_FX + 10], 9, G.glint, 'fx_spark', 16);

    const a = L.apple;
    G.apple = group('apple', [a.x, a.y, Z_ITEMS], G.screen);
    G.apple_spin = group('apple_spin', [a.x, a.y + ITEM_H / 2, Z_ITEMS], G.apple);
    extrude('apple', 'apple_item', ITEM_PIVOT, [a.x, a.y, Z_ITEMS], ITEM_PX, G.apple_spin);

    L.plings.forEach((p, i) => {
      G['pling_' + i] = group('pling_' + i, [p.x, p.y, Z_FX + 5], G.screen);
      plane('pling_plane', [p.x, p.y, Z_FX + 5], 8, G['pling_' + i], 'fx_spark', 16);
    });
    L.puffs.forEach((p, i) => {
      G['puff_' + i] = group('puff_' + i, [p.x, p.y, Z_FX + i], G.screen);
      plane('puff_plane', [p.x, p.y, Z_FX + i], PUFFS[i][1], G['puff_' + i], 'fx_puff', 16);
    });
    L.dust.forEach((p, i) => {
      G['dust_' + i] = group('dust_' + i, [p.x, p.y, Z_FX + 12 + i], G.screen);
      plane('dust_plane', [p.x, p.y, Z_FX + 12 + i], DUST[i][1], G['dust_' + i], 'fx_puff', 16);
    });
    L.land.forEach((p, i) => {
      G['land_' + i] = group('land_' + i, [p.x, p.y, Z_FX + 16 + i], G.screen);
      plane('land_plane', [p.x, p.y, Z_FX + 16 + i], LAND[i][1], G['land_' + i], 'fx_puff', 16);
    });
    [0, 1].forEach(k => {
      const x = L.brk.x + (k ? 7 : -7);
      G['brk_puff_' + k] = group('brk_puff_' + k, [x, L.brk.y, Z_FX + 18 + k], G.screen);
      plane('brk_puff_plane', [x, L.brk.y, Z_FX + 18 + k], 16, G['brk_puff_' + k], 'fx_puff', 16, k === 0);
    });
    BRK_CHIPS.forEach((c, k) => {
      const g = G['bchip_' + k] = group('bchip_' + k, [L.brk.x, L.brk.y, Z_DEBRIS + 6 + k], G.screen);
      const h = c.size / 2;
      cube('bchip', [L.brk.x - h, L.brk.y - h, Z_DEBRIS + 6 + k - h], [L.brk.x + h, L.brk.y + h, Z_DEBRIS + 6 + k + h], g, { all: ['chunks', c.uv] });
    });
    CHIPS.forEach((c, k) => {
      const g = G['chip_' + k] = group('chip_' + k, [L.tip.x, L.tip.y, Z_DEBRIS + k], G.screen);
      const h = c.size / 2;
      cube('chip', [L.tip.x - h, L.tip.y - h, Z_DEBRIS + k - h], [L.tip.x + h, L.tip.y + h, Z_DEBRIS + k + h], g, { all: ['chunks', c.uv] });
    });
    LEAFBITS.forEach((c, k) => {
      const p = L.leafFrom;
      const g = G['leafbit_' + k] = group('leafbit_' + k, [p.x, p.y, Z_DEBRIS + k], G.screen);
      const h = c.size / 2;
      cube('leafbit', [p.x - h, p.y - h, Z_DEBRIS + k - h], [p.x + h, p.y + h, Z_DEBRIS + k + h], g, { all: ['chunks', c.uv] });
    });

    G.star = group('star', [L.tip.x, L.tip.y, Z_FX + 8], G.screen);
    plane('star_plane', [L.tip.x, L.tip.y, Z_FX + 8], 18, G.star, 'fx_star', 16);
    G.ring = group('ring', [L.tip.x, L.tip.y, Z_FX + 7], G.screen);
    plane('ring_plane', [L.tip.x, L.tip.y, Z_FX + 7], 22, G.ring, 'fx_ring', 16);
    SPARKS.forEach((ang, k) => {
      G['spark_' + k] = group('spark_' + k, [L.tip.x, L.tip.y, Z_FX + 9], G.screen);
      plane('spark_plane', [L.tip.x, L.tip.y, Z_FX + 9], 6, G['spark_' + k], 'fx_spark', 16);
    });

    Canvas.updateAll();
    return Outliner.elements.length;
  }

  // ---- geometry ----
  const AXE_PX = 1.4, AXE_GRIP = [10.5, 19], AXE_TIP = [1, 6.5], AXE_GLINT = [2.5, 3.5], TRAIL_DIR = 80, SMEAR_R = 24;
  const CHOP = [3, 11, 8];                       // yaw-space point on log 0's south face that the blade bites
  const MINI_S = 9;
  const ITEM_PX = 0.9, ITEM_PIVOT = [8, 15], ITEM_H = 10 * 0.9;
  const CHIPS = [
    { size: 2.8, uv: [0, 0, 4, 4], ang: 20 }, { size: 2.4, uv: [4, 0, 8, 4], ang: 55 },
    { size: 2.2, uv: [4, 0, 8, 4], ang: 95 }, { size: 2.6, uv: [0, 0, 4, 4], ang: -15 },
  ];
  const LEAFBITS = [
    { size: 2.8, uv: [8, 0, 12, 4], ang: 60 }, { size: 2.4, uv: [12, 0, 16, 4], ang: 95 },
    { size: 2.6, uv: [8, 0, 12, 4], ang: 125 }, { size: 2.2, uv: [12, 0, 16, 4], ang: 150 },
    { size: 2.4, uv: [8, 0, 12, 4], ang: 35 },
  ];
  const BRK_CHIPS = [
    { size: 3.2, uv: [0, 0, 4, 4], ang: 15 }, { size: 2.8, uv: [4, 0, 8, 4], ang: 60 }, { size: 3.0, uv: [0, 0, 4, 4], ang: 115 },
    { size: 2.6, uv: [4, 0, 8, 4], ang: 160 }, { size: 2.4, uv: [0, 0, 4, 4], ang: 85 },
  ];
  const SPARKS = [100, 160, 210, 250];
  // poof clouds over the fallen tree [yaw point on the upright tree, size]; dust at the slam [yaw point on the upright tree,
  // dropped to the ground once fallen, size]; dust where the dropped trunk lands [yaw ground point, size]
  const PUFFS = [[[0, 26, 0], 22], [[0, 42, 0], 22], [[0, 56, -14], 28], [[0, 60, 14], 28], [[0, 76, 0], 26]];
  const DUST = [[[-24, 52, 18], 14], [[-24, 66, -10], 16], [[-24, 78, 6], 14]];
  const LAND = [[[-12, 0, 8], 12], [[8, 0, 14], 12]];

  function rot2(v, deg) {
    const a = deg * RAD;
    return [v[0] * Math.cos(a) - v[1] * Math.sin(a), v[0] * Math.sin(a) + v[1] * Math.cos(a)];
  }
  function layout() {
    const tip = yawToScreen(CHOP);
    const tipOff = rot2([(AXE_TIP[0] - AXE_GRIP[0]) * AXE_PX, (AXE_GRIP[1] - AXE_TIP[1]) * AXE_PX], P.impactRot);
    const grip = { x: tip.x - tipOff[0], y: tip.y - tipOff[1] };
    const gOff = rot2([(AXE_GLINT[0] - AXE_GRIP[0]) * AXE_PX, (AXE_GRIP[1] - AXE_GLINT[1]) * AXE_PX], P.restRot);
    const glint = { x: grip.x + P.rest[0] + gOff[0], y: grip.y + P.rest[1] + gOff[1] };
    const [ax, az] = P.appleSlot;
    const apple = yawToScreen([ax, 0, az]);
    const plings = P.logSlots.map(([x, z]) => yawToScreen([x, MINI_S + 3, z])).concat([yawToScreen([ax, 0, az])]);
    plings[3].y += ITEM_H + 2;
    const puffs = PUFFS.map(([p]) => yawToScreen(fallen(p, P.landing)));
    const dust = DUST.map(([p]) => { const f = fallen(p, P.landing); return yawToScreen([f[0], 0, f[2]]); });
    const leafFrom = yawToScreen(fallen([0, 60, 0], P.landing));
    const land = LAND.map(([p]) => yawToScreen(p));
    const brk = yawToScreen([0, 8, 0]);
    return { tip, grip, glint, apple, plings, puffs, dust, leafFrom, land, brk };
  }

  // ---- animation ----
  let A = null;
  function K(g, ch, t, v, interp) {
    const [x, y, z] = typeof v === 'number' ? [v, v, v] : v;
    A.getBoneAnimator(g).addKeyframe({ channel: ch, time: q(t), interpolation: interp || 'linear', data_points: [{ x, y, z }] });
  }
  const track = (g, ch, keys, interp) => keys.forEach(([t, v, i]) => K(g, ch, t, v, i || interp));
  const z = r => [0, 0, r];

  function ensureG() {
    if (!Object.keys(G).length) Group.all.forEach(g => { G[g.name] = g; });
  }
  // tree fall angle per frame: tip-over (accelerating) then the bounces; returns [[t, deg]]
  function fallCurve() {
    const keys = [[0, 0], [T.fallStart, 0]];
    const n = Math.round((T.slam - T.fallStart) * FPS);
    for (let f = 1; f <= n; f++) keys.push([T.fallStart + f * DT, P.landing * Math.pow(f / n, P.fallPow)]);
    let tb = T.slam;
    for (const [h, m] of P.bounce) {
      for (let f = 1; f <= m; f++) { const u = f / m; keys.push([tb + f * DT, P.landing - 4 * h * u * (1 - u)]); }
      tb += m * DT;
    }
    return keys;
  }
  function animate() {
    ensureTex(); ensureG();
    Animation.all.slice().forEach(a => a.remove(false));
    A = new Animation({ name: 'icon_loop', length: LEN, loop: 'loop', snapping: FPS }).add(false);
    A.select();
    const L = layout(), warnings = [];
    const add = (a, b) => a.map((v, i) => v + b[i]);

    // axe: still at rest -> anticipation dip -> cock back -> 2-frame whip -> hit-stop -> recoil wobble
    const I = T.impact, W = T.windStart, rr = P.restRot, R = P.rest, E = T.stopEnd;
    track(G.axe, 'rotation', [
      [0, z(rr), 'catmullrom'], [W, z(rr), 'catmullrom'],
      [W + 0.08, z(rr + 6), 'catmullrom'], [W + 0.16, z(P.cockedRot + 12), 'catmullrom'], [T.cocked, z(P.cockedRot)],
      [T.mid, z(-8)], [I, z(P.impactRot)], [I + 0.04, z(P.impactRot + 3)], [E, z(P.impactRot + 3)],
      [E + 0.04, z(12), 'catmullrom'], [E + 0.08, z(-4), 'catmullrom'], [E + 0.16, z(rr - 12), 'catmullrom'],
      [E + 0.26, z(rr + 9), 'catmullrom'], [E + 0.36, z(rr - 4), 'catmullrom'], [E + 0.48, z(rr + 1.5), 'catmullrom'],
      [E + 0.60, z(rr), 'catmullrom'], [LEN, z(rr), 'catmullrom'],
    ]);
    track(G.axe, 'position', [
      [0, R, 'catmullrom'], [W, R, 'catmullrom'],
      [W + 0.08, add(R, [-1, -1, 0]), 'catmullrom'], [W + 0.16, add(P.cocked, [-2, -2, 0]), 'catmullrom'], [T.cocked, P.cocked],
      [T.mid, [2, 4, 0]], [I, [0, 0, 0]], [I + 0.04, [-0.6, -0.4, 0]], [E, [-0.6, -0.4, 0]],
      [E + 0.04, [3, 2, 0], 'catmullrom'], [E + 0.12, add(R, [0, 4, 0]), 'catmullrom'], [E + 0.24, R, 'catmullrom'],
      [LEN, R, 'catmullrom'],
    ]);
    track(G.axe, 'scale', [
      [0, 1], [W, 1], [W + 0.08, [1.05, 0.95, 1]], [T.cocked, [0.92, 1.12, 1]], [T.mid, [1.1, 0.94, 1]],
      [I, [1.06, 0.95, 1]], [E, [1.06, 0.95, 1]], [E + 0.04, [0.95, 1.06, 1]], [E + 0.12, 1], [LEN, 1],
    ]);
    const smearRot = th => TRAIL_DIR + th - 2 - 180;
    track(G.smear, 'rotation', [[T.mid, z(smearRot(-8)), 'step'], [I, z(smearRot(P.impactRot)), 'step']]);
    track(G.smear, 'position', [[T.mid, [2, 4, 0], 'step'], [I, [0, 0, 0], 'step']]);
    track(G.smear, 'scale', [[0, 0, 'step'], [T.mid, 1, 'step'], [I, 0.85, 'step'], [I + 0.04, 0, 'step']]);
    track(G.glint, 'scale', [[0, 0], [T.glint, 0], [T.glint + 0.04, 0.6], [T.glint + 0.08, 1.2], [T.glint + 0.12, 0.7], [T.glint + 0.16, 0]]);
    track(G.glint, 'rotation', [[T.glint, z(0)], [T.glint + 0.16, z(45)]]);

    // chop FX
    track(G.star, 'scale', [[0, 0], [I - 0.04, 0], [I, 1.35], [I + 0.04, 1.0], [I + 0.08, 0.5], [I + 0.12, 0]]);
    track(G.star, 'rotation', [[I, z(0)], [I + 0.12, z(30)]]);
    track(G.ring, 'scale', [[0, 0], [I - 0.04, 0], [I, 0.45], [I + 0.04, 0.9], [I + 0.08, 1.3], [I + 0.12, 0]]);
    SPARKS.forEach((a, k) => {
      const keys = [[0, 0], [I - 0.04, 0]], pos = [];
      for (let f = 0; f <= 4; f++) {
        const t = I + f * DT, d = 17 * (1 - Math.pow(1 - f / 4, 2));
        keys.push([t, f === 4 ? 0 : 1 - f * 0.2]);
        pos.push([t, [Math.cos(a * RAD) * d, Math.sin(a * RAD) * d, 0]]);
      }
      track(G['spark_' + k], 'scale', keys);
      track(G['spark_' + k], 'position', pos);
    });
    const burst = (g, t0, ang, sp, up, frames, spin) => {
      const pos = [], rot = [], grav = 900;
      const vx = Math.cos(ang * RAD) * sp, vy = Math.sin(ang * RAD) * sp + up;
      for (let f = 0; f <= frames; f++) {
        const t = f * DT;
        pos.push([t0 + t, [vx * t, vy * t - grav * t * t / 2, 0]]);
        rot.push([t0 + t, [f * 40, 0, f * spin]]);
      }
      track(g, 'position', pos);
      track(g, 'rotation', rot);
      track(g, 'scale', [[0, 0], [t0 - 0.04, 0], [t0, 1], [t0 + (frames - 3) * DT, 1], [t0 + (frames - 1) * DT, 0.5], [t0 + frames * DT, 0]]);
    };
    CHIPS.forEach((c, k) => burst(G['chip_' + k], I, c.ang, 50 + 10 * k, 45, 7, k % 2 ? 55 : -55));
    const B = T.brk;
    track(G.flash, 'scale', [[0, 0, 'step'], [I, 1, 'step'], [I + 0.04, 0, 'step'], [B - 0.04, 1, 'step'], [B, 0, 'step']]);

    // log_0 shatters: tremble, swell with a second flash, pop into a puff and chips; its log item pops out at once
    const hide = T.poof + 0.04, regrow = T.respawn[0] - 0.04;
    const boingKeys = tr => [[tr, 0], [tr + 0.04, [0.8, 1.3, 0.8]], [tr + 0.08, [1.25, 0.78, 1.25]],
      [tr + 0.12, [0.92, 1.1, 0.92]], [tr + 0.16, [1.04, 0.97, 1.04]], [tr + 0.24, 1], [LEN, 1]];
    track(G.log_0, 'scale', [[0, 1], [I, 1], [I + 0.04, [1.06, 0.95, 1.06]], [B - 0.04, [1.22, 1.05, 1.22]], [B, 0], ...boingKeys(T.respawn[0])]);
    // two puffs burst sideways so the gap under the hanging tree stays visible
    [0, 1].forEach(k => {
      const g = G['brk_puff_' + k], dx = k ? 1 : -1;
      track(g, 'scale', [[0, 0], [B - 0.04, 0], [B, 1.2], [B + 0.04, 1.0], [B + 0.08, 0.6], [B + 0.12, 0.25], [B + 0.16, 0]]);
      track(g, 'position', [[B, [0, 0, 0]], [B + 0.04, [dx * 6, 2, 0]], [B + 0.08, [dx * 10, 4, 0]], [B + 0.16, [dx * 13, 6, 0]]]);
    });
    BRK_CHIPS.forEach((c, k) => burst(G['bchip_' + k], B, c.ang, 60 + 8 * k, 55, 8, k % 2 ? 60 : -60));

    // the rest hangs (cartoon beat), drops one block, lands with a squash and dust
    track(G.sink, 'position', [
      [0, [0, 0, 0]], [B, [0, 0, 0]], [B + 0.04, [0.5, 0, 0]], [T.drop, [0, 0, 0]], [T.drop + 0.04, [0, -4, 0]],
      [T.land, [0, -16, 0], 'step'], [regrow, [0, 0, 0], 'step'],
    ]);
    track(G.sink, 'scale', [[0, 1], [T.drop, 1], [T.drop + 0.04, [0.95, 1.06, 0.95]], [T.land, [1.1, 0.86, 1.1]],
      [T.land + 0.04, [0.95, 1.06, 0.95]], [T.land + 0.08, 1]]);
    L.land.forEach((p, i) => {
      const t0 = T.land;
      track(G['land_' + i], 'scale', [[0, 0], [t0 - 0.04, 0], [t0, 0.7], [t0 + 0.04, 1.2], [t0 + 0.08, 0.9], [t0 + 0.12, 0.4], [t0 + 0.16, 0]]);
      track(G['land_' + i], 'position', [[t0, [0, 0, 0]], [t0 + 0.16, [i ? 5 : -5, 3, 0]]]);
    });

    // tree: lean back and creak, tip over (accelerating), slam, bounces, rest, poof
    // lean back fast, then hold the pose (a held pose costs almost no GIF bytes; a wiggle redraws the whole tree)
    track(G.lean, 'rotation', [
      [0, z(0)], [T.leanStart, z(0)], [T.leanStart + 0.04, z(P.lean * 0.6)], [T.leanPeak, z(P.lean)], [T.fallStart, z(P.lean)],
      [T.fallStart + 0.08, z(P.lean * 0.4)], [T.fallStart + 0.16, z(0)],
    ]);
    const curve = fallCurve();
    track(G.fall, 'rotation', curve.map(([t, d]) => [t, z(d)]).concat([[hide, z(P.landing), 'step'], [regrow, z(0), 'step']]));
    track(G.fall, 'scale', [[0, 1, 'step'], [hide, 0, 'step'], [regrow, 1, 'step']]);
    const swap = curve.find(([t, d]) => t > T.fallStart && d >= 45)[0];
    track(G.caps, 'scale', [[0, 0, 'step'], [swap, 1, 'step'], [hide, 0, 'step']]);
    const S0 = T.slam;
    track(G.yaw, 'position', [[S0 - 0.04, [0, 0, 0]], [S0, [0, -1.6, 0]], [S0 + 0.04, [0, 0, 0]]]);
    L.dust.forEach((p, i) => {
      const t0 = S0 + i * 0.04;
      track(G['dust_' + i], 'scale', [[0, 0], [t0 - 0.04, 0], [t0, 0.7], [t0 + 0.04, 1.2], [t0 + 0.08, 1.0], [t0 + 0.12, 0.5], [t0 + 0.16, 0]]);
      track(G['dust_' + i], 'position', [[t0, [0, 0, 0]], [t0 + 0.16, [(i - 1) * 4, 5, 0]]]);
    });
    LEAFBITS.forEach((c, k) => burst(G['leafbit_' + k], S0, c.ang, 70 + 12 * k, 60, 9, k % 2 ? 50 : -50));

    L.puffs.forEach((p, i) => {
      const t0 = T.poof + (i % 2) * 0.04;
      track(G['puff_' + i], 'scale', [[0, 0], [t0 - 0.04, 0], [t0, 0.8], [t0 + 0.04, 1.3], [t0 + 0.08, 1.15], [t0 + 0.12, 0.7], [t0 + 0.16, 0.3], [t0 + 0.20, 0]]);
      track(G['puff_' + i], 'position', [[t0, [0, 0, 0]], [t0 + 0.20, [0, 5, 0]]]);
    });

    // regrow: logs pop up bottom-first with an elastic boing, then the crown (which also splats at the slam)
    [1, 2].forEach(i => track(G['log_' + i], 'scale', [[0, 1], [hide, 1], [hide + 0.04, 0], ...boingKeys(T.respawn[i])]));
    track(G.crown, 'scale', [[0, 1], [S0 - 0.04, 1], [S0, [0.82, 1.08, 1.1]], [S0 + 0.04, [1.06, 0.97, 0.97]], [S0 + 0.08, 1],
      [hide, 1], [hide + 0.04, 0], ...boingKeys(T.crown)]);

    // drops: 3 mini logs (yaw space) + the apple (screen space) arc out of the poof, land, bounce, get picked up
    const flight = (start, H, n) => {
      const Tf = n * DT, dy = -start[1];
      const g = Math.pow((Math.sqrt(2 * H) + Math.sqrt(2 * (H - dy))) / Tf, 2), vy = Math.sqrt(2 * g * H);
      return t => [start[0] - start[0] * t / Tf, start[1] + vy * t - g * t * t / 2, start[2] - start[2] * t / Tf];
    };
    const drop = (it, spin, spinAxis, start, i, up, t0) => {
      const n = P.flight[i], path = flight(start, P.apex[i], n), tu = T.pickup[i];
      const pos = [[0, start, 'step'], [t0 - 0.04, start]], rotK = [];
      for (let f = 0; f <= n; f++) {
        const p = path(f * DT);
        pos.push([t0 + f * DT, spinAxis === 'y' ? p : [p[0], p[1], 0]]);
        const r = 360 * (1 - f / n) * (start[0] > 0 ? -1 : 1);
        rotK.push([t0 + f * DT, spinAxis === 'y' ? [0, r, 0] : [0, 0, r]]);
      }
      let tb = t0 + n * DT;
      const sc = [[0, 0], [t0 - 0.04, 0], [t0, 0.6], [t0 + 0.04, 1.2], [t0 + 0.08, 1], [tb - 0.04, [0.9, 1.1, 0.9]], [tb, [1.35, 0.65, 1.35]]];
      P.dropBounce.forEach(([h, m], j) => {
        for (let f = 1; f <= m; f++) { const u = f / m; pos.push([tb + f * DT, [0, 4 * h * u * (1 - u), 0]]); }
        sc.push([tb + DT, j ? [0.95, 1.06, 0.95] : [0.85, 1.2, 0.85]]);
        if (m > 2) sc.push([tb + 2 * DT, 1]);
        tb += m * DT;
        sc.push([tb, j ? [1.1, 0.9, 1.1] : [1.22, 0.8, 1.22]]);
      });
      sc.push([tb + DT, [0.96, 1.04, 0.96]], [tb + 2 * DT, 1]);
      if (tb + 2 * DT > tu + 1e-6) warnings.push(`drop ${i} still bouncing at pickup (${tb.toFixed(2)} > ${tu})`);
      pos.push([tu, [0, 0, 0]], [tu + 0.04, [0, up * 0.4, 0]], [tu + 0.08, [0, up * 0.8, 0]], [tu + 0.12, [0, up, 0]]);
      sc.push([tu, [0.9, 1.15, 0.9]], [tu + 0.04, 1.3], [tu + 0.08, 0.6], [tu + 0.12, 0]);
      track(it, 'position', pos);
      track(it, 'scale', sc);
      track(spin, 'rotation', rotK);
      const pl = G['pling_' + i];
      track(pl, 'scale', [[0, 0], [tu, 0], [tu + 0.04, 0.5], [tu + 0.08, 1.3], [tu + 0.12, 0.9], [tu + 0.16, 0]]);
      track(pl, 'rotation', [[tu, [0, 0, 0]], [tu + 0.16, [0, 0, 45]]]);
    };
    P.logSlots.forEach(([sx, sz], i) => {
      const from = i ? fallen([0, 16 * i + 8, 0], P.landing) : [0, 8, 0];
      drop(G['drop_' + i], G['drop_spin_' + i], 'y', [from[0] - sx, from[1] - MINI_S / 2, from[2] - sz], i, 6, i ? T.launch : B);
    });
    const af = yawToScreen(fallen(PUFFS[3][0], P.landing));
    drop(G.apple, G.apple_spin, 'z', [af.x - L.apple.x, af.y - ITEM_H / 2 - L.apple.y, 0], 3, 6, T.launch);

    Animator.preview();
    return warnings.length ? warnings.join('; ') : layoutInfo();
  }
  function layoutInfo() {
    const L = layout(), r = v => Math.round(v * 10) / 10;
    return JSON.stringify({ grip: [r(L.grip.x), r(L.grip.y)], tip: [r(L.tip.x), r(L.tip.y)], apple: [r(L.apple.x), r(L.apple.y)], puffs: L.puffs.map(p => [r(p.x), r(p.y)]) });
  }

  // ---- camera + render ----
  function camera(zoom) {
    const p = Preview.selected;
    p.setProjectionMode(true);
    const pos = CAM_POS.map((v, i) => v + CAM_PAN[i]), tgt = CAM_TARGET.map((v, i) => v + CAM_PAN[i]);
    p.camera.position.set(...pos);
    p.controls.target.set(...tgt);
    p.camera.lookAt(...tgt);
    p.camera.zoom = zoom || CAM_ZOOM; p.camera.updateProjectionMatrix();
    p.controls.update();
  }
  function setTime(t) {
    Timeline.setTime(t);
    Animator.preview();
  }
  function render(first, last, res, dir) {
    res = res || 1600;
    dir = dir || DIR + 'frames/';
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const shot = f => new Promise(done => {
      setTime(f * DT);
      Screencam.advancedScreenshot(Preview.selected, { angle_preset: 'view', resolution: [res, res], anti_aliasing: 'none', shading: false }, url => {
        fs.writeFileSync(dir + 'frame_' + String(f).padStart(3, '0') + '.png', Buffer.from(url.split(',')[1], 'base64'));
        done();
      });
    });
    return (async () => { for (let f = first; f <= last; f++) await shot(f); return `rendered ${first}..${last}`; })();
  }
  function scaleSweep() {
    const bad = [];
    for (let f = 0; f <= Math.round(LEN * FPS); f++) {
      setTime(f * DT);
      Group.all.forEach(g => { const s = g.mesh.scale; if (s.x < 0 || s.y < 0 || s.z < 0) bad.push(g.name + '@' + f); });
    }
    return bad.length ? bad.join(',') : 'no negative scale';
  }
  // world position of a group's pivot at time t, for numeric checks
  function probe(name, t) {
    setTime(t);
    const v = new THREE.Vector3();
    G[name].mesh.getWorldPosition(v);
    return v.toArray().map(n => Math.round(n * 10) / 10);
  }

  return { FPS, DT, LEN, T, P, PITCH, G, tex, loadTextures, build, animate, camera, render, setTime, scaleSweep, probe, layout, layoutInfo, pixels, worldToScreen, yawToWorld, yawToScreen, fallen, q };
})();
