package io.github.profetgit.vanillatimber.server;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/** Players whose client draws falls itself (they said Hello). Cleared on leave and on server stop. */
public final class Viewers {
    private static final Set<UUID> LOCAL = ConcurrentHashMap.newKeySet();

    private Viewers() {}

    public static boolean local(ServerPlayer player) {
        return LOCAL.contains(player.getUUID());
    }

    static void hello(ServerPlayer player) {
        LOCAL.add(player.getUUID());
    }

    static void leave(ServerPlayer player) {
        LOCAL.remove(player.getUUID());
    }

    static void clear() {
        LOCAL.clear();
    }
}
