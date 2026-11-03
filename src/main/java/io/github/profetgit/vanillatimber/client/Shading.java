package io.github.profetgit.vanillatimber.client;

import net.minecraft.core.Direction;

/** Vanilla's directional face shade for any normal (SeamlessDisplays {@code Shade}): an ambient cube of the six face values. */
final class Shading {
    private Shading() {}

    /** {@code byFace}: the six face values by {@link Direction#ordinal}, from {@link RenderPass#shades}. */
    static float factor(float nx, float ny, float nz, float[] byFace) {
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-6f) return 1.0f;
        nx /= len;
        ny /= len;
        nz /= len;
        return nx * nx * byFace[(nx >= 0 ? Direction.EAST : Direction.WEST).ordinal()]
            + ny * ny * byFace[(ny >= 0 ? Direction.UP : Direction.DOWN).ordinal()]
            + nz * nz * byFace[(nz >= 0 ? Direction.SOUTH : Direction.NORTH).ordinal()];
    }
}
