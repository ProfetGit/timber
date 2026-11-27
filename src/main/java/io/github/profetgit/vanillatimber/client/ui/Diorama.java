package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.profetsettings.Skin;
import java.util.Random;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

// The preview stage: the subject in a soft pool of light on the box's own dark, and its tool: the player's own copper
// pickaxe or axe swung along a Blockbench animation (Tool), or a throwing arm modelled, animated and rendered there
// (dev/preview/scene.js, compose.py). Blocks, chips and items are drawn live from the player's own textures; the ground is not drawn, only its shadows. The art is 2 texture pixels
// per GUI pixel. Block space: 1 = a block, the spot's floor centre is the origin, +x runs to the lower right, +z to the
// lower left.
final class Diorama {
    private Diorama() {}

    // the art is made for a W x H GUI pixel box with the spot's floor centre at (AX, AY); a block is 2 * HX wide and TALL high
    static final int W = 198, H = 150, AX = 99, AY = 71;
    static final float HX = 32, HZ = 16, TALL = 40;
    // screen length of one block seen face on, for camera-facing things (chips, items)
    static final float UNIT = 45.25f;

    private static final Identifier GLOW = art("glow"), SHADOW = art("shadow_block"), ITEM_SHADOW = art("shadow_item"),
        SHADE_TOP = art("shade_top"), SHADE_LEFT = art("shade_left"), SHADE_RIGHT = art("shade_right");
    // warm light on top, cool shade on the right, like the tools' render
    private static final int LIT_TOP = 0xFFFFF5E3, LIT_LEFT = 0xFFD2C2BC, LIT_RIGHT = 0xFF918AA3, LIT_CHIP = 0xFFBBB0A8;

    // an animation sheet as printed by compose.py: the crop's place and cell size in texture pixels, 7 cells a row
    record Sheet(Identifier tex, int x, int y, int w, int h, int rows) {
        void draw(Gfx g, int boxX, int boxY, int frame) {
            art(g, tex, boxX + x / 2f, boxY + y / 2f, frame % 7 * w, frame / 7 * h, w, h, 7 * w, rows * h);
        }
    }

    static final Sheet ARM = new Sheet(art("arm"), 0, 46, 120, 102, 5);
    // the tools' clips in the swing's frames (preview/swing.json)
    static final int SWING = 0, SWING_FRAMES = 18, FOLLOW = 18, FOLLOW_FRAMES = 31, RAISE = 49, RAISE_FRAMES = 16, MINE = 65, MINE_FRAMES = 12;
    // the arm's one clip; the fist lets go on frame RELEASE, at (HAND_X, HAND_Y) from the box's corner
    static final int THROW_FRAMES = 31, RELEASE = 15;
    static final float HAND_X = 46, HAND_Y = 52;

    // the textures of a block's three visible faces
    record Look(Identifier top, Identifier left, Identifier right) {
        static Look all(Identifier tex) {
            return new Look(tex, tex, tex);
        }
    }

    private static Identifier art(String name) {
        return Identifier.fromNamespaceAndPath("vanilla_timber", "textures/gui/preview/" + name + ".png");
    }

    // a piece of art (given in its own pixels) at half size, its top-left corner at (x, y)
    private static void art(Gfx g, Identifier tex, float x, float y, int u, int v, int w, int h, int tw, int th) {
        g.pushTranslate(x, y);
        g.scale(0.5f, 0.5f);
        g.blit(tex, 0, 0, u, v, w, h, tw, th, 0xFFFFFFFF);
        g.pop();
    }

    static void backdrop(Gfx g, int x, int y) {
        art(g, GLOW, x, y, 0, 0, W * 2, H * 2, W * 2, H * 2);
    }

    // the stage fading to empty and back: the box's dark over the subject, then the light again on top of that
    static void curtain(Gfx g, int x, int y, int w, int h, float amount) {
        if (amount <= 0.004f) return;
        amount = Math.min(1, amount);
        g.rect(x, y + 2, w, h - 4, Skin.argb(Math.round(amount * 255), Skin.IN_BG & 0xFFFFFF));
        float a = g.alpha;
        g.alpha *= amount;
        backdrop(g, x + (w - W) / 2, y);
        g.alpha = a;
    }

    static float sx(float cx, double x, double z) {
        return (float) (cx + (x - z) * HX);
    }

    static float sy(float floor, double x, double y, double z) {
        return (float) (floor + (x + z) * HZ - y * TALL);
    }

    // ---- the block ----

    // the block under the break burst's transform: {@code shape} is Bursts.shape's {width, height, size}, applied in three
    // dimensions as the real renderer does (width and height about the floor, size about the centre)
    static void block(Gfx g, float cx, float floor, Look look, double[] shape, int crack) {
        shadow(g, cx, floor, (float) (shape[0] * shape[2]));
        body(g, cx, floor, look, shape, crack);
    }

    // the same without its ground shadow
    static void body(Gfx g, float cx, float floor, Look look, double[] shape, int crack) {
        double wide = shape[0] * shape[2], size = shape[2];
        if (size <= 0.001) return;
        double h = 0.5 * wide, lo = shape[1] * 0.5 * (1 - size), hi = shape[1] * 0.5 * (1 + size);
        float backX = sx(cx, -h, -h), backY = sy(floor, -h, hi, -h);
        float rightX = sx(cx, h, -h), rightY = sy(floor, h, hi, -h);
        float leftX = sx(cx, -h, h), leftY = sy(floor, -h, hi, h);
        float frontX = sx(cx, h, h), frontY = sy(floor, h, hi, h);
        float drop = (float) ((hi - lo) * TALL);
        Identifier cracks = crack < 0 ? null : Stage.tex("destroy_stage_" + Math.min(9, crack));
        face(g, look.top(), cracks, SHADE_TOP, 64, 64, LIT_TOP, backX, backY, rightX - backX, rightY - backY, leftX - backX, leftY - backY);
        face(g, look.left(), cracks, SHADE_LEFT, 64, 80, LIT_LEFT, leftX, leftY, frontX - leftX, frontY - leftY, 0, drop);
        face(g, look.right(), cracks, SHADE_RIGHT, 64, 80, LIT_RIGHT, frontX, frontY, rightX - frontX, rightY - frontY, 0, drop);
    }

    // one textured parallelogram from (ox, oy): the texture's u axis (ux, uy) and v axis (vx, vy), then its light
    private static void face(Gfx g, Identifier tex, Identifier cracks, Identifier shade, int sw, int sh, int tint,
        float ox, float oy, float ux, float uy, float vx, float vy) {
        g.push();
        g.mul(new Matrix3x2f(ux / 16, uy / 16, vx / 16, vy / 16, ox, oy));
        g.blit(tex, 0, 0, 0, 0, 16, 16, 16, 16, tint);
        if (cracks != null) g.blit(cracks, 0, 0, 0, 0, 16, 16, 16, 16, 0x70000000);
        g.pop();
        g.push();
        g.mul(new Matrix3x2f(ux / sw, uy / sw, vx / sh, vy / sh, ox, oy));
        g.blit(shade, 0, 0, 0, 0, sw, sh, sw, sh, 0xFFFFFFFF);
        g.pop();
    }

    static void shadow(Gfx g, float cx, float floor, float size) {
        if (size <= 0.01f) return;
        g.pushTranslate(cx, floor);
        g.scale(size / 2, size / 2);
        float a = g.alpha;
        g.alpha *= Math.min(1, size);
        g.blit(SHADOW, -96, -56, 0, 0, 192, 112, 192, 112, 0xFFFFFFFF);
        g.alpha = a;
        g.pop();
    }

    // ---- chips ----

    // one chip, flown with vanilla's particle physics for {@code age} ticks; drawn only on the asked side of the block's
    // centre, as seen by the camera
    // {@code gone} is the age by which it has to have faded, so a loop can start clean.
    static void chip(Gfx g, Random r, float cx, float floor, Identifier tex, float age, double x, double y, double z, double[] v,
        float gravity, float friction, double scale, float fadeTicks, float gone, boolean front) {
        float life = Math.min((int) (4 / (r.nextFloat() * 0.9f + 0.1f)), gone);
        double quad = 0.1 * (r.nextFloat() * 0.5 + 0.5) * scale;
        float u0 = r.nextFloat() * 12, v0 = r.nextFloat() * 12;
        if (age < 0 || age >= life) return;
        double xd = v[0], yd = v[1], zd = v[2], px = x, py = y, pz = z;
        for (int i = 0; i <= (int) age; i++) {
            px = x;
            py = y;
            pz = z;
            yd -= 0.04 * gravity;
            x += xd;
            y += yd;
            z += zd;
            xd *= friction;
            yd *= friction;
            zd *= friction;
            if (y <= 0) {
                y = 0;
                yd = 0;
                xd *= 0.7;
                zd *= 0.7;
            }
        }
        double a = age - (int) age;
        double rx = px + (x - px) * a, ry = py + (y - py) * a, rz = pz + (z - pz) * a;
        float fade = Math.max(0, Math.min(1, (life - age) / fadeTicks));
        float s = (float) (quad * 2 * UNIT) * fade;
        if (s < 1) return;
        // with no floor drawn, the shadows are what shows where the ground is (the back pass runs first, under everything)
        if (!front) groundShadow(g, sx(cx, rx, rz), sy(floor, rx, 0, rz), s / 20, ry);
        if ((0.61 * (rx + rz) + 0.5 * (ry - 0.5) >= 0) != front) return;
        g.pushTranslate(sx(cx, rx, rz) - s / 2, sy(floor, rx, ry, rz) - s / 2);
        g.scale(s / 8, s / 8);
        g.blit(tex, 0, 0, u0, v0, 8, 8, 4, 4, 16, 16, LIT_CHIP);
        g.pop();
    }

    // ---- items ----

    // half an item as drawn: 1.5 icon pixels per GUI pixel (half a block wide, as in the world)
    static final float ITEM_HALF = 12;
    private static final int[][] COPIES = {{-4, -3}, {4, -4}, {-2, -7}, {5, -8}};

    // a dropped stack standing on the screen point (x, bottom) with its squash; like vanilla, a bigger stack is drawn as
    // more copies (ItemClusterRenderState.getRenderedAmount)
    static void item(Gfx g, Identifier tex, float x, float bottom, double wide, double tall, int count) {
        if (wide <= 0.001 || tall <= 0.001) return;
        int copies = count <= 1 ? 1 : count <= 16 ? 2 : count <= 32 ? 3 : count <= 48 ? 4 : 5;
        g.pushTranslate(x, bottom);
        g.scale((float) (wide * 1.5), (float) (tall * 1.5));
        for (int i = copies - 2; i >= 0; i--) {
            g.pushTranslate(COPIES[i][0] / 1.5f, COPIES[i][1] / 1.5f);
            g.blit(tex, -8, -16, 0, 0, 16, 16, 16, 16, 0xFFB4ACB4);
            g.pop();
        }
        g.blit(tex, -8, -16, 0, 0, 16, 16, 16, 16, 0xFFFFFFFF);
        g.pop();
    }

    // a soft contact shadow, fading as its owner rises {@code height} blocks
    static void groundShadow(Gfx g, float x, float y, float size, double height) {
        float a = g.alpha;
        g.alpha *= (float) Math.max(0, Math.min(1, 1 - height * 0.7));
        g.pushTranslate(x, y);
        g.scale(size / 2, size / 2);
        g.blit(ITEM_SHADOW, -32, -16, 0, 0, 64, 32, 64, 32, 0xFFFFFFFF);
        g.pop();
        g.alpha = a;
    }

    // ---- captions ----

    // the timing line along the bottom edge, or in the accent colour why the scene shows vanilla
    static void caption(Gfx g, int x, int y, int w, int h, String line) {
        caption(g, x, y, w, h, line, 0xFFE9E2E0);
    }

    static void note(Gfx g, int x, int y, int w, int h, String line) {
        caption(g, x, y, w, h, line, 0xFFF0A566);
    }

    private static void caption(Gfx g, int x, int y, int w, int h, String line, int color) {
        java.util.List<String> lines = io.github.profetgit.profetsettings.Rows.wrap(g, line, w - 8, 2);
        int top = y + h - 3 - lines.size() * 10;
        for (int i = 0; i < lines.size(); i++) g.vanilla(lines.get(i), x + (w - g.vanillaWidth(lines.get(i))) / 2, top + i * 10 + 1, color, true);
    }

}
