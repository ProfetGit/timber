package io.github.profetgit.vanillatimber.net;

import io.github.profetgit.vanillatimber.Timber;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server, once per join: "my client draws falls itself, protocol {@code version}". The only thing a modded
 * client ever sends (PRD 7); the server then sends it {@link TreeFall} instead of displays.
 */
public record Hello(int version) implements CustomPacketPayload {
    public static final int VERSION = 2;
    public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Timber.ID, "hello"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.of((buf, h) -> buf.writeVarInt(h.version()), buf -> new Hello(buf.readVarInt()));

    @Override
    public Type<Hello> type() {
        return TYPE;
    }
}
