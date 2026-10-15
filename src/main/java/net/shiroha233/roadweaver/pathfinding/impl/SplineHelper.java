package net.shiroha233.roadweaver.pathfinding.impl;

/**
 * 样条曲线与贝塞尔曲线数学工具
 */
public final class SplineHelper {
    private SplineHelper() {}

    public record Vec2d(double x, double z) {
        public double dist(Vec2d o) { return Math.sqrt(distSqr(o)); }
        public double distSqr(Vec2d o) { return (x - o.x) * (x - o.x) + (z - o.z) * (z - o.z); }
    }

    public enum CurveMode { CATMULL_ROM, BEZIER_CASTELJAU }

    public static Vec2d lerp2d(double ax, double az, double bx, double bz, double t) {
        return new Vec2d(ax + (bx - ax) * t, az + (bz - az) * t);
    }

    public static Vec2d catmullRomSpline(double x0, double z0, double x1, double z1,
                                         double x2, double z2, double x3, double z3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double f0 = -0.5 * t3 + t2 - 0.5 * t;
        double f1 = 1.5 * t3 - 2.5 * t2 + 1.0;
        double f2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t;
        double f3 = 0.5 * t3 - 0.5 * t2;
        return new Vec2d(x0 * f0 + x1 * f1 + x2 * f2 + x3 * f3,
                         z0 * f0 + z1 * f1 + z2 * f2 + z3 * f3);
    }

    public static Vec2d catmullRomBezierCasteljau(double x0, double z0, double x1, double z1,
                                                   double x2, double z2, double x3, double z3, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        Vec2d b0 = new Vec2d(x1, z1);
        Vec2d b1 = new Vec2d(x1 + (x2 - x0) / 6.0, z1 + (z2 - z0) / 6.0);
        Vec2d b2 = new Vec2d(x2 - (x3 - x1) / 6.0, z2 - (z3 - z1) / 6.0);
        Vec2d b3 = new Vec2d(x2, z2);
        Vec2d[] elevated = elevateBezierDegree(new Vec2d[]{b0, b1, b2, b3}, 5);
        return bezierDeCasteljau(elevated, t);
    }

    static Vec2d[] elevateBezierDegree(Vec2d[] ctrl, int targetDegree) {
        if (ctrl == null || ctrl.length < 2) return ctrl;
        int degree = ctrl.length - 1;
        if (targetDegree <= degree) return ctrl;

        Vec2d[] cur = ctrl;
        int n = degree;
        while (n < targetDegree) {
            Vec2d[] next = new Vec2d[n + 2];
            next[0] = cur[0];
            next[n + 1] = cur[n];
            for (int i = 1; i <= n; i++) {
                double a = (double) i / (double) (n + 1);
                next[i] = new Vec2d(a * cur[i - 1].x + (1.0 - a) * cur[i].x,
                                    a * cur[i - 1].z + (1.0 - a) * cur[i].z);
            }
            cur = next;
            n++;
        }
        return cur;
    }

    static Vec2d bezierDeCasteljau(Vec2d[] ctrl, double t) {
        int n = ctrl.length;
        double[] xs = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = ctrl[i].x;
            zs[i] = ctrl[i].z;
        }
        for (int k = n - 1; k > 0; k--) {
            for (int i = 0; i < k; i++) {
                xs[i] = xs[i] + (xs[i + 1] - xs[i]) * t;
                zs[i] = zs[i] + (zs[i + 1] - zs[i]) * t;
            }
        }
        return new Vec2d(xs[0], zs[0]);
    }

    public static double distToSegmentSq(double px, double pz, Vec2d v, Vec2d w) {
        double l2 = v.distSqr(w);
        if (l2 == 0) return (px - v.x) * (px - v.x) + (pz - v.z) * (pz - v.z);
        double t = ((px - v.x) * (w.x - v.x) + (pz - v.z) * (w.z - v.z)) / l2;
        t = Math.max(0, Math.min(1, t));
        double projX = v.x + t * (w.x - v.x);
        double projZ = v.z + t * (w.z - v.z);
        return (px - projX) * (px - projX) + (pz - projZ) * (pz - projZ);
    }
}
