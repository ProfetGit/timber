package io.github.profetgit.vanillatimber.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.vanillatimber.client.TimberClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

// A tool on the stage: the player's own item texture, one pixel thick as a held item is, moved by the swing animated in
// Blockbench (dev/preview/scene.js writes a 3 x 4 matrix for every 60 fps frame) and seen by the stage's camera.
final class Tool {
    // model space as in scene.js: 16 units are a block, the grip texel (of 16) sits on the pivot, the head leans to -x
    private static final float PIVOT_X = 24.4f, PIVOT_Y = 10.4f, PIVOT_Z = -4.6f, GRIP_X = 2.5f, GRIP_Y = 13.5f, HALF = 0.6f;
    // the light of the arm's render: from the upper front, warm, with a cool shade
    private static final float[] LIGHT = {0.239f, 0.813f, 0.526f};

    private record Face(float depth, Matrix3x2f pose, int u, int v, int w, int h, int tint) {}

    private final Identifier texture;
    // turned half round its handle, so that an axe's blade leads the swing
    private final boolean turned;
    private boolean read;
    private int size;
    private boolean[] solid;
    private static float[][] motion;

    private Tool(String item, boolean turned) {
        this.texture = Stage.itemTex(item);
        this.turned = turned;
    }

    // the player's copper tools exist from 1.21.9; before that the preview swings the iron ones
    //? if >=1.21.9 {
    private static final String METAL = "copper";
    //?} else {
    /*private static final String METAL = "iron";
    *///?}

    static Tool pickaxe() {
        return new Tool(METAL + "_pickaxe", false);
    }

    static net.minecraft.resources.Identifier itemTexture() {
        return Stage.itemTex(METAL + "_axe");
    }

    static Tool of(String item, boolean turned) {
        return new Tool(item, turned);
    }

    static Tool axe() {
        return new Tool(METAL + "_axe", true);
    }

    // read once per scene, so a changed resource pack shows the next time the preview opens
    private void load() {
        read = true;
        var resources = Minecraft.getInstance().getResourceManager();
        try (InputStream in = resources.getResourceOrThrow(texture).open(); NativeImage image = NativeImage.read(in)) {
            // an animated texture's first frame
            size = Math.min(image.getWidth(), image.getHeight());
            solid = new boolean[size * size];
            for (int v = 0; v < size; v++) for (int u = 0; u < size; u++) solid[v * size + u] = (pixel(image, u, v) >>> 24) > 127;
        } catch (IOException e) {
            TimberClient.LOG.warn("Preview tool {} not read: {}", texture, e.toString());
            solid = null;
        }
        if (motion == null) motion = motion();
    }

    private static int pixel(NativeImage image, int u, int v) {
        //? if >=1.21.2 {
        return image.getPixel(u, v);
        //?} else {
        /*return image.getPixelRGBA(u, v);
        *///?}
    }

    private static float[][] motion() {
        Identifier id = Identifier.tryParse("vanilla_timber:" + "preview/swing.json");
        try (Reader in = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
            JsonArray frames = JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("frames");
            float[][] out = new float[frames.size()][12];
            for (int i = 0; i < out.length; i++) for (int j = 0; j < 12; j++) out[i][j] = frames.get(i).getAsJsonArray().get(j).getAsFloat();
            return out;
        } catch (IOException e) {
            TimberClient.LOG.warn("Preview swing not read: {}", e.toString());
            return null;
        }
    }

    void draw(Gfx g, int boxX, int boxY, int frame) {
        if (!read) load();
        if (solid == null || motion == null) return;
        float[] m = motion[Math.min(frame, motion.length - 1)];
        List<Face> sides = new ArrayList<>();
        for (int t = 0; t < size; t++) {
            for (int s = 0; s < size; s++) {
                if (!solid(s, t)) continue;
                // the texel's open edges: from (s0, t0) to (s1, t1), facing (ds, dt) in the texture
                if (!solid(s, t - 1)) side(sides, m, boxX, boxY, s, t, s, t, s + 1, t, 0, -1);
                if (!solid(s, t + 1)) side(sides, m, boxX, boxY, s, t, s, t + 1, s + 1, t + 1, 0, 1);
                if (!solid(s - 1, t)) side(sides, m, boxX, boxY, s, t, s, t, s, t + 1, -1, 0);
                if (!solid(s + 1, t)) side(sides, m, boxX, boxY, s, t, s + 1, t, s + 1, t + 1, 1, 0);
            }
        }
        sides.sort(Comparator.comparingDouble(Face::depth));
        for (Face f : sides) blit(g, f);
        // whichever flat face looks at the camera, drawn whole and last: the edges all lie behind it
        float front = seen(m, 0, 0, 1) > 0 ? HALF : -HALF;
        int tint = tint(m, 0, 0, front > 0 ? 1 : -1);
        float[] o = screen(m, boxX, boxY, 0, 0, front), a = screen(m, boxX, boxY, size, 0, front), b = screen(m, boxX, boxY, 0, size, front);
        blit(g, new Face(0, pose(o, a, b, size, size), 0, 0, size, size, tint));
    }

    private boolean solid(int s, int t) {
        return s >= 0 && t >= 0 && s < size && t < size && solid[t * size + s];
    }

    private void side(List<Face> out, float[] m, int boxX, int boxY, int s, int t, int s0, int t0, int s1, int t1, int ds, int dt) {
        // texture directions in model space (see model())
        float nx = turned ? dt : -ds, ny = turned ? ds : -dt;
        if (seen(m, nx, ny, 0) <= 0) return;
        float[] o = screen(m, boxX, boxY, s0, t0, -HALF), a = screen(m, boxX, boxY, s1, t1, -HALF), b = screen(m, boxX, boxY, s0, t0, HALF);
        float depth = (o[2] + a[2] + b[2] + (a[2] + b[2] - o[2])) / 4;
        out.add(new Face(depth, pose(o, a, b, 1, 1), s, t, 1, 1, tint(m, nx, ny, 0)));
    }

    // where the texture point (s, t) at depth z lands: {screen x, screen y, nearness to the camera}
    private float[] screen(float[] m, int boxX, int boxY, float s, float t, float z) {
        float k = 16f / size;
        // turned, the texture is mirrored across the handle's diagonal
        float u = turned ? 16 - t * k : s * k, v = turned ? 16 - s * k : t * k;
        float x = PIVOT_X + GRIP_X - u, y = PIVOT_Y + GRIP_Y - v, zz = PIVOT_Z + z;
        float wx = m[0] * x + m[1] * y + m[2] * zz + m[3];
        float wy = m[4] * x + m[5] * y + m[6] * zz + m[7];
        float wz = m[8] * x + m[9] * y + m[10] * zz + m[11];
        return new float[] {boxX + Diorama.AX + 2 * (wx - wz), boxY + Diorama.AY + wx + wz - 2.5f * wy, wx + 0.8f * wy + wz};
    }

    // above zero when a face with this model normal looks at the camera
    private static float seen(float[] m, float nx, float ny, float nz) {
        return m[0] * nx + m[1] * ny + m[2] * nz + 0.8f * (m[4] * nx + m[5] * ny + m[6] * nz) + m[8] * nx + m[9] * ny + m[10] * nz;
    }

    private static int tint(float[] m, float nx, float ny, float nz) {
        float x = m[0] * nx + m[1] * ny + m[2] * nz, y = m[4] * nx + m[5] * ny + m[6] * nz, z = m[8] * nx + m[9] * ny + m[10] * nz;
        float lit = Math.max(0, Math.min(1, (x * LIGHT[0] + y * LIGHT[1] + z * LIGHT[2]) * 1.15f));
        int r = channel(0.70f, 1.12f, lit), gr = channel(0.64f, 1.07f, lit), b = channel(0.74f, 1.0f, lit);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private static int channel(float shade, float light, float lit) {
        return Math.round(255 * Math.min(1, shade + (light - shade) * lit));
    }

    // the matrix taking a w x h piece of the texture onto the parallelogram o, a (along u), b (along v)
    private static Matrix3x2f pose(float[] o, float[] a, float[] b, int w, int h) {
        return new Matrix3x2f((a[0] - o[0]) / w, (a[1] - o[1]) / w, (b[0] - o[0]) / h, (b[1] - o[1]) / h, o[0], o[1]);
    }

    private void blit(Gfx g, Face f) {
        g.push();
        g.mul(f.pose());
        // the GUI pipeline drops mirrored quads: draw those from the far end with the texture reversed
        boolean mirrored = f.pose().determinant() < 0;
        if (mirrored) {
            g.translate(f.w(), 0);
            g.scale(-1, 1);
        }
        g.blit(texture, 0, 0, mirrored ? f.u() + f.w() : f.u(), f.v(), f.w(), f.h(),
            mirrored ? -f.w() : f.w(), f.h(), size, size, f.tint());
        g.pop();
    }
}
