package io.github.profetgit.vanillatimber.client;

import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;

/** Vanilla's directional face shade for any normal (SeamlessDisplays {@code Shade}): an ambient cube of the six face values. */
final class Shading {
    private Shading() {}

    /** {@code byFace}, not the record fields: Iris patches it when a shader pack turns vanilla shading off. */
    static float factor(float nx, float ny, float nz, CardinalLighting cl) {
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-6f) return 1.0f;
        nx /= len;
        ny /= len;
        nz /= len;
        return nx * nx * cl.byFace(nx >= 0 ? Direction.EAST : Direction.WEST)
            + ny * ny * cl.byFace(ny >= 0 ? Direction.UP : Direction.DOWN)
            + nz * nz * cl.byFace(nz >= 0 ? Direction.SOUTH : Direction.NORTH);
    }
}
