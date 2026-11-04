package io.github.profetgit.vanillatimber.platform.forge;

import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.net.Hello;
import io.github.profetgit.vanillatimber.net.TreeFall;
import io.github.profetgit.vanillatimber.net.legacy.CustomPacketPayload;
import io.github.profetgit.vanillatimber.platform.Platform;
import io.github.profetgit.vanillatimber.selftest.TestBlocks;
import io.github.profetgit.vanillatimber.server.Hold;
import io.github.profetgit.vanillatimber.server.Hooks;
import io.github.profetgit.vanillatimber.server.TreeTypes;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.RegisterEvent;

// Forge 47 (1.20.1): its events into Hooks; the channel accepts a missing peer, so vanilla clients and servers still connect
@Mod(Timber.ID)
public final class ForgeEntry implements Platform {
    private static final String PROTOCOL = "1";
    static SimpleChannel channel;

    public ForgeEntry() {
        Timber.init(this);
        Hold.register();
        channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(Timber.ID, "main"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
        channel.messageBuilder(Hello.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder((m, buf) -> Hello.CODEC.encode(buf, m)).decoder(Hello.CODEC::decode)
            .consumerMainThread((msg, ctx) -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) Hooks.hello(p, msg.version());
            }).add();
        channel.messageBuilder(TreeFall.class, 1, NetworkDirection.PLAY_TO_CLIENT).encoder((m, buf) -> TreeFall.CODEC.encode(buf, m))
            .decoder(TreeFall.CODEC::decode).consumerMainThread((msg, ctx) -> ForgeClientEntry.received(msg)).add();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((RegisterEvent e) -> {
            if (e.getRegistryKey().equals(Registries.BLOCK) && TestBlocks.wanted()) {
                e.register(Registries.BLOCK, helper -> TestBlocks.register((key, block) -> helper.register(key.location(), block)));
            }
        });
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((AddReloadListenerEvent e) -> e.addListener(TreeTypes.listener()));
        bus.addListener((ServerStartedEvent e) -> Hooks.serverStarted(e.getServer()));
        bus.addListener((ServerStoppingEvent e) -> Hooks.serverStopping(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> Hooks.serverStopped(e.getServer()));
        // a null player is the sync after /reload (a join syncs to that player only); it comes before the new functions
        bus.addListener((OnDatapackSyncEvent e) -> {
            if (e.getPlayer() == null) Hooks.dataReloadedSoon();
        });
        bus.addListener((TickEvent.LevelTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START && e.level instanceof ServerLevel level) Hooks.levelTickStart(level);
        });
        bus.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) Hooks.serverTickEnd(e.getServer());
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Hooks.join(p.server, p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Hooks.leave(p);
        });
        bus.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel() instanceof ServerLevel) Hooks.entityLoad(e.getEntity());
        });
        bus.addListener((RegisterCommandsEvent e) -> Hooks.commands(e.getDispatcher()));
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
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String modVersion(String modId) {
        return ModList.get().getModContainerById(modId).map(m -> m.getModInfo().getVersion().toString()).orElse(null);
    }

    // post returns true when a listener cancelled
    @Override
    public boolean mayBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player));
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && channel.isRemotePresent(player.connection.connection);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
