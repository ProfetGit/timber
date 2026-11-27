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
import net.minecraft.world.level.block.VineBlock;
//? if >=1.21.5
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * PRD 13 M3 {@code modded_fallback}: a mod's log, an untagged {@code LeavesBlock} subclass and an untagged {@code VineBlock}, registered only for the
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
        // the one self-test call every loader makes at init on a dedicated server too: arms the server-side run
        if (ServerRun.wanted()) ServerRun.arm();
//? if >=1.21.5 {
        block("test_log", k -> new RotatedPillarBlock(BlockBehaviour.Properties.of().strength(2f).setId(k)));
        block("test_leaves", k -> new TintedParticleLeavesBlock(0.01f, BlockBehaviour.Properties.of().strength(0.2f).randomTicks().noOcclusion()
            .setId(k)));
        block("test_vine", k -> new VineBlock(BlockBehaviour.Properties.of().strength(0.2f).setId(k)));
        block("test_hanging", k -> new Block(BlockBehaviour.Properties.of().strength(0.2f).noCollision().setId(k)));
        block("test_fruit", k -> new Fruit(BlockBehaviour.Properties.of().strength(0.2f).noOcclusion().setId(k), false));
        block("test_cast", k -> new Fruit(BlockBehaviour.Properties.of().strength(0.2f).noOcclusion().setId(k), true));
//?} else if >=1.21.2 {
/*        block("test_log", k -> new RotatedPillarBlock(BlockBehaviour.Properties.of().strength(2f).setId(k)));
        block("test_leaves", k -> new LeavesBlock(BlockBehaviour.Properties.of().strength(0.2f).randomTicks().noOcclusion().setId(k)));
        block("test_vine", k -> new VineBlock(BlockBehaviour.Properties.of().strength(0.2f).setId(k)));
        block("test_hanging", k -> new Block(BlockBehaviour.Properties.of().strength(0.2f).noCollission().setId(k)));
        block("test_fruit", k -> new Fruit(BlockBehaviour.Properties.of().strength(0.2f).noOcclusion().setId(k), false));
        block("test_cast", k -> new Fruit(BlockBehaviour.Properties.of().strength(0.2f).noOcclusion().setId(k), true));
*///?} else {
/*        block("test_log", k -> new RotatedPillarBlock(BlockBehaviour.Properties.of().strength(2f)));
        block("test_leaves", k -> new LeavesBlock(BlockBehaviour.Properties.of().strength(0.2f).randomTicks().noOcclusion()));
        block("test_vine", k -> new VineBlock(BlockBehaviour.Properties.of().strength(0.2f)));
        block("test_hanging", k -> new Block(BlockBehaviour.Properties.of().strength(0.2f).noCollission()));
        block("test_fruit", k -> new Fruit(BlockBehaviour.Properties.of().strength(0.2f).noOcclusion(), false));
        block("test_cast", k -> new Fruit(BlockBehaviour.Properties.of().strength(0.2f).noOcclusion(), true));
*///?}
    }

    /** Stands in for a mod's fruit: a small collision box (so it is no "loose" block) and a support check that needs leaves above; {@code cast} reads the level as a Level, which Timber's tree-less view is not. */
    private static final class Fruit extends Block {
        private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = Block.box(4, 4, 4, 12, 16, 12);
        private final boolean cast;

        Fruit(BlockBehaviour.Properties props, boolean cast) {
            super(props);
            this.cast = cast;
        }

        @Override
        public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState s, net.minecraft.world.level.BlockGetter l, net.minecraft.core.BlockPos p, net.minecraft.world.phys.shapes.CollisionContext c) {
            return SHAPE;
        }

        @Override
        public boolean canSurvive(net.minecraft.world.level.block.state.BlockState s, net.minecraft.world.level.LevelReader l, net.minecraft.core.BlockPos p) {
            net.minecraft.world.level.BlockGetter g = cast ? (net.minecraft.world.level.Level) l : l;
            return g.getBlockState(p.above()).getBlock() instanceof LeavesBlock;
        }
    }

    private static java.util.function.BiConsumer<ResourceKey<Block>, Block> sink;

    private static void block(String name, java.util.function.Function<ResourceKey<Block>, Block> make) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Timber.ID, name));
        sink.accept(key, make.apply(key));
    }
}
