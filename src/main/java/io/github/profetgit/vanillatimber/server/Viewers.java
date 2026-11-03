package io.github.profetgit.vanillatimber.server;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/** Players whose client draws falls itself (they said Hello). Cleared on leave and on server stop. */
public final class Viewers {
    private static final Set<UUID> LOCAL = ConcurrentHashMap.newKeySet();
    /**
     * Self-test only: {@link #VANILLA} treats every player as a vanilla client (displays, no payload); {@link #BOTH} gives
     * modded viewers the displays as well as the payload, so both drawings can be compared in one client.
     */
    public static volatile int test;
    public static final int VANILLA = 1, BOTH = 2;

    private Viewers() {}

    public static boolean local(ServerPlayer player) {
        return test != VANILLA && LOCAL.contains(player.getUUID());
    }

    /** Whether the displays are hidden from this player (it draws the fall itself). */
    public static boolean hidden(ServerPlayer player) {
        return test != BOTH && local(player);
    }

    public static void hello(ServerPlayer player) {
        LOCAL.add(player.getUUID());
    }

    public static void leave(ServerPlayer player) {
        LOCAL.remove(player.getUUID());
    }

    static void clear() {
        LOCAL.clear();
    }
}
