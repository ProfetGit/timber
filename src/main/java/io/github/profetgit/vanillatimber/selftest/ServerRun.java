package io.github.profetgit.vanillatimber.selftest;

import com.mojang.authlib.GameProfile;
import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.server.Hooks;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.20.2 {
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
//?}

/**
 * The parity groups on a dedicated server (ModTest {@code "side": "server"} jobs, mode {@code server}): no client, no
 * rendering, a server slot instead of a client slot. A bot player joins on an embedded channel that drops every packet,
 * ticks like a connected player (item pickup, the client-load timer), and stands in for the client's player; the
 * groups then run as in the client's integrated server, results.json is written and the server halts.
 */
final class ServerRun {
    private static final String USER = System.getProperty("vanilla_timber.selftest.user", "Builder");
    private static boolean started;

    private ServerRun() {}

    static boolean wanted() {
        return SelfTest.active() && "server".equals(System.getProperty("vanilla_timber.selftest.mode"));
    }

    /** Called once at mod init (from {@link TestBlocks#register}); the first server tick starts the run. */
    static void arm() {
        Hooks.testTick = ServerRun::tick;
    }

    private static void tick(MinecraftServer server) {
        if (started || !server.isDedicatedServer() || server.getTickCount() < 20) return;
        started = true;
        try {
            ModTestHook.audit();
            SelfTest.record("boot", true, "dedicated server up");
            join(server);
            SelfTest.record("bot joined", server.getPlayerList().getPlayers().size() == 1, server.getPlayerList().getPlayers().size() + " players");
            Parity.begin(server, System.getProperty("vanilla_timber.selftest.groups", "all"));
        } catch (Throwable t) {
            Timber.LOG.error("[selftest] server run failed to start", t);
            SelfTest.record("server run", false, t.toString());
            stop(server);
            return;
        }
        Thread watch = new Thread(() -> {
            while (!Parity.finished() && server.isRunning()) Parity.sleep(50);
            stop(server);
        }, "timber-selftest-server");
        watch.setDaemon(true);
        watch.start();
    }

    private static void stop(MinecraftServer server) {
        SelfTest.writeResults();
        server.execute(() -> server.halt(false));
    }

    private static void join(MinecraftServer server) throws ReflectiveOperationException {
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(USER), USER);
        ServerLevel level = server.overworld();
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        Channel channel = new EmbeddedChannel(new Sink());
        // the channel is private and named by intermediary/SRG on the 1.x builds: find it by type
        for (Field f : Connection.class.getDeclaredFields()) {
            if (f.getType() == Channel.class) {
                f.setAccessible(true);
                f.set(connection, channel);
            }
        }
//? if >=1.20.2 {
        Bot bot = new Bot(server, level, profile, ClientInformation.createDefault());
        server.getPlayerList().placeNewPlayer(connection, bot, CommonListenerCookie.createInitial(profile, false));
//?} else {
/*        Bot bot = new Bot(server, level, profile);
        server.getPlayerList().placeNewPlayer(connection, bot);
*///?}
    }

    /** Drops whatever the server writes to the bot (packets, pipeline reconfiguration). */
    private static final class Sink extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ReferenceCountUtil.release(msg);
            promise.trySuccess();
        }
    }

    /** A connected player's tick without a connection tick: the level ticks the player, and this adds the player tick. */
    private static final class Bot extends ServerPlayer {
//? if >=1.20.2 {
        Bot(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
            super(server, level, profile, info);
        }
//?} else {
/*        Bot(MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile);
        }
*///?}

        @Override
        public void tick() {
            super.tick();
            doTick();
            // what a client does each tick: report where it is (chunk tracking follows teleports) and acknowledge the
            // chunk batches it was sent; unacknowledged chunks stay "pending", and Timber sees no viewer of the tree
            ((ServerLevel) level()).getChunkSource().move(this);
//? if >=1.20.2
            connection.chunkSender.onChunkBatchReceivedByClient(64f);
        }
    }
}
