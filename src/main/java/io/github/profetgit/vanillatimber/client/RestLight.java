package io.github.profetgit.vanillatimber.client;

import com.mojang.blaze3d.vertex.QuadInstance;
import io.github.profetgit.vanillatimber.net.TreeFall;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla's tessellation of each tree block where it stands (SeamlessDisplays {@code Baker}): per vertex the terrain's
 * colour (AO x tint, shade left out: the mesh applies its own per frame) and smooth light, so the mesh at rest draws what
 * the terrain drew and the swap shows nothing. The tree's blocks count as standing even when frame 1's removal arrived
 * with the payload. Every face is emitted (no culling), each quad once, keyed by identity.
 */
final class RestLight implements BlockQuadOutput {
    private static final CardinalLighting FLAT = new CardinalLighting(1f, 1f, 1f, 1f, 1f, 1f);
    private final ModelBlockRenderer renderer;
    private final Getter getter = new Getter();
    private final Map<BakedQuad, int[]> quads = new IdentityHashMap<>();

    RestLight(ClientLevel level, TreeFall msg) {
        Minecraft mc = Minecraft.getInstance();
        renderer = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), false, mc.getBlockColors());
        getter.level = level;
        getter.lightEngine = level.getLightEngine();
        getter.cardinalLighting = FLAT;
        BlockPos o = msg.origin();
        int[] cells = msg.cells();
        for (int i = 0; i < msg.blocks(); i++) {
            getter.tree.put(BlockPos.asLong(o.getX() + cells[i * 4], o.getY() + cells[i * 4 + 1], o.getZ() + cells[i * 4 + 2]),
                msg.palette().get(cells[i * 4 + 3] >> 1));
        }
    }

    void run(BlockPos cell, BlockState state) {
        quads.clear();
        BlockPos at = cell.immutable();
        getter.blockPos = at;
        getter.randomSeedPos = at;
        getter.blockState = state;
        BlockModelLighter.enableCaching();
        try {
            renderer.tesselateBlock(this, 0, 0, 0, getter, at, state, Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state),
                state.getSeed(at));
        } finally {
            BlockModelLighter.clearCache();
        }
    }

    /** The quad's 4 colours then 4 packed lights from the last {@link #run}, or null if vanilla didn't emit it. */
    int[] get(BakedQuad quad) {
        return quads.get(quad);
    }

    @Override
    public void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
        int[] v = new int[8];
        for (int i = 0; i < 4; i++) {
            v[i] = instance.getColor(i);
            v[4 + i] = instance.getLightCoords(i);
        }
        quads.put(quad, v);
    }

    private static final class Getter extends MovingBlockRenderState {
        ClientLevel level;
        final Long2ObjectOpenHashMap<BlockState> tree = new Long2ObjectOpenHashMap<>();

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState s = tree.get(pos.asLong());
            return s != null ? s : level.getBlockState(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return level.getBlockTint(pos, color);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinY() {
            return level.getMinY();
        }
    }
}
