package io.github.profetgit.vanillatimber.net;

import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.server.Viewers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server, once per join: "my client draws falls itself, protocol {@code version}". The only thing a modded
 * client ever sends (PRD 7); the server then sends it {@link TreeFall} instead of displays.
 */
public record Hello(int version) implements CustomPacketPayload {
    public static final int VERSION = 1;
    public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Timber.ID, "hello"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Hello::version, Hello::new);

    @Override
    public Type<Hello> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        TreeFall.register();
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (msg, ctx) -> ctx.server().execute(() -> {
            if (msg.version() == VERSION) Viewers.hello(ctx.player());
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Viewers.leave(handler.getPlayer()));
    }
}
