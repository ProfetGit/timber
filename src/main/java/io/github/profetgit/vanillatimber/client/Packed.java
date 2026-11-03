package io.github.profetgit.vanillatimber.client;

//? if >=1.21.2 {
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
//?} else {
/*import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
*///?}

/** Packed colours and light coordinates: the helpers whose class and package differ between versions. */
final class Packed {
    private Packed() {}

//? if >=1.21.2 {
    static int scaleRGB(int color, float scale) {
        return ARGB.scaleRGB(color, scale);
    }

    static int srgbLerp(float t, int a, int b) {
        return ARGB.srgbLerp(t, a, b);
    }

    static int light(int block, int sky) {
        return LightCoordsUtil.pack(block, sky);
    }

    static int block(int packed) {
        return LightCoordsUtil.block(packed);
    }

    static int sky(int packed) {
        return LightCoordsUtil.sky(packed);
    }
//?} else {
/*    static int scaleRGB(int color, float scale) {
        return FastColor.ARGB32.color(FastColor.ARGB32.alpha(color),
            Mth.clamp((int) (FastColor.ARGB32.red(color) * scale), 0, 255),
            Mth.clamp((int) (FastColor.ARGB32.green(color) * scale), 0, 255),
            Mth.clamp((int) (FastColor.ARGB32.blue(color) * scale), 0, 255));
    }

    static int srgbLerp(float t, int a, int b) {
        return FastColor.ARGB32.color(Mth.lerpInt(t, FastColor.ARGB32.alpha(a), FastColor.ARGB32.alpha(b)),
            Mth.lerpInt(t, FastColor.ARGB32.red(a), FastColor.ARGB32.red(b)),
            Mth.lerpInt(t, FastColor.ARGB32.green(a), FastColor.ARGB32.green(b)),
            Mth.lerpInt(t, FastColor.ARGB32.blue(a), FastColor.ARGB32.blue(b)));
    }

    static int light(int block, int sky) {
        return LightTexture.pack(block, sky);
    }

    static int block(int packed) {
        return LightTexture.block(packed);
    }

    static int sky(int packed) {
        return LightTexture.sky(packed);
    }
*///?}
}
