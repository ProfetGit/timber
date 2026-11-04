package io.github.profetgit.vanillatimber.server;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Auto-replant (PRD 5.11): when a fall ends, a free sapling of the tree's kind goes into each of the tree's own ground
 * trunk cells, so a 2x2 giant regrows as a giant. Plain block sets and vanilla level events, so every client sees it.
 */
final class Replant {
    private Replant() {}

    static void plant(Fall f) {
        if (!Config.on(Config.Key.REPLANT) || f.tree.roots().isEmpty()) return;
        Block sapling = choose(f);
        if (sapling == null) return;
        ServerLevel level = f.level;
        for (BlockPos pos : f.tree.roots()) {
            if (!level.isLoaded(pos)) continue;
            BlockState now = level.getBlockState(pos), st = sapling.defaultBlockState();
            if (!now.isAir()) {
                if (!now.is(Blocks.WATER) || !now.getFluidState().isSource() || !st.hasProperty(BlockStateProperties.WATERLOGGED)) continue;
                st = st.setValue(BlockStateProperties.WATERLOGGED, true);
            }
            if (!st.canSurvive(level, pos)) continue;
            level.setBlock(pos, st, Block.UPDATE_ALL);
            SoundType type = st.getSoundType();
            SoundEvent place = type.getPlaceSound();
            Vec3 at = Vec3.atCenterOf(pos);
            Fx.sound(level, at, place, (type.getVolume() + 1f) / 2f, type.getPitch() * 0.8f);
            level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 3);
        }
    }

    /** PRD 5.11: loot sapling, crown {@code _leaves}, trunk {@code _log}/{@code _wood}/{@code _stem}, vanilla exceptions, else none. */
    static Block choose(Fall f) {
        Tree t = f.tree;
        Block b = fromLoot(f);
        if (b != null) return b;
        String leaf = id(t.leaf()), log = id(t.log());
        if (leaf != null) {
            b = named(leaf, "_leaves", "_sapling");
            if (b != null) return b;
            // an azalea crown sits on an oak trunk: its own exception comes before the trunk's name
            if (leaf.equals("minecraft:azalea_leaves")) return valid(Blocks.AZALEA);
            if (leaf.equals("minecraft:flowering_azalea_leaves")) return valid(Blocks.FLOWERING_AZALEA);
        }
        if (log != null) {
            for (String suffix : new String[] {"_log", "_wood", "_stem"}) {
                b = named(log, suffix, "_sapling");
                if (b != null) return b;
            }
        }
        for (String id : new String[] {log, leaf}) {
            if (id == null) continue;
            switch (id) {
                case "minecraft:mangrove_log", "minecraft:mangrove_wood", "minecraft:mangrove_leaves" -> {
                    return valid(Blocks.MANGROVE_PROPAGULE);
                }
                case "minecraft:crimson_stem", "minecraft:crimson_hyphae" -> {
                    return valid(Blocks.CRIMSON_FUNGUS);
                }
                case "minecraft:warped_stem", "minecraft:warped_hyphae" -> {
                    return valid(Blocks.WARPED_FUNGUS);
                }
                case "minecraft:red_mushroom_block" -> {
                    return valid(Blocks.RED_MUSHROOM);
                }
                case "minecraft:brown_mushroom_block" -> {
                    return valid(Blocks.BROWN_MUSHROOM);
                }
                default -> { }
            }
        }
        return null;
    }

    private static Block fromLoot(Fall f) {
        Block best = null;
        int most = 0;
        for (var e : f.loot.entrySet()) {
            Identifier key = Identifier.tryParse(e.getKey());
            Optional<Item> item = key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
            if (item.isEmpty() || !item.get().getDefaultInstance().is(ItemTags.SAPLINGS) || e.getValue() <= most) continue;
            Block b = valid(Block.byItem(item.get()));
            if (b == null) continue;
            best = b;
            most = e.getValue();
        }
        return best;
    }

    private static String id(BlockState s) {
        return s == null ? null : BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    /** The block whose id is {@code id} with {@code from} swapped for {@code to}, when it exists and is a sapling kind. */
    private static Block named(String id, String from, String to) {
        if (!id.endsWith(from)) return null;
        Identifier key = Identifier.tryParse(id.substring(0, id.length() - from.length()) + to);
        return key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).map(Replant::valid).orElse(null);
    }

    /** Saplings (the tag holds azalea and the propagule too) plus the fungi and small mushrooms the exceptions name. */
    private static Block valid(Block b) {
        if (b == null || b == Blocks.AIR) return null;
        BlockState s = b.defaultBlockState();
        boolean ok = s.is(BlockTags.SAPLINGS) || b == Blocks.CRIMSON_FUNGUS || b == Blocks.WARPED_FUNGUS || b == Blocks.RED_MUSHROOM
            || b == Blocks.BROWN_MUSHROOM;
        return ok ? b : null;
    }
}
