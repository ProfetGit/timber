package io.github.profetgit.vanillatimber.server;

//? if >=26.3
import net.minecraft.commands.arguments.coordinates.LocalCoordinates;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The fall's sounds and particles, the same ids, volumes, pitches, spreads and counts as 1.3.6 ({@code anim/fx/*},
 * {@code anim/ring_pop}, {@code anim/step_impact}, {@code disp/start}). Vanilla only, so every client hears them.
 */
final class Fx {
    private Fx() {}

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    static void particle(ServerLevel level, ParticleOptions p, Vec3 at, double dx, double dy, double dz, double speed, int count) {
        level.sendParticles(p, at.x, at.y, at.z, count, dx, dy, dz, speed);
    }

    static BlockParticleOption block(BlockState state) {
        return new BlockParticleOption(ParticleTypes.BLOCK, state);
    }

    /** {@code execute rotated <yaw> <pitch> positioned ^ ^up ^forward}. */
    static Vec3 local(Vec3 from, double pitch, double yaw, double up, double forward) {
        return local(from, new Vec2((float) pitch, (float) yaw), 0, up, forward);
    }

    /** {@code ^left ^up ^forward} from {@code from} turned by {@code rot} (x pitch, y yaw), the maths /execute uses. */
    static Vec3 local(Vec3 from, Vec2 rot, double left, double up, double forward) {
        //? if >=26.3 {
        return new LocalCoordinates(left, up, forward).apply(from, rot);
        //?} else if >=1.21.2 {
        /*return Vec3.applyLocalCoordinatesToRotation(rot, new Vec3(left, up, forward)).add(from);
        *///?} else {
        /*float yaw = (rot.y + 90f) * (float) (Math.PI / 180.0), pitch = -rot.x * (float) (Math.PI / 180.0), pitchUp = (-rot.x + 90f) * (float) (Math.PI / 180.0);
        Vec3 fwd = new Vec3(Mth.cos(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.sin(yaw) * Mth.cos(pitch));
        Vec3 upv = new Vec3(Mth.cos(yaw) * Mth.cos(pitchUp), Mth.sin(pitchUp), Mth.sin(yaw) * Mth.cos(pitchUp));
        Vec3 lft = fwd.cross(upv).scale(-1.0);
        return new Vec3(from.x + fwd.x * forward + upv.x * up + lft.x * left, from.y + fwd.y * forward + upv.y * up + lft.y * left,
            from.z + fwd.z * forward + upv.z * up + lft.z * left);
        *///?}
    }

    /** The falling-leaf particle 1.3.6 picked per leaf block ({@code disp/leaf_fx}). */
    static ParticleOptions leafParticle(BlockState leaf) {
        if (leaf == null) return tinted(0.282f, 0.71f, 0.094f);
        // a wart crown or a mushroom cap sheds dust of its own colour, not green flakes
        if (!leafy(leaf)) return new BlockParticleOption(ParticleTypes.FALLING_DUST, leaf.getBlock().defaultBlockState());
        if (leaf.is(Blocks.CHERRY_LEAVES)) return ParticleTypes.CHERRY_LEAVES;
//? if >=1.21.2
        if (leaf.is(Blocks.PALE_OAK_LEAVES)) return ParticleTypes.PALE_OAK_LEAVES;
        if (leaf.is(Blocks.BIRCH_LEAVES)) return tinted(0.502f, 0.655f, 0.333f);
        if (leaf.is(Blocks.SPRUCE_LEAVES)) return tinted(0.38f, 0.6f, 0.38f);
        if (leaf.is(Blocks.MANGROVE_LEAVES)) return tinted(0.573f, 0.776f, 0.282f);
        if (leaf.is(Blocks.AZALEA_LEAVES)) return tinted(0.55f, 0.72f, 0.28f);
        if (leaf.is(Blocks.FLOWERING_AZALEA_LEAVES)) return tinted(0.6f, 0.68f, 0.36f);
        return tinted(0.282f, 0.71f, 0.094f);
    }

    /** A leaf block (tagged or a modded {@code LeavesBlock}), not a connected type's crown block. */
    static boolean leafy(BlockState leaf) {
        return leaf.is(net.minecraft.tags.BlockTags.LEAVES) || leaf.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock;
    }

    private static ParticleOptions tinted(float r, float g, float b) {
//? if >=1.21.2 {
        return ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, r, g, b);
//?} else {
/*        return new DustParticleOptions(new org.joml.Vector3f(r, g, b), 1f);
*///?}
    }

    /** The chop: the crack, a puff at the cut, the crown shakes ({@code disp/start}, {@code fx/shake}). */
    static void chop(ServerLevel level, Fall f, Vec3 at) {
        Tree t = f.tree;
        sound(level, at, SoundEvents.WOOD_BREAK, 1, 0.6f);
        particle(level, ParticleTypes.SMALL_GUST, at.add(0, t.hang() ? -0.5 : 0.5, 0), 0.25, 0.2, 0.25, 0, 1);
        if (t.leaf() == null) return;
        Vec3 crown = local(at, 0, t.yaw(), 0, f.lz).add(0, f.cy, 0);
        particle(level, f.leafParticle, crown, f.cr, 0.8, f.cr, 0, 4);
        sound(level, at.add(0, 3, 0), SoundEvents.AZALEA_LEAVES_BREAK, 0.7f, 1.2f);
        particle(level, block(t.leaf().getBlock().defaultBlockState()), crown, f.cr, 0.8, f.cr, 0.05, 20);
    }

    /** {@code animation} 0 ({@code disp/instant}): a puff and the crack of wood where the tree stood. */
    static void instant(ServerLevel level, Vec3 at) {
        particle(level, ParticleTypes.SMALL_GUST, at.add(0, 1, 0), 0.5, 0.6, 0.5, 0, 3);
        sound(level, at, SoundEvents.WOOD_BREAK, 1, 0.6f);
    }

    /** The trunk lands on the stump after a plunge ({@code fx/land}, {@code land_at}). */
    static void land(ServerLevel level, Fall f, Vec3 at) {
        sound(level, at, SoundEvents.WOOD_FALL, 1, 0.6f);
        sound(level, at, SoundEvents.GENERIC_BIG_FALL, 0.6f, 1.1f);
        Vec3 p = local(at, 0, f.tree.yaw(), 0.15, f.lz);
        particle(level, ParticleTypes.SMALL_GUST, p, 0.4, 0.05, 0.4, 0, 3);
        particle(level, block(f.tree.log()), p, 0.4, 0.1, 0.4, 0, 8);
    }

    static void creak(ServerLevel level, Vec3 at) {
        sound(level, at, SoundEvents.WOODEN_DOOR_OPEN, 1, 0.5f);
    }

    static void crack(ServerLevel level, Fall f, Vec3 at) {
        sound(level, at, SoundEvents.WOOD_BREAK, 0.9f, 0.55f);
        sound(level, at, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.3f, 0.9f);
        particle(level, block(f.tree.log()), at.add(0, 0.2, 0), 0.3, 0.1, 0.3, 0, 6);
    }

    static void whoosh(ServerLevel level, Vec3 at) {
        sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
    }

    static void thud(ServerLevel level, Vec3 at, float v, float p) {
        sound(level, at, SoundEvents.WOOD_FALL, v, p);
        sound(level, at, SoundEvents.GENERIC_BIG_FALL, v, p);
    }

    /**
     * The slam: sounds, dust and leaf bursts walked along the lying trunk, the flutter over the crown
     * ({@code fx/impact}, {@code walk_impact}, {@code step_impact}, {@code fx/flutter}). Returns the leaf drop points.
     */
    static java.util.List<Vec3> impact(ServerLevel level, Fall f, Vec3 at, double pitch) {
        Tree t = f.tree;
        sound(level, at, SoundEvents.GENERIC_BIG_FALL, 1.6f, 0.6f);
        sound(level, at, SoundEvents.WOOD_BREAK, 1.2f, 0.5f);
        sound(level, at, SoundEvents.AZALEA_LEAVES_BREAK, 1.4f, 0.7f);
        sound(level, at, SoundEvents.AZALEA_LEAVES_BREAK, 1.0f, 1.1f);
        java.util.List<Vec3> points = new java.util.ArrayList<>();
        double cw = t.cw() / 1000.0;
        int rs = t.spec().rs(), h = t.spec().h();
        BlockParticleOption leaf = t.leaf() == null ? null : block(t.leaf().getBlock().defaultBlockState());
        int burst = t.leaf() == null || leafy(t.leaf()) ? 24 : 36;
        BlockParticleOption log = block(t.log());
        Vec3 p = local(at, pitch, t.yaw(), 0.5, f.lz);
        for (int k = 0; k < h; k++) {
            boolean even = k % 2 == 0;
            if (k >= rs && leaf != null) {
                particle(level, leaf, p.add(0, 0.5, 0), cw, cw, cw, 0.09, burst);
                particle(level, ParticleTypes.SMALL_GUST, p.add(0, 0.5, 0), cw, cw, cw, 0, 3);
                if (even) points.add(p.add(0, 0.5, 0));
            }
            particle(level, ParticleTypes.DUST_PLUME, p.add(0, -0.3, 0), 0.35, 0.05, 0.35, 0.02, 3);
            if (k < rs && even) particle(level, log, p, 0.4, 0.3, 0.4, 0, 5);
            p = local(p, pitch, t.yaw(), 1, 0);
        }
        Vec3 flutter = local(at, pitch, t.yaw(), f.cy, f.lz).add(0, 0.6, 0);
        particle(level, f.leafParticle, flutter, f.cr, 0.4, f.cr, 0, 6);
        return points;
    }

    /** The trunk bonks a mob: a wooden thunk, a knock on a door, and a few crits. */
    static void bonk(ServerLevel level, Vec3 at) {
        sound(level, at, SoundEvents.WOOD_HIT, 1f, 0.7f);
        sound(level, at, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.3f, 1.5f);
        particle(level, ParticleTypes.CRIT, at, 0.25, 0.3, 0.25, 0.4, 6);
    }

    /** A log ring pops ({@code ring_pop}): chips, a puff, a crack and a pop, pitch rising up the trunk. */
    static void ringPop(ServerLevel level, Fall f, Vec3 at, float pp) {
        particle(level, block(f.tree.log()), at, 0.35, 0.35, 0.35, 0.12, 22);
        particle(level, ParticleTypes.SMALL_GUST, at, 0.25, 0.25, 0.25, 0, 2);
        sound(level, at, SoundEvents.WOOD_BREAK, 0.7f, pp);
        sound(level, at, SoundEvents.CHICKEN_EGG, 0.6f, pp + 0.3f);
    }
}
