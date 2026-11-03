package io.github.profetgit.vanillatimber.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.profetgit.vanillatimber.Timber;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

/**
 * PRD 5.9: a world played with the 1.3.x data pack. On the server start, before {@link Api} writes the meta, the pack's
 * state is imported (placed logs, config scores, player toggles), its objectives and storages removed, and the pack
 * itself disabled if it is still selected. Its entities are cleaned whenever their chunk loads: a controller returns the
 * loot it held, every other {@code timber.} marker and display goes.
 */
public final class Migrate extends SavedData {
    /** Tests only: 2.0 does nothing, so the 1.3.6 pack can run alone in a world. */
    public static volatile boolean inert;
    /** Migrations that ran since the JVM started (tests). */
    public static volatile int runs;
    /** The last summary line (tests). */
    public static volatile String summary;

    private static final String PREFIX = "timber.";
    private static final String OLD_WELCOMED = "timber.welcomed";
    private static final Identifier PLACED = Identifier.fromNamespaceAndPath("timber", "placed");
    private static final List<Identifier> STORAGES = List.of(PLACED, id("types"), id("curve"), id("menu"), id("op"), id("anim"));
    private static final Identifier PACK_TICK = Identifier.fromNamespaceAndPath("timber", "function/tick.mcfunction");

    private static final Codec<Migrate> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.optionalFieldOf("notice", "").forGetter(d -> d.notice)
    ).apply(i, Migrate::new));
    private static final SavedDataType<Migrate> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Timber.ID, "migrate"),
        () -> new Migrate(""), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    /** The id of the old pack we disabled, until an operator has been told. */
    private String notice;
    private static final ArrayDeque<Entity> QUEUE = new ArrayDeque<>();

    private Migrate(String notice) {
        this.notice = notice;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("timber", path);
    }

    private static Migrate get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(Migrate::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> QUEUE.clear());
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!inert && old(entity)) QUEUE.add(entity);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!inert && !QUEUE.isEmpty()) {
                int[] c = drain(server);
                if (c[1] > 0 || c[2] > 0) Timber.LOG.info("Timber: removed {} old entities, {} controllers returned {} items", c[2], c[0], c[1]);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (inert) return;
            ServerPlayer p = handler.getPlayer();
            if (p.entityTags().contains(OLD_WELCOMED)) p.addTag(Players.WELCOMED);
            for (String t : new ArrayList<>(p.entityTags())) if (t.startsWith(PREFIX)) p.removeTag(t);
            tell(server, p);
        });
    }

    private static boolean old(Entity e) {
        if (!(e instanceof Marker) && !(e instanceof Display.BlockDisplay)) return false;
        for (String t : e.entityTags()) if (t.startsWith(PREFIX)) return true;
        return false;
    }

    private static void start(MinecraftServer server) {
        if (inert) return;
        Scoreboard sb = server.getScoreboard();
        int versionId = server.getCommandStorage().get(Api.META).getIntOr("version_id", -1);
        String pack = oldPack(server);
        boolean found = versionId >= 0 && versionId < Api.VERSION_ID || sb.getObjective("timber.config") != null
            || sb.getObjective("timber.off") != null || !server.getCommandStorage().get(PLACED).isEmpty() || pack != null;
        if (!found) {
            drain(server);
            return;
        }
        if (pack != null) {
            CompoundTag meta = server.getCommandStorage().get(Api.META);
            meta.remove("version");
            server.getCommandStorage().set(Api.META, meta);
        }
        int placed = 0, other = 0;
        CompoundTag stored = server.getCommandStorage().get(PLACED);
        for (String dim : stored.keySet()) {
            if (!(stored.get(dim) instanceof CompoundTag chunks)) continue;
            ServerLevel level = switch (dim) {
                case "o" -> server.overworld();
                case "n" -> server.getLevel(Level.NETHER);
                case "e" -> server.getLevel(Level.END);
                default -> null;
            };
            for (String chunk : chunks.keySet()) {
                if (!(chunks.get(chunk) instanceof CompoundTag keys)) continue;
                for (String key : keys.keySet()) {
                    BlockPos pos = parse(key);
                    if (level == null || pos == null) {
                        other++;
                        continue;
                    }
                    Placed.add(level, pos);
                    placed++;
                }
            }
        }
        int config = 0;
        Objective scores = sb.getObjective("timber.config");
        if (scores != null) {
            for (PlayerScoreEntry e : sb.listPlayerScores(scores)) {
                Config.Key key = Config.Key.of(e.owner().startsWith("#") ? e.owner().substring(1) : e.owner());
                if (key == null || Config.authored(key)) continue;
                Config.set(key, e.value());
                config++;
            }
        }
        int toggles = 0, unknown = 0;
        Objective off = sb.getObjective("timber.off");
        if (off != null) {
            for (PlayerScoreEntry e : sb.listPlayerScores(off)) {
                if (e.value() != 1) continue;
                UUID id = uuid(server, e.owner());
                if (id == null) {
                    Timber.LOG.warn("Timber: 1.3.x toggle of unknown player {} not imported", e.owner());
                    unknown++;
                    continue;
                }
                Players.setEnabled(server, id, false);
                toggles++;
            }
        }
        for (Objective o : new ArrayList<>(sb.getObjectives())) {
            if (o.getName().startsWith(PREFIX)) sb.removeObjective(o);
        }
        for (Identifier storage : STORAGES) server.getCommandStorage().set(storage, new CompoundTag());
        int[] c = drain(server);
        runs++;
        summary = "Timber: migrated a 1.3.x world: " + placed + " placed logs (" + other + " in other dimensions skipped), " + config
            + " config keys, " + toggles + " toggles (" + unknown + " unknown players), " + c[0] + " controllers returned " + c[1]
            + " items, " + c[2] + " entities removed, pack " + (pack == null ? "was not enabled" : pack + " disabled");
        Timber.LOG.info(summary);
        if (pack != null) {
            Migrate state = get(server);
            state.notice = pack;
            state.setDirty();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "datapack disable \"" + pack.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
            for (ServerPlayer p : server.getPlayerList().getPlayers()) tell(server, p);
        }
    }

    private static BlockPos parse(String key) {
        String[] p = key.split(",");
        if (p.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID uuid(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        Optional<NameAndId> cached = server.services().nameToIdCache().get(name);
        return cached.map(NameAndId::id).orElse(null);
    }

    /** The id of a selected world data pack that is the 1.3.x Timber (it has {@code timber:tick}), or null. */
    private static String oldPack(MinecraftServer server) {
        for (Pack p : server.getPackRepository().getSelectedPacks()) {
            if (!p.getId().startsWith("file/")) continue;
            List<PackResources> opened = p.open().toList();
            try {
                for (PackResources r : opened) if (r.getResource(PackType.SERVER_DATA, PACK_TICK) != null) return p.getId();
            } finally {
                for (PackResources r : opened) r.close();
            }
        }
        return null;
    }

    /** Tells the operators once that the old pack was replaced. */
    private static void tell(MinecraftServer server, ServerPlayer player) {
        Migrate state = get(server);
        if (state.notice.isEmpty() || !server.getPlayerList().isOp(player.nameAndId())) return;
        Say.chat(player, Component.literal("🪓 Timber 2.0 replaced the Timber 1.3 data pack ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("(" + state.notice + "): its settings, placed-log record and unfinished falls were carried over and the pack was disabled. "
                + "You can delete the pack from the world's datapacks folder.").withStyle(ChatFormatting.GRAY)));
        state.notice = "";
        state.setDirty();
    }

    /** Cleans the queued entities; returns controllers, items returned, entities removed. */
    private static int[] drain(MinecraftServer server) {
        int controllers = 0, items = 0, removed = 0;
        Entity e;
        while ((e = QUEUE.poll()) != null) {
            if (e.isRemoved() || !(e.level() instanceof ServerLevel level)) continue;
            if (e instanceof Marker && e.entityTags().contains("timber.ctl")) {
                CustomData data = e.get(DataComponents.CUSTOM_DATA);
                List<ItemStack> loot = data == null ? List.of() : loot(server, data.copyTag());
                for (ItemStack s : loot) {
                    ItemEntity item = new ItemEntity(level, e.getX(), e.getY() + 0.5, e.getZ(), s, 0, 0.25, 0);
                    item.setPickUpDelay(10);
                    level.addFreshEntity(item);
                    items += s.getCount();
                }
                controllers++;
            }
            e.discard();
            removed++;
        }
        return new int[] {controllers, items, removed};
    }

    /** The stacks a 1.3.6 controller's {@code data} holds: {@code drops[].Item} and {@code ldrops[]}. */
    public static List<ItemStack> loot(MinecraftServer server, CompoundTag data) {
        List<ItemStack> out = new ArrayList<>();
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (Tag t : data.getListOrEmpty("drops")) {
            if (t instanceof CompoundTag c && c.get("Item") instanceof CompoundTag item) ItemStack.CODEC.parse(ops, item).result().ifPresent(out::add);
        }
        for (Tag t : data.getListOrEmpty("ldrops")) ItemStack.CODEC.parse(ops, t).result().ifPresent(out::add);
        out.removeIf(ItemStack::isEmpty);
        return out;
    }
}
