package io.github.profetgit.vanillatimber.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The ground shadow of a falling tree, for vanilla rendering (a shader pack casts the real one). The standing tree's
 * shadow is the light engine's, held by {@link LightHold} for a few ticks after the chop; from then on this casts it:
 * every leaf and log of the live mesh counts as sky-light opacity in its (x,z) column (leaf 1, log 15, like the engine),
 * the columns are relaxed sideways like the engine floods light, and the result darkens the ground with the translucent
 * quads vanilla uses for entity shadows. One {@link Zone} per fall, a dense grid around its pivot; it outlives the fall
 * until its strength has faded out. Ported from SeamlessDisplays' TreeShadow.
 */
public final class GroundShadow {
    /** Off with -Dvanilla_timber.groundShadow=false (the tests switch it for before/after sheets). */
    public static volatile boolean on = !"false".equals(System.getProperty("vanilla_timber.groundShadow"));
    private static final float STRENGTH = 0.55f, DECAY_PER_TICK = 0.25f, MAX_ALPHA = 0.92f;
    private static final int RELAX = 8, MAX_REACH = 56, GROUND_BUDGET = 300, GROUND_REFRESH = 40;
    private static final List<Zone> ZONES = new ArrayList<>();
    private static int budget;
    private static final Vector3f SCRATCH = new Vector3f();
    private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();

    private GroundShadow() {}

    static boolean enabled() {
        return on;
    }

    static boolean pending() {
        return !ZONES.isEmpty();
    }

    static void start(MeshFall f) {
        if (!on || f.reach > MAX_REACH) return;
        ZONES.add(new Zone(f));
    }

    static void clear() {
        ZONES.clear();
    }

    /** Once per frame, after the falls were drawn: {@code dt} is the time since the last frame in ticks. */
    static void frame(ClientLevel level, double dt, long tick) {
        if (!level.dimensionType().hasSkyLight()) {
            ZONES.clear();
            return;
        }
        budget = GROUND_BUDGET;
        float sky = Mth.clamp(1.0f - level.getSkyDarken() / 15.0f, 0.0f, 1.0f);
        float decay = (float) (dt * DECAY_PER_TICK);
        for (var it = ZONES.iterator(); it.hasNext();) {
            Zone z = it.next();
            z.update(level, sky, decay, tick);
            if (z.fall.dead && z.sx0 > z.sx1) it.remove();
        }
    }

    /** Darkens the ground this frame; {@code pose} maps world coordinates (the pass's camera is already subtracted). */
    static void render(Matrix4f pose, VertexConsumer vc) {
        float fade = LightHold.active() ? 1.0f - LightHold.weight() : 1.0f;
        if (fade <= 0.003f) return;
        long t0 = System.nanoTime();
        float k = STRENGTH * fade;
        for (Zone z : ZONES) z.render(pose, vc, k);
        ClientFalls.spent += System.nanoTime() - t0;
    }

    /** The alpha of the shadow over a ground cell this frame (tests). */
    public static float alphaAt(int x, int z) {
        float fade = LightHold.active() ? 1.0f - LightHold.weight() : 1.0f;
        float a = 0;
        for (Zone zn : ZONES) {
            int i = x - zn.ox, j = z - zn.oz;
            if (i >= 1 && j >= 1 && i < zn.n && j < zn.n) a = Math.max(a, zn.corner(i, j, STRENGTH * fade));
        }
        return a;
    }

    private static float curve(float level) {
        float v = Mth.clamp(level, 0.0f, 15.0f) / 15.0f;
        return v / (4.0f - 3.0f * v);
    }

    static final class Zone {
        final MeshFall fall;
        final int ox, oz, n;
        final float[] opac, g, str, ground;
        final int[] stamp;
        /** Grid box (inclusive) that holds this frame's opacity and the last frame's, and the cells with strength. */
        private int bx0 = 1, bx1 = 0, bz0, bz1, px0 = 1, px1 = 0, pz0, pz1;
        int sx0 = 1, sx1 = 0, sz0, sz1;
        private final int top, bottom;

        Zone(MeshFall f) {
            fall = f;
            int half = (int) Math.ceil(f.reach) + RELAX + 2;
            n = half * 2 + 1;
            ox = Mth.floor(f.x) - half;
            oz = Mth.floor(f.z) - half;
            opac = new float[n * n];
            g = new float[n * n];
            str = new float[n * n];
            ground = new float[n * n];
            stamp = new int[n * n];
            Arrays.fill(ground, Float.NaN);
            top = Mth.floor(f.y0) + 10;
            bottom = Mth.floor(f.y0) - 24;
        }

        /** One tree block this frame: (wx, wy, wz) is its centre, weight its sky-light opacity. */
        void add(double wx, double wz, float weight) {
            double fx = wx - 0.5, fz = wz - 0.5;
            int x0 = Mth.floor(fx) - ox, z0 = Mth.floor(fz) - oz;
            if (x0 < 0 || z0 < 0 || x0 + 1 >= n || z0 + 1 >= n) return;
            float tx = (float) (fx - Math.floor(fx)), tz = (float) (fz - Math.floor(fz));
            int i = x0 * n + z0;
            opac[i] += weight * (1 - tx) * (1 - tz);
            opac[i + n] += weight * tx * (1 - tz);
            opac[i + 1] += weight * (1 - tx) * tz;
            opac[i + n + 1] += weight * tx * tz;
            if (bx0 > bx1) {
                bx0 = x0;
                bx1 = x0 + 1;
                bz0 = z0;
                bz1 = z0 + 1;
            } else {
                bx0 = Math.min(bx0, x0);
                bx1 = Math.max(bx1, x0 + 1);
                bz0 = Math.min(bz0, z0);
                bz1 = Math.max(bz1, z0 + 1);
            }
        }

        void update(ClientLevel level, float sky, float decay, long tick) {
            for (int x = px0; x <= px1; x++) Arrays.fill(opac, x * n + pz0, x * n + pz1 + 1, 0);
            bx0 = 1;
            bx1 = 0;
            if (!fall.dead) fall.shadowBlocks(this);
            px0 = bx0;
            px1 = bx1;
            pz0 = bz0;
            pz1 = bz1;
            int rx0 = 1, rx1 = 0, rz0 = 0, rz1 = 0;
            if (bx0 <= bx1) {
                rx0 = Math.max(0, bx0 - RELAX);
                rx1 = Math.min(n - 1, bx1 + RELAX);
                rz0 = Math.max(0, bz0 - RELAX);
                rz1 = Math.min(n - 1, bz1 + RELAX);
                relax(rx0, rx1, rz0, rz1);
            }
            int ux0 = rx0 > rx1 ? sx0 : sx0 > sx1 ? rx0 : Math.min(rx0, sx0), ux1 = rx0 > rx1 ? sx1 : sx0 > sx1 ? rx1 : Math.max(rx1, sx1);
            int uz0 = rx0 > rx1 ? sz0 : sx0 > sx1 ? rz0 : Math.min(rz0, sz0), uz1 = rx0 > rx1 ? sz1 : sx0 > sx1 ? rz1 : Math.max(rz1, sz1);
            float full = curve(15.0f);
            int nx0 = 1, nx1 = 0, nz0 = 0, nz1 = 0;
            for (int x = ux0; x <= ux1; x++) {
                for (int z = uz0; z <= uz1; z++) {
                    int i = x * n + z;
                    float t = 0;
                    if (x >= rx0 && x <= rx1 && z >= rz0 && z <= rz1 && g[i] < 14.95f) t = (1.0f - curve(g[i]) / full) * sky;
                    float cur = str[i];
                    if (t >= cur) cur = t;
                    else cur = Math.max(t, cur - decay);
                    if (cur < 0.003f) cur = 0;
                    str[i] = cur;
                    if (cur > 0) {
                        if (nx0 > nx1) {
                            nx0 = nx1 = x;
                            nz0 = nz1 = z;
                        } else {
                            nx0 = Math.min(nx0, x);
                            nx1 = Math.max(nx1, x);
                            nz0 = Math.min(nz0, z);
                            nz1 = Math.max(nz1, z);
                        }
                        if (budget > 0 && (stamp[i] == 0 || tick - stamp[i] > GROUND_REFRESH)) {
                            budget--;
                            ground[i] = groundTop(level, ox + x, oz + z);
                            stamp[i] = (int) tick + 1;
                        }
                    }
                }
            }
            sx0 = nx0;
            sx1 = nx1;
            sz0 = nz0;
            sz1 = nz1;
        }

        /** Engine-like sky light (0..15) per column inside the box: down through the opacity, flooded sideways. */
        private void relax(int x0, int x1, int z0, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    float o = opac[x * n + z];
                    g[x * n + z] = o > 0 ? Math.max(0.0f, 15.0f - Math.min(15.0f, o)) : 15.0f;
                }
            }
            for (int pass = 0; pass < RELAX; pass++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        int i = x * n + z;
                        float v = g[i];
                        if (x > x0) v = Math.max(v, g[i - n] - 1.0f);
                        if (z > z0) v = Math.max(v, g[i - 1] - 1.0f);
                        g[i] = v;
                    }
                }
                for (int x = x1; x >= x0; x--) {
                    for (int z = z1; z >= z0; z--) {
                        int i = x * n + z;
                        float v = g[i];
                        if (x < x1) v = Math.max(v, g[i + n] - 1.0f);
                        if (z < z1) v = Math.max(v, g[i + 1] - 1.0f);
                        g[i] = v;
                    }
                }
            }
        }

        /** Top of the ground in a column, or NaN: leaves, logs and blocks without collision (plants, water) are looked through, and only a full-block top takes a quad, as vanilla's entity shadows do. */
        private float groundTop(ClientLevel level, int x, int z) {
            int lo = Math.max(level.getMinY(), bottom);
            for (int y = top; y >= lo; y--) {
                MP.set(x, y, z);
                BlockState s = level.getBlockState(MP);
                if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) continue;
                if (s.getCollisionShape(level, MP).isEmpty()) continue;
                return s.isCollisionShapeFullBlock(level, MP) ? y + 1.0f : Float.NaN;
            }
            return Float.NaN;
        }

        private float corner(int x, int z, float k) {
            float s = at(x - 1, z - 1) + at(x, z - 1) + at(x - 1, z) + at(x, z);
            return Math.min(MAX_ALPHA, s * 0.25f * k);
        }

        private float at(int x, int z) {
            return x < 0 || z < 0 || x >= n || z >= n ? 0 : str[x * n + z];
        }

        void render(Matrix4f pose, VertexConsumer vc, float k) {
            if (sx0 > sx1) return;
            for (int x = sx0 - 1; x <= sx1 + 1; x++) {
                for (int z = sz0 - 1; z <= sz1 + 1; z++) {
                    if (x < 0 || z < 0 || x >= n || z >= n) continue;
                    float c00 = corner(x, z, k), c10 = corner(x + 1, z, k), c01 = corner(x, z + 1, k), c11 = corner(x + 1, z + 1, k);
                    if (c00 + c10 + c01 + c11 < 0.004f) continue;
                    float gy = ground[x * n + z];
                    if (Float.isNaN(gy)) continue;
                    int wx = ox + x, wz = oz + z;
                    vertex(pose, vc, wx, gy, wz, c00);
                    vertex(pose, vc, wx, gy, wz + 1, c01);
                    vertex(pose, vc, wx + 1, gy, wz + 1, c11);
                    vertex(pose, vc, wx + 1, gy, wz, c10);
                }
            }
        }
    }

    private static void vertex(Matrix4f pose, VertexConsumer vc, float x, float y, float z, float alpha) {
        pose.transformPosition(x, y, z, SCRATCH);
        vc.addVertex(SCRATCH.x(), SCRATCH.y(), SCRATCH.z(), (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24 | 0xFFFFFF), 0.5f, 0.5f, 0, 15728880, 0.0f, 1.0f, 0.0f);
    }
}
