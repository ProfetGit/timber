package io.github.profetgit.vanillatimber.mixin;

import io.github.profetgit.vanillatimber.client.ClientFalls;
import io.github.profetgit.vanillatimber.client.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} else {
/*import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
*///?}

/** Falling trees are submitted with the entities, so shader packs shade them like entities (HavingABlast debris). */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
//? if >=1.21.2 {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void vanilla_timber$submitFalls(PoseStack ps, LevelRenderState state, SubmitNodeCollector out, CallbackInfo ci) {
        ClientFalls.submit(new RenderPass(ps, state, out));
    }
//?} else {
/*    // after the entities, before their buffers are flushed: the falls share vanilla's entity batches
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V"))
    private void vanilla_timber$submitFalls(DeltaTracker delta, boolean outline, Camera camera, GameRenderer renderer, LightTexture light,
        Matrix4f view, Matrix4f projection, CallbackInfo ci, @Local PoseStack ps, @Local MultiBufferSource.BufferSource buffers) {
        ClientFalls.submit(new RenderPass(ps, buffers, camera.getPosition()));
    }
*///?}
}
