package com.kazisayed.deliverit3d;

/** Pure-Java port of collision.js: OBB, circle and mixed contacts (SAT). */
public final class Collision {
    private Collision() {}
    private static final double EPS = 1e-8;

    /** Collider; radius > 0 means circle, otherwise an oriented box. */
    public static final class Box {
        public float x, z, yaw, halfWidth, halfLength, radius;
        public String kind;
        public Box(float x, float z, float halfWidth, float halfLength, float yaw, String kind) {
            this.x = x; this.z = z; this.halfWidth = halfWidth; this.halfLength = halfLength; this.yaw = yaw; this.kind = kind;
        }
        public static Box circle(float x, float z, float radius, String kind) {
            Box b = new Box(x, z, radius, radius, 0, kind); b.radius = radius; return b;
        }
        boolean isCircle() { return radius > 0; }
    }

    /** Normal points from the obstacle toward the first collider (push direction). */
    public record Contact(float x, float z, float depth) {}

    private static double[][] axes(double yaw) {
        return new double[][]{{Math.cos(yaw), -Math.sin(yaw)}, {Math.sin(yaw), Math.cos(yaw)}};
    }
    private static double reach(double hw, double hl, double yaw, double ax, double az) {
        double[][] a = axes(yaw);
        return hw * Math.abs(a[0][0] * ax + a[0][1] * az) + hl * Math.abs(a[1][0] * ax + a[1][1] * az);
    }

    private static Contact boxBox(Box a, Box b, float pad) {
        double dx = b.x - a.x, dz = b.z - a.z, min = Double.MAX_VALUE, nx = 0, nz = 0;
        double[][] aa = axes(a.yaw), bb = axes(b.yaw);
        double[][] all = {aa[0], aa[1], bb[0], bb[1]};
        for (double[] ax : all) {
            double proj = dx * ax[0] + dz * ax[1];
            double depth = reach(a.halfWidth, a.halfLength, a.yaw, ax[0], ax[1]) + pad
                         + reach(b.halfWidth, b.halfLength, b.yaw, ax[0], ax[1]) - Math.abs(proj);
            if (depth <= 0) return null;
            if (depth < min) { min = depth; double s = proj >= 0 ? -1 : 1; nx = ax[0] * s; nz = ax[1] * s; }
        }
        return new Contact((float) nx, (float) nz, (float) min);
    }

    private static Contact circleBox(Box box, Box circle, float pad) {
        double[][] ax = axes(box.yaw);
        double dx = circle.x - box.x, dz = circle.z - box.z;
        double lx = dx * ax[0][0] + dz * ax[0][1], lz = dx * ax[1][0] + dz * ax[1][1];
        double cx = Math.max(-box.halfWidth, Math.min(box.halfWidth, lx));
        double cz = Math.max(-box.halfLength, Math.min(box.halfLength, lz));
        double wx = box.x + ax[0][0] * cx + ax[1][0] * cz, wz = box.z + ax[0][1] * cx + ax[1][1] * cz;
        double nx = wx - circle.x, nz = wz - circle.z, dist = Math.hypot(nx, nz), r = circle.radius + pad;
        if (dist > r) return null;
        if (dist > EPS) return new Contact((float) (nx / dist), (float) (nz / dist), (float) (r - dist));
        double ex = box.halfWidth - Math.abs(lx), ez = box.halfLength - Math.abs(lz);
        if (ex < ez) { double s = lx < 0 ? -1 : 1; return new Contact((float) (ax[0][0] * s), (float) (ax[0][1] * s), (float) (r + ex)); }
        double s = lz < 0 ? -1 : 1;
        return new Contact((float) (ax[1][0] * s), (float) (ax[1][1] * s), (float) (r + ez));
    }

    private static Contact circleCircle(Box a, Box b, float pad) {
        double dx = a.x - b.x, dz = a.z - b.z, dist = Math.hypot(dx, dz), depth = a.radius + b.radius + pad - dist;
        if (depth <= 0) return null;
        return new Contact(dist > EPS ? (float) (dx / dist) : 1f, dist > EPS ? (float) (dz / dist) : 0f, (float) depth);
    }

    public static Contact contact(Box a, Box b, float padding) {
        if (a.isCircle() && b.isCircle()) return circleCircle(a, b, padding);
        if (!a.isCircle() && b.isCircle()) return circleBox(a, b, padding);
        if (a.isCircle()) {
            Contact c = circleBox(b, a, padding);
            return c == null ? null : new Contact(-c.x(), -c.z(), c.depth());
        }
        return boxBox(a, b, padding);
    }

    /** Convenience used by the rider: player OBB (half extents w,l) against {@code b}. */
    public static Contact contact(float x, float z, float yaw, float w, float l, Box b) {
        return contact(new Box(x, z, w, l, yaw, "player"), b, 0f);
    }
    public static boolean overlap(Box a, Box b, float padding) { return contact(a, b, padding) != null; }
}
