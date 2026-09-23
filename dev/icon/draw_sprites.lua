-- Timber icon sprites. Run through the aseprite MCP: dofile("<abs>/Timber/dev/icon/draw_sprites.lua")
dofile("/home/emppu/Projects/Minecraft Datapacks/.claude/skills/pack-icon-animation/assets/pixel_art.lua")
local OUT = "/home/emppu/Projects/Minecraft Datapacks/Timber/dev/icon/sprites/"

local bark = { Z = "#2B1712", a = "#43251A", b = "#633922", c = "#85522E", d = "#A8703C", e = "#C8944F", f = "#E2B66A" }
local wood = { g = "#E8C27E", k = "#C99A58", h = "#9E6C36" }
local leaf = { Z = "#0F3325", p = "#17492D", q = "#236B33", r = "#378F38", s = "#5BB23E", t = "#8ED14E", u = "#C3EA6C" }
local iron = { O = "#2A2E3A", D = "#6E7486", M = "#A8AEBC", L = "#CDD2DC", P = "#EEF1F5", X = "#FFFFFF" }
local handle = { Q = "#2E1D0B", V = "#5E3F1B", w = "#9C6D35", h = "#C0904E" }
local red = { O = "#4A0B24", A = "#8E1530", B = "#C9272E", C = "#E84A3A", D = "#FF7F5E", E = "#FFD2B8" }
local function merge(...)
  local out = {}
  for _, t in ipairs({ ... }) do for k, v in pairs(t) do out[k] = v end end
  return out
end
-- one and two ramp steps darker, for the left (west) and right (south) faces; the render uses shading:false
local function steps(ramp, order, n)
  local m = {}
  for i = 1, #order do
    local src = order:sub(i, i)
    m[ramp[src]] = ramp[order:sub(math.max(1, i - n), math.max(1, i - n))]
  end
  return m
end
local function sides(name, ramp, order)
  PA.remap(OUT .. name .. ".aseprite", OUT .. name .. "_left", steps(ramp, order, 1))
  PA.remap(OUT .. name .. ".aseprite", OUT .. name .. "_right", steps(ramp, order, 2))
end

-- oak bark: three plates split by wandering grooves, one knot; 1px bevel (light top/left, dark bottom/right)
PA.sprite_from_grid(OUT .. "log_side", {
  "eeeeeeeeeeeeeeed",
  "fedcbedddcbeddcb",
  "fedcbedddcbedccb",
  "fedcbedcdcbeddcb",
  "fedcaedcdcaeddcb",
  "fedcbedddcaedccb",
  "fecbeddddcbedccb",
  "fecaeddddcbeddcb",
  "fecaedbbdcbeddcb",
  "fecbebccbcbeddcb",
  "fecbedbbdcaedccb",
  "fedcbedddcaeddcb",
  "fedcbedddcbedccb",
  "fedcaedcdcbeddcb",
  "fedcbedcdcbeddcb",
  "cbbbbbbbbbbbbbbb",
}, bark)
sides("log_side", bark, "Zabcdef")
PA.whiten(OUT .. "log_side.aseprite", OUT .. "hit_flash", 0.72)

-- leaves: round clumps (highlight top-left, shadow bottom-right) over a darker gap colour; tiles seamlessly
PA.sprite_from_grid(OUT .. "leaves", {
  "ttttttttttttttts",
  "trttsrrrtutssrrq",
  "ttutssrrttsssrrq",
  "tttsssrrtsssqrrq",
  "ttsssqrrrsqqrrrq",
  "trsqqrrrrrrrrttq",
  "trrrrttsrrrrtutq",
  "trrrtutssrrrttsq",
  "trrrttsssrrrtssq",
  "trrrtsssqrrrrsqq",
  "trrrrsqqrrttsrrq",
  "tttsrrrrrtutssrq",
  "tutssrrrrttssstq",
  "tssssrrrrtssstuq",
  "tsssqrrrrrsqqttq",
  "tqqqqqqqqqqqqqqq",
}, leaf)
sides("leaves", leaf, "Zpqrstu")

-- dropped log: 8px textures so the mini cube keeps readable rings and bark at icon size
PA.sprite_from_grid(OUT .. "mini_log_top", {
  "cccccccb",
  "cggggggb",
  "cghhhhgb",
  "cghkkhgb",
  "cghkkhgb",
  "cghhhhgb",
  "cggggggb",
  "bbbbbbba",
}, merge(bark, wood))
PA.sprite_from_grid(OUT .. "mini_log_side", {
  "eeeeeeed",
  "fdcbedcb",
  "fdcbedcb",
  "fdcaedcb",
  "fdcbedcb",
  "fdcbeccb",
  "fdcbedcb",
  "cbbbbbbb",
}, bark)
sides("mini_log_side", bark, "Zabcdef")

-- iron axe, upright: blade left, handle end showing through the eye, grip wrap near the bottom.
-- 16x24 art padded to 24x24 (Blockbench animates any texture taller than wide).
PA.sprite_from_grid(OUT .. "axe_item", {
  "........................",
  "..OO....................",
  ".OPLO....VhQ............",
  ".PXMLO..OOOOO...........",
  ".PPMMLOOLLLLO...........",
  ".PPMMMLLMMMDO...........",
  ".PPMMMMMMMMDO...........",
  ".PPMMMDDDDDDO...........",
  ".PPMMDOOOOOOO...........",
  ".PPMDO...VwQ............",
  ".OPDO....VhQ............",
  "..OO.....VwQ............",
  ".........VhQ............",
  ".........VwQ............",
  ".........VhQ............",
  ".........VwQ............",
  ".........VhQ............",
  ".........VwQ............",
  ".........QVQ............",
  ".........QVQ............",
  ".........VhQ............",
  ".........VwQ............",
  ".........QQQ............",
  "........................",
}, merge(iron, handle))

PA.sprite_from_grid(OUT .. "apple_item", {
  "................",
  "................",
  ".........qqq....",
  "........Vqstq...",
  "........Vtqq....",
  "....OOO.VOOO....",
  "...ODEDOOBBBO...",
  "..OCDEDCBBBBBO..",
  "..OCDDCBBBBBAO..",
  "..OCCCBBBBBAAO..",
  "..OCCBBBBBBAAO..",
  "..OBBBBBBBAAAO..",
  "...OBBBBBAAAO...",
  "....OAAOOAAO....",
  ".....OO..OO.....",
  "................",
}, merge(red, { V = handle.V, q = leaf.q, s = leaf.s, t = leaf.t }))

-- debris tiles (4x4 each): bark chip, pale wood chip, leaf, dark leaf
PA.sprite_from_grid(OUT .. "chunks", {
  "eddcgggktutsrsrq",
  "dccbggkhtsssssrq",
  "dcbbgkkhssrqrrqp",
  "cbbakhhhsrqqrqpp",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
}, merge(bark, wood, leaf))

-- swing smear: 40 deg arc around the bottom-left corner (the axe only turns ~35 deg per frame), 5px thick at the
-- bottom end tapering to the tip; white outer edge, pale steel inner
PA.sprite_from_grid(OUT .. "fx_smear", {
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "...........aa...",
  "...........baa..",
  "...........bbaa.",
  "...........bbaa.",
  "...........bbbaa",
  "...........bbbaa",
  "...........bbbaa",
  "...........bbbaa",
  "...........bbbaa",
  "...........bbbaa",
}, { a = "#FFFFFF", b = "#D6E4F0" })

-- backgrounds: flat sky blue plus a ground-shadow ellipse, tilted along the ground line the tree falls onto.
-- Placed for the current crop (icon 64px grid, x8 = 512px), so move it if the camera, crop or fall changes.
local SKY, SHADOW = "#4B98D8", "#3B7CBC"
local function shadow_bg(path, w, h, cx, cy, a, b, deg, extra)
  local px, t = {}, math.rad(deg)
  for y = 0, h - 1 do
    for x = 0, w - 1 do
      local dx, dy = x + 0.5 - cx, y + 0.5 - cy
      local u, v = (dx * math.cos(t) + dy * math.sin(t)) / a, (-dx * math.sin(t) + dy * math.cos(t)) / b
      px[PA.key(x, y)] = (u * u + v * v <= 1) and SHADOW or SKY
    end
  end
  for k, c in pairs(extra or {}) do px[k] = c end
  PA.save_pixels(path, w, h, px)
end
shadow_bg(OUT .. "bg_flat", 64, 64, 31, 52, 27, 6.5, -11)

-- banner (192x64, x8): same shadow under the art (banner camera zoom 0.1, ART_OFFSET in make_banner.py) plus pixel clouds
local CLOUDS = {
  { "..wwww.....", ".wwwwww.ww.", "wwwwwwwwwww", ".sssssssss." },
  { "...ww...", ".wwwwww.", "wwwwwwww", ".ssssss." },
}
local cloud_px = {}
for _, c in ipairs({ { 1, 6, 4 }, { 2, 78, 9 }, { 1, 98, 51 }, { 2, 180, 8 }, { 2, 30, 55 } }) do
  local shape, x0, y0 = CLOUDS[c[1]], c[2], c[3]
  for y = 1, #shape do
    for x = 1, #shape[y] do
      local ch = shape[y]:sub(x, x)
      if ch ~= "." then cloud_px[PA.key(x0 + x - 1, y0 + y - 1)] = (ch == "w") and "#DDEEFA" or "#AFD3F0" end
    end
  end
end
shadow_bg(OUT .. "banner_bg", 192, 64, 142, 52.2, 26, 6.3, -11, cloud_px)

-- banner lettering: leaf-green title bands with a dark green extrude; tagline white + green accent
PA.title_sprite(OUT .. "banner_title", "TIMBER", {
  bands = {"#F2FFC0","#C3EA6C","#C3EA6C","#C3EA6C","#8ED14E","#8ED14E","#8ED14E","#5BB23E","#5BB23E","#5BB23E"},
  extrude = {"#236B33", "#17492D"},
})
PA.label_sprite(OUT .. "banner_tagline", "ONE CHOP. WHOLE TREE.", function(i) return i > 10 and "#8ED14E" or "#FFFFFF" end)
