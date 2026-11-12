package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.net.Settings;
import io.github.profetgit.vanillatimber.server.Falls;
import io.github.profetgit.vanillatimber.server.TreeScan;
import io.github.profetgit.vanillatimber.server.TreeTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chop mode on the client (PRD 5.13): the server's settings, and the question "will the server keep this log?". The
 * answer only decides whether the client predicts the removal; the server decides everything.
 */
public final class ChopClient {
    private static final TagKey<Block> MUSHROOM_STEMS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "mushroom_stems"));
    private static volatile int chops, sneak, requireAxe = 1;
    /** The server's settings as it last sent them, or null (no Timber on the server, or not joined). */
    private static volatile Settings server;

    private ChopClient() {}

    public static void set(Settings s) {
        server = s;
        chops = s.get("chops", 0);
        sneak = s.get("sneak", 0);
        requireAxe = s.get("require_axe", 1);
    }

    public static void reset() {
        server = null;
        chops = 0;
        sneak = 0;
        requireAxe = 1;
    }

    /** The server's settings for the settings screen, or null when it sent none. */
    public static Settings server() {
        return server;
    }

    public static boolean on() {
        return chops == 1;
    }

    /** The local break of {@code pos} would be a chop: no local removal. */
    public static boolean keeps(BlockPos pos) {
        if (chops != 1) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameMode == null) return false;
        GameType mode = mc.gameMode.getPlayerMode();
        if (mode != GameType.SURVIVAL && mode != GameType.ADVENTURE) return false;
        boolean shift = mc.player.isShiftKeyDown();
        if (sneak == 0 && shift || sneak == 1 && !shift) return false;
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.is(BlockTags.LOGS) || state.is(TreeScan.LOGS) || state.is(MUSHROOM_STEMS)) || state.is(TreeTypes.NEVER)) return false;
        if (requireAxe != 1) return true;
        ItemStack tool = mc.player.getMainHandItem();
        return tool.isEmpty() || tool.is(ItemTags.AXES) || tool.is(Falls.C_AXES);
    }
}
