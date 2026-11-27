package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.vanillatimber.core.Timeline;
import io.github.profetgit.vanillatimber.server.Config.Key;
import net.minecraft.resources.Identifier;

// A zombie stands where the tree comes down. With knockback on, the sweeping crown and trunk shove it along the fall
// (Knock: strength = the trunk's speed at the mob, clamped MIN..MAX, then vanilla's knockback and mob physics), and with
// trunk damage it flashes red (dies at 20). Off, the falling tree passes through it, as displays do in vanilla.
final class KnockScene extends TreeScene {
    private static final float START = 350, REST = 1600, CLEAR = 300;
    // Knock.java: speed in blocks a tick at the mob -> strength
    private static final double PER_SPEED = 1.0, MIN = 0.4, MAX = 1.0;
    // the zombie: where it stands (block space) and its health
    private static final double ZX = -2.5, ZZ = 2.6;
    private static final int HEALTH = 20;
    private static final Identifier SKIN = Identifier.tryParse("textures/entity/zombie/zombie.png");

    private final Grove.Tree tree = new Grove.Tree(Grove.OAK, 4);
    private final Timeline tl = tree.timeline(false, 0);

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        boolean knock = on(Key.KNOCKBACK);
        int damage = get(Key.TRUNK_DAMAGE);
        float broke = START + MINE;
        float period = broke + tl.end * MS + REST + CLEAR;
        float time = time(c, period, knock, damage);
        float cx = cx(x, w), floor = y + STUMP_Y;
        Diorama.backdrop(g, x + (w - Diorama.W) / 2, y);

        // the tick the crown reaches the zombie, and how fast the tree moves there (Knock.test, simplified to the zombie's centre)
        double rho = Math.hypot(Grove.PIVOT_X - ZX, 1.0);
        double reach = Math.toDegrees(Math.atan2(Grove.PIVOT_X - ZX, 3.0));
        int hit = -1;
        double speed = 0;
        for (int n = 1; n <= tl.end; n++) {
            if (tl.p[n] / 100.0 >= reach) {
                hit = n;
                speed = rho * Math.toRadians((tl.p[n] - tl.p[n - 1]) / 100.0);
                break;
            }
        }
        double strength = Math.min(MAX, Math.max(MIN, speed * PER_SPEED));
        double tau = (time - broke) / MS;
        double since = hit < 0 ? -1 : tau - hit;
        boolean struck = knock && since >= 0;
        // vanilla's knockback and mob physics from the hit on
        double zx = ZX, zy = 0;
        if (struck) {
            double vx = -strength, vy = Math.min(0.4, strength), px = zx, py = zy;
            for (int t = 0; t <= (int) since; t++) {
                px = zx;
                py = zy;
                zx += vx;
                zy += vy;
                vy = (vy - 0.08) * 0.98;
                if (zy <= 0) {
                    zy = 0;
                    vy = 0;
                    vx *= 0.546;
                } else vx *= 0.91;
            }
            double a = since - (int) since;
            zx = px + (zx - px) * a;
            zy = py + (zy - py) * a;
        }
        boolean hurt = struck && damage > 0 && since < 10;
        boolean dies = struck && damage >= HEALTH;
        double fallOver = dies ? Math.min(1, since / 20.0) : 0;
        boolean gone = dies && since >= 20;

        chopAndFall(g, tree, tl, cx, floor, time, START);
        if (!gone) {
            Grove.shadow(g, Grove.sx(cx, zx, ZZ), Grove.sy(floor, zx, 0, ZZ), 0.7f, zy);
            zombie(cx, floor, zx, zy, ZZ, fallOver, hurt || dies ? 0xFFFF8F8F : 0xFFFFFFFF);
        }
        painter.flush(g);
        burst(g, tree, tl, cx, floor, time, broke);
        if (gone && since < 28) poofAt(g, cx, floor, zx, ZZ, (float) (since - 20));
        mine(g, cx, floor, time, START);
        attack(g, c, x, y, w, time >= START && time < broke);
        float clear = period - CLEAR;
        if (time >= clear) Diorama.curtain(g, x, y, w, h, (time - clear) / (CLEAR - 30));
        if (!knock) Diorama.note(g, x, y, w, h, t("off.knockback"));
    }

    // vanilla's death smoke: a few of Timber's small puffs
    private static void poofAt(Gfx g, float cx, float floor, double x, double z, float age) {
        if (age < 0) return;
        for (int i = 0; i < 5; i++) {
            double ox = (i % 3 - 1) * 0.3, oz = (i / 3 - 0.5) * 0.4, rise = 0.3 + age * 0.04 + i * 0.1;
            float s = 12;
            g.pushTranslate(Grove.sx(cx, x + ox, z + oz) - s / 2, Grove.sy(floor, x + ox, rise, z + oz) - s / 2);
            g.scale(s / 8, s / 8);
            g.blit(Identifier.tryParse("vanilla_timber:textures/particle/poof_small_" + Math.min(7, (int) age) + ".png"), 0, 0, 0, 0, 8, 8, 8, 8, 8, 8, 0xFFFFFFFF);
            g.pop();
        }
    }

    // the zombie, from its own skin: legs, body, arms held forward, head; facing the player (+x). {@code over} 0..1 tips
    // it onto its side (vanilla's death)
    private void zombie(float cx, float floor, double x, double y, double z, double over, int tint) {
        double ang = over * Math.PI / 2, ca = Math.cos(ang), sa = Math.sin(ang);
        Grove.Painter.Placer pl = (px, py, pz, out) -> {
            // model pixels to blocks; tip about the feet toward +z
            double mx = px / 16, my = py / 16, mz = pz / 16;
            out[0] = x + mx;
            out[1] = y + my * ca - mz * sa;
            out[2] = z + my * sa + mz * ca;
        };
        int[][] leg = {{4, 16, 4, 4}, {0, 20, 4, 12}, {4, 20, 4, 12}, {8, 20, 4, 12}, {12, 20, 4, 12}, {8, 16, 4, 4}};
        int[][] body = {{20, 16, 8, 4}, {20, 20, 8, 12}, {16, 20, 4, 12}, {32, 20, 8, 12}, {28, 20, 4, 12}, {28, 16, 8, 4}};
        int[][] head = {{8, 0, 8, 8}, {8, 8, 8, 8}, {0, 8, 8, 8}, {24, 8, 8, 8}, {16, 8, 8, 8}, {16, 0, 8, 8}};
        // an arm held out forward: its length runs along +x
        int[][] arm = {{44, 20, 4, 12}, {48, 16, 4, 4}, {40, 20, 4, 12}, {44, 16, 4, 4}, {48, 20, 4, 12}, {52, 20, 4, 12}};
        painter.skinned(cx, floor, -2, 0, -4, 2, 12, 0, pl, SKIN, 64, 64, leg, tint);
        painter.skinned(cx, floor, -2, 0, 0, 2, 12, 4, pl, SKIN, 64, 64, leg, tint);
        painter.skinned(cx, floor, -2, 12, -4, 2, 24, 4, pl, SKIN, 64, 64, body, tint);
        painter.skinned(cx, floor, 0, 18, -8, 12, 22, -4, pl, SKIN, 64, 64, arm, tint);
        painter.skinned(cx, floor, 0, 18, 4, 12, 22, 8, pl, SKIN, 64, 64, arm, tint);
        painter.skinned(cx, floor, -4, 24, -4, 4, 32, 4, pl, SKIN, 64, 64, head, tint);
    }
}
