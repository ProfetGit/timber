package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.vanillatimber.core.Timeline;
import io.github.profetgit.vanillatimber.server.Config.Key;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Axe durability: the player's worn axe has a few uses left, shown in its hotbar slot. With the option on every log of
// the tree costs one; a tree that would break the axe still falls and the axe breaks. Off, only the chopped log costs
// one, as in vanilla.
final class DurabilityScene extends TreeScene {
    private static final float START = 350, REST = 1500, CLEAR = 300;
    private static final int LEFT = 3;
    private final Grove.Tree tree = new Grove.Tree(Grove.OAK, 4);
    private final Timeline tl = tree.timeline(false, 0);
    private ItemStack axe;

    private ItemStack axe() {
        if (axe == null) {
            try {
                //? if >=1.21.9 {
                axe = new ItemStack(Items.COPPER_AXE);
                //?} else {
                /*axe = new ItemStack(Items.IRON_AXE);
                *///?}
            } catch (RuntimeException e) {
                // before a world has loaded item components are not bound: draw the slot empty
                return null;
            }
        }
        return axe;
    }

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        boolean wear = on(Key.DURABILITY);
        float broke = START + MINE;
        float period = broke + tl.end * MS + REST + CLEAR;
        float time = time(c, period, wear);
        float cx = cx(x, w), floor = y + STUMP_Y;
        Diorama.backdrop(g, x + (w - Diorama.W) / 2, y);
        int logs = 0;
        for (boolean b : tree.logRings) if (b) logs++;
        int cost = wear ? logs : 1;
        boolean broken = time >= broke && cost >= LEFT;

        chopAndFall(g, tree, tl, cx, floor, time, START);
        painter.flush(g);
        burst(g, tree, tl, cx, floor, time, broke);
        if (!broken || time < broke + 80) mine(g, cx, floor, time, START);
        attack(g, c, x, y, w, time >= START && time < broke);

        // the hotbar slot
        int sx = x + w - 30, sy = y + 8;
        slot(g, sx, sy);
        ItemStack s = axe();
        if (s != null && !broken) {
            s.setDamageValue(s.getMaxDamage() - (time >= broke ? Math.max(1, LEFT - cost) : LEFT));
            item(g, s, sx + 1, sy + 1);
        }
        if (broken) shards(g, sx + 9, sy + 9, (time - broke) / MS);
        float clear = period - CLEAR;
        if (time >= clear) Diorama.curtain(g, x, y, w, h, (time - clear) / (CLEAR - 30));
        if (!wear) Diorama.note(g, x, y, w, h, t("off.durability"));
    }

    // vanilla's item-break crumbs: bits of the axe's own texture thrown out of the slot, falling
    private static void shards(Gfx g, int cx, int cy, double t) {
        if (t > 14) return;
        java.util.Random r = new java.util.Random(5);
        for (int i = 0; i < 6; i++) {
            double vx = (r.nextDouble() - 0.5) * 3, vy = -1.5 - r.nextDouble() * 1.5;
            double px = cx + vx * t, py = cy + vy * t + 0.25 * t * t;
            int u = r.nextInt(12), v = r.nextInt(12);
            g.pushTranslate((float) px, (float) py);
            g.blit(Tool.itemTexture(), 0, 0, u, v, 3, 3, 3, 3, 16, 16, 0xFFFFFFFF);
            g.pop();
        }
    }
}
