package timberprobe;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Dev-only motion capture for the Timber 2.0 M1 exit (a vanilla client's view: no Timber on it). Inert unless
 * -Dtimberprobe.out=&lt;dir&gt;. A system chat line "[probe] start &lt;name&gt;" starts a recording: every rendered frame, the
 * world-space transform of every block display exactly as the client draws it (entity lerp, rotation lerp and
 * transformation interpolation applied) to &lt;name&gt;.displays.tsv, the frame's wall clock (epoch microseconds, the
 * same clock as the other client on this machine) to &lt;name&gt;.frames.tsv, and the first 240 frames to
 * frames/&lt;name&gt;/. "[probe] stop" ends it, "[probe] quit" writes results.json and quits.
 */
public final class Probe {
    static final String OUT = System.getProperty("timberprobe.out");
    static final int CAPTURE = Integer.getInteger("timberprobe.frames", 240);
    static final Map<Object, Integer> IDS = new IdentityHashMap<>();
    static final Set<Integer> SEEN = new HashSet<>();
    static final List<String> RESULTS = new ArrayList<>();
    static final ExecutorService WRITER = Executors.newFixedThreadPool(2);
    static Vec3 cam = Vec3.ZERO;
    static BufferedWriter out, blocks, frames;
    static String name;
    static int frame, rows, frameRows, captured, displaysMax;
    static long wall = -1;
    static boolean quit;

    static long nowMicros() {
        Instant i = Instant.now();
        return i.getEpochSecond() * 1_000_000L + i.getNano() / 1000;
    }

    public static void cue(String text) {
        if (OUT == null || !text.startsWith("[probe] ")) return;
        String[] w = text.substring(8).trim().split("\\s+");
        switch (w[0]) {
            case "start" -> start(w.length > 1 ? w[1] : "clip");
            case "stop" -> stop();
            case "quit" -> quit = true;
            default -> { }
        }
    }

    static void start(String n) {
        stop();
        try {
            Files.createDirectories(Path.of(OUT));
            name = n;
            out = Files.newBufferedWriter(Path.of(OUT, n + ".displays.tsv"));
            blocks = Files.newBufferedWriter(Path.of(OUT, n + ".blocks.tsv"));
            frames = Files.newBufferedWriter(Path.of(OUT, n + ".frames.tsv"));
            out.write("frame\twall_us\tpartial\tid\tm00\tm01\tm02\tm03\tm10\tm11\tm12\tm13\tm20\tm21\tm22\tm23\txrot\tprogress\n");
            blocks.write("id\tblock\n");
            frames.write("frame\twall_us\tdisplays\n");
            frame = rows = frameRows = captured = displaysMax = 0;
            wall = -1;
            SEEN.clear();
        } catch (IOException e) {
            RESULTS.add(result("probe " + n + " opened", false, e.toString()));
            name = null;
        }
    }

    static void stop() {
        if (name == null) return;
        try {
            out.close();
            blocks.close();
            frames.close();
        } catch (IOException ignored) {
        }
        RESULTS.add(result("probe " + name, rows > 0, frame + " frames, " + rows + " display rows, up to " + displaysMax
            + " displays a frame, " + captured + " frames captured"));
        name = null;
    }

    static String result(String n, boolean pass, String detail) {
        return String.format("{\"name\":\"%s\",\"pass\":%s,\"detail\":\"%s\"}", n, pass, detail.replace("\"", "'"));
    }

    public static void extracted(Object state, int id, String block) {
        IDS.put(state, id);
        if (name != null && block != null && SEEN.add(id)) {
            try {
                blocks.write(id + "\t" + block + "\n");
            } catch (IOException ignored) {
            }
        }
    }

    public static void camera(Vec3 pos) {
        cam = pos;
    }

    static java.lang.reflect.Method irisShadows;
    static boolean irisChecked;

    /** Iris draws every entity again from the sun for its shadow map; those passes are not what the camera sees. */
    static boolean shadowPass() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                irisShadows = Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState").getMethod("areShadowsCurrentlyBeingRendered");
            } catch (ReflectiveOperationException e) {
                irisShadows = null;
            }
        }
        try {
            return irisShadows != null && (boolean) irisShadows.invoke(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static void submitted(Object state, Matrix4f m, float xRot, float progress) {
        if (name == null || shadowPass()) return;
        Integer id = IDS.get(state);
        if (id == null) return;
        if (wall < 0) wall = nowMicros();
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        StringBuilder sb = new StringBuilder();
        sb.append(frame).append('\t').append(wall).append('\t').append(pt).append('\t').append(id);
        // JOML is column-major: m<col><row>; write rows (world x, y, z) with the camera offset added to the translation
        float[] r0 = {m.m00(), m.m10(), m.m20(), (float) (m.m30() + cam.x)};
        float[] r1 = {m.m01(), m.m11(), m.m21(), (float) (m.m31() + cam.y)};
        float[] r2 = {m.m02(), m.m12(), m.m22(), (float) (m.m32() + cam.z)};
        for (float[] row : new float[][] {r0, r1, r2}) for (float v : row) sb.append('\t').append(v);
        sb.append('\t').append(xRot).append('\t').append(progress);
        try {
            out.write(sb.append('\n').toString());
        } catch (IOException ignored) {
        }
        rows++;
        frameRows++;
    }

    /** After each rendered frame: the frame's row, a capture, the cues. */
    public static void frameDone(Minecraft mc) {
        if (OUT == null) return;
        if (name != null) {
            try {
                frames.write(frame + "\t" + (wall < 0 ? nowMicros() : wall) + "\t" + frameRows + "\n");
            } catch (IOException ignored) {
            }
            displaysMax = Math.max(displaysMax, frameRows);
            if (captured < CAPTURE) {
                Path p = Path.of(OUT, "frames", name, String.format("f%03d.png", captured++));
                Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage img) -> WRITER.execute(() -> {
                    try (img) {
                        Files.createDirectories(p.getParent());
                        img.writeToFile(p);
                    } catch (IOException ignored) {
                    }
                }));
            }
            frame++;
            frameRows = 0;
            wall = -1;
        }
        if (quit) {
            quit = false;
            stop();
            WRITER.shutdown();
            try {
                WRITER.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            try {
                Files.createDirectories(Path.of(OUT));
                Files.writeString(Path.of(OUT, "results.json"), "{\"results\":[" + String.join(",", RESULTS) + "]}\n");
            } catch (IOException ignored) {
            }
            mc.stop();
        }
    }
}
