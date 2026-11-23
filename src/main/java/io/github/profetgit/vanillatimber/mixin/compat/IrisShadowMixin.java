package io.github.profetgit.vanillatimber.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.vanillatimber.client.ClientFalls;
import io.github.profetgit.vanillatimber.client.RenderPass;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/**
 * Iris's shadow pass never calls {@code LevelRenderer.submitEntities}/{@code renderEntities}: it draws entities itself
 * from the sun's camera (1.21.9+: into its own submit storage; before: into the buffer source it is handed). The falls join
 * them here, so the mesh casts a shadow like the standing tree did. Applied only when Iris has a {@code renderEntities}
 * with the shape {@code MixinPlugin} lists.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer")
abstract class IrisShadowMixin {
//? if >=1.21.9 {
    @Shadow
    @Final
    private SubmitNodeStorage submitNodeStorage;

    @Inject(method = "renderEntities", at = @At("RETURN"), require = 0)
    private void vanilla_timber$shadowFalls(CallbackInfoReturnable<Integer> cir, @Local(argsOnly = true) PoseStack ps,
        @Local(argsOnly = true, ordinal = 0) double cx, @Local(argsOnly = true, ordinal = 1) double cy, @Local(argsOnly = true, ordinal = 2) double cz) {
        ClientFalls.submit(new RenderPass(ps, new Vec3(cx, cy, cz), submitNodeStorage));
    }
//?} else {
/*    @Inject(method = "renderEntities", at = @At("RETURN"), require = 0)
    private void vanilla_timber$shadowFalls(CallbackInfoReturnable<Integer> cir, @Local(argsOnly = true) PoseStack ps,
        @Local(argsOnly = true) MultiBufferSource.BufferSource buffers, @Local(argsOnly = true, ordinal = 0) double cx,
        @Local(argsOnly = true, ordinal = 1) double cy, @Local(argsOnly = true, ordinal = 2) double cz) {
        ClientFalls.submit(new RenderPass(ps, buffers, new Vec3(cx, cy, cz)));
    }
*///?}
}
