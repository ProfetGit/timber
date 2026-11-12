package io.github.profetgit.vanillatimber.client;

//? if fabric && >=26.2 {
import io.github.profetgit.vanillatimber.core.Cut;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Chop mode's shrinking logs (PRD 5.13): the one class that names Fabric's model loading and Renderer APIs. A log's
 * baked model is wrapped, and at a cell the server said is cut ({@link ChoppedCells}) the wrapper emits the box the cell
 * still fills instead of the cube: the stripped log's own quads, cropped so texels stay 1:1, plus the neighbours' faces at
 * the cell boundary, since the cell stays a full occluder and the neighbours' faces toward it are culled. The cells of
 * one layer (a 2x2 trunk's four) share a footprint: where two cut cells meet, neither draws a face. A port to another
 * loader or version swaps this class and the call in the client entry.
 */
public final class ChopModels {
    private static final Direction[] DIRS = Direction.values();
    private static final Map<BlockState, Skin> SKINS = new ConcurrentHashMap<>();
    private static volatile Object skinsFor;

    private ChopModels() {}

    public static void register() {
        ModelLoadingPlugin.register(ctx -> ctx.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE,
            (model, c) -> trunk(c.state()) ? new Chopped(model) : model));
    }

    // tags aren't bound when the models bake, so the wrapper goes on everything log-shaped; a cell the server hasn't chopped is untouched
    private static boolean trunk(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof RotatedPillarBlock || block instanceof HugeMushroomBlock) return true;
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return path.contains("log") || path.contains("wood") || path.contains("stem") || path.contains("hyphae") || path.contains("trunk");
    }

    private static Direction.Axis axisOf(BlockState state) {
        return state.getOptionalValue(BlockStateProperties.AXIS).orElse(Direction.Axis.Y);
    }

    // ---- the core's faces: the stripped log's quads, one per direction ----

    private record Skin(BakedQuad[] faces) {}

    private static BlockStateModelSet models() {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet();
    }

    private static BlockState allFaces(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof BooleanProperty b && switch (p.getName()) {
                case "north", "east", "south", "west", "up", "down" -> true;
                default -> false;
            }) state = state.setValue(b, true);
        }
        return state;
    }

    private static @Nullable BlockState stripped(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id.getPath().startsWith("stripped_")) return null;
        Block block = BuiltInRegistries.BLOCK.getOptional(id.withPath("stripped_" + id.getPath())).orElse(null);
        if (block == null) return null;
        BlockState out = block.defaultBlockState();
        for (Property<?> p : state.getProperties()) out = copy(state, out, p);
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> p) {
        return to.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
    }

    private static BakedQuad[] faces(BlockState state) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        models().get(state).collectParts(RandomSource.create(42L), parts);
        BakedQuad[] out = new BakedQuad[6];
        for (Direction d : DIRS) {
            for (BlockStateModelPart part : parts) {
                if (out[d.ordinal()] != null) break;
                List<BakedQuad> list = part.getQuads(d);
                if (!list.isEmpty()) out[d.ordinal()] = list.getFirst();
                else for (BakedQuad q : part.getQuads(null)) if (q.direction() == d) out[d.ordinal()] = q;
            }
        }
        return out;
    }

    private static Skin skin(BlockState state) {
        Object set = models();
        if (skinsFor != set) {
            SKINS.clear();
            skinsFor = set;
        }
        return SKINS.computeIfAbsent(state, ChopModels::buildSkin);
    }

    private static Skin buildSkin(BlockState state) {
        Direction.Axis axis = axisOf(state);
        BakedQuad[] own = faces(allFaces(state));
        BlockState strip = stripped(state);
        BakedQuad[] theirs = strip == null ? new BakedQuad[6] : faces(allFaces(strip));
        BakedQuad end = own[Direction.get(Direction.AxisDirection.POSITIVE, axis).ordinal()];
        if (end == null) end = own[Direction.get(Direction.AxisDirection.NEGATIVE, axis).ordinal()];
        BakedQuad[] out = new BakedQuad[6];
        for (Direction d : DIRS) {
            BakedQuad q = theirs[d.ordinal()];
            if (q == null) {
                q = own[d.ordinal()];
                if (q != null && end != null && d.getAxis() != axis) q = resprite(q, end.materialInfo().sprite());
            }
            out[d.ordinal()] = q;
        }
        return new Skin(out);
    }

    // ---- quad surgery ----

    private static float u(BakedQuad q, int i) {
        return UVPair.unpackU(q.packedUV(i));
    }

    private static float v(BakedQuad q, int i) {
        return UVPair.unpackV(q.packedUV(i));
    }

    private static BakedQuad.MaterialInfo withSprite(BakedQuad.MaterialInfo m, TextureAtlasSprite sprite, int tint) {
//? if >=26.3 {
        return new BakedQuad.MaterialInfo(sprite, m.layer(), m.itemRenderType(), m.itemGlintRenderType(), m.itemGlintSpecialRenderType(), tint,
            m.shadeDirectionOverride(), m.lightEmission());
//?} else {
/*        return new BakedQuad.MaterialInfo(sprite, m.layer(), m.itemRenderType(), tint, m.shade(), m.lightEmission());
*///?}
    }

    /** The same quad showing {@code sprite}'s whole texture where it showed its own sprite's. */
    private static BakedQuad resprite(BakedQuad q, TextureAtlasSprite sprite) {
        TextureAtlasSprite old = q.materialInfo().sprite();
        long[] uv = new long[4];
        for (int i = 0; i < 4; i++) {
            float nu = (u(q, i) - old.getU0()) / (old.getU1() - old.getU0());
            float nv = (v(q, i) - old.getV0()) / (old.getV1() - old.getV0());
            uv[i] = UVPair.pack(sprite.getU0() + nu * (sprite.getU1() - sprite.getU0()), sprite.getV0() + nv * (sprite.getV1() - sprite.getV0()));
        }
        return new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(), uv[0], uv[1], uv[2], uv[3], q.direction(),
            withSprite(q.materialInfo(), sprite, q.materialInfo().tintIndex()));
    }

    private static float coord(Vector3fc p, int axis) {
        return axis == 0 ? p.x() : axis == 1 ? p.y() : p.z();
    }

    /**
     * A full face of a log cropped to {@code box} ({@link Cut}): the face moves to the box's bound on its own axis and
     * loses what lies outside the box on the other two. The UVs follow the positions, so the texture is cropped, never
     * scaled.
     */
    static BakedQuad crop(BakedQuad q, int box) {
        Direction d = q.direction();
        int plane = d.getAxis().ordinal();
        Vector3fc p0 = q.position0(), p1 = q.position1(), p3 = q.position3();
        float[] e1 = {p1.x() - p0.x(), p1.y() - p0.y(), p1.z() - p0.z()};
        float[] e2 = {p3.x() - p0.x(), p3.y() - p0.y(), p3.z() - p0.z()};
        float l1 = e1[0] * e1[0] + e1[1] * e1[1] + e1[2] * e1[2], l2 = e2[0] * e2[0] + e2[1] * e2[1] + e2[2] * e2[2];
        float u0 = u(q, 0), v0 = v(q, 0), du1 = u(q, 1) - u0, dv1 = v(q, 1) - v0, du2 = u(q, 3) - u0, dv2 = v(q, 3) - v0;
        Vector3f[] pos = new Vector3f[4];
        long[] uv = new long[4];
        for (int i = 0; i < 4; i++) {
            Vector3fc p = q.position(i);
            float[] c = {p.x(), p.y(), p.z()};
            float[] n = new float[3];
            for (int a = 0; a < 3; a++) {
                float lo = Cut.lo(box, a) / 16f, hi = Cut.hi(box, a) / 16f;
                if (a == plane) n[a] = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? hi : lo;
                else n[a] = c[a] < 0.5f ? lo : hi;
            }
            float dx = n[0] - p0.x(), dy = n[1] - p0.y(), dz = n[2] - p0.z();
            float s = (dx * e1[0] + dy * e1[1] + dz * e1[2]) / l1, t = (dx * e2[0] + dy * e2[1] + dz * e2[2]) / l2;
            pos[i] = new Vector3f(n[0], n[1], n[2]);
            uv[i] = UVPair.pack(u0 + s * du1 + t * du2, v0 + s * dv1 + t * dv2);
        }
        return new BakedQuad(pos[0], pos[1], pos[2], pos[3], uv[0], uv[1], uv[2], uv[3], d, q.materialInfo());
    }

    private static BakedQuad moved(BakedQuad q, Direction by) {
        Vector3f[] pos = new Vector3f[4];
        for (int i = 0; i < 4; i++) pos[i] = new Vector3f(q.position(i)).add(by.getStepX(), by.getStepY(), by.getStepZ());
        return new BakedQuad(pos[0], pos[1], pos[2], pos[3], q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(), q.direction(),
            withSprite(q.materialInfo(), q.materialInfo().sprite(), -1));
    }

    // ---- the cell's quads ----

    /** What the neighbour in {@code d} shows toward the cell, moved onto the cell's boundary: the face the culling took away. */
    private static List<BakedQuad> closing(BlockAndTintGetter level, BlockPos pos, Direction d) {
        BlockState n = level.getBlockState(pos.relative(d));
        List<BlockStateModelPart> parts = new ArrayList<>();
        models().get(n).collectParts(RandomSource.create(42L), parts);
        List<BakedQuad> out = new ArrayList<>();
        for (BlockStateModelPart part : parts) for (BakedQuad q : part.getQuads(d.getOpposite())) out.add(moved(q, d));
        return out;
    }

    /** The core's quads, each with the direction it is culled by (null: never). */
    private record Cell(BakedQuad quad, @Nullable Direction cull) {}

    /**
     * A cut cell's quads. Per face: the neighbour's face toward the cell where the box leaves the boundary open (unless the
     * neighbour is cut too: it draws its own box), then the box's own face, culled like a full face when it lies on the
     * boundary, left out where it meets the neighbour's box face to face (inside one layer), always drawn otherwise.
     */
    private static List<Cell> cell(BlockAndTintGetter level, BlockPos pos, BlockState state, int box) {
        Skin skin = skin(state);
        List<Cell> out = new ArrayList<>();
        for (Direction d : DIRS) {
            int axis = d.getAxis().ordinal();
            boolean up = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            int next = ChoppedCells.box(pos.relative(d));
            boolean nextCut = next != Cut.WHOLE;
            if (!nextCut && !Cut.covers(box, axis, up)) for (BakedQuad q : closing(level, pos, d)) out.add(new Cell(q, null));
            BakedQuad q = skin.faces()[d.ordinal()];
            if (q == null) continue;
            Direction cull = null;
            if (Cut.touches(box, axis, up)) {
                if (nextCut && (up ? Cut.meets(box, next, axis) : Cut.meets(next, box, axis))) continue;
                if (!nextCut) cull = d;
            }
            out.add(new Cell(crop(q, box), cull));
        }
        return out;
    }

    private static void emit(QuadEmitter e, Cell c) {
        e.cullFace(c.cull());
        e.fromBakedQuad(c.quad());
        e.tintIndex(c.quad().materialInfo().tintIndex());
        e.ambientOcclusion(TriState.DEFAULT);
        e.shadeMode(ShadeMode.VANILLA);
        e.emit();
    }

    /** The breaking overlay's quads at a cut cell: the box only, so the crack sits on what is drawn. */
    public static @Nullable BlockStateModelPart crackPart(BlockState state, BlockPos pos) {
        int box = ChoppedCells.box(pos);
        if (box == Cut.WHOLE) return null;
        Skin skin = skin(state);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction d : DIRS) if (skin.faces()[d.ordinal()] != null) quads.add(crop(skin.faces()[d.ordinal()], box));
        BlockStateModel model = models().get(state);
        return new BlockStateModelPart() {
            @Override
            public List<BakedQuad> getQuads(@Nullable Direction direction) {
                return direction == null ? quads : List.of();
            }

            @Override
            public boolean useAmbientOcclusion() {
                return false;
            }

            @Override
            public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial() {
                return model.particleMaterial();
            }

            @Override
            public int materialFlags() {
                return model.materialFlags();
            }
        };
    }

    /** The log's model, which at a chopped cell draws the core. */
    private static final class Chopped extends WrapperBlockStateModel {
        Chopped(BlockStateModel wrapped) {
            super(wrapped);
        }

        @Override
        public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                              Predicate<@Nullable Direction> cullTest) {
            int box = ChoppedCells.box(pos);
            if (box == Cut.WHOLE) {
                super.emitQuads(emitter, level, pos, state, random, cullTest);
                return;
            }
            for (Cell c : cell(level, pos, state, box)) emit(emitter, c);
        }

        @Override
        public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return ChoppedCells.box(pos) == Cut.WHOLE ? super.createGeometryKey(level, pos, state, random) : null;
        }
    }
}
//?} else {
/*public final class ChopModels {
    private ChopModels() {}
}
*///?}
