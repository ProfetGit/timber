package io.github.profetgit.vanillatimber.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.profetgit.vanillatimber.core.Layout;
import io.github.profetgit.vanillatimber.core.Rig;
import io.github.profetgit.vanillatimber.core.Timeline;
import io.github.profetgit.vanillatimber.net.TreeFall;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One fall drawn by this client (PRD 5.5): the tree's block models baked once into tree-frame vertices, grouped by
 * (ring, log|leaf, render type), and posed every frame from the same {@link Timeline} the server's displays follow, at
 * the exact angle (no byte steps). The pose of group k is the display rig in one matrix:
 * T(pivot + dy) Ry(-yaw) T(origin k) Rx(pitch) S T(0, -k, 0), applied to v = rest corner + Ry(yaw) m.
 */
final class MeshFall {
    static final Direction[] DIRS = Direction.values();

    final TreeFall msg;
    final Timeline tl;
    final double x, y0, z;
    final float yaw;
    final int kn, kt;
    final List<Group> groups = new ArrayList<>();
    /** Per block: tree-frame centre, ring, leaf, light (eased block/sky and the packed value drawn). */
    final float[] cx, cy, cz, bl, sk;
    final int[] ring, light;
    final boolean[] leaf;
    final int blocks;
    /** The client tick count when the payload was handled, moved to the frame-1 removal when it shows; see {@link #tau}. */
    long c0;
    /** The cell the server clears at frame 1 and its state, while this client still shows it (null: no anchor to wait for). */
    private BlockPos probe;
    private BlockState probeState;
    /** How far the frame-1 anchor moved {@link #c0} (tests; 0 when it agreed or there was none). */
    volatile int shift;
    private final double[] oy, oz;
    private final Timeline.Sample sample = new Timeline.Sample();
    private final float[] pop = new float[2];
    // this frame's face normals (model direction -> world) and their cardinal shade, shared by every group
    final float[] normals = new float[18], shade = new float[6];
    boolean dead;
    /** The pitch of the last drawn frame and its drawing time (tests). */
    volatile double drawnPitch = Double.NaN, drawnTau = Double.NaN;

    MeshFall(TreeFall msg, ClientLevel level, long c0) {
        this.msg = msg;
        this.c0 = c0;
        tl = new Timeline(msg.spec(), msg.logRings());
        x = msg.px() / 1000.0;
        y0 = msg.py() / 1000.0;
        z = msg.pz() / 1000.0;
        yaw = (float) (msg.bin() * 11.25);
        blocks = msg.blocks();
        cx = new float[blocks];
        cy = new float[blocks];
        cz = new float[blocks];
        bl = new float[blocks];
        sk = new float[blocks];
        ring = new int[blocks];
        light = new int[blocks];
        leaf = new boolean[blocks];
        int lo = 0, hi = 0;
        for (int i = 0; i < blocks; i++) {
            int wy = (msg.origin().getY() + msg.cells()[i * 4 + 1]) * 1000 - msg.py();
            int k = Layout.ring(wy);
            lo = Math.min(lo, k);
            hi = Math.max(hi, k);
        }
        kn = lo;
        kt = hi;
        oy = new double[kt - kn + 1];
        oz = new double[kt - kn + 1];
        if (msg.probe() >= 0) {
            int[] cells = msg.cells();
            int i = msg.probe() * 4;
            BlockPos at = msg.origin().offset(cells[i], cells[i + 1], cells[i + 2]);
            BlockState st = msg.palette().get(cells[i + 3] >> 1);
            if (level.getBlockState(at) == st) {
                probe = at;
                probeState = st;
            }
        }
        bake(level);
    }

    /**
     * Drawing time in server frames: frame n's pose is fully drawn at tau = n, as the displays draw it. A display update
     * handled before client tick C starts interpolating when C ticks the entity and is reached one tick later, at client
     * time C + 2 (ticks done + partial); the payload left with frame {@code e}'s packets, so tau = T - c0 - 2 + e.
     */
    double tau(double t) {
        double tau = t - c0 - 2 + msg.e();
        // until frame 1's removal shows the clock is only a guess from the payload's arrival: hold the rest pose
        return probe != null ? Math.min(tau, 0) : tau;
    }

    /**
     * Every client tick, before anything reads {@link #tau}: the server's frame-1 packets (the tree's blocks cleared,
     * the displays' first pose) arrive together, and a display starts toward that pose on the first client tick after
     * them, so frame 1 is fully drawn one tick after the tick that first shows the cell gone. That fixes the clock to
     * the server's frames; the payload's own arrival can be a tick off when the chop tick ran long (frame 0 late,
     * frame 1 on time). Gives up after 3 ticks (the cell was changed some other way) and keeps the payload's clock.
     */
    void anchor(ClientLevel level, long ticks) {
        if (probe == null) return;
        if (level.getBlockState(probe) != probeState) {
            long c = ticks - 2;
            shift = (int) (c - c0);
            c0 = c;
            probe = null;
        } else if (ticks - c0 > 4) {
            probe = null;
        }
    }

    private void bake(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        int bin = msg.bin();
        Quaternionf right = new Quaternionf(0, (float) Layout.qs(bin), 0, (float) Layout.qc(bin));
        Vector3f q = new Vector3f(), c = right.transform(new Vector3f(0.5f, 0.5f, 0.5f));
        boolean cutoutLeaves = mc.options.cutoutLeaves().get();
        Map<Long, Group> byKey = new HashMap<>();
        List<BlockStateModelPart> parts = new ArrayList<>();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos o = msg.origin();
        int[] cells = msg.cells();
        for (int i = 0; i < blocks; i++) {
            cell.set(o.getX() + cells[i * 4], o.getY() + cells[i * 4 + 1], o.getZ() + cells[i * 4 + 2]);
            BlockState state = msg.palette().get(cells[i * 4 + 3] >> 1);
            boolean lf = (cells[i * 4 + 3] & 1) != 0;
            int wx = cell.getX() * 1000 - msg.px(), wy = cell.getY() * 1000 - msg.py(), wz = cell.getZ() * 1000 - msg.pz();
            int k = Layout.ring(wy);
            float tx = Layout.tx(wx, wz, bin) / 1000f, ty = wy / 1000f, tz = Layout.tz(wx, wz, bin) / 1000f;
            cx[i] = tx + c.x;
            cy[i] = ty + c.y;
            cz[i] = tz + c.z;
            ring[i] = k;
            leaf[i] = lf;
            int lit = lightAt(level, cell);
            bl[i] = LightCoordsUtil.block(lit);
            sk[i] = LightCoordsUtil.sky(lit);
            light[i] = lit;
            List<BlockTintSource> sources = mc.getBlockColors().getTintSources(state);
            int[] tints = new int[sources.size()];
            for (int t = 0; t < tints.length; t++) {
                BlockTintSource src = sources.get(t);
                tints[t] = src == null ? -1 : src.colorInWorld(state, level, cell) | 0xFF000000;
            }
            parts.clear();
            mc.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(state.getSeed(cell)), parts);
            boolean opaque = ModelBlockRenderer.forceOpaque(cutoutLeaves, state);
            for (BlockStateModelPart part : parts) {
                for (int d = 0; d <= 6; d++) {
                    for (BakedQuad quad : part.getQuads(d < 6 ? DIRS[d] : null)) {
                        ChunkSectionLayer layer = opaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
                        long key = (long) k << 8 | (lf ? 4 : 0) | layer.ordinal();
                        Group g = byKey.computeIfAbsent(key, kk -> {
                            Group ng = new Group(this, k, lf, type(layer));
                            groups.add(ng);
                            return ng;
                        });
                        int ti = quad.materialInfo().tintIndex();
                        Direction sd = quad.materialInfo().shadeDirectionOverride();
                        g.add(quad, right, tx, ty, tz, c, q, ti >= 0 && ti < tints.length ? tints[ti] : -1,
                            (sd != null ? sd : quad.direction()).ordinal(), i, quad.materialInfo().lightEmission());
                    }
                }
            }
        }
    }

    private static RenderType type(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }

    /** Light at a cell; inside an opaque block (a log at rest, a trunk swinging through the ground) the brightest open neighbour. */
    static int lightAt(ClientLevel level, BlockPos cell) {
        if (!level.getBlockState(cell).isSolidRender()) return LightCoordsUtil.getLightCoords(level, cell);
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        int b = 0, s = 0;
        for (Direction d : DIRS) {
            n.setWithOffset(cell, d);
            int l = LightCoordsUtil.getLightCoords(level, n);
            b = Math.max(b, LightCoordsUtil.block(l));
            s = Math.max(s, LightCoordsUtil.sky(l));
        }
        return LightCoordsUtil.pack(b, s);
    }

    /** The group matrix of ring k at the sample, translated by -cam. */
    private Matrix4f pose(Matrix4f out, int k, boolean lf, Timeline.Sample s, double camX, double camY, double camZ) {
        double sx = lf ? s.lx : s.gx, sz = lf ? s.lz : s.gz;
        return out.translation((float) (x - camX), (float) (y0 + s.dy - camY), (float) (z - camZ))
            .rotateY((float) Math.toRadians(-yaw))
            .translate(0, (float) oy[k - kn], (float) oz[k - kn])
            .rotateX((float) Math.toRadians(s.pitch))
            .scale((float) sx, (float) s.gy, (float) sz)
            .translate(0, -k, 0);
    }

    private void origins(Timeline.Sample s) {
        Rig.origins(s.pitch, s.bend, s.gy, tl.spec.h(), kn, kt, oy, oz);
    }

    /** World centres of the blocks drawn at client time {@code t} into {@code out} (x, y, z each); returns their count (tests). */
    int centres(double t, double[] out) {
        double tau = tau(t);
        if (tau >= tl.end - 1) return 0;
        Timeline.Sample s = tl.sample(tau, new Timeline.Sample());
        Rig.origins(s.pitch, s.bend, s.gy, tl.spec.h(), kn, kt, oy, oz);
        Matrix4f m = new Matrix4f();
        Vector3f w = new Vector3f();
        int n = 0;
        for (int i = 0; i < blocks && n * 3 + 2 < out.length; i++) {
            if (leaf[i] ? !s.leaves : isGone(ring[i], tau)) continue;
            pose(m, ring[i], leaf[i], s, 0, 0, 0).transformPosition(cx[i], cy[i], cz[i], w);
            out[n * 3] = w.x;
            out[n * 3 + 1] = w.y;
            out[n * 3 + 2] = w.z;
            n++;
        }
        return n;
    }

    /**
     * World centres of every block at server frame {@code n} (its pose fully drawn), with each block's ring in
     * {@code rings} (-1 for leaves, -2 for a log whose ring is popping at n); returns the count (tests).
     */
    int centresAt(int n, double[] out, int[] rings) {
        Timeline.Sample s = tl.sample(n, new Timeline.Sample());
        Rig.origins(s.pitch, s.bend, s.gy, tl.spec.h(), kn, kt, oy, oz);
        Matrix4f m = new Matrix4f();
        Vector3f w = new Vector3f();
        float[] pp = new float[2];
        int c = 0;
        for (int i = 0; i < blocks && c * 3 + 2 < out.length; i++) {
            pose(m, ring[i], leaf[i], s, 0, 0, 0).transformPosition(cx[i], cy[i], cz[i], w);
            out[c * 3] = w.x;
            out[c * 3 + 1] = w.y;
            out[c * 3 + 2] = w.z;
            rings[c] = leaf[i] ? -1 : tl.pop(ring[i], n, pp) != null ? -2 : ring[i];
            c++;
        }
        return c;
    }

    private boolean isGone(int k, double tau) {
        float[] p = tl.pop(k, tau, new float[2]);
        return p != null && p[0] == 0 && p[1] == 0;
    }

    /** Light every second client tick: each block's current centre, eased toward it (HavingABlast debris light). */
    void tick(ClientLevel level, long ticks) {
        anchor(level, ticks);
        double t = tau(ticks);
        if (t >= tl.end - 1) {
            dead = true;
            return;
        }
        if ((ticks & 1) != 0 || t < 0) return;
        tl.sample(t, sample);
        origins(sample);
        Matrix4f[] m = new Matrix4f[(kt - kn + 1) * 2];
        Vector3f w = new Vector3f();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int i = 0; i < blocks; i++) {
            int gi = (ring[i] - kn) * 2 + (leaf[i] ? 1 : 0);
            if (m[gi] == null) m[gi] = pose(new Matrix4f(), ring[i], leaf[i], sample, 0, 0, 0);
            m[gi].transformPosition(cx[i], cy[i], cz[i], w);
            cell.set(Math.floor(w.x), Math.floor(w.y), Math.floor(w.z));
            int l = lightAt(level, cell);
            bl[i] += (LightCoordsUtil.block(l) - bl[i]) * 0.35f;
            sk[i] += (LightCoordsUtil.sky(l) - sk[i]) * 0.35f;
            light[i] = LightCoordsUtil.pack(Math.round(bl[i]), Math.round(sk[i]));
        }
    }

    /** This frame: one custom-geometry node per live group, posed at {@code t} (client ticks + partial). */
    void submit(PoseStack ps, Vec3 cam, SubmitNodeCollector out, double t, CardinalLighting cl) {
        double tau = tau(t);
        if (tau >= tl.end - 1) {
            dead = true;
            return;
        }
        Timeline.Sample s = tl.sample(tau, sample);
        origins(s);
        drawnPitch = s.pitch;
        drawnTau = tau;
        Matrix3f r = new Matrix3f().rotationY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(s.pitch))
            .rotateY((float) Math.toRadians(yaw));
        Vector3f n = new Vector3f();
        for (Direction d : DIRS) {
            r.transform(d.getStepX(), d.getStepY(), d.getStepZ(), n);
            int o = d.ordinal() * 3;
            normals[o] = n.x;
            normals[o + 1] = n.y;
            normals[o + 2] = n.z;
            shade[d.ordinal()] = Shading.factor(n.x, n.y, n.z, cl);
        }
        float inflate = (float) s.inflate;
        Matrix4f m = new Matrix4f();
        for (Group g : groups) {
            float ex = inflate, ey = inflate;
            if (g.leaf) {
                if (!s.leaves) continue;
            } else {
                float[] p = tl.pop(g.k, tau, pop);
                if (p != null) {
                    if (p[0] == 0 && p[1] == 0) continue;
                    ex = p[0] * (1 + inflate) - 1;
                    ey = p[1] * (1 + inflate) - 1;
                }
            }
            g.ex = ex;
            g.ey = ey;
            ps.pushPose();
            ps.mulPose(pose(m, g.k, g.leaf, s, cam.x, cam.y, cam.z));
            out.submitCustomGeometry(ps, g.type, g);
            ps.popPose();
        }
    }

    /** The baked quads of one (ring, log|leaf, render type): tree-frame positions, offsets from each block's centre, UVs. */
    static final class Group implements SubmitNodeCollector.CustomGeometryRenderer {
        final MeshFall fall;
        final int k;
        final boolean leaf;
        final RenderType type;
        float[] pos = new float[48], off = new float[48], uv = new float[32];
        int[] tint = new int[4], block = new int[4];
        byte[] dir = new byte[4], emission = new byte[4];
        int quads;
        float ex, ey;

        Group(MeshFall fall, int k, boolean leaf, RenderType type) {
            this.fall = fall;
            this.k = k;
            this.leaf = leaf;
            this.type = type;
        }

        void add(BakedQuad quad, Quaternionf right, float tx, float ty, float tz, Vector3f c, Vector3f q, int col, int sdir, int bi, int em) {
            if (quads == tint.length) {
                int n = quads * 2;
                pos = java.util.Arrays.copyOf(pos, n * 12);
                off = java.util.Arrays.copyOf(off, n * 12);
                uv = java.util.Arrays.copyOf(uv, n * 8);
                tint = java.util.Arrays.copyOf(tint, n);
                block = java.util.Arrays.copyOf(block, n);
                dir = java.util.Arrays.copyOf(dir, n);
                emission = java.util.Arrays.copyOf(emission, n);
            }
            for (int v = 0; v < 4; v++) {
                var p = quad.position(v);
                right.transform(p.x(), p.y(), p.z(), q);
                int i = (quads * 4 + v) * 3;
                pos[i] = tx + q.x;
                pos[i + 1] = ty + q.y;
                pos[i + 2] = tz + q.z;
                off[i] = q.x - c.x;
                off[i + 1] = q.y - c.y;
                off[i + 2] = q.z - c.z;
                long packed = quad.packedUV(v);
                uv[(quads * 4 + v) * 2] = UVPair.unpackU(packed);
                uv[(quads * 4 + v) * 2 + 1] = UVPair.unpackV(packed);
            }
            tint[quads] = col;
            block[quads] = bi;
            // the shade and normal follow the face's model direction, turned by Ry(yaw) like the block
            dir[quads] = (byte) sdir;
            emission[quads] = (byte) em;
            quads++;
        }

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer vc) {
            long t0 = System.nanoTime();
            Matrix4f m = pose.pose();
            float[] nr = fall.normals, sh = fall.shade;
            int[] lights = fall.light;
            float gx = ex, gy = ey;
            for (int qi = 0; qi < quads; qi++) {
                int d = dir[qi];
                int col = ARGB.scaleRGB(tint[qi], sh[d]);
                int li = lights[block[qi]];
                if (emission[qi] > 0) li = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(li), emission[qi]), LightCoordsUtil.sky(li));
                float nx = nr[d * 3], ny = nr[d * 3 + 1], nz = nr[d * 3 + 2];
                for (int v = 0; v < 4; v++) {
                    int i = (qi * 4 + v) * 3, u = (qi * 4 + v) * 2;
                    vc.addVertex(m, pos[i] + gx * off[i], pos[i + 1] + gy * off[i + 1], pos[i + 2] + gx * off[i + 2])
                        .setColor(col).setUv(uv[u], uv[u + 1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(li).setNormal(nx, ny, nz);
                }
            }
            ClientFalls.spent += System.nanoTime() - t0;
        }
    }
}
