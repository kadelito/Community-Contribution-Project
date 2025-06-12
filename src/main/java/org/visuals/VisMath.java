package org.visuals;

public class VisMath {

    private static final double BOUNCE = 200.0;

    public static Vec3 bounce(double t, Vec3 p1, Vec3 p2) {
        Vec3 linePoint = lerp(clamp(t), p1, p2);
        double bounceZ = BOUNCE * (t - t*t);
        return linePoint.add(new Vec3(0, bounceZ, 0));
    }

    public static Vec3 lerp(double t, Vec3 p1, Vec3 p2) {
        return new Vec3(p1).scale(1 - t).add(new Vec3(p2).scale(t));
    }

    private static double clamp(double x) {return clamp(x,0,1);}
    private static double clamp(double x, double min, double max) {
        return Math.max(min, Math.min(max, x));
    }

}
