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

    @Invoker("setTransformationInterpolationDuration")
    void timber$setInterpolationDuration(int duration);

    /** Always resends (entityData.set with force), which restarts the client's interpolation. */
    @Invoker("setTransformationInterpolationDelay")
    void timber$setInterpolationDelay(int ticks);

    @Invoker("setPosRotInterpolationDuration")
    void timber$setTeleportDuration(int duration);

    @Invoker("setViewRange")
    void timber$setViewRange(float range);

    @Invoker("setBrightnessOverride")
    void timber$setBrightnessOverride(net.minecraft.util.Brightness brightness);
}
