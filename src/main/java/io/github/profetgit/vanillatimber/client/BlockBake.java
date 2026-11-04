package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.net.TreeFall;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
//? if >=26.1 {
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.RandomSource;
//?} else if >=1.21.5 {
/*import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.util.RandomSource;
*///?} else {
/*import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
*///?}
//? if >=1.21.11 <26.1
/*import net.minecraft.client.model.geom.builders.UVPair;*/

/**
 * The version's block models as the mesh wants them (the other half of the port seam, beside {@link RenderPass}): per
 * block its quads in the tree's block frame with the tint, the face whose shade the quad takes, the render layer and the
 * terrain's own colour and light where the block stood ({@link RestLight}).
 */
final class BlockBake {
    private static final Direction[] DIRS = Direction.values();

    /** One baked quad: 4 vertices of x, y, z and u, v, its tint colour (-1: none), shade face, light emission and rest colours and lights. */
    static final class Quad {
        final float[] pos = new float[12], uv = new float[8];
        int color = -1, shade, emission;
        RenderPass.Layer layer;
        int[] rest;
    }

//? if >=26.3 {
    private static Direction shadeOverride(BakedQuad quad) {
        return quad.materialInfo().shadeDirectionOverride();
    }
//?} else if >=26.1 {
/*    private static Direction shadeOverride(BakedQuad quad) {
        return quad.materialInfo().shade() ? null : Direction.UP;
    }
*///?}

    private final Minecraft mc = Minecraft.getInstance();
    private final ClientLevel level;
    private final RestLight rest;
    private final List<Quad> out = new ArrayList<>();

    /** Call once before the first block and {@link #end} after the last (a shared light cache, see {@link RestLight}). */
    void begin() {
        rest.begin();
    }

    void end() {
        rest.end();
    }
//? if >=26.1 {
    private final boolean cutoutLeaves = mc.options.cutoutLeaves().get();
    private final List<BlockStateModelPart> parts = new ArrayList<>();

    BlockBake(ClientLevel level, TreeFall msg) {
        this.level = level;
        rest = new RestLight(level, msg);
    }

    // the quads of the block at cell; the list is reused by the next call
    List<Quad> block(BlockPos cell, BlockState state) {
        List<BlockTintSource> sources = mc.getBlockColors().getTintSources(state);
        int[] tints = new int[sources.size()];
        for (int t = 0; t < tints.length; t++) {
            BlockTintSource src = sources.get(t);
            tints[t] = src == null ? -1 : src.colorInWorld(state, level, cell) | 0xFF000000;
        }
        parts.clear();
        mc.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(state.getSeed(cell)), parts);
        rest.run(cell, state);
        boolean opaque = ModelBlockRenderer.forceOpaque(cutoutLeaves, state);
        out.clear();
        for (BlockStateModelPart part : parts) {
            for (int d = 0; d <= 6; d++) {
                for (BakedQuad quad : part.getQuads(d < 6 ? DIRS[d] : null)) {
                    Quad q = new Quad();
                    ChunkSectionLayer layer = opaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
                    q.layer = switch (layer) {
                        case SOLID -> RenderPass.Layer.SOLID;
                        case CUTOUT -> RenderPass.Layer.CUTOUT;
                        case TRANSLUCENT -> RenderPass.Layer.TRANSLUCENT;
                    };
                    int ti = quad.materialInfo().tintIndex();
                    q.color = ti >= 0 && ti < tints.length ? tints[ti] : -1;
                    Direction sd = shadeOverride(quad);
                    q.shade = (sd != null ? sd : quad.direction()).ordinal();
                    q.emission = quad.materialInfo().lightEmission();
                    q.rest = rest.get(quad);
                    for (int v = 0; v < 4; v++) {
                        var p = quad.position(v);
                        q.pos[v * 3] = p.x();
                        q.pos[v * 3 + 1] = p.y();
                        q.pos[v * 3 + 2] = p.z();
                        long packed = quad.packedUV(v);
                        q.uv[v * 2] = UVPair.unpackU(packed);
                        q.uv[v * 2 + 1] = UVPair.unpackV(packed);
                    }
                    out.add(q);
                }
            }
        }
        return out;
    }
//?} else if >=1.21.5 {
/*    private final List<BlockModelPart> parts = new ArrayList<>();

    BlockBake(ClientLevel level, TreeFall msg) {
        this.level = level;
        rest = new RestLight(level, msg);
    }

    // the quads of the block at cell; the list is reused by the next call
    List<Quad> block(BlockPos cell, BlockState state) {
        parts.clear();
        mc.getBlockRenderer().getBlockModel(state).collectParts(RandomSource.create(state.getSeed(cell)), parts);
        rest.run(cell, state);
        RenderPass.Layer layer = layer(state);
        out.clear();
        for (BlockModelPart part : parts) {
            for (int d = 0; d <= 6; d++) {
                for (BakedQuad quad : part.getQuads(d < 6 ? DIRS[d] : null)) {
                    Quad q = new Quad();
                    q.layer = layer;
                    if (quad.isTinted()) {
                        int c = mc.getBlockColors().getColor(state, level, cell, quad.tintIndex());
                        q.color = c == -1 ? -1 : c | 0xFF000000;
                    }
                    q.shade = (quad.shade() ? quad.direction() : Direction.UP).ordinal();
                    q.emission = quad.lightEmission();
                    q.rest = rest.get(quad);
                    corners(quad, q);
                    out.add(q);
                }
            }
        }
        return out;
    }

    private static RenderPass.Layer layer(BlockState state) {
        ChunkSectionLayer layer = ItemBlockRenderTypes.getChunkRenderType(state);
        return switch (layer) {
            case SOLID -> RenderPass.Layer.SOLID;
            case CUTOUT -> RenderPass.Layer.CUTOUT;
            case TRANSLUCENT, TRIPWIRE -> RenderPass.Layer.TRANSLUCENT;
            default -> RenderPass.Layer.CUTOUT_MIPPED;
        };
    }
*///?} else {
/*    BlockBake(ClientLevel level, TreeFall msg) {
        this.level = level;
        rest = new RestLight(level, msg);
    }

    // the quads of the block at cell; the list is reused by the next call
    List<Quad> block(BlockPos cell, BlockState state) {
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        rest.run(cell, state);
        RenderPass.Layer layer = layer(state);
        RandomSource random = RandomSource.create();
        out.clear();
        for (int d = 0; d <= 6; d++) {
            random.setSeed(state.getSeed(cell));
            for (BakedQuad quad : model.getQuads(state, d < 6 ? DIRS[d] : null, random)) {
                Quad q = new Quad();
                q.layer = layer;
                if (quad.isTinted()) {
                    int c = mc.getBlockColors().getColor(state, level, cell, quad.getTintIndex());
                    q.color = c == -1 ? -1 : c | 0xFF000000;
                }
                q.shade = (quad.isShade() ? quad.getDirection() : Direction.UP).ordinal();
                q.rest = rest.get(quad);
                int[] vertices = quad.getVertices();
                for (int v = 0; v < 4; v++) {
                    for (int i = 0; i < 3; i++) q.pos[v * 3 + i] = Float.intBitsToFloat(vertices[v * 8 + i]);
                    q.uv[v * 2] = Float.intBitsToFloat(vertices[v * 8 + 4]);
                    q.uv[v * 2 + 1] = Float.intBitsToFloat(vertices[v * 8 + 5]);
                }
                out.add(q);
            }
        }
        return out;
    }

    private static RenderPass.Layer layer(BlockState state) {
        RenderType type = ItemBlockRenderTypes.getMovingBlockRenderType(state);
        if (type == RenderType.solid()) return RenderPass.Layer.SOLID;
        if (type == RenderType.cutout()) return RenderPass.Layer.CUTOUT;
        if (type == RenderType.cutoutMipped()) return RenderPass.Layer.CUTOUT_MIPPED;
        return RenderPass.Layer.TRANSLUCENT;
    }
*///?}
//? if >=1.21.11 <26.1 {
/*    private static void corners(BakedQuad quad, Quad q) {
        for (int v = 0; v < 4; v++) {
            var p = quad.position(v);
            q.pos[v * 3] = p.x();
            q.pos[v * 3 + 1] = p.y();
            q.pos[v * 3 + 2] = p.z();
            long packed = quad.packedUV(v);
            q.uv[v * 2] = UVPair.unpackU(packed);
            q.uv[v * 2 + 1] = UVPair.unpackV(packed);
        }
    }
*///?}
//? if >=1.21.5 <1.21.11 {
/*    private static void corners(BakedQuad quad, Quad q) {
        int[] vertices = quad.vertices();
        for (int v = 0; v < 4; v++) {
            for (int i = 0; i < 3; i++) q.pos[v * 3 + i] = Float.intBitsToFloat(vertices[v * 8 + i]);
            q.uv[v * 2] = Float.intBitsToFloat(vertices[v * 8 + 4]);
            q.uv[v * 2 + 1] = Float.intBitsToFloat(vertices[v * 8 + 5]);
        }
    }
*///?}
}
