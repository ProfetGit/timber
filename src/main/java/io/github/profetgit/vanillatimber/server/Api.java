package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.api.TimberEvents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * PRD 5.7 add-on API: {@code storage timber:meta} and {@code #timber:api/loaded} on load and after every reload,
 * {@code #timber:api/cancel} (and the Java {@link TimberEvents#CANCEL}) at each chop, and the {@code requires}
 * list the menu and join hint show.
 */
public final class Api {
    static final Identifier META = Identifier.fromNamespaceAndPath("timber", "meta");
    static final Identifier CANCEL = Identifier.fromNamespaceAndPath("timber", "api/cancel");
    static final Identifier LOADED = Identifier.fromNamespaceAndPath("timber", "api/loaded");
    public static final String VERSION = "2.0.0";
    public static final int VERSION_ID = 20000;

    private Api() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(Api::load);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) load(server);
        });
    }

    static void load(MinecraftServer server) {
        if (Migrate.inert) return;
        // 1.3.6 set only these keys (data modify), so other keys an add-on keeps here survive
        CompoundTag meta = server.getCommandStorage().get(META).copy();
        meta.putString("version", VERSION);
        meta.putInt("version_id", VERSION_ID);
        meta.put("requires", new ListTag());
        server.getCommandStorage().set(META, meta);
        if (server.getFunctions().getTag(LOADED).isEmpty()) return;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "function #" + LOADED);
    }

    /** Null to go on; otherwise a cancel, with the text to show ({@link Component#empty()} for none). */
    static @Nullable Component cancel(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        for (TimberEvents.Cancel c : TimberEvents.CANCEL.listeners()) {
            Component why = c.cancel(level, player, pos, state);
            if (why != null) return why;
        }
        MinecraftServer server = level.getServer();
        if (server.getFunctions().getTag(CANCEL).isEmpty()) return null;
        boolean[] cancelled = {false};
        CommandSourceStack source = player.createCommandSourceStack().withLevel(level).withPosition(Vec3.atCenterOf(pos))
            .withRotation(new Vec2(player.getXRot(), player.getYRot())).withPermission(LevelBasedPermissionSet.GAMEMASTER)
            .withSuppressedOutput().withCallback((ok, result) -> {
                if (ok) cancelled[0] = true;
            });
        server.getCommands().performPrefixedCommand(source, "execute if function #" + CANCEL + " run return 1");
        return cancelled[0] ? Component.empty() : null;
    }

    /** The add-ons' requirement lines from {@code timber:meta requires}. */
    public static List<Component> requires(MinecraftServer server) {
        List<Component> out = new ArrayList<>();
        if (!(server.getCommandStorage().get(META).get("requires") instanceof ListTag list)) return out;
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (Tag t : list) ComponentSerialization.CODEC.parse(ops, t).result().ifPresent(out::add);
        return out;
    }
}
