package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.net.TreeFall;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.2 {
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.level.CardinalLighting;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
*///?}

/**
 * Vanilla's tessellation of each tree block where it stands (SeamlessDisplays {@code Baker}): per vertex the terrain's
 * colour (AO x tint, shade left out: the mesh applies its own per frame) and smooth light, so the mesh at rest draws what
 * the terrain drew and the swap shows nothing. The tree's blocks count as standing even when frame 1's removal arrived
 * with the payload. Faces vanilla culls there (against the ground, the other column of a 2x2 trunk) are left out: their
 * light is the inside of a block (0), and once the fall turns them to the camera they would draw black where 1.3.6's
 * displays draw lit, so they take the path light like a display. Each quad once, keyed by identity.
 */
//? if >=1.21.2 {
final class RestLight implements BlockQuadOutput {
    private static final CardinalLighting FLAT = new CardinalLighting(1f, 1f, 1f, 1f, 1f, 1f);
    private final ModelBlockRenderer renderer;
    private final Getter getter = new Getter();
    private final Map<BakedQuad, int[]> quads = new IdentityHashMap<>();

    RestLight(ClientLevel level, TreeFall msg) {
        Minecraft mc = Minecraft.getInstance();
        renderer = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true, mc.getBlockColors());
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

    // the quad's 4 colours then 4 packed lights from the last run, or null if vanilla didn't emit it
    int[] get(Object quad) {
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
//?} else {
/*final class RestLight {
    private final ModelBlockRenderer renderer;
    private final Getter getter = new Getter();
    private final Sink sink = new Sink();
    private final Map<BakedQuad, int[]> quads = new IdentityHashMap<>();

    RestLight(ClientLevel level, TreeFall msg) {
        renderer = new ModelBlockRenderer(Minecraft.getInstance().getBlockColors());
        getter.level = level;
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
        ModelBlockRenderer.enableCaching();
        try {
            renderer.tesselateBlock(getter, Minecraft.getInstance().getBlockRenderer().getBlockModel(state), state, at, new PoseStack(), sink, true,
                RandomSource.create(), state.getSeed(at), OverlayTexture.NO_OVERLAY);
        } finally {
            ModelBlockRenderer.clearCache();
        }
    }

    // the quad's 4 colours then 4 packed lights from the last run, or null if vanilla didn't emit it
    int[] get(Object quad) {
        return quads.get(quad);
    }

    // vanilla hands each quad over whole: its brightness (AO, shade left out by the getter) and light per vertex, and the tint
    private final class Sink implements VertexConsumer {
        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, float a, int[] lights, int overlay,
                                boolean readAlpha) {
            int[] vertices = quad.getVertices(), v = new int[8];
            for (int i = 0; i < 4; i++) {
                int c = vertices[i * 8 + 3];
                v[i] = 0xFF000000 | (int) ((c & 0xFF) * brightness[i] * r) << 16 | (int) ((c >> 8 & 0xFF) * brightness[i] * g) << 8
                    | (int) ((c >> 16 & 0xFF) * brightness[i] * b);
                v[4 + i] = lights[i];
            }
            quads.put(quad, v);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    private static final class Getter implements BlockAndTintGetter {
        ClientLevel level;
        final Long2ObjectOpenHashMap<BlockState> tree = new Long2ObjectOpenHashMap<>();

        @Override
        public float getShade(Direction direction, boolean shade) {
            return 1.0f;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState s = tree.get(pos.asLong());
            return s != null ? s : level.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
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
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }
}
*///?}
