package io.github.profetgit.vanillatimber.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.profetgit.vanillatimber.Timber;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.2
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Per-player state (PRD 8, overworld {@code vanilla_timber_players}): who turned Timber off for themselves. Also the
 * {@code /trigger timber} objective, enabled for everyone and polled each tick as 1.3.6 did (1 toggles, 2 on, 3 off),
 * and the once-per-player join hint.
 */
public final class Players extends SavedData {
    static final String OBJECTIVE = "timber";
    static final String WELCOMED = "vanilla_timber.welcomed";
    private static final Codec<Players> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.listOf().optionalFieldOf("off", List.of()).forGetter(d -> List.copyOf(d.off))
    ).apply(i, Players::new));
//? if >=1.21.2 {
    private static final SavedDataType<Players> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Timber.ID, "players"),
        () -> new Players(List.of()), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
//?} else {
/*    private static final SavedData.Factory<Players> TYPE = new SavedData.Factory<>(() -> new Players(List.of()),
        (tag, lookup) -> SavedCodec.load(CODEC, tag, lookup, () -> new Players(List.of())), DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        return SavedCodec.save(CODEC, this, tag, lookup);
    }
*///?}

    private final Set<UUID> off = new HashSet<>();

    private Players(List<UUID> off) {
        this.off.addAll(off);
    }

    private static Players get(MinecraftServer server) {
//? if >=1.21.2 {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
//?} else {
/*        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "vanilla_timber_players");
*///?}
    }

    public static boolean enabled(MinecraftServer server, UUID player) {
        return !get(server).off.contains(player);
    }

    public static void setEnabled(MinecraftServer server, UUID player, boolean enabled) {
        Players data = get(server);
        if (enabled ? data.off.remove(player) : data.off.add(player)) data.setDirty();
    }

    static void stopping(MinecraftServer server) {
        Objective o = server.getScoreboard().getObjective(OBJECTIVE);
        if (o != null) server.getScoreboard().removeObjective(o);
    }

    static void tick(MinecraftServer server) {
        Config.pollReload();
        poll(server);
    }

    static void join(ServerPlayer player) {
        if (!player.entityTags().contains(WELCOMED)) welcome(player);
    }

    static Objective ensureObjective(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        Objective o = sb.getObjective(OBJECTIVE);
        if (o == null) {
            o = sb.addObjective(OBJECTIVE, ObjectiveCriteria.TRIGGER, Component.literal("Timber"), ObjectiveCriteria.RenderType.INTEGER, true, null);
        }
        return o;
    }

    private static void poll(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        Scoreboard sb = server.getScoreboard();
        Objective o = ensureObjective(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ScoreAccess score = sb.getOrCreatePlayerScore(p, o);
            int v = score.get();
            if (v > 0) {
                score.set(0);
                toggle(server, p, v);
            }
            if (score.locked()) score.unlock();
        }
    }

    /** {@code /trigger timber}: 1 toggles, 2 turns on, 3 turns off. Tells the player. */
    static void toggle(MinecraftServer server, ServerPlayer player, int value) {
        if (value < 1 || value > 3) return;
        boolean on = value == 1 ? !enabled(server, player.getUUID()) : value == 2;
        setEnabled(server, player.getUUID(), on);
        Say.chat(player, Component.literal("🪓 Timber ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(on ? "enabled" : "disabled").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED))
            .append(Component.literal(" for you. ").withStyle(ChatFormatting.GRAY))
            .append(button(on ? "[Turn off]" : "[Turn on]", on ? ChatFormatting.RED : ChatFormatting.GREEN, "/trigger timber", "/trigger timber")));
    }

    static MutableComponent button(String label, ChatFormatting color, String hover, String command) {
        return Component.literal(label).withStyle(Style.EMPTY.withColor(color)
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))).withClickEvent(new ClickEvent.RunCommand(command)));
    }

    /** The join hint, tagged so it fires once per player, and only with {@code welcome} on (1.3.4: off by default). */
    public static void welcome(ServerPlayer player) {
        player.addTag(WELCOMED);
        if (!Config.on(Config.Key.WELCOME)) return;
        int sneak = Config.get(Config.Key.SNEAK);
        String text = sneak == 1 ? "sneak while chopping a tree with an axe to fell the whole tree. "
            : sneak == 2 ? "chop a tree with an axe and the whole tree comes down. "
            : "chop a tree with an axe and the whole tree comes down. Sneak to take a single log. ";
        MutableComponent hint = Component.literal("🪓 Timber: ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(ChatFormatting.GRAY));
        for (Component c : Api.requires(player.level().getServer())) hint.append(c);
        Say.chat(player, hint.append(button("[Toggle]", ChatFormatting.AQUA, "/trigger timber", "/trigger timber")));
    }
}
