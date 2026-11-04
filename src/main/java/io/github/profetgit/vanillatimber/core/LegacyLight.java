package io.github.profetgit.vanillatimber.core;

//? if <26.1 {
/*import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

// stands in for 26.x's LightCoordsUtil (dev/renames.json points it here): packed light, usable on a dedicated server
public final class LegacyLight {
    private LegacyLight() {}

    public static int block(int packed) {
        return packed >> 4 & 0xFFFF;
    }

    public static int sky(int packed) {
        return packed >> 20 & 0xFFFF;
    }

    public static int pack(int block, int sky) {
        return block << 4 | sky << 20;
    }

    public static int getLightCoords(BlockAndTintGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.emissiveRendering(level, pos)) return 15728880;
        int sky = level.getBrightness(LightLayer.SKY, pos);
        int block = Math.max(level.getBrightness(LightLayer.BLOCK, pos), state.getLightEmission());
        return pack(block, sky);
    }
}
*///?}
