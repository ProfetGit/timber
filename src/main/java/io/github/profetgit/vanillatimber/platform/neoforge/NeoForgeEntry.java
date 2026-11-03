package io.github.profetgit.vanillatimber.platform.neoforge;

//? neoforge {
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

// NeoForge: its events into Hooks; payloads optional both ways, so vanilla clients and servers still connect
@Mod(Timber.ID)
public final class NeoForgeEntry implements Platform {
    public NeoForgeEntry(IEventBus modBus) {
        Timber.init(this);
        modBus.addListener(NeoForgeEntry::payloads);
        modBus.addListener((RegisterEvent e) -> {
            if (e.getRegistryKey().equals(Registries.TICKET_TYPE)) Hold.register();
            if (e.getRegistryKey().equals(Registries.BLOCK) && TestBlocks.wanted()) TestBlocks.register();
        });
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((AddServerReloadListenersEvent e) -> e.addListener(TreeTypes.ID, TreeTypes.listener()));
        bus.addListener((ServerStartedEvent e) -> Hooks.serverStarted(e.getServer()));
        bus.addListener((ServerStoppingEvent e) -> Hooks.serverStopping(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> Hooks.serverStopped(e.getServer()));
        // a null player is the sync after /reload (a join syncs to that player only); it comes before the new functions
        bus.addListener((OnDatapackSyncEvent e) -> {
            if (e.getPlayer() == null) Hooks.dataReloadedSoon();
        });
        bus.addListener((LevelTickEvent.Pre e) -> {
            if (e.getLevel() instanceof ServerLevel level) Hooks.levelTickStart(level);
        });
        bus.addListener((ServerTickEvent.Post e) -> Hooks.serverTickEnd(e.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Hooks.join(p.level().getServer(), p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Hooks.leave(p);
        });
        bus.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel() instanceof ServerLevel) Hooks.entityLoad(e.getEntity());
        });
        bus.addListener((RegisterCommandsEvent e) -> Hooks.commands(e.getDispatcher()));
    }

    private static void payloads(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1").optional();
        r.playToServer(Hello.TYPE, Hello.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) Hooks.hello(p, msg.version());
        }));
        r.playToClient(TreeFall.TYPE, TreeFall.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> TimberClient.received(msg)));
    }

    @Override
    public String loader() {
        return "neoforge";
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

    @Override
    public boolean mayBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        return !NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, state, player)).isCanceled();
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
*///?}
