package io.github.profetgit.vanillatimber.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.vanillatimber.client.ClientFalls;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Falling trees are submitted with the entities, so shader packs shade them like entities (HavingABlast debris). */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void vanilla_timber$submitFalls(PoseStack ps, LevelRenderState state, SubmitNodeCollector out, CallbackInfo ci) {
        ClientFalls.submit(ps, state, out);
    }
}
