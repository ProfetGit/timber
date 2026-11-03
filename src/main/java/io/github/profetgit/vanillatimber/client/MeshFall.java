package io.github.profetgit.vanillatimber.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.profetgit.vanillatimber.core.DrawnPose;
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
 * (ring, log|leaf, render type), and posed every frame from the same {@link Timeline} the server's displays follow, as
 * a vanilla client draws those displays ({@link DrawnPose}: the transformation's share trails like theirs, so sharp
 * keys are rounded off the same way). The pose of group k is the display rig in one matrix:
 * T(pivot + dy) Ry(-yaw) Rx(byte step) T(origin k) Rx(residual) S T(0, -k, 0), applied to v = rest corner + Ry(yaw) m.
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
    /**
     * Ticks from a server frame's packets arriving to that frame's pose fully drawn by a vanilla client, on average. A
     * display starts toward an update on the first client tick run after the frame that handled it and gets there one
     * tick later: 1 to 2 ticks after the previous frame started, depending on the client's tick phase, so 1.5 minus half
     * a frame (60 fps) after the arrival. Every vanilla client lands within about half a tick of it, whatever its phase;
     * copying this client's own phase instead (the 0.0.5 anchor) left the mesh up to 1.3 ticks off another client's.
     */
    static final double LAG = 1.5 - 1.0 / 6;
    /** The client tick count when the payload was handled (the anchor gives up 4 ticks later). */
    final long handled;
    /** Client time (ticks + partial) at which the drawing time is 0, NaN until the first drawn frame; see {@link #frame}. */
    double start = Double.NaN;
    /** The cell the server clears at frame 1 and its state, while this client still shows it (null: no anchor to wait for). */
    private BlockPos probe;
    private BlockState probeState;
    /** How far the frame-1 anchor moved {@link #start}, in ticks (tests; 0 when there was none). */
    volatile double shift;
    private final double[] oy, oz;
    private final Timeline.Sample sample = new Timeline.Sample();
    /** The pose this client's displays would draw, advanced once per drawn frame in {@link #submit}. */
    private final DrawnPose drawn;
    private final float[] pop = new float[2];
    // this frame's face normals (model direction -> world) and their cardinal shade, shared by every group
    final float[] normals = new float[18], shade = new float[6];
    boolean dead;
    /** The pitch of the last drawn frame and its drawing time (tests). */
    volatile double drawnPitch = Double.NaN, drawnTau = Double.NaN;

    MeshFall(TreeFall msg, ClientLevel level, long handled) {
        this.msg = msg;
        this.handled = handled;
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
        drawn = new DrawnPose(tl, kn, kt);
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

    /** Drawing time in server frames at client time {@code t}: frame n's pose is fully drawn at tau = n. */
    double tau(double t) {
        if (Double.isNaN(start)) return -1;
        double tau = t - start;
        // until frame 1's removal shows the clock is only a guess from the payload's arrival: hold the rest pose
        return probe != null ? Math.min(tau, 0) : tau;
    }

    /**
     * Each drawn frame at client time {@code t}, before {@link #submit}; {@code dt} = time since the previous frame.
     * Packets are handled at the start of a frame, before its ticks and its drawing, so the first frame that shows the
     * payload (or the frame-1 removal) handled it, and it arrived during the {@code dt} before: half of it on average.
     * The payload sets the clock from its own frame {@code e}; the frame-1 removal, which leaves with the displays' first
     * pose, replaces it (the chop tick can run long, so frame 0 leaves late and frame 1 on time).
     */
    void frame(ClientLevel level, double t, double dt) {
        double arrived = t - Math.max(0, Math.min(1, dt)) / 2;
        if (Double.isNaN(start)) start = arrived + LAG - msg.e();
        if (probe != null && level.getBlockState(probe) != probeState) {
            double s = arrived + LAG - 1;
            shift = s - start;
            start = s;
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
        RestLight rest = new RestLight(level, msg);
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
            rest.run(cell, state);
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
                            (sd != null ? sd : quad.direction()).ordinal(), i, quad.materialInfo().lightEmission(), rest.get(quad), lit);
                    }
                }
            }
        }
        Timeline.Sample s0 = tl.sample(0, new Timeline.Sample());
        origins(s0);
        Matrix4f m = new Matrix4f();
        for (Group g : groups) pose(m, g.k, g.leaf, s0, 0, 0, 0).transformPosition(0, g.k + 0.5f, 0, g.rest);
    }

    /**
     * How much of the terrain's own AO and light a group keeps at this pose: all of it at rest (the swap shows nothing),
     * none once it has moved 4 blocks from where it stood (SeamlessDisplays' fade), where the eased light along its path
     * takes over.
     */
    private static float restWeight(Matrix4f m, Group g, Vec3 cam, Vector3f tmp) {
        m.transformPosition(0, g.k + 0.5f, 0, tmp);
        double dx = tmp.x + cam.x - g.rest.x, dy = tmp.y + cam.y - g.rest.y, dz = tmp.z + cam.z - g.rest.z;
        return (float) Math.max(0, 1 - Math.sqrt(dx * dx + dy * dy + dz * dz) / 4);
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

    /** The group matrix of ring k at the exact sample (origins from {@link #origins}), translated by -cam. */
    private Matrix4f pose(Matrix4f out, int k, boolean lf, Timeline.Sample s, double camX, double camY, double camZ) {
        return pose(out, k, 0, s.pitch, s.dy, lf ? s.lx : s.gx, s.gy, lf ? s.lz : s.gz, oy, oz, camX, camY, camZ);
    }

    /** The group matrix of ring k as drawn this frame, translated by -cam. */
    private Matrix4f pose(Matrix4f out, int k, boolean lf, DrawnPose d, double camX, double camY, double camZ) {
        return pose(out, k, d.rot, d.res, d.dy, lf ? d.lx : d.gx, d.gy, lf ? d.lz : d.gz, d.oy, d.oz, camX, camY, camZ);
    }

    private Matrix4f pose(Matrix4f out, int k, double rot, double res, double dy, double sx, double sy, double sz, double[] oy,
                          double[] oz, double camX, double camY, double camZ) {
        return out.translation((float) (x - camX), (float) (y0 + dy - camY), (float) (z - camZ))
            .rotateY((float) Math.toRadians(-yaw))
            .rotateX((float) Math.toRadians(rot))
            .translate(0, (float) oy[k - kn], (float) oz[k - kn])
            .rotateX((float) Math.toRadians(res))
            .scale((float) sx, (float) sy, (float) sz)
            .translate(0, -k, 0);
    }

    private void origins(Timeline.Sample s) {
        Rig.origins(s.pitch, s.bend, s.gy, tl.spec.h(), kn, kt, oy, oz);
    }

    /** World centres of the blocks in the last drawn frame into {@code out} (x, y, z each); returns their count (tests). */
    int centres(double[] out) {
        DrawnPose d = drawn;
        if (dead || d.tau >= tl.end - 1) return 0;
        Matrix4f m = new Matrix4f();
        Vector3f w = new Vector3f();
        float[] pp = new float[2];
        int n = 0;
        for (int i = 0; i < blocks && n * 3 + 2 < out.length; i++) {
            float[] p = leaf[i] ? null : d.pop(ring[i], pp);
            if (leaf[i] ? !d.leaves : p != null && p[0] == 0 && p[1] == 0) continue;
            pose(m, ring[i], leaf[i], d, 0, 0, 0).transformPosition(cx[i], cy[i], cz[i], w);
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

    /** Light every second client tick: each block's current centre, eased toward it (HavingABlast debris light). */
    void tick(ClientLevel level, long ticks) {
        // the frame-1 cell was changed some other way: keep the payload's clock
        if (probe != null && ticks - handled > 4) probe = null;
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
        DrawnPose s = drawn.draw(tau);
        drawnPitch = s.rot + s.res;
        drawnTau = tau;
        Matrix3f r = new Matrix3f().rotationY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(s.rot + s.res))
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
        Vector3f tmp = new Vector3f();
        for (Group g : groups) {
            float ex = inflate, ey = inflate;
            if (g.leaf) {
                if (!s.leaves) continue;
            } else {
                float[] p = s.pop(g.k, pop);
                if (p != null) {
                    if (p[0] == 0 && p[1] == 0) continue;
                    ex = p[0] * (1 + inflate) - 1;
                    ey = p[1] * (1 + inflate) - 1;
                }
            }
            g.ex = ex;
            g.ey = ey;
            pose(m, g.k, g.leaf, s, cam.x, cam.y, cam.z);
            g.w = restWeight(m, g, cam, tmp);
            ps.pushPose();
            ps.mulPose(m);
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
        /** Per vertex: the terrain's colour (AO x tint) and light where the block stood (RestLight). */
        int[] rcol = new int[16], rlight = new int[16];
        byte[] dir = new byte[4], emission = new byte[4];
        int quads;
        float ex, ey;
        /** This frame's share of the rest colour and light, see {@link MeshFall#restWeight}. */
        float w = 1;
        /** World position of the ring's axis point at rest. */
        final Vector3f rest = new Vector3f();

        Group(MeshFall fall, int k, boolean leaf, RenderType type) {
            this.fall = fall;
            this.k = k;
            this.leaf = leaf;
            this.type = type;
        }

        /** {@code lv}: the quad's 4 rest colours then 4 rest lights, or null when vanilla didn't emit it ({@code col}, {@code flat}). */
        void add(BakedQuad quad, Quaternionf right, float tx, float ty, float tz, Vector3f c, Vector3f q, int col, int sdir, int bi, int em,
                 int[] lv, int flat) {
            if (quads == tint.length) {
                int n = quads * 2;
                pos = java.util.Arrays.copyOf(pos, n * 12);
                off = java.util.Arrays.copyOf(off, n * 12);
                uv = java.util.Arrays.copyOf(uv, n * 8);
                tint = java.util.Arrays.copyOf(tint, n);
                block = java.util.Arrays.copyOf(block, n);
                rcol = java.util.Arrays.copyOf(rcol, n * 4);
                rlight = java.util.Arrays.copyOf(rlight, n * 4);
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
                rcol[quads * 4 + v] = lv != null ? lv[v] : col;
                rlight[quads * 4 + v] = lv != null ? lv[4 + v] : flat;
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
            float gx = ex, gy = ey, rw = w;
            for (int qi = 0; qi < quads; qi++) {
                int d = dir[qi];
                int path = lights[block[qi]], pu = path & 0xFFFF, pv = path >>> 16;
                float nx = nr[d * 3], ny = nr[d * 3 + 1], nz = nr[d * 3 + 2];
                for (int v = 0; v < 4; v++) {
                    int i = (qi * 4 + v) * 3, u = (qi * 4 + v) * 2, rv = qi * 4 + v;
                    int col, li;
                    if (rw >= 1) {
                        col = rcol[rv];
                        li = rlight[rv];
                    } else {
                        col = rw <= 0 ? tint[qi] : ARGB.srgbLerp(rw, tint[qi], rcol[rv]);
                        int rl = rlight[rv];
                        li = Math.round(pu + ((rl & 0xFFFF) - pu) * rw) | Math.round(pv + ((rl >>> 16) - pv) * rw) << 16;
                    }
                    if (emission[qi] > 0) li = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(li), emission[qi]), LightCoordsUtil.sky(li));
                    vc.addVertex(m, pos[i] + gx * off[i], pos[i + 1] + gy * off[i + 1], pos[i + 2] + gx * off[i + 2])
                        .setColor(ARGB.scaleRGB(col, sh[d])).setUv(uv[u], uv[u + 1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(li)
                        .setNormal(nx, ny, nz);
                }
            }
            ClientFalls.spent += System.nanoTime() - t0;
        }
    }
}
