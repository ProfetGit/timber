package io.github.profetgit.vanillatimber.mixin;

import io.github.profetgit.vanillatimber.client.ChopClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chop mode (PRD 5.13): a modded client doesn't predict the removal of a log the server will keep. */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void vanilla_timber$keep(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ChopClient.keeps(pos)) cir.setReturnValue(false);
    }
}
