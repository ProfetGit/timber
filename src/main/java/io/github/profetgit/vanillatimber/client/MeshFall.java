package io.github.profetgit.vanillatimber.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.profetgit.vanillatimber.client.compat.IrisCompat;
import io.github.profetgit.vanillatimber.core.DrawnPose;
import io.github.profetgit.vanillatimber.core.Layout;
import io.github.profetgit.vanillatimber.core.Rig;
import io.github.profetgit.vanillatimber.core.Timeline;
import io.github.profetgit.vanillatimber.net.TreeFall;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
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
    final BlockState[] states;
    final int blocks;
    /** Farthest a block centre is from the pivot (sizes the ground shadow's grid). */
    final float reach;
    private Matrix4f[] shadowMats;
    private byte[] shadowSeen;
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
        states = new BlockState[blocks];
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
        float far = 0;
        for (int i = 0; i < blocks; i++) far = Math.max(far, cx[i] * cx[i] + cy[i] * cy[i] + cz[i] * cz[i]);
        reach = (float) Math.sqrt(far) + 1;
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
        int bin = msg.bin();
        Quaternionf right = new Quaternionf(0, (float) Layout.qs(bin), 0, (float) Layout.qc(bin));
        Vector3f q = new Vector3f(), c = right.transform(new Vector3f(0.5f, 0.5f, 0.5f));
        Map<Long, Group> byKey = new HashMap<>();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos o = msg.origin();
        int[] cells = msg.cells();
        BlockBake baker = new BlockBake(level, msg);
        baker.begin();
        try {
            bakeBlocks(level, baker, right, q, c, byKey, cell, o, cells, bin);
        } finally {
            baker.end();
        }
        Timeline.Sample s0 = tl.sample(0, new Timeline.Sample());
        origins(s0);
        Matrix4f m = new Matrix4f();
        for (Group g : groups) pose(m, g.k, g.leaf, s0, 0, 0, 0).transformPosition(0, g.k + 0.5f, 0, g.rest);
    }

    private void bakeBlocks(ClientLevel level, BlockBake baker, Quaternionf right, Vector3f q, Vector3f c, Map<Long, Group> byKey,
                            BlockPos.MutableBlockPos cell, BlockPos o, int[] cells, int bin) {
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
            states[i] = state;
            int lit = lightAt(level, cell);
            bl[i] = Packed.block(lit);
            sk[i] = Packed.sky(lit);
            light[i] = lit;
            for (BlockBake.Quad quad : baker.block(cell, state)) {
                long key = (long) k << 8 | (lf ? 4 : 0) | quad.layer.ordinal();
                Group g = byKey.computeIfAbsent(key, kk -> {
                    Group ng = new Group(this, k, lf, quad.layer);
                    groups.add(ng);
                    return ng;
                });
                g.add(quad, right, tx, ty, tz, c, q, i, lit);
            }
        }
    }

    /**
     * How much of the terrain's own AO and light a group keeps at this pose: all of it at rest (the swap shows nothing),
     * none once it has moved 4 blocks from where it stood (SeamlessDisplays' fade), where the eased light along its path
     * takes over. {@link #submit} also fades it by the pitch: the stump end barely moves but turns face up.
     */
    private static float restWeight(Matrix4f m, Group g, Vec3 cam, Vector3f tmp) {
        m.transformPosition(0, g.k + 0.5f, 0, tmp);
        double dx = tmp.x + cam.x - g.rest.x, dy = tmp.y + cam.y - g.rest.y, dz = tmp.z + cam.z - g.rest.z;
        return (float) Math.max(0, 1 - Math.sqrt(dx * dx + dy * dy + dz * dz) / 4);
    }

    static int lightAt(ClientLevel level, BlockPos cell) {
        return io.github.profetgit.vanillatimber.server.Displays.lightAt(level, cell);
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

    /** Feeds the ground shadow with the blocks as drawn in the last frame: a leaf stops 1 of the sky's 15, a log all of it. */
    void shadowBlocks(GroundShadow.Zone zone) {
        DrawnPose d = drawn;
        if (dead || d.tau >= tl.end - 1) return;
        int rings = kt - kn + 1;
        if (shadowMats == null) {
            shadowMats = new Matrix4f[rings * 2];
            shadowSeen = new byte[rings * 2];
        } else {
            java.util.Arrays.fill(shadowSeen, (byte) 0);
        }
        Vector3f w = new Vector3f();
        for (int i = 0; i < blocks; i++) {
            int gi = (ring[i] - kn) * 2 + (leaf[i] ? 1 : 0);
            if (shadowSeen[gi] == 0) {
                float[] p = leaf[i] ? null : d.pop(ring[i], pop);
                boolean shown = leaf[i] ? d.leaves : p == null || p[0] != 0 || p[1] != 0;
                shadowSeen[gi] = (byte) (shown ? 1 : 2);
                if (shown) shadowMats[gi] = pose(shadowMats[gi] == null ? new Matrix4f() : shadowMats[gi], ring[i], leaf[i], d, 0, 0, 0);
            }
            if (shadowSeen[gi] == 2) continue;
            shadowMats[gi].transformPosition(cx[i], cy[i], cz[i], w);
            zone.add(w.x, w.z, leaf[i] ? 1 : 15);
        }
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
            bl[i] += (Packed.block(l) - bl[i]) * 0.35f;
            sk[i] += (Packed.sky(l) - sk[i]) * 0.35f;
            light[i] = Packed.light(Math.round(bl[i]), Math.round(sk[i]));
        }
    }

    /** This frame: one geometry per live group, posed at {@code t} (client ticks + partial); {@code shades}: see {@link RenderPass#shades}. */
    void submit(RenderPass pass, double t, float[] shades) {
        Vec3 cam = pass.cam;
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
            shade[d.ordinal()] = Shading.factor(n.x, n.y, n.z, shades);
        }
        float inflate = (float) s.inflate;
        // a ring that turns in place (the stump end) keeps none of its rest AO and light once it has turned 30 degrees
        float turned = (float) Math.max(0, 1 - Math.abs(drawnPitch) / 30);
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
            g.w = Math.min(turned, restWeight(m, g, cam, tmp));
            pass.submit(m, g.layer, g);
        }
    }

    /** The baked quads of one (ring, log|leaf, render type): tree-frame positions, offsets from each block's centre, UVs. */
    static final class Group implements RenderPass.Geometry {
        final MeshFall fall;
        final int k;
        final boolean leaf;
        final RenderPass.Layer layer;
        float[] pos = new float[48], off = new float[48], uv = new float[32];
        int[] tint = new int[4], block = new int[4];
        /** Per vertex: the terrain's colour (AO x tint) and light where the block stood (RestLight). */
        int[] rcol = new int[16], rlight = new int[16];
        byte[] dir = new byte[4], emission = new byte[4];
        /** Quads vanilla culls at rest: no rest colour or light to keep, so they always take the path light. */
        boolean[] culled = new boolean[4];
        int quads;
        float ex, ey;
        /** This frame's share of the rest colour and light, see {@link MeshFall#restWeight}. */
        float w = 1;
        /** World position of the ring's axis point at rest. */
        final Vector3f rest = new Vector3f();

        Group(MeshFall fall, int k, boolean leaf, RenderPass.Layer layer) {
            this.fall = fall;
            this.k = k;
            this.leaf = leaf;
            this.layer = layer;
        }

        void add(BlockBake.Quad quad, Quaternionf right, float tx, float ty, float tz, Vector3f c, Vector3f q, int bi, int flat) {
            int[] lv = quad.rest;
            int col = quad.color;
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
                culled = java.util.Arrays.copyOf(culled, n);
            }
            for (int v = 0; v < 4; v++) {
                right.transform(quad.pos[v * 3], quad.pos[v * 3 + 1], quad.pos[v * 3 + 2], q);
                int i = (quads * 4 + v) * 3;
                pos[i] = tx + q.x;
                pos[i + 1] = ty + q.y;
                pos[i + 2] = tz + q.z;
                off[i] = q.x - c.x;
                off[i + 1] = q.y - c.y;
                off[i + 2] = q.z - c.z;
                uv[(quads * 4 + v) * 2] = quad.uv[v * 2];
                uv[(quads * 4 + v) * 2 + 1] = quad.uv[v * 2 + 1];
                rcol[quads * 4 + v] = lv != null ? lv[v] : col;
                rlight[quads * 4 + v] = lv != null ? lv[4 + v] : flat;
            }
            tint[quads] = col;
            block[quads] = bi;
            // the shade and normal follow the face's model direction, turned by Ry(yaw) like the block
            dir[quads] = (byte) quad.shade;
            emission[quads] = (byte) quad.emission;
            culled[quads] = lv == null;
            quads++;
        }

        @Override
        public void render(Matrix4f m, VertexConsumer vc) {
            long t0 = System.nanoTime();
            Vector3f c = new Vector3f();
            float[] nr = fall.normals, sh = fall.shade;
            int[] lights = fall.light;
            float gx = ex, gy = ey, rw = w;
            Object2IntMap<BlockState> ids = IrisCompat.applies(vc) ? IrisCompat.ids() : null;
            int open = -1;
            for (int qi = 0; qi < quads; qi++) {
                if (ids != null && block[qi] != open) {
                    if (open >= 0) IrisCompat.end(vc);
                    open = block[qi];
                    BlockState st = fall.states[open];
                    m.transformPosition(fall.cx[open], fall.cy[open], fall.cz[open], c);
                    IrisCompat.begin(vc, ids.getOrDefault(st, -1), st.getLightEmission(), Mth.floor(c.x), Mth.floor(c.y), Mth.floor(c.z));
                }
                int d = dir[qi];
                int path = lights[block[qi]], pu = path & 0xFFFF, pv = path >>> 16;
                float nx = nr[d * 3], ny = nr[d * 3 + 1], nz = nr[d * 3 + 2];
                // the bake-time light of a culled face is its emptied cell's (0 until the client's light engine catches up)
                float qw = culled[qi] ? 0 : rw;
                for (int v = 0; v < 4; v++) {
                    int i = (qi * 4 + v) * 3, u = (qi * 4 + v) * 2, rv = qi * 4 + v;
                    int col, li;
                    if (qw >= 1) {
                        col = rcol[rv];
                        li = rlight[rv];
                    } else {
                        col = qw <= 0 ? tint[qi] : Packed.srgbLerp(qw, tint[qi], rcol[rv]);
                        int rl = rlight[rv];
                        li = Math.round(pu + ((rl & 0xFFFF) - pu) * qw) | Math.round(pv + ((rl >>> 16) - pv) * qw) << 16;
                    }
                    if (emission[qi] > 0) li = Packed.light(Math.max(Packed.block(li), emission[qi]), Packed.sky(li));
                    vc.addVertex(m, pos[i] + gx * off[i], pos[i + 1] + gy * off[i + 1], pos[i + 2] + gx * off[i + 2])
                        .setColor(Packed.scaleRGB(col, sh[d])).setUv(uv[u], uv[u + 1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(li)
                        .setNormal(nx, ny, nz);
                }
            }
            if (open >= 0) IrisCompat.end(vc);
            ClientFalls.spent += System.nanoTime() - t0;
        }
    }
}
