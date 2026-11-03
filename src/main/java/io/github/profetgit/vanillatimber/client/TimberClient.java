package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.net.Hello;
import io.github.profetgit.vanillatimber.net.TreeFall;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entry: says Hello on join, then draws every TreeFall itself (the server hides its displays from us). */
public final class TimberClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Profet's Timber");

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientFalls.clear();
            if (ClientPlayNetworking.canSend(Hello.TYPE)) ClientPlayNetworking.send(new Hello(Hello.VERSION));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientFalls.clear());
        ClientPlayNetworking.registerGlobalReceiver(TreeFall.TYPE, (payload, ctx) -> {
            if (ctx.client().isSameThread()) ClientFalls.onPayload(payload);
            else ctx.client().execute(() -> ClientFalls.onPayload(payload));
        });
        ClientTickEvents.END_LEVEL_TICK.register(ClientFalls::tick);
        LOG.info("Profet's Timber client loaded");
    }
}
