package io.github.profetgit.vanillatimber.mixin;

import io.github.profetgit.vanillatimber.server.Displays;
import io.github.profetgit.vanillatimber.server.Viewers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A fall's displays are never saved, and players who draw the fall themselves never get them. */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void timber$neverSave(CallbackInfoReturnable<Boolean> cir) {
        if (Displays.ours((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void timber$hide(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (Displays.ours((Entity) (Object) this) && Viewers.local(player)) cir.setReturnValue(false);
    }
}
