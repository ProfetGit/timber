package io.github.profetgit.vanillatimber.mixin.forge;

import io.github.profetgit.vanillatimber.server.Hooks;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The chop on Forge 1.x: the common ServerPlayerGameModeMixin is empty in the Fabric target's sources this side project
 * compiles (Stonecutter can't tell Forge from Fabric there), so the same hook lives here. Forge's break event fires before
 * the block is removed and the tool is worn before removal, so the hook runs when destroyBlock returns true and the block
 * really changed.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ForgeChopMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    // a stack, since another mod may break blocks from inside a break
    @Unique private final ArrayDeque<BlockState> vanilla_timber$broken = new ArrayDeque<>();

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void vanilla_timber$before(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        vanilla_timber$broken.push(level.getBlockState(pos));
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void vanilla_timber$after(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = vanilla_timber$broken.poll();
        if (state != null && cir.getReturnValueZ() && level.getBlockState(pos) != state) Hooks.blockBroken(level, player, pos, state);
    }
}
