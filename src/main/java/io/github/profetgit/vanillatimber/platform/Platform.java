package io.github.profetgit.vanillatimber.platform;

import io.github.profetgit.vanillatimber.Timber;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What Timber needs from the mod loader. Each loader's entry class in {@code platform/<loader>/} implements it, hands
 * it to {@link Timber#init} and calls {@link io.github.profetgit.vanillatimber.server.Hooks} from its events.
 */
public interface Platform {
    static Platform get() {
        return Timber.platform();
    }

    String loader();

    Path configDir();

    boolean isModLoaded(String modId);

    /** The installed version of a mod, or null when it isn't loaded. */
    @Nullable String modVersion(String modId);

    /** PRD 5.7: the loader's break event that claim mods listen to, asked for one tree block. */
    boolean mayBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state);

    /** True when this player's client registered the payload (a modded client). */
    boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

    void send(ServerPlayer player, CustomPacketPayload payload);

    /** The client half, set by the loader's client entry; null on a dedicated server. */
    interface Client {
        boolean canSendToServer(CustomPacketPayload.Type<?> type);

        void sendToServer(CustomPacketPayload payload);
    }
}
