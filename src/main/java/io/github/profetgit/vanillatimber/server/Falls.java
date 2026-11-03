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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/** The chop: a log broken by a player who may fell starts a fall; falls step at the start of their level's tick. */
public final class Falls {
    /** The last tree scanned and the last fall started (self-test asserts read them). */
    public static volatile Tree last;
    public static volatile Fall lastFall;
    private static final Map<ServerLevel, List<Fall>> ACTIVE = new IdentityHashMap<>();

    private Falls() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) chop(sl, sp, pos, state);
        });
        ServerTickEvents.START_LEVEL_TICK.register(Falls::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (List<Fall> falls : ACTIVE.values()) for (Fall f : falls) f.abort();
            ACTIVE.clear();
            Viewers.clear();
        });
    }

    static boolean mayFell(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator() && !player.isShiftKeyDown()
            && player.getMainHandItem().is(ItemTags.AXES);
    }

    static void chop(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (!state.is(TreeScan.LOGS) || !mayFell(player) || busy(level, pos)) return;
        Tree tree = TreeScan.scan(level, pos, state, player.getYRot());
        if (tree == null) return;
        last = tree;
        if (!wear(level, player, tree.logs())) return;
        Fall fall = new Fall(level, tree, player, level.getGameRules().get(GameRules.BLOCK_DROPS));
        lastFall = fall;
        ACTIVE.computeIfAbsent(level, l -> new ArrayList<>()).add(fall);
        Timber.LOG.debug("tree at {}: {} logs, {} leaves, {}", pos, tree.logs(), tree.leaves(), tree.spec());
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
        if (!tool.isDamageableItem() || logs <= 0) return true;
        int dmg = EnchantmentHelper.processDurabilityChange(level, tool, logs);
        int left = tool.getMaxDamage() - tool.getDamageValue() - 1;
        if (dmg > left) {
            player.sendOverlayMessage(Component.literal("🪓 ").append(Component.translatableWithFallback("vanilla_timber.msg.worn",
                "Timber: your axe is too worn to fell this tree")).withStyle(ChatFormatting.RED));
            return false;
        }
        if (dmg > 0) tool.setDamageValue(tool.getDamageValue() + dmg);
        return true;
    }

    static void feedback(ServerPlayer player, int logs) {
        if (player.isRemoved()) return;
        player.sendOverlayMessage(Component.literal("🪓 ").withStyle(ChatFormatting.GOLD)
            .append(Component.translatableWithFallback("vanilla_timber.msg.timber", "Timber! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal(Integer.toString(logs)).withStyle(ChatFormatting.WHITE))
            .append(Component.translatableWithFallback("vanilla_timber.msg.logs", " logs").withStyle(ChatFormatting.GRAY)));
    }

    private static void tick(ServerLevel level) {
        List<Fall> falls = ACTIVE.get(level);
        if (falls == null || falls.isEmpty()) return;
        falls.removeIf(Fall::step);
    }

    /** Falls in progress in a level (tests). */
    public static int active(ServerLevel level) {
        List<Fall> falls = ACTIVE.get(level);
        return falls == null ? 0 : falls.size();
    }
}
