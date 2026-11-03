package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.Timber;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * PRD 8: a chunk ticket around a fall's stump for the fall's lifetime, so the chunks its drops land in can't unload
 * mid-fall. Two falls on the same chunk and radius share one ticket (the storage dedupes them), so releases are counted.
 */
final class Hold {
    private record Key(long chunk, int radius) {}

    private static TicketType type;
    private static final Map<ServerLevel, Map<Key, Integer>> HELD = new IdentityHashMap<>();

    private Hold() {}

    /** Registers the ticket type; runs at mod init, while the registry is still open. */
    static void register() {
        type = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Timber.ID, "fall"),
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));
    }

    static int radius(int treeHeight) {
        return Math.min(12, treeHeight / 16 + 2);
    }

    static void add(ServerLevel level, ChunkPos chunk, int radius) {
        Map<Key, Integer> held = HELD.computeIfAbsent(level, l -> new HashMap<>());
        if (held.merge(new Key(chunk.pack(), radius), 1, Integer::sum) == 1) level.getChunkSource().addTicketWithRadius(type, chunk, radius);
    }

    static void release(ServerLevel level, ChunkPos chunk, int radius) {
        Map<Key, Integer> held = HELD.get(level);
        if (held == null) return;
        Key k = new Key(chunk.pack(), radius);
        Integer n = held.get(k);
        if (n == null) return;
        if (n > 1) {
            held.put(k, n - 1);
            return;
        }
        held.remove(k);
        level.getChunkSource().removeTicketWithRadius(type, chunk, radius);
    }

    /** Tickets held in the level (tests). */
    static int count(ServerLevel level) {
        Map<Key, Integer> held = HELD.get(level);
        return held == null ? 0 : held.size();
    }

    static void clear() {
        HELD.clear();
    }
}
