package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.net.TreeFall;
import java.util.ArrayList;
import java.util.List;
import io.github.profetgit.vanillatimber.client.compat.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

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

    public static void submit(RenderPass pass) {
        long t0 = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        IrisCompat.frame();
        double t = ticks + RenderPass.partial(mc);
        // a second pass in the same frame (a shader pack's shadow pass) keeps the frame's interval
        if (Double.isNaN(lastFrame) || t < lastFrame) frameDt = 1.0 / 3;
        else if (t > lastFrame) frameDt = t - lastFrame;
        lastFrame = t;
        if (FALLS.isEmpty()) return;
        float[] shades = pass.shades(level);
        for (MeshFall f : FALLS) {
            if (f.dead) continue;
            f.frame(level, t, frameDt);
            f.submit(pass, t, shades);
        }
        pass.flush();
        spent += System.nanoTime() - t0;
    }

    /** Client time of the last drawn frame and the interval before it (the falls' arrival estimate). */
    private static double lastFrame = Double.NaN, frameDt = 1.0 / 3;

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
        return f.centres(out);
    }

    /** The last fall's blocks at server frame {@code n}; see {@link MeshFall#centresAt}. */
    public static int centresAt(int n, double[] out, int[] rings) {
        MeshFall f = lastFall;
        return f == null ? 0 : f.centresAt(n, out, rings);
    }

    /** How far the last fall's frame-1 anchor moved its clock, in ticks (tests). */
    public static double anchorShift() {
        MeshFall f = lastFall;
        return f == null ? 0 : f.shift;
    }

    /** Client time (level ticks + partial) of this frame. */
    public static double now() {
        return ticks + RenderPass.partial(Minecraft.getInstance());
    }

    public static int quads() {
        MeshFall f = lastFall;
        if (f == null) return 0;
        int n = 0;
        for (MeshFall.Group g : f.groups) n += g.quads;
        return n;
    }
}
