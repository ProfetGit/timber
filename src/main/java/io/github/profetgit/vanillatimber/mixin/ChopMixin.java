package io.github.profetgit.vanillatimber.mixin;

import io.github.profetgit.vanillatimber.server.Chops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chop mode (PRD 5.13): a break of a trunk log is first offered to {@link Chops}, which keeps the log standing and counts
 * a chop. The mining packets and the delayed break are bracketed so {@code Chops} knows which {@code destroyBlock} is
 * the player's own mining and which is another mod's break.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ChopMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void vanilla_timber$chop(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Chops.intercept(player, level, pos)) cir.setReturnValue(false);
    }

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
    private void vanilla_timber$actionIn(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxY, int sequence,
                                         CallbackInfo ci) {
        Chops.actionIn(player, level, pos, action);
    }

    @Inject(method = "handleBlockBreakAction", at = @At("RETURN"))
    private void vanilla_timber$actionOut(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxY, int sequence,
                                          CallbackInfo ci) {
        Chops.actionOut();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void vanilla_timber$tickIn(CallbackInfo ci) {
        Chops.tickIn(player);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void vanilla_timber$tickOut(CallbackInfo ci) {
        Chops.tickOut();
    }
}
