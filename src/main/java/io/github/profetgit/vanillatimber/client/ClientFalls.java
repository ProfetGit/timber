package io.github.profetgit.vanillatimber.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.vanillatimber.net.TreeFall;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/** The falls this client draws, on a clock of level ticks (paused with the game, like the displays' interpolation). */
public final class ClientFalls {
    static final List<MeshFall> FALLS = new ArrayList<>();
    /** Level ticks since join. */
    static long ticks;
    /** Nanoseconds this mod spent on the render thread since the tests last read it (submit + geometry), and the last bake. */
    public static long spent, bakeNanos;
    /** The last payload handled (tests). */
    public static volatile TreeFall last;
    static volatile MeshFall lastFall;

    private ClientFalls() {}

    static void onPayload(TreeFall msg) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long t0 = System.nanoTime();
        MeshFall f = new MeshFall(msg, level, ticks);
        bakeNanos = System.nanoTime() - t0;
        FALLS.add(f);
        last = msg;
        lastFall = f;
    }

    static void tick(ClientLevel level) {
        ticks++;
        for (MeshFall f : FALLS) f.tick(level, ticks);
        FALLS.removeIf(f -> f.dead);
    }

    static void clear() {
        FALLS.clear();
        lastFall = null;
    }

    public static void submit(PoseStack ps, LevelRenderState state, SubmitNodeCollector out) {
        if (FALLS.isEmpty()) return;
        long t0 = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double t = ticks + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (MeshFall f : FALLS) {
            if (!f.dead) f.submit(ps, state.cameraRenderState.pos, out, t, level.cardinalLighting());
        }
        spent += System.nanoTime() - t0;
    }

    /** Falls being drawn (tests). */
    public static int active() {
        return FALLS.size();
    }

    /** The last fall's drawn pitch and drawing time this frame, NaN when there is none (tests). */
    public static double drawnPitch() {
        MeshFall f = lastFall;
        return f == null || f.dead ? Double.NaN : f.drawnPitch;
    }

    public static double drawnTau() {
        MeshFall f = lastFall;
        return f == null || f.dead ? Double.NaN : f.drawnTau;
    }

    /** The last fall's timeline (tests compare it with the server's). */
    public static io.github.profetgit.vanillatimber.core.Timeline timeline() {
        MeshFall f = lastFall;
        return f == null ? null : f.tl;
    }

    /** World centres of the last fall's blocks as drawn this frame (tests); see {@link MeshFall#centres}. */
    public static int centres(double[] out) {
        MeshFall f = lastFall;
        if (f == null || f.dead) return 0;
        return f.centres(ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false), out);
    }

    /** The last fall's blocks at server frame {@code n}; see {@link MeshFall#centresAt}. */
    public static int centresAt(int n, double[] out, int[] rings) {
        MeshFall f = lastFall;
        return f == null ? 0 : f.centresAt(n, out, rings);
    }

    /** How far the last fall's frame-1 anchor moved its clock, in ticks (tests). */
    public static int anchorShift() {
        MeshFall f = lastFall;
        return f == null ? 0 : f.shift;
    }

    /** Client time (level ticks + partial) of this frame. */
    public static double now() {
        return ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    public static int quads() {
        MeshFall f = lastFall;
        if (f == null) return 0;
        int n = 0;
        for (MeshFall.Group g : f.groups) n += g.quads;
        return n;
    }
}
