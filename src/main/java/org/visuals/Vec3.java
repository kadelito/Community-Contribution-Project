package org.visuals;

public class Vec3 {
    public double x, y, z;

    // Constructors
    public Vec3() {
        this(0, 0,0 );
    }
    public Vec3(Vec3 other) {
        this(other.x, other.y, other.z);
    }
    public Vec3(double a, double b) {
        this(a, b, 0);
    }
    public Vec3(double a, double b, double c) {
        x = a; y = b; z = c;
    }

    // Vector math
    public Vec3 add(Vec3 other) {
        x += other.x;
        y += other.y;
        z += other.z;
        return this;
    }
    
    public Vec3 sub(Vec3 other) {
        x -= other.x;
        y -= other.y;
        z -= other.z;
        return this;
    }
    
    public Vec3 scale(double s) {
        x *= s;
        y *= s;
        z *= s;
        return this;
    }
    
    public double lengthSquared() {
        return x*x + y*y + z*z;
    }
    
    public double length() {
        return Math.sqrt(lengthSquared());
    }
    
    public Vec3 normal() {
        return this.scale(1 / length());
    }

    public Vec3 rayTo(Vec3 dir, double t) {
        return new Vec3(this).add(new Vec3(dir).scale(t));
    }

    @Override
    public String toString() {
        return String.format("<%.5f %.5f %.5f>", x, y, z);
    }

//    =======================================

    // Returns a random unit vector
    public static Vec3 random() {
        Vec3 v;
        double sqrMag;
        do {
            v = new Vec3(
                    Math.random(),
                    Math.random(),
                    Math.random()
            );
            sqrMag = v.lengthSquared();
        } while (1e-20 > sqrMag || sqrMag > 1);

        return v.scale(Math.sqrt(sqrMag));
    }

    public static Vec3 random(double len) {
        return random().scale(len);
    }
}
