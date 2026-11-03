package io.github.profetgit.vanillatimber.mixin;

import io.github.profetgit.vanillatimber.selftest.SelfTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick", at = @At("TAIL"))
    private void vanilla_timber$frame(boolean advance, CallbackInfo ci) {
        if (SelfTest.active()) SelfTest.onFrame((Minecraft) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void vanilla_timber$tick(CallbackInfo ci) {
        if (SelfTest.active()) SelfTest.onClientTick((Minecraft) (Object) this);
    }
}
