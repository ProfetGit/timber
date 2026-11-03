package timberprobe.mixin;

import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import timberprobe.Probe;

@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void timberprobe$cue(Component message, boolean remote, CallbackInfo ci) {
        Probe.cue(message.getString());
    }
}
