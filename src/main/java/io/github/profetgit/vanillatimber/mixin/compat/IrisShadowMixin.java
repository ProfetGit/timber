package io.github.profetgit.vanillatimber.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
//? if >=26.1 {
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.vanillatimber.client.ClientFalls;
import io.github.profetgit.vanillatimber.client.RenderPass;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/**
 * Iris's shadow pass never calls {@code LevelRenderer.submitEntities}: it submits entities into its own storage from the
 * sun's camera. The falls join them here, so the mesh casts a shadow like the standing tree did. Applied only when
 * Iris has {@code renderEntities} with exactly the descriptor listed in {@code MixinPlugin}.
 */
//? if >=26.1 {
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer")
//?} else {
/*@Mixin(net.minecraft.client.Minecraft.class) // before 26.1 Iris's shadow pass draws entities itself: no hook, MixinPlugin skips this
*///?}
abstract class IrisShadowMixin {
//? if >=26.1 {
    @Shadow
    @Final
    private SubmitNodeStorage submitNodeStorage;

    @Inject(method = "renderEntities", at = @At("RETURN"), require = 0)
    private void vanilla_timber$shadowFalls(CallbackInfoReturnable<Integer> cir, @Local(argsOnly = true) PoseStack ps,
        @Local(argsOnly = true, ordinal = 0) double cx, @Local(argsOnly = true, ordinal = 1) double cy, @Local(argsOnly = true, ordinal = 2) double cz) {
        ClientFalls.submit(new RenderPass(ps, new Vec3(cx, cy, cz), submitNodeStorage));
    }
//?}
}
