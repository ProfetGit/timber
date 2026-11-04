package io.github.profetgit.vanillatimber.platform.fabric;

//? fabric {
import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.net.Hello;
import io.github.profetgit.vanillatimber.net.TreeFall;
import io.github.profetgit.vanillatimber.platform.Platform;
import io.github.profetgit.vanillatimber.selftest.TestBlocks;
import io.github.profetgit.vanillatimber.server.Hold;
import io.github.profetgit.vanillatimber.server.Hooks;
import io.github.profetgit.vanillatimber.server.TreeTypes;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//? if >=1.21.11 {
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
//?} else {
/*import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
*///?}
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric and Quilt (Quilt loads the Fabric jar): Fabric API events into {@link Hooks}. */
public final class FabricEntry implements ModInitializer, Platform {
    @Override
    public void onInitialize() {
        Timber.init(this);
//? if >=26.1 {
        PayloadTypeRegistry.serverboundPlay().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TreeFall.TYPE, TreeFall.CODEC);
//?} else if >=1.20.5 {
/*        PayloadTypeRegistry.playC2S().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.playS2C().register(TreeFall.TYPE, TreeFall.CODEC);
*///?}
//? if >=1.20.5 {
        ServerPlayNetworking.registerGlobalReceiver(Hello.TYPE, (msg, ctx) -> ctx.server().execute(() -> Hooks.hello(ctx.player(), msg.version())));
//?} else {
/*        ServerPlayNetworking.registerGlobalReceiver(Hello.TYPE.id(), (server, player, handler, buf, sender) -> {
            Hello msg = Hello.CODEC.decode(buf);
            server.execute(() -> Hooks.hello(player, msg.version()));
        });
*///?}
        Hold.register();
//? if >=1.21.11 {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(TreeTypes.ID, TreeTypes.listener());
//?} else if >=1.21.2 {
/*        PreparableReloadListener types = TreeTypes.listener();
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return TreeTypes.ID;
            }

            @Override
            public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, Executor backgroundExecutor, Executor gameExecutor) {
                return types.reload(barrier, manager, backgroundExecutor, gameExecutor);
            }
        });
*///?} else {
/*        PreparableReloadListener types = TreeTypes.listener();
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return TreeTypes.ID;
            }

            @Override
            public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, ProfilerFiller prepare,
                                                  ProfilerFiller apply, Executor backgroundExecutor, Executor gameExecutor) {
                return types.reload(barrier, manager, prepare, apply, backgroundExecutor, gameExecutor);
            }
        });
*///?}
        if (TestBlocks.wanted()) TestBlocks.register();
        ServerLifecycleEvents.SERVER_STARTED.register(Hooks::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(Hooks::serverStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(Hooks::serverStopped);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> Hooks.dataReloaded(server, success));
//? if >=26.1 {
        ServerTickEvents.START_LEVEL_TICK.register(Hooks::levelTickStart);
//?} else {
/*        ServerTickEvents.START_WORLD_TICK.register(Hooks::levelTickStart);
*///?}
        ServerTickEvents.END_SERVER_TICK.register(Hooks::serverTickEnd);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Hooks.join(server, handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Hooks.leave(handler.getPlayer()));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Hooks.entityLoad(entity));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Hooks.blockBroken(sl, sp, pos, state);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> Hooks.commands(dispatcher));
    }

    @Override
    public String loader() {
        return FabricLoader.getInstance().isModLoaded("quilt_loader") ? "quilt" : "fabric";
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public String modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(null);
    }

    @Override
    public boolean mayBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
    }

//? if >=1.20.5 {
    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
//?} else {
/*    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type.id());
    }

    // TreeFall is the only server-to-client payload
    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        net.minecraft.network.FriendlyByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        TreeFall.CODEC.encode(buf, (TreeFall) payload);
        ServerPlayNetworking.send(player, TreeFall.TYPE.id(), buf);
    }
*///?}
}
//?}
