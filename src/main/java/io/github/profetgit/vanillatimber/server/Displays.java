package io.github.profetgit.vanillatimber.server;

import com.mojang.math.Transformation;
import io.github.profetgit.vanillatimber.mixin.BlockDisplayAccess;
import io.github.profetgit.vanillatimber.mixin.DisplayAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The block displays vanilla viewers see a fall with, posed straight from Java (no NBT merges). */
public final class Displays {
    public static final String TAG = "vanilla_timber.fall";

    private Displays() {}

    public static boolean ours(Entity e) {
        return e instanceof Display.BlockDisplay && e.entityTags().contains(TAG);
    }

    /** A display at the pivot, turned to the fall's yaw: teleport over 1 tick, view range 1.5, as 1.3.6 spawned them. */
    static Display.BlockDisplay spawn(ServerLevel level, BlockState state, double x, double y, double z, float yaw, float pitch,
                                      Transformation pose) {
        Display.BlockDisplay d = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        d.addTag(TAG);
        d.snapTo(x, y, z, yaw, pitch);
        ((BlockDisplayAccess) d).timber$setBlockState(state);
        DisplayAccess a = (DisplayAccess) d;
        //? if >=1.20.2
        a.timber$setTeleportDuration(1);
        a.timber$setViewRange(1.5f);
        a.timber$setTransformation(pose);
        level.addFreshEntity(d);
        return d;
    }

    /** Light at a cell; inside an opaque block (a log at rest, a trunk swinging through the ground) the brightest open neighbour. */
    public static int lightAt(BlockAndLightGetter level, BlockPos cell) {
//? if >=1.21.2 {
        if (!level.getBlockState(cell).isSolidRender()) return LightCoordsUtil.getLightCoords(level, cell);
//?} else {
/*        if (!level.getBlockState(cell).isSolidRender(level, cell)) return LightCoordsUtil.getLightCoords(level, cell);
*///?}
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        int b = 0, s = 0;
        for (Direction d : Direction.values()) {
            n.setWithOffset(cell, d);
            int l = LightCoordsUtil.getLightCoords(level, n);
            b = Math.max(b, LightCoordsUtil.block(l));
            s = Math.max(s, LightCoordsUtil.sky(l));
        }
        return LightCoordsUtil.pack(b, s);
    }

    /** Lights a display like the mesh's path light (a display samples its entity's cell, the pivot, by itself). */
    static void light(Display d, int block, int sky) {
        ((DisplayAccess) d).timber$setBrightnessOverride(new Brightness(block, sky));
    }

    /** A new pose, reached over {@code ticks} from when it arrives. */
    static void pose(Display d, Transformation pose, int ticks) {
        DisplayAccess a = (DisplayAccess) d;
        a.timber$setTransformation(pose);
        a.timber$setInterpolationDuration(ticks);
        a.timber$setInterpolationDelay(0);
    }

    static Transformation of(double x, double y, double z, Quaternionf left, double sx, double sy, double sz, Quaternionf right) {
        return new Transformation(new Vector3f((float) x, (float) y, (float) z), left, new Vector3f((float) sx, (float) sy, (float) sz),
            right);
    }
}
