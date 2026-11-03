package io.github.profetgit.vanillatimber.core;

/**
 * How a pose places the tree's blocks, shared by the server's displays and the client's mesh (1.3.6 {@code anim/xf}).
 *
 * <p>The tree frame is the yaw frame at the pivot: x across the fall, y up the trunk, z along the fall. Each block keeps
 * its rest corner (tx, wy, tz) in that frame and its ring k = round(wy). A pose turns every ring by the same pitch
 * (rings stay flush) and slides ring k+1 off ring k along the chain: one squashed block up the trunk, plus the bend
 * as a sideways step of bend * min(1, (k/h)^2) blocks (a shear; per-ring rotation opened seams in 1.3.0).
 */
public final class Rig {
    private Rig() {}

    /**
     * Ring origins for rings kn..kt at pitch {@code deg} (about x, positive toward the fall), tip bend {@code bendDeg}
     * and trunk squash {@code gy}: (oy, oz) of ring k at index k - kn. Rings below 0 (leaves under the cut) turn rigidly.
     */
    public static void origins(double deg, double bendDeg, double gy, int h, int kn, int kt, double[] oy, double[] oz) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg)), br = Math.toRadians(bendDeg);
        double y = 0, z = 0, hh = (double) h * h;
        for (int k = kn; k <= kt; k++) {
            if (k < 0) {
                oy[k - kn] = k * gy * c;
                oz[k - kn] = k * gy * s;
                continue;
            }
            oy[k - kn] = y;
            oz[k - kn] = z;
            y += c * gy;
            z += s * gy;
            if (br != 0) {
                double d = br * Math.min(1, k * (double) k / hh);
                y -= d * s;
                z += d * c;
            }
        }
    }

    /**
     * A block's pose in the tree frame, as a display transformation: {@code out} = translation (x, y, z) of its rest
     * corner, from the ring origin (oy, oz), the pitch {@code deg} and the group's scale (sx, gy, sz).
     */
    public static void corner(double tx, double wy, double tz, int k, double sx, double gy, double sz, double deg, double oy,
                              double oz, double[] out) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        double x = tx * sx, y = (wy - k) * gy, z = tz * sz;
        out[0] = x;
        out[1] = y * c - z * s + oy;
        out[2] = y * s + z * c + oz;
    }

    /** The entity's share of a pitch: the byte step at or below it (1.40625 degrees), clamped to +-90 degrees. */
    public static double step(double deg) {
        int q = (int) Math.floor(deg * 64 / 90 + 1e-9);
        q = Math.max(-64, Math.min(64, q));
        return q * 90.0 / 64;
    }
}
