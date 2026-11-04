package io.github.profetgit.vanillatimber.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The private Display setters the server poses falls with (Display.java:231-328). */
@Mixin(Display.class)
public interface DisplayAccess {
    @Invoker("setTransformation")
    void timber$setTransformation(Transformation transformation);

//? if >=1.20.2 {
    @Invoker("setTransformationInterpolationDuration")
    void timber$setInterpolationDuration(int duration);

    /** Always resends (entityData.set with force), which restarts the client's interpolation. */
    @Invoker("setTransformationInterpolationDelay")
    void timber$setInterpolationDelay(int ticks);

    @Invoker("setPosRotInterpolationDuration")
    void timber$setTeleportDuration(int duration);
//?} else {
/*    @Invoker("setInterpolationDuration")
    void timber$setInterpolationDuration(int duration);

    // 1.20.1 has one interpolation (the transformation's) and no teleport interpolation
    @Invoker("setInterpolationDelay")
    void timber$setInterpolationDelay(int ticks);
*///?}

    /** Tests: the pose the entity data holds (an invoker, not reflection: 1.x Fabric runs with intermediary names). */
    @Invoker("createTransformation")
    static Transformation timber$createTransformation(net.minecraft.network.syncher.SynchedEntityData data) {
        throw new AssertionError();
    }

    @Invoker("setViewRange")
    void timber$setViewRange(float range);

    @Invoker("setBrightnessOverride")
    void timber$setBrightnessOverride(net.minecraft.util.Brightness brightness);
}
