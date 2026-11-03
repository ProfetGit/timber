package io.github.profetgit.vanillatimber.server;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Every server event Timber listens to, in one place and one order. Each loader's entry ({@code platform/<loader>/})
 * calls these from its own events; nothing else in {@code server/} names a loader.
 */
public final class Hooks {
    /** Self-test only: runs after Timber's own end-of-tick work. */
    public static volatile @Nullable Consumer<MinecraftServer> testTick;

    private Hooks() {}

    public static void serverStarted(MinecraftServer server) {
        Migrate.start(server);
        Api.load(server);
        Pending.returnAll(server);
        Players.ensureObjective(server);
        Conflicts.started();
    }

    public static void serverStopping(MinecraftServer server) {
        Migrate.stopping();
        Falls.stopping();
        Players.stopping(server);
    }

    public static void serverStopped(MinecraftServer server) {
        Conflicts.stopped();
    }

    public static void dataReloaded(MinecraftServer server, boolean success) {
        if (success) Api.load(server);
    }

    private static boolean reloadPending;

    /**
     * NeoForge and Forge announce a reload (their datapack sync) before the server swaps in the new functions, so
     * {@code #timber:api/loaded} would run the old ones: run it at the end of that tick instead.
     */
    public static void dataReloadedSoon() {
        reloadPending = true;
    }

    public static void levelTickStart(ServerLevel level) {
        Falls.tick(level);
    }

    public static void serverTickEnd(MinecraftServer server) {
        if (reloadPending) {
            reloadPending = false;
            Api.load(server);
        }
        Migrate.tick(server);
        Players.tick(server);
        Consumer<MinecraftServer> t = testTick;
        if (t != null) t.accept(server);
    }

    public static void join(MinecraftServer server, ServerPlayer player) {
        Migrate.join(server, player);
        Players.join(player);
        Conflicts.tell(player);
    }

    public static void leave(ServerPlayer player) {
        Viewers.leave(player);
        Falls.leave(player);
    }

    public static void entityLoad(Entity entity) {
        Migrate.entityLoad(entity);
    }

    /** After a player broke a block (the log is gone): the chop. */
    public static void blockBroken(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        Falls.chop(level, player, pos, state);
    }

    /** A modded client's {@link io.github.profetgit.vanillatimber.net.Hello}, on the server thread. */
    public static void hello(ServerPlayer player, int version) {
        if (version == io.github.profetgit.vanillatimber.net.Hello.VERSION) Viewers.hello(player);
    }

    public static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        TimberCommand.register(dispatcher);
    }
}
