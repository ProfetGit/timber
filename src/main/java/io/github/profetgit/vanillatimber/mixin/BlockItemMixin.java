package io.github.profetgit.vanillatimber.mixin;

import io.github.profetgit.vanillatimber.server.Placed;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PRD 5.7: a log a player places is remembered at the cell it went into (Fabric has no block-place event). */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void vanilla_timber$placed(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && context.getPlayer() != null && context.getLevel() instanceof ServerLevel level
            && Placed.isLog(level.getBlockState(context.getClickedPos()))) Placed.add(level, context.getClickedPos());
    }
}
