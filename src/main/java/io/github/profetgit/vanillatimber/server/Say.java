package io.github.profetgit.vanillatimber.server;

import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Every line Timber sends a player, so the self-test can read them. */
public final class Say {
    /** Self-test only: sees every message before it is sent. */
    public static volatile BiConsumer<ServerPlayer, Component> tap;

    private Say() {}

    static void bar(ServerPlayer player, Component text) {
        var t = tap;
        if (t != null) t.accept(player, text);
        player.sendOverlayMessage(text);
    }

    static void chat(ServerPlayer player, Component text) {
        var t = tap;
        if (t != null) t.accept(player, text);
        player.sendSystemMessage(text);
    }
}
