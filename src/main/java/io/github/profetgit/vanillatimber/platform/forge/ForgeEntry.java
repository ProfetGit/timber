package io.github.profetgit.vanillatimber.platform.forge;

//? forge {
/*import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.client.TimberClient;
import io.github.profetgit.vanillatimber.net.Hello;
import io.github.profetgit.vanillatimber.net.TreeFall;
import io.github.profetgit.vanillatimber.platform.Platform;
import io.github.profetgit.vanillatimber.selftest.TestBlocks;
import io.github.profetgit.vanillatimber.server.Hold;
import io.github.profetgit.vanillatimber.server.Hooks;
import io.github.profetgit.vanillatimber.server.TreeTypes;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.registries.RegisterEvent;

// Forge: its events into Hooks; the channel is optional, so vanilla clients and servers still connect
@Mod(Timber.ID)
public final class ForgeEntry implements Platform {
    static SimpleChannel channel;

    public ForgeEntry(FMLJavaModLoadingContext context) {
        Timber.init(this);
        channel = ChannelBuilder.named(Identifier.fromNamespaceAndPath(Timber.ID, "main")).networkProtocolVersion(1).optional().simpleChannel();
        channel.play().serverbound().addMain(Hello.class, Hello.CODEC, (msg, ctx) -> {
            if (ctx.getSender() != null) Hooks.hello(ctx.getSender(), msg.version());
        });
        channel.play().clientbound().addMain(TreeFall.class, TreeFall.CODEC, (msg, ctx) -> TimberClient.received(msg));
        RegisterEvent.getBus(context.getModBusGroup()).addListener(e -> {
            if (e.getRegistryKey().equals(Registries.TICKET_TYPE)) Hold.register();
            if (e.getRegistryKey().equals(Registries.BLOCK) && TestBlocks.wanted()) TestBlocks.register();
        });
        AddReloadListenerEvent.BUS.addListener(e -> e.addListener(TreeTypes.listener()));
        ServerStartedEvent.BUS.addListener(e -> Hooks.serverStarted(e.getServer()));
        ServerStoppingEvent.BUS.addListener(e -> Hooks.serverStopping(e.getServer()));
        ServerStoppedEvent.BUS.addListener(e -> Hooks.serverStopped(e.getServer()));
        // a null player is the sync after /reload (a join syncs to that player only)
        OnDatapackSyncEvent.BUS.addListener(e -> {
            if (e.getPlayer() == null) Hooks.dataReloaded(e.getPlayerList().getServer(), true);
        });
        TickEvent.LevelTickEvent.Pre.BUS.addListener(e -> {
            if (e.level() instanceof ServerLevel level) Hooks.levelTickStart(level);
        });
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> Hooks.serverTickEnd(e.server()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer p) Hooks.join(p.level().getServer(), p);
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer p) Hooks.leave(p);
        });
        EntityJoinLevelEvent.BUS.addListener(e -> {
            if (e.getLevel() instanceof ServerLevel) Hooks.entityLoad(e.getEntity());
            return false;
        });
        RegisterCommandsEvent.BUS.addListener(e -> Hooks.commands(e.getDispatcher()));
        if (FMLEnvironment.dist.isClient()) ForgeClientEntry.register();
    }

    @Override
    public String loader() {
        return "forge";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.isLoaded(modId);
    }

    @Override
    public String modVersion(String modId) {
        return ModList.getModContainerById(modId).map(m -> m.getModInfo().getVersion().toString()).orElse(null);
    }

    // Forge's own hook only reads the result; a listener may also cancel
    @Override
    public boolean mayBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        BlockEvent.BreakEvent e = new BlockEvent.BreakEvent(level, pos, state, player, Result.DEFAULT);
        return !(BlockEvent.BreakEvent.BUS.post(e) | e.getResult().isDenied());
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && channel.isRemotePresent(player.connection.getConnection());
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        channel.send(payload, PacketDistributor.PLAYER.with(player));
    }
}
*///?}
