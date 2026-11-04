package io.github.profetgit.vanillatimber.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
//? if >=1.21.2 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import io.github.profetgit.vanillatimber.client.compat.IrisCompat;
import net.minecraft.world.level.CardinalLighting;
//?} else {
/*import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * What the world draw hook hands to the falls: the pose stack, the camera position and where geometry goes. The only
 * class (with the mixin that builds it) that names the version's draw API: 26.x submits custom geometry to the entity
 * pass, 1.21.1 writes into the entity buffer source and flushes it at the end of the hook.
 */
public final class RenderPass {
    enum Layer { SOLID, CUTOUT, CUTOUT_MIPPED, TRANSLUCENT }

    interface Geometry {
        void render(Matrix4f pose, VertexConsumer vc);
    }

    final PoseStack ps;
    final Vec3 cam;
//? if >=1.21.2 {
    private final SubmitNodeCollector out;

    public RenderPass(PoseStack ps, LevelRenderState state, SubmitNodeCollector out) {
        this.ps = ps;
        this.cam = state.cameraRenderState.pos;
        this.out = out;
    }

    /** A pass into another collector with its own camera: Iris's shadow pass submits entities from the sun. */
    public RenderPass(PoseStack ps, Vec3 cam, SubmitNodeCollector out) {
        this.ps = ps;
        this.cam = cam;
        this.out = out;
    }

    // the six face values by Direction.ordinal; byFace, not the record fields: Iris patches it when a shader pack turns vanilla shading off
    float[] shades(ClientLevel level) {
        CardinalLighting cl = level.cardinalLighting();
        float[] s = new float[6];
        for (Direction d : Direction.values()) s[d.ordinal()] = cl.byFace(d);
        return s;
    }

    void submit(Matrix4f m, Layer layer, Geometry g) {
        ps.pushPose();
        ps.mulPose(m);
        out.submitCustomGeometry(ps, type(layer), (pose, vc) -> g.render(pose.pose(), vc));
        ps.popPose();
    }

    void flush() {
    }

    static float partial(Minecraft mc) {
        return mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private static RenderType type(Layer layer) {
        if (IrisCompat.active() && layer != Layer.TRANSLUCENT) return RenderTypes.itemCutout(TextureAtlas.LOCATION_BLOCKS);
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT, CUTOUT_MIPPED -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }
//?} else {
/*    private final MultiBufferSource.BufferSource buffers;
    private final Set<RenderType> used = new LinkedHashSet<>();

    public RenderPass(PoseStack ps, MultiBufferSource.BufferSource buffers, Vec3 cam) {
        this.ps = ps;
        this.buffers = buffers;
        this.cam = cam;
    }

    float[] shades(ClientLevel level) {
        float[] s = new float[6];
        for (Direction d : Direction.values()) s[d.ordinal()] = level.getShade(d, true);
        return s;
    }

    void submit(Matrix4f m, Layer layer, Geometry g) {
        RenderType type = type(layer);
        ps.pushPose();
        ps.mulPose(m);
        g.render(ps.last().pose(), buffers.getBuffer(type));
        ps.popPose();
        used.add(type);
    }

    // the shared buffer holds one batch at a time: close each used type so the frame's falls are drawn before the hook returns
    void flush() {
        for (RenderType t : used) buffers.endBatch(t);
        used.clear();
    }

    static float partial(Minecraft mc) {
        return mc.getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static RenderType type(Layer layer) {
        return switch (layer) {
            case SOLID -> RenderType.solid();
            case CUTOUT -> RenderType.cutout();
            case CUTOUT_MIPPED -> RenderType.cutoutMipped();
            case TRANSLUCENT -> RenderType.translucentMovingBlock();
        };
    }
*///?}
}
