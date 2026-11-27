package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.profetsettings.Preview;
import java.util.Objects;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

// What the preview scenes share: the loop clock, tool swings, vanilla's item physics, keycaps. Every movement a scene
// shows must come from the mod's own curve functions with the config as it is right now; only vanilla's physics is
// repeated here. Curve times are in ticks (50 ms).
abstract class Stage extends Preview {
    static final float MS = 50;
    // ms the raise clip lasts: it ends in the first hit
    static final float RAISE = 250;

    static Identifier tex(String block) {
        return Identifier.tryParse("textures/block/" + block + ".png");
    }

    static Identifier itemTex(String item) {
        return Identifier.tryParse("textures/item/" + item + ".png");
    }

    // restarts the loop when any of the values the scene depends on changes
    float time(Ctx c, float period, Object... settings) {
        return clock(c, Objects.hash(settings), period);
    }

    static String t(String key, Object... args) {
        return I18n.get("vanilla_timber.preview." + key, args);
    }

    // ---- the tools ----

    static int rest() {
        return Diorama.FOLLOW + Diorama.FOLLOW_FRAMES - 1;
    }

    // a tool's frame at {@code time}: at rest, raising into the first hit at {@code first}, swinging until the block
    // breaks at {@code end}, then following through. The swing is vanilla's 300 ms one, or the {@code quick} one that
    // keeps time with the 200 ms between mining hits.
    static int swing(float time, float first, float end, boolean quick) {
        if (time < first - RAISE) return rest();
        if (time < first) return Diorama.RAISE + frame(time - (first - RAISE), Diorama.RAISE_FRAMES);
        if (time >= end) return Diorama.FOLLOW + frame(time - end, Diorama.FOLLOW_FRAMES);
        int clip = quick ? Diorama.MINE : Diorama.SWING, frames = quick ? Diorama.MINE_FRAMES : Diorama.SWING_FRAMES;
        // the nudge keeps a whole number of frames from rounding down
        return clip + (int) ((time - first) * 60 / 1000 + 0.01f) % frames;
    }

    // the frame of a 60 fps clip {@code ms} after it started
    static int frame(float ms, int count) {
        return Math.max(0, Math.min(count - 1, (int) (ms * 60 / 1000)));
    }

    // ---- vanilla's items ----

    // how high vanilla's bob holds an item {@code t} ticks after it appeared, in blocks
    static double bob(double t) {
        return 0.1 - 0.1 * Math.cos(t / 10);
    }

    // vanilla's flight of an item entity after {@code t} ticks: {x, y, ticks since it touched down or -1}
    static double[] fall(double x, double y, double vx, double vy, double t) {
        double px = x, py = y, landed = -1;
        for (int i = 0; i <= (int) t; i++) {
            px = x;
            py = y;
            if (landed < 0) {
                vy -= 0.04;
                x += vx;
                y += vy;
                vx *= 0.98;
                vy *= 0.98;
                if (y <= 0) {
                    y = 0;
                    landed = i + 1;
                }
            }
        }
        double a = t - (int) t;
        return new double[] {px + (x - px) * a, py + (y - py) * a, landed < 0 || t < landed ? -1 : t - landed};
    }

    // a key cap showing the player's own binding, centred on cx
    static void key(Gfx g, Ctx c, KeyMapping mapping, float cx, int y, boolean pressed) {
        String label = mapping.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
        int kw = (int) Math.ceil(g.width(Gfx.Face.FIVE_B, label, c.sz.fs)) + 14;
        g.keycap(label, Math.round(cx - kw / 2f), y, c.sz.fs, pressed);
    }

    // a key the scene holds down: the player's binding, rimmed in the accent colour (as a mouse button lights up)
    static void heldKey(Gfx g, Ctx c, KeyMapping mapping, float cx, int y, boolean held) {
        g.binding(mapping, cx, y, c.sz.fs, held);
        if (!held) return;
        String label = mapping.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
        int kw = (int) Math.ceil(g.width(Gfx.Face.FIVE_B, label, c.sz.fs)) + 14;
        int left = Math.round(cx - kw / 2f);
        g.outline(left - 2, y - 1, kw + 4, 18, 0xFFF0A566);
    }

    static int ms(double ticks) {
        return (int) Math.round(ticks * MS);
    }
}
