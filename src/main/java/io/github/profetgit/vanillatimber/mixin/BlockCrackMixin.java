package io.github.profetgit.vanillatimber.mixin;

import org.spongepowered.asm.mixin.Mixin;
//? if >=26.3 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.profetgit.vanillatimber.client.ChopModels;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.injection.At;

/** Chop mode (PRD 5.13): the breaking crack is drawn on the narrowed log's core, not the full cube around it. */
@Mixin(LevelRenderer.class)
abstract class BlockCrackMixin {
    @WrapOperation(method = "submitBlockDestroyAnimation", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;collectParts(Lnet/minecraft/util/RandomSource;Ljava/util/List;)V"))
    private void vanilla_timber$core(BlockStateModel model, RandomSource random, List<BlockStateModelPart> parts, Operation<Void> original,
                                     @Local(name = "state") BlockBreakingRenderState state) {
        BlockStateModelPart core = ChopModels.crackPart(state.blockState(), state.blockPos());
        if (core != null) parts.add(core);
        else original.call(model, random, parts);
    }
}
//?} else {
/*@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer")
abstract class BlockCrackMixin {
}
*///?}
