package io.github.profetgit.vanillatimber.selftest;

import io.github.profetgit.vanillatimber.Timber;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
//? if >=1.21.5
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * PRD 13 M3 {@code modded_fallback}: a mod's log and an untagged {@code LeavesBlock} subclass, registered only for the
 * parity scene (a normal game, and every other scene and mp case, never sees them).
 */
public final class TestBlocks {
    private TestBlocks() {}

    public static boolean wanted() {
        return System.getProperty("vanilla_timber.selftest") != null && System.getProperty("vanilla_timber.selftest.scene", "").contains("parity");
    }

    public static void register() {
        register((key, block) -> Registry.register(BuiltInRegistries.BLOCK, key, block));
    }

    /** Through a loader's own registration (NeoForge 1.20.1 refuses Registry.register inside RegisterEvent). */
    public static void register(java.util.function.BiConsumer<ResourceKey<Block>, Block> sink) {
        TestBlocks.sink = sink;
//? if >=1.21.5 {
        block("test_log", k -> new RotatedPillarBlock(BlockBehaviour.Properties.of().strength(2f).setId(k)));
        block("test_leaves", k -> new TintedParticleLeavesBlock(0.01f, BlockBehaviour.Properties.of().strength(0.2f).randomTicks().noOcclusion()
            .setId(k)));
//?} else if >=1.21.2 {
/*        block("test_log", k -> new RotatedPillarBlock(BlockBehaviour.Properties.of().strength(2f).setId(k)));
        block("test_leaves", k -> new LeavesBlock(BlockBehaviour.Properties.of().strength(0.2f).randomTicks().noOcclusion().setId(k)));
*///?} else {
/*        block("test_log", k -> new RotatedPillarBlock(BlockBehaviour.Properties.of().strength(2f)));
        block("test_leaves", k -> new LeavesBlock(BlockBehaviour.Properties.of().strength(0.2f).randomTicks().noOcclusion()));
*///?}
    }

    private static java.util.function.BiConsumer<ResourceKey<Block>, Block> sink;

    private static void block(String name, java.util.function.Function<ResourceKey<Block>, Block> make) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Timber.ID, name));
        sink.accept(key, make.apply(key));
    }
}
