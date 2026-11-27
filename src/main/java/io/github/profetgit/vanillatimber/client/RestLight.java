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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=26.1 {
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
//? if >=1.21.5 <26.1
/*import net.minecraft.client.renderer.block.model.BlockModelPart;*/

/**
 * Vanilla's tessellation of each tree block where it stands (SeamlessDisplays {@code Baker}): per vertex the terrain's
 * colour (AO x tint, shade left out: the mesh applies its own per frame) and smooth light, so the mesh at rest draws what
 * the terrain drew and the swap shows nothing. The tree's blocks count as standing even when frame 1's removal arrived
 * with the payload. Faces vanilla culls there (against the ground, the other column of a 2x2 trunk) are left out: their
 * light is the inside of a block (0), and once the fall turns them to the camera they would draw black where 1.3.6's
 * displays draw lit, so they take the path light like a display. Each quad once, keyed by identity.
 */
//? if >=26.1 {
final class RestLight implements BlockQuadOutput {
    private static final CardinalLighting FLAT = new CardinalLighting(1f, 1f, 1f, 1f, 1f, 1f);
    private final ModelBlockRenderer renderer;
    private final Getter getter = new Getter();
    private final Map<BakedQuad, int[]> quads = new IdentityHashMap<>();
    private final boolean treeOnly;

    RestLight(ClientLevel level, TreeFall msg, boolean treeOnly) {
        this.treeOnly = treeOnly;
        getter.treeOnly = treeOnly;
        Minecraft mc = Minecraft.getInstance();
        renderer = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true, mc.getBlockColors());
        getter.level = level;
        getter.lightEngine = level.getLightEngine();
        getter.cardinalLighting = FLAT;
        BlockPos o = msg.origin();
        int[] cells = msg.cells();
        for (int i = 0; i < msg.blocks(); i++) {
            long at = BlockPos.asLong(o.getX() + cells[i * 4], o.getY() + cells[i * 4 + 1], o.getZ() + cells[i * 4 + 2]);
            getter.tree.put(at, msg.palette().get(cells[i * 4 + 3] >> 2));
            if ((cells[i * 4 + 3] & 1) != 0) getter.leaves.add(at);
        }
    }

    // the world with the kept parts put in at their resting cells and the tree's own cells emptied
    RestLight(ClientLevel level, Long2ObjectOpenHashMap<BlockState> lying, it.unimi.dsi.fastutil.longs.LongOpenHashSet air) {
        treeOnly = false;
        Minecraft mc = Minecraft.getInstance();
        renderer = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true, mc.getBlockColors());
        getter.level = level;
        getter.lightEngine = level.getLightEngine();
        getter.cardinalLighting = FLAT;
        getter.tree.putAll(lying);
        getter.air = air;
    }

    void run(BlockPos cell, BlockState state) {
        quads.clear();
        BlockPos at = cell.immutable();
        getter.blockPos = at;
        getter.randomSeedPos = at;
        getter.blockState = state;
        // a log's own shading ignores the leaves: they go at the slam and the trunk must not stay dark for them
        getter.skipLeaves = treeOnly && !getter.leaves.contains(at.asLong());
        // the shared cache holds the other pass's world: the tree-only pass runs uncached, then the cache comes back
        if (treeOnly) BlockModelLighter.clearCache();
        try {
            renderer.tesselateBlock(this, 0, 0, 0, getter, at, state, Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state),
                state.getSeed(at));
        } finally {
            if (treeOnly) BlockModelLighter.enableCaching();
        }
    }

    /** One bake shares vanilla's light and AO cache between its blocks (the getter's world doesn't change meanwhile). */
    void begin() {
        if (!treeOnly) BlockModelLighter.enableCaching();
    }

    void end() {
        BlockModelLighter.clearCache();
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
        boolean treeOnly, skipLeaves;
        final Long2ObjectOpenHashMap<BlockState> tree = new Long2ObjectOpenHashMap<>();
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet leaves = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet air;

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState s = tree.get(pos.asLong());
            if (s != null) return skipLeaves && leaves.contains(pos.asLong()) ? Blocks.AIR.defaultBlockState() : s;
            if (air != null && air.contains(pos.asLong())) return Blocks.AIR.defaultBlockState();
            return treeOnly ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos);
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

    void begin() {}

    void end() {}

    RestLight(ClientLevel level, TreeFall msg, boolean treeOnly) {
        renderer = new ModelBlockRenderer(Minecraft.getInstance().getBlockColors());
        getter.level = level;
        getter.treeOnly = treeOnly;
        BlockPos o = msg.origin();
        int[] cells = msg.cells();
        for (int i = 0; i < msg.blocks(); i++) {
            long at = BlockPos.asLong(o.getX() + cells[i * 4], o.getY() + cells[i * 4 + 1], o.getZ() + cells[i * 4 + 2]);
            getter.tree.put(at, msg.palette().get(cells[i * 4 + 3] >> 2));
            if ((cells[i * 4 + 3] & 1) != 0) getter.leaves.add(at);
        }
    }

    RestLight(ClientLevel level, Long2ObjectOpenHashMap<BlockState> lying, it.unimi.dsi.fastutil.longs.LongOpenHashSet air) {
        renderer = new ModelBlockRenderer(Minecraft.getInstance().getBlockColors());
        getter.level = level;
        getter.tree.putAll(lying);
        getter.air = air;
    }

    // the quad's 4 colours then 4 packed lights from the last run, or null if vanilla didn't emit it
    int[] get(Object quad) {
        return quads.get(quad);
    }
*///?}
//? if >=1.21.5 <26.1 {
/*    void run(BlockPos cell, BlockState state) {
        quads.clear();
        BlockPos at = cell.immutable();
        getter.skipLeaves = getter.treeOnly && !getter.leaves.contains(at.asLong());
        java.util.List<BlockModelPart> parts = Minecraft.getInstance().getBlockRenderer().getBlockModel(state).collectParts(RandomSource.create(state.getSeed(at)));
        ModelBlockRenderer.enableCaching();
        try {
            renderer.tesselateBlock(getter, parts, state, at, new PoseStack(), sink, true, OverlayTexture.NO_OVERLAY);
        } finally {
            ModelBlockRenderer.clearCache();
        }
    }
*///?} else if <26.1 {
/*    void run(BlockPos cell, BlockState state) {
        quads.clear();
        BlockPos at = cell.immutable();
        getter.skipLeaves = getter.treeOnly && !getter.leaves.contains(at.asLong());
        ModelBlockRenderer.enableCaching();
        try {
            renderer.tesselateBlock(getter, Minecraft.getInstance().getBlockRenderer().getBlockModel(state), state, at, new PoseStack(), sink, true,
                RandomSource.create(), state.getSeed(at), OverlayTexture.NO_OVERLAY);
        } finally {
            ModelBlockRenderer.clearCache();
        }
    }
*///?}
//? if <26.1 {
/*
    // vanilla hands each quad over whole: its brightness (AO, shade left out by the getter) and light per vertex, and the tint
    private final class Sink implements VertexConsumer {
*///?}
//? if >=1.21.11 <26.1 {
/*        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, float a, int[] lights, int overlay) {
            int[] v = new int[8];
            for (int i = 0; i < 4; i++) {
                v[i] = 0xFF000000 | (int) (255 * brightness[i] * r) << 16 | (int) (255 * brightness[i] * g) << 8 | (int) (255 * brightness[i] * b);
                v[4 + i] = lights[i];
            }
            quads.put(quad, v);
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
*///?} else if >=1.21.5 <26.1 {
/*        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, float a, int[] lights, int overlay,
                                boolean readAlpha) {
            colors(quad.vertices(), brightness, r, g, b, lights, quad);
        }
*///?} else if >=1.21 <26.1 {
/*        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, float a, int[] lights, int overlay,
                                boolean readAlpha) {
            colors(quad.getVertices(), brightness, r, g, b, lights, quad);
        }
*///?} else if <1.21 {
/*        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, int[] lights, int overlay,
                                boolean readExisting) {
            colors(quad.getVertices(), brightness, r, g, b, lights, quad);
        }
*///?}
//? if <1.21.11 {
/*
        private void colors(int[] vertices, float[] brightness, float r, float g, float b, int[] lights, BakedQuad quad) {
            int[] v = new int[8];
            for (int i = 0; i < 4; i++) {
                int c = vertices[i * 8 + 3];
                v[i] = 0xFF000000 | (int) ((c & 0xFF) * brightness[i] * r) << 16 | (int) ((c >> 8 & 0xFF) * brightness[i] * g) << 8
                    | (int) ((c >> 16 & 0xFF) * brightness[i] * b);
                v[4 + i] = lights[i];
            }
            quads.put(quad, v);
        }
*///?}
//? if >=1.21 <26.1 {
/*
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
*///?}
//? if <1.21 {
/*
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
*///?}
//? if <26.1 {
/*

    private static final class Getter implements BlockAndTintGetter {
        ClientLevel level;
        boolean treeOnly, skipLeaves;
        final Long2ObjectOpenHashMap<BlockState> tree = new Long2ObjectOpenHashMap<>();
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet leaves = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet air;

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
            if (s != null) return skipLeaves && leaves.contains(pos.asLong()) ? Blocks.AIR.defaultBlockState() : s;
            if (air != null && air.contains(pos.asLong())) return Blocks.AIR.defaultBlockState();
            return treeOnly ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos);
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
        public int getMinY() {
            return level.getMinY();
        }
    }
}
*///?}
