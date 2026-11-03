package timberprobe.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import timberprobe.Probe;

@Mixin(DisplayRenderer.BlockDisplayRenderer.class)
public abstract class BlockDisplayRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$BlockDisplay;Lnet/minecraft/client/renderer/entity/state/BlockDisplayEntityRenderState;F)V", at = @At("TAIL"))
    private void timberprobe$extract(Display.BlockDisplay entity, BlockDisplayEntityRenderState state, float partial, CallbackInfo ci) {
        Display.BlockDisplay.BlockRenderState b = entity.blockRenderState();
        Probe.extracted(state, entity.getId(), b == null ? null : String.valueOf(b.blockState()));
    }

    @Inject(method = "submitInner(Lnet/minecraft/client/renderer/entity/state/BlockDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V", at = @At("HEAD"))
    private void timberprobe$submit(BlockDisplayEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, float progress, CallbackInfo ci) {
        Probe.submitted(state, new org.joml.Matrix4f(poseStack.last().pose()), state.entityXRot, state.interpolationProgress);
    }
}
