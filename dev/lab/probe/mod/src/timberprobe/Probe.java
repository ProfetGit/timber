package timberprobe;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Dev-only motion capture for Timber's lab: for every rendered frame, the world-space transform of every block display
 * exactly as the client draws it (entity lerp, rotation lerp and transformation interpolation all applied).
 * Writes <sc.out>/<sc.name>.displays.tsv: frame, game time, partial tick, entity id, then the 3x4 model matrix
 * (world = M * block-local point, block-local in 0..1). Block states go to <name>.blocks.tsv once per entity.
 */
public final class Probe {
    static final Map<Object, Integer> IDS = new IdentityHashMap<>();
    static final Set<Integer> SEEN = new HashSet<>();
    static Vec3 cam = Vec3.ZERO;
    static BufferedWriter out, blocks;
    static java.lang.reflect.Field frameField;
    static boolean failed;

    static void open() {
        if (out != null || failed) return;
        try {
            String dir = System.getProperty("sc.out"), name = System.getProperty("sc.name", "clip");
            if (dir == null) { failed = true; return; }
            Files.createDirectories(Path.of(dir));
            out = Files.newBufferedWriter(Path.of(dir, name + ".displays.tsv"));
            blocks = Files.newBufferedWriter(Path.of(dir, name + ".blocks.tsv"));
            out.write("frame\tgametime\tpartial\tid\tm00\tm01\tm02\tm03\tm10\tm11\tm12\tm13\tm20\tm21\tm22\tm23\txrot\tprogress\n");
            blocks.write("id\tblock\n");
            try { frameField = Class.forName("showcase.Director").getField("frame"); } catch (ReflectiveOperationException e) { frameField = null; }
            Runtime.getRuntime().addShutdownHook(new Thread(Probe::close));
        } catch (IOException e) {
            failed = true;
        }
    }

    static int frame() {
        try { return frameField == null ? -1 : frameField.getInt(null); } catch (IllegalAccessException e) { return -1; }
    }

    public static void extracted(Object state, int id, String block) {
        IDS.put(state, id);
        open();
        if (blocks != null && block != null && SEEN.add(id)) {
            try { blocks.write(id + "\t" + block + "\n"); } catch (IOException ignored) { }
        }
    }

    public static void camera(Vec3 pos) { cam = pos; }

    static java.lang.reflect.Method irisShadows;
    static boolean irisChecked;

    /** Iris draws every entity again from the sun for its shadow map; those passes are not what the camera sees. */
    static boolean shadowPass() {
        if (!irisChecked) {
            irisChecked = true;
            try { irisShadows = Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState").getMethod("areShadowsCurrentlyBeingRendered"); }
            catch (ReflectiveOperationException e) { irisShadows = null; }
        }
        try { return irisShadows != null && (boolean) irisShadows.invoke(null); } catch (ReflectiveOperationException e) { return false; }
    }

    public static void submitted(Object state, Matrix4f m, float xRot, float progress) {
        if (shadowPass()) return;
        Integer id = IDS.get(state);
        if (id == null || out == null) return;
        Minecraft mc = Minecraft.getInstance();
        long gt = mc.level == null ? -1 : mc.level.getGameTime();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        StringBuilder sb = new StringBuilder();
        sb.append(frame()).append('\t').append(gt).append('\t').append(pt).append('\t').append(id);
        // JOML is column-major: m<col><row>; write rows (world x, y, z) with the camera offset added to the translation
        float[] r0 = {m.m00(), m.m10(), m.m20(), (float) (m.m30() + cam.x)};
        float[] r1 = {m.m01(), m.m11(), m.m21(), (float) (m.m31() + cam.y)};
        float[] r2 = {m.m02(), m.m12(), m.m22(), (float) (m.m32() + cam.z)};
        for (float[] row : new float[][] {r0, r1, r2}) for (float v : row) sb.append('\t').append(v);
        sb.append('\t').append(xRot).append('\t').append(progress);
        try { out.write(sb.append('\n').toString()); } catch (IOException ignored) { }
    }

    static void close() {
        try {
            if (out != null) out.close();
            if (blocks != null) blocks.close();
        } catch (IOException ignored) { }
    }
}
