package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.Timber;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.state.BlockState;

/** The chop: a log broken by a player who may fell starts a fall. */
public final class Falls {
    /** The last tree scanned (self-test asserts read it). */
    public static volatile Tree last;

    private Falls() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) chop(sl, sp, pos, state);
        });
    }

    static boolean mayFell(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator() && !player.isShiftKeyDown()
            && player.getMainHandItem().is(ItemTags.AXES);
    }

    static void chop(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (!state.is(TreeScan.LOGS) || !mayFell(player)) return;
        Tree tree = TreeScan.scan(level, pos, state, player.getYRot());
        if (tree == null) return;
        last = tree;
        Timber.LOG.debug("tree at {}: {} logs, {} leaves, {}", pos, tree.logs(), tree.leaves(), tree.spec());
    }
}
