#!/usr/bin/env python3
"""Compose the Modrinth description banner (3:1) from Blockbench renders in dev/icon/banner_frames/.

Outputs: dist/banner.png (static, STILL_FRAME) and dist/banner-animated.gif (full loop).
Art: dev/icon/build_scene.js rendered with VM.camera(0.1) -> banner_frames/ (1600px, 1:1, no resampling).
Background, title and tagline: dev/icon/sprites/banner_*.aseprite, scaled up nearest-neighbour."""
import sys
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ICON = ROOT / "dev" / "icon"
SPRITES = ICON / "sprites"
DIST = ROOT / "dist"
W, H = 1536, 512
FPS = 25
STILL_FRAME = 32  # mid-fall, with the chopped log lying at the base
ART_OFFSET = (315, -504)  # banner = frame + offset: the loop's extent centred in x 734..1536 and vertically
OUTLINE = 17
LAYERS = (("banner_bg", 8, (0, 0)), ("banner_title", 10, (70, 146)), ("banner_tagline", 6, (74, 308)))
GIF_LIMIT = 5 * 1024 * 1024
GRID = 8
CORNER = (4, 2, 1, 1)  # pixel-art rounded corners: cells cut from the edge per row, on the 8px grid; symmetric, so rows = columns
KEY = (255, 0, 255)  # GIF palette slot 255, reserved for the transparent corners


def sprite(name: str, scale: int) -> Image.Image:
    im = Image.open(SPRITES / f"{name}.png").convert("RGBA")
    return im.resize((im.width * scale, im.height * scale), Image.NEAREST)


def art(frame: Path) -> Image.Image:
    im = Image.open(frame).convert("RGBA")
    dx, dy = ART_OFFSET
    return im.crop((-dx, -dy, W - dx, H - dy))


def corner_mask() -> Image.Image:
    """255 inside the banner, 0 in the stepped corners."""
    gw, gh = W // GRID, H // GRID
    m = Image.new("L", (gw, gh), 255)
    for row, cut in enumerate(CORNER):
        for x in range(cut):
            for p in ((x, row), (gw - 1 - x, row), (x, gh - 1 - row), (gw - 1 - x, gh - 1 - row)):
                m.putpixel(p, 0)
    return m.resize((W, H), Image.NEAREST)


def compose(a: Image.Image, bg: Image.Image, text: list[tuple[Image.Image, tuple[int, int]]]) -> Image.Image:
    out = bg.copy()
    outline = Image.new("RGBA", (W, H), (10, 12, 18, 0))
    outline.putalpha(a.getchannel("A").filter(ImageFilter.MaxFilter(OUTLINE)))
    out.alpha_composite(outline)
    out.alpha_composite(a)
    for im, pos in text:
        out.alpha_composite(im, pos)
    return out


def main() -> None:
    files = sorted((ICON / "banner_frames").glob("frame_*.png"))
    if not files:
        sys.exit("no frames in dev/icon/banner_frames - render them from Blockbench first (VM.camera(0.1))")
    (bg_name, bg_scale, _), *text_layers = LAYERS
    bg = sprite(bg_name, bg_scale)
    text = [(sprite(name, scale), pos) for name, scale, pos in text_layers]
    frames = [compose(art(f), bg, text).convert("RGB") for f in files]

    DIST.mkdir(exist_ok=True)
    mask = corner_mask()
    still = frames[STILL_FRAME].convert("RGBA")
    still.putalpha(mask)
    still.save(DIST / "banner.png", optimize=True)

    # exact palette: the art uses few colours (median cut over all frames dropped the background purple)
    used = sorted({c for f in frames for _, c in f.getcolors(W * H)})
    if len(used) > 255:
        sys.exit(f"{len(used)} colours - more than a GIF palette holds next to the transparent slot")
    palette = Image.new("P", (1, 1))
    palette.putpalette([v for c in used for v in c] + [0, 0, 0] * (255 - len(used)) + list(KEY))
    corners = mask.point(lambda v: 255 - v)
    gif = []
    for f in frames:
        q = f.quantize(palette=palette, dither=Image.Dither.NONE)
        q.paste(255, mask=corners)
        gif.append(q)
    out = DIST / "banner-animated.gif"
    gif[0].save(out, save_all=True, append_images=gif[1:], duration=1000 // FPS, loop=0, optimize=False, disposal=1,
                transparency=255)

    size = out.stat().st_size
    print(f"dist/banner.png (frame {STILL_FRAME}); {len(frames)} frames -> {out.relative_to(ROOT)} {size / 1024:.0f} KiB "
          f"({'OK' if size <= GIF_LIMIT else 'OVER'} Modrinth 5 MiB gallery limit)")
    if size > GIF_LIMIT:
        sys.exit(1)


if __name__ == "__main__":
    main()
