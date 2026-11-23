package io.github.profetgit.vanillatimber.mixin;

import org.spongepowered.asm.mixin.Mixin;
//? if fabric && >=26.2 {
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
//?} else if fabric && >=26.1 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.vanillatimber.client.ChopModels;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class BlockCrackMixin {
    @WrapOperation(method = "submitBlockDestroyAnimation", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitBreakingBlockModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;JI)V"))
    private void vanilla_timber$core(SubmitNodeCollector collector, PoseStack poseStack, BlockStateModel model, long seed, int progress,
                                     Operation<Void> original, @Local(name = "state") BlockBreakingRenderState state) {
        BlockStateModel core = ChopModels.crackModel(state.blockState(), state.blockPos());
        original.call(collector, poseStack, core != null ? core : model, seed, progress);
    }
}
*///?} else if fabric && >=1.21.11 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.profetgit.vanillatimber.client.ChopModels;
import java.util.List;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockRenderDispatcher.class)
abstract class BlockCrackMixin {
    @WrapOperation(method = "renderBreakingTexture", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/model/BlockStateModel;collectParts(Lnet/minecraft/util/RandomSource;Ljava/util/List;)V"))
    private void vanilla_timber$core(BlockStateModel model, RandomSource random, List<BlockStateModelPart> parts, Operation<Void> original,
                                     @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) BlockPos pos) {
        BlockStateModelPart core = ChopModels.crackPart(state, pos);
        if (core != null) parts.add(core);
        else original.call(model, random, parts);
    }
}
*///?} else {
/*@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer")
abstract class BlockCrackMixin {
}
*///?}
