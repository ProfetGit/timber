package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.Timber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=26.3 {
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
//?} else {
/*import net.minecraft.core.particles.ParticleTypes;
*///?}
import net.minecraft.util.RandomSource;

/**
 * The poof style's cloud on a modded client: Timber's own cartoon puff sprites ({@code textures/particle/poof_*.png}, drawn
 * in Aseprite), spawned straight into the particle engine with no registry entry. The server sends modded viewers no
 * vanilla poof particles; older targets fall back to the vanilla poof.
 */
final class PoofClouds {
    private PoofClouds() {}

    /** Spawns a puff of {@code size} blocks at the point, drifting up and out. */
    static void puff(ClientLevel level, double x, double y, double z, double ox, double oz, float size, boolean small, RandomSource r) {
//? if >=26.3 {
        TextureAtlasSprite[] frames = frames(small);
        if (frames == null) return;
        Minecraft.getInstance().particleEngine.add(new Cloud(level, x, y, z, ox * 0.06 + (r.nextDouble() - 0.5) * 0.04,
            0.03 + r.nextDouble() * 0.04, oz * 0.06 + (r.nextDouble() - 0.5) * 0.04, frames, size, r));
//?} else {
        /*level.addParticle(ParticleTypes.POOF, x, y, z, ox * 0.05, 0.04, oz * 0.05);
*///?}
    }

//? if >=26.3 {
    /** Frames per set ({@code poof_0..N-1}). */
    private static final int FRAMES = 16;
    private static TextureAtlasSprite[] big, little;
    private static Object builtFor;

    private static TextureAtlasSprite[] frames(boolean small) {
        try {
            var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
            if (atlas != builtFor) {
                builtFor = atlas;
                big = null;
                little = null;
            }
            TextureAtlasSprite[] have = small ? little : big;
            if (have != null) return have;
            TextureAtlasSprite[] out = new TextureAtlasSprite[FRAMES];
            for (int i = 0; i < FRAMES; i++) out[i] = atlas.getSprite(Identifier.fromNamespaceAndPath(Timber.ID, (small ? "poof_small_" : "poof_") + i));
            if (small) little = out;
            else big = out;
            return out;
        } catch (RuntimeException e) {
            Timber.LOG.warn("poof sprites unavailable: {}", e.toString());
            return null;
        }
    }

    private static final class Cloud extends SingleQuadParticle {
        private final TextureAtlasSprite[] frames;
        private final float full;

        Cloud(ClientLevel level, double x, double y, double z, double xa, double ya, double za, TextureAtlasSprite[] frames, float size,
              RandomSource r) {
            super(level, x, y, z, frames[0]);
            this.frames = frames;
            this.full = size;
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            this.friction = 0.82F;
            this.gravity = -0.012F;
            this.lifetime = 9 + r.nextInt(3);
            this.quadSize = size * 0.5F;
            this.roll = this.oRoll = 0;
            this.hasPhysics = false;
        }

        @Override
        public void tick() {
            super.tick();
            float u = Math.min(1F, (float) this.age / this.lifetime);
            this.setSprite(this.frames[Math.min(this.frames.length - 1, (int) (u * this.frames.length))]);
            // pops open fast, then holds its size while the frames break it up
            float grow = u < 0.25F ? 0.5F + 2F * u : 1F;
            this.quadSize = this.full * grow;
        }

        /** A puff inside a block would be lit by the block's own light (black): take the brightest of its cell and the six around it. */
        @Override
        protected int getLightCoords(float a) {
            BlockPos at = BlockPos.containing(this.x, this.y, this.z);
            if (!this.level.hasChunkAt(at)) return 15728640;
            int b = 0, sk = 0;
            for (int i = 0; i < 7; i++) {
                BlockPos p = i == 0 ? at : at.relative(net.minecraft.core.Direction.values()[i - 1]);
                int l = LightCoordsUtil.getLightCoords(this.level, p);
                b = Math.max(b, LightCoordsUtil.block(l));
                sk = Math.max(sk, LightCoordsUtil.sky(l));
            }
            return LightCoordsUtil.pack(b, sk);
        }

        @Override
        public float getQuadSize(float a) {
            return this.quadSize;
        }

        @Override
        protected SingleQuadParticle.Layer getLayer() {
            return SingleQuadParticle.Layer.TRANSLUCENT;
        }
    }
//?}
}
