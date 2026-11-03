package io.github.profetgit.vanillatimber.platform.fabric;

//? fabric {
import io.github.profetgit.vanillatimber.client.TimberClient;
import io.github.profetgit.vanillatimber.net.TreeFall;
import io.github.profetgit.vanillatimber.platform.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric client events into {@link TimberClient}. */
public final class FabricClientEntry implements ClientModInitializer, Platform.Client {
    @Override
    public void onInitializeClient() {
        TimberClient.init(this);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> TimberClient.joined());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TimberClient.left());
        ClientPlayNetworking.registerGlobalReceiver(TreeFall.TYPE, (payload, ctx) -> {
            if (ctx.client().isSameThread()) TimberClient.received(payload);
            else ctx.client().execute(() -> TimberClient.received(payload));
        });
//? if >=1.21.2 {
        ClientTickEvents.END_LEVEL_TICK.register(TimberClient::levelTick);
//?} else {
/*        ClientTickEvents.END_WORLD_TICK.register(TimberClient::levelTick);
*///?}
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
//?}
