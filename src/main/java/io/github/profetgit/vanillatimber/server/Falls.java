package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.Timber;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/** The chop: a log broken by a player who may fell starts a fall; falls step at the start of their level's tick. */
public final class Falls {
    /** The last tree scanned and the last fall started (self-test asserts read them). */
    public static volatile Tree last;
    public static volatile Fall lastFall;
    /** -Dvanilla_timber.perf=true: each fall logs its chop and per-frame server time when it ends. */
    static final boolean PERF = Boolean.getBoolean("vanilla_timber.perf");

    /** Times chops and frames: the perf log, or a self-test probe reading {@link Fall#chopMs()}. */
    static boolean timed() {
        return PERF || Fall.probe != null;
    }
    private static final Map<ServerLevel, List<Fall>> ACTIVE = new IdentityHashMap<>();
    /** Server tick of each player's last fell (PRD 5.1); cleared on leave and server stop. */
    private static final Map<java.util.UUID, Integer> FELLED = new java.util.HashMap<>();
    private static int nextId;

    private Falls() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) chop(sl, sp, pos, state);
        });
        ServerTickEvents.START_LEVEL_TICK.register(Falls::tick);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FELLED.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (List<Fall> falls : ACTIVE.values()) for (Fall f : falls) f.abort();
            ACTIVE.clear();
            FELLED.clear();
            Viewers.clear();
        });
    }

    static final TagKey<Item> C_AXES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/axe"));

    /** PRD 5.1 gates 1 to 4, in order: the player's toggle, game mode, sneak mode, the axe. */
    static boolean mayFell(ServerPlayer player) {
        if (!Players.enabled(player.level().getServer(), player.getUUID())) return false;
        GameType mode = player.gameMode.getGameModeForPlayer();
        if (mode != GameType.SURVIVAL && mode != GameType.ADVENTURE) return false;
        int sneak = Config.get(Config.Key.SNEAK);
        if (sneak == 0 && player.isShiftKeyDown() || sneak == 1 && !player.isShiftKeyDown()) return false;
        ItemStack tool = player.getMainHandItem();
        return !Config.on(Config.Key.REQUIRE_AXE) || tool.is(ItemTags.AXES) || tool.is(C_AXES);
    }

    static void chop(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        long t0 = System.nanoTime();
        boolean placed = Placed.remove(level, pos);
        if (TreeTypes.forTrunk(state) == null || placed && Config.on(Config.Key.PROTECT) || !mayFell(player) || busy(level, pos)) return;
        // PRD 5.1: one fell per player per tick, so a multi-break mod breaking 20 logs fells one tree
        int now = level.getServer().getTickCount();
        Integer before = FELLED.put(player.getUUID(), now);
        if (before != null && before == now) return;
        Tree tree = TreeScan.scan(level, pos, state, player.getYRot());
        if (tree == null) {
            if (TreeScan.tooBig) refuse(player, "toobig", "Timber: this tree is too big to fell");
            FELLED.put(player.getUUID(), before == null ? -1 : before);
            return;
        }
        last = tree;
        if (!allowed(level, player, tree)) {
            refuse(player, "protected", "Timber: part of this tree is protected");
            return;
        }
        if (!wear(level, player, tree.logs())) return;
        Fall fall = new Fall(nextId++ & 0x7FFFFFFF, level, tree, player, level.getGameRules().get(GameRules.BLOCK_DROPS));
        lastFall = fall;
        if (!Config.on(Config.Key.ANIMATION)) {
            fall.instantly();
            return;
        }
        ACTIVE.computeIfAbsent(level, l -> new ArrayList<>()).add(fall);
        if (timed()) fall.chopNs = System.nanoTime() - t0;
        Timber.LOG.debug("tree at {}: {} logs, {} leaves, {}", pos, tree.logs(), tree.leaves(), tree.spec());
    }

    /**
     * PRD 5.7 claims: every block of the tree passes the break checks the chopper's own break did (spawn protection,
     * world border, adventure mode, and the loader's break event claim mods listen to), or nothing beyond the chopped log goes.
     */
    static boolean allowed(ServerLevel level, ServerPlayer player, Tree tree) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        for (Tree.Part p : tree.parts()) {
            BlockPos q = p.pos();
            if (!level.mayInteract(player, q) || player.blockActionRestricted(level, q, mode)) return false;
            if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, q, p.state(), level.getBlockEntity(q))) return false;
        }
        return true;
    }

    /** PRD 5.10: a refusal says so on the action bar, with {@code feedback} on. */
    private static void refuse(ServerPlayer player, String key, String fallback) {
        if (!Config.on(Config.Key.FEEDBACK)) return;
        Say.bar(player, Component.literal("🪓 ").append(Component.translatableWithFallback("vanilla_timber.msg." + key, fallback)).withStyle(ChatFormatting.RED));
    }

    private static boolean busy(ServerLevel level, BlockPos pos) {
        List<Fall> falls = ACTIVE.get(level);
        if (falls == null) return false;
        for (Fall f : falls) if (f.holds(pos)) return true;
        return false;
    }

    /**
     * The axe pays one damage per felled log, Unbreaking rolled per log ({@code tool/check}); a tree that would break it
     * isn't felled. Unbreakable and non-damageable tools pay nothing.
     */
    private static boolean wear(ServerLevel level, ServerPlayer player, int logs) {
        ItemStack tool = player.getMainHandItem();
        if (!Config.on(Config.Key.DURABILITY) || !tool.isDamageableItem() || logs <= 0) return true;
        int dmg = EnchantmentHelper.processDurabilityChange(level, tool, logs);
        int left = tool.getMaxDamage() - tool.getDamageValue() - 1;
        if (dmg > left) {
            refuse(player, "worn", "Timber: your axe is too worn to fell this tree");
            return false;
        }
        if (dmg > 0) tool.setDamageValue(tool.getDamageValue() + dmg);
        return true;
    }

    static void feedback(ServerPlayer player, int logs) {
        if (player.isRemoved() || !Config.on(Config.Key.FEEDBACK)) return;
        Say.bar(player, Component.literal("🪓 ").withStyle(ChatFormatting.GOLD)
            .append(Component.translatableWithFallback("vanilla_timber.msg.timber", "Timber! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal(Integer.toString(logs)).withStyle(ChatFormatting.WHITE))
            .append(Component.translatableWithFallback("vanilla_timber.msg.logs", " logs").withStyle(ChatFormatting.GRAY)));
    }

    private static void tick(ServerLevel level) {
        List<Fall> falls = ACTIVE.get(level);
        if (falls == null || falls.isEmpty()) return;
        if (!timed()) {
            falls.removeIf(Fall::step);
            return;
        }
        falls.removeIf(f -> {
            long t0 = System.nanoTime();
            boolean done = f.step();
            if (f.stepNs != null && f.n >= 0 && f.n < f.stepNs.length) f.stepNs[f.n] = System.nanoTime() - t0;
            if (done && PERF) perf(f);
            return done;
        });
    }

    /** Frames 2 to end - 1 are the moving ticks: frame 0 spawns, frame 1 removes the blocks, the last one drops. */
    private static void perf(Fall f) {
        long[] t = f.stepNs;
        int end = f.tl.end, k = 0;
        double sum = 0, max = 0;
        for (int i = 2; i < end; i++) {
            sum += t[i];
            max = Math.max(max, t[i]);
            k++;
        }
        Timber.LOG.info(String.format(java.util.Locale.ROOT,
            "[perf] fall %d: %d logs %d leaves, chop %.3f ms, frame 0 %.3f ms, frame 1 %.3f ms, moving mean %.4f max %.4f ms over %d ticks, finish %.3f ms, displays %s, payloads %d",
            f.id, f.tree.logs(), f.tree.leaves(), f.chopNs / 1e6, t[0] / 1e6, t[1] / 1e6, k == 0 ? 0 : sum / k / 1e6, max / 1e6, k,
            t[end] / 1e6, f.spawned() ? "yes" : "no", f.payloads));
    }

    /** Falls in progress in a level (tests). */
    public static int active(ServerLevel level) {
        List<Fall> falls = ACTIVE.get(level);
        return falls == null ? 0 : falls.size();
    }
}
