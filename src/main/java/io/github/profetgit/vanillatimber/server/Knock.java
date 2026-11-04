package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.core.Timeline;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Trunk knockback (PRD 5.12): from frame 1 to the slam, the falling tree knocks back every mob it sweeps through, once
 * each. The hit test runs in the fall's own frame: the tree is a bar through the pivot at the timeline's pitch, so a mob
 * at distance rho and angle phi from the pivot (in the vertical plane of the fall) is inside the trunk when
 * {@code rho * sin(phi - pitch)} lies in the trunk's band, and the swept pitch of the frame says when. Players are never
 * touched (the query is for mobs, which a player is not).
 */
final class Knock {
    /** Knockback strength per block/tick of the trunk's speed at the mob, and its clamp (tune by feel). */
    static final double PER_SPEED = 1.5, MIN = 0.4, MAX = 1.6;
    /** Slack around a mob's box so a thin sweep does not slip through it. */
    private static final double SLACK = 0.1;

    private final Fall f;
    private final int last;
    private final double dirX, dirZ, reach, trunk, crown, lz;
    private final IntOpenHashSet hit = new IntOpenHashSet();
    private double ns;
    private int frames;
    private double maxNs;

    Knock(Fall f) {
        this.f = f;
        int impact = -1;
        for (Timeline.Event e : f.tl.events) {
            if (e.kind() == Timeline.Kind.IMPACT) {
                impact = e.tick();
                break;
            }
        }
        last = impact;
        // the direction the tree falls in: ^up when pitched flat, in the fall frame the pivot's effects already use
        Vec3 d = Fx.local(Vec3.ZERO, 90, f.yaw(), 1, 0);
        double len = Math.hypot(d.x, d.z);
        dirX = d.x / len;
        dirZ = d.z / len;
        reach = f.tree.spec().h() + 0.5;
        trunk = f.tree.hw() / 1000.0;
        crown = Math.max(trunk, f.tree.cw() / 1000.0);
        lz = f.lz;
    }

    /** The mean and worst server time of one knock frame, in ms (tests; timed falls only). */
    double meanMs() {
        return frames == 0 ? 0 : ns / frames / 1e6;
    }

    double maxMs() {
        return maxNs / 1e6;
    }

    int frames() {
        return frames;
    }

    /** Frame {@code n}: everything the trunk swept between the last frame's pitch and this one. */
    void step(int n) {
        if (n < 1 || n > last || last < 0 || !Config.on(Config.Key.KNOCKBACK)) return;
        boolean timed = Falls.timed();
        long t0 = timed ? System.nanoTime() : 0;
        sweep(n);
        if (timed) {
            long dt = System.nanoTime() - t0;
            ns += dt;
            maxNs = Math.max(maxNs, dt);
            frames++;
        }
    }

    private void sweep(int n) {
        Timeline tl = f.tl;
        double p0 = tl.p[n - 1] / 100.0, p1 = tl.p[n] / 100.0;
        double lo = Math.min(p0, p1) - 0.01, hi = Math.max(p0, p1) + 0.01;
        double px = f.x, py = f.y0 + tl.dy[n], pz = f.z;
        // one query over the swept wedge: the pivot, the tip at both pitches and between, plus the crown and a mob's size
        double minX = px, maxX = px, minY = py, maxY = py, minZ = pz, maxZ = pz;
        for (int i = 0; i <= 2; i++) {
            double p = p0 + (p1 - p0) * i / 2.0, sp = Math.sin(Math.toRadians(p)), cp = Math.cos(Math.toRadians(p));
            double tx = px + dirX * (reach * sp + lz * cp), ty = py + reach * cp - lz * sp, tz = pz + dirZ * (reach * sp + lz * cp);
            minX = Math.min(minX, tx);
            maxX = Math.max(maxX, tx);
            minY = Math.min(minY, ty);
            maxY = Math.max(maxY, ty);
            minZ = Math.min(minZ, tz);
            maxZ = Math.max(maxZ, tz);
        }
        double pad = crown + 2.0 + reach * (1 - Math.cos(Math.toRadians(Math.abs(p1 - p0) / 2)));
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(pad);
        ServerLevel level = f.level;
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, e -> e.isAlive() && !e.isSpectator() && !e.isPassenger() && !hit.contains(e.getId()));
        if (mobs.isEmpty()) return;
        double stepRad = Math.toRadians(Math.abs(p1 - p0));
        for (Mob e : mobs) {
            double speed = test(e, px, py, pz, lo, hi, stepRad);
            if (speed >= 0) knock(e, speed);
        }
    }

    /** The trunk's speed at the mob (blocks/tick) when the sweep touches it, else -1. */
    private double test(Mob e, double px, double py, double pz, double lo, double hi, double stepRad) {
        double w = e.getBbWidth() / 2 + SLACK, h = e.getBbHeight() / 2 + SLACK;
        double rx = e.getX() - px, ry = e.getY() + e.getBbHeight() / 2 - py, rz = e.getZ() - pz;
        double a = rx * dirX + rz * dirZ, s = rz * dirX - rx * dirZ;
        double rho = Math.max(Math.hypot(a, ry), 1e-3), phi = Math.toDegrees(Math.atan2(a, ry));
        // f = rho * sin(phi - pitch) is the offset across the trunk; the band is widest where the crown is
        double band = crown + Math.max(w, h), flo = lz - band, fhi = lz + band;
        double bandLo = phi - Math.toDegrees(Math.asin(Math.clamp(fhi / rho, -1.0, 1.0))), bandHi = phi - Math.toDegrees(Math.asin(Math.clamp(flo / rho, -1.0, 1.0)));
        double from = Math.max(lo, bandLo), to = Math.min(hi, bandHi);
        if (from > to) return -1;
        for (int i = 0; i <= 2; i++) {
            double p = from + (to - from) * i / 2.0, pr = Math.toRadians(p), th = Math.toRadians(phi - p);
            double u = rho * Math.cos(th), off = rho * Math.sin(th);
            if (u < -0.25 || u > reach + w) continue;
            boolean inCrown = u >= f.tree.spec().rs();
            double half = inCrown ? crown : trunk;
            double mf = Math.abs(Math.cos(pr)) * w + Math.abs(Math.sin(pr)) * h;
            if (Math.abs(off - lz) <= half + mf && Math.abs(s) <= half + w) return rho * stepRad;
        }
        return -1;
    }

    private void knock(Mob e, double speed) {
        hit.add(e.getId());
        ServerLevel level = f.level;
        double strength = Math.min(MAX, Math.max(MIN, speed * PER_SPEED));
        int damage = Config.get(Config.Key.TRUNK_DAMAGE);
        Vec3 v0 = e.getDeltaMovement();
        DamageSource src = f.cutter.isRemoved() ? level.damageSources().generic() : level.damageSources().fallingBlock(f.cutter);
        if (damage > 0 && e instanceof Enemy && !e.hasCustomName()) {
            //? if >=1.21.2 {
            e.hurtServer(level, src, damage);
            //?} else {
            /*e.hurt(src, damage);
            *///?}
            // the hit pushes the mob away from the chopper: the trunk's own push replaces it
            e.setDeltaMovement(v0);
        }
        //? if >=26.2 {
        e.knockback(strength, -dirX, -dirZ, src, damage);
        //?} else {
        /*e.knockback(strength, -dirX, -dirZ);
        *///?}
        //? if >=26.3 {
        e.needsSync = true;
        //?} else {
        /*e.hurtMarked = true;
        *///?}
        Fx.bonk(level, new Vec3(e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ()));
    }
}
