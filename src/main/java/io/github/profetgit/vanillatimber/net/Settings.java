package io.github.profetgit.vanillatimber.net;

import io.github.profetgit.vanillatimber.Timber;
import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to a modded client, after {@link Hello} and whenever the config changes (PRD 5.13): every server setting by
 * its config name. The client needs {@code chops}, {@code sneak} and {@code require_axe} to tell a chop from a break (so
 * it doesn't predict the removal of a log the server keeps); the settings screen shows the rest on a server.
 */
public record Settings(String[] names, int[] values) implements CustomPacketPayload {
    /** Name prefix of a {@code tools} entry ("tool:#minecraft:axes", value 0). */
    public static final String TOOL = "tool:";
    public static final Type<Settings> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Timber.ID, "settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = StreamCodec.of(
        (buf, s) -> {
            buf.writeVarInt(s.names().length);
            for (int i = 0; i < s.names().length; i++) {
                buf.writeUtf(s.names()[i], 64);
                buf.writeVarInt(s.values()[i]);
            }
        },
        buf -> {
            int n = Math.min(buf.readVarInt(), 256);
            String[] names = new String[n];
            int[] values = new int[n];
            for (int i = 0; i < n; i++) {
                names[i] = buf.readUtf(64);
                values[i] = buf.readVarInt();
            }
            return new Settings(names, values);
        });

    /** The value of {@code name}, or {@code fallback} when the server didn't send it. */
    public int get(String name, int fallback) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(name)) return values[i];
        return fallback;
    }

    /** The server's tools list, or null when it sent none (a 2.1 server: axes). */
    public java.util.List<String> tools() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String n : names) if (n.startsWith(TOOL)) out.add(n.substring(TOOL.length()));
        return out.isEmpty() ? null : out;
    }

    public boolean has(String name) {
        for (String n : names) if (n.equals(name)) return true;
        return false;
    }

    public boolean same(Settings o) {
        return o != null && Arrays.equals(names, o.names) && Arrays.equals(values, o.values);
    }

    @Override
    public Type<Settings> type() {
        return TYPE;
    }
}
