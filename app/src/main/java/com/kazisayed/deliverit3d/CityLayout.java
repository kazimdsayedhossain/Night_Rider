package com.kazisayed.deliverit3d;

import java.util.*;

/**
 * Procedural, libGDX-free city geometry for one 160 m tile of the lattice described by
 * {@link WorldGrid}.  Fixes the map-building defects from the audit:
 *
 * <ul>
 *  <li>Road connectivity - asphalt strips end exactly on the tile border (+/-80 m) and the
 *      neighbour tile mirrors the same cross street (hash is shared), so roads never stop short.</li>
 *  <li>Sidewalks/curbs are split around every cross street (interval exclusion) and meet
 *      the cross street sidewalk through a rounded corner (R = 3 m) with a 0.12 m curb step.</li>
 *  <li>Sidewalk -> verge -> lawn is a ramp, so there is no hard polygon seam against the grass.</li>
 *  <li>Every building lot gets a paved walkway from the sidewalk to its door.</li>
 *  <li>Road, sidewalk and curb follow {@link WorldGrid#elevation} (the riders/cars use the same).</li>
 *  <li>Mountains are only produced in the distant backdrop, never near a street.</li>
 * </ul>
 */
public final class CityLayout {
    private CityLayout() {}

    public enum Layer { GRASS, VERGE, ASPHALT, SIDEWALK, CURB, PAINT, WATER, STONE, GRAVEL, WOOD, STEEL, POOL, PUDDLE }

    public static final float CURB_FACE = 5.0f, CURB_W = 0.25f, WALK_OUT = 8.5f, RAMP_OUT = 11.0f,
        STEP = 0.12f, CORNER_R = 3.0f, HALF = WorldGrid.TILE / 2f, WATER_Y = -0.45f, LOT_FRONT = 17.5f,
        LOT_DEPTH = 16f, LOT_ALONG = 18f;

    public static final class MeshBuf {
        public float[] v = new float[1024]; public int n;           // 8 floats per vertex
        public short[] idx = new short[1536]; public int ni;
        short add(float x, float y, float z, float nx, float ny, float nz, float u, float w) {
            if (n * 8 + 8 > v.length) v = Arrays.copyOf(v, v.length * 2);
            int o = n * 8; v[o] = x; v[o + 1] = y; v[o + 2] = z; v[o + 3] = nx; v[o + 4] = ny; v[o + 5] = nz; v[o + 6] = u; v[o + 7] = w;
            return (short) n++;
        }
        void tri(short a, short b, short c) {
            if (ni + 3 > idx.length) idx = Arrays.copyOf(idx, idx.length * 2);
            idx[ni++] = a; idx[ni++] = b; idx[ni++] = c;
        }
        public int vertexCount() { return n; }
    }
    public record Prop(String kind, float x, float y, float z, float yaw, float pitch, float roll, float scale, int variant) {
        public Prop(String kind, float x, float y, float z, float yaw, float scale, int variant) {
            this(kind, x, y, z, yaw, 0f, 0f, scale, variant);
        }
    }
    public record Lot(float x, float z, float yaw, float halfAlong, float halfDepth, int variant, float doorX) {}

    public static final class Tile {
        public final int cx, cz;
        public final EnumMap<Layer, MeshBuf> mesh = new EnumMap<>(Layer.class);
        public final List<Prop> lamps = new ArrayList<>(), signals = new ArrayList<>(), trees = new ArrayList<>(), props = new ArrayList<>();
        public final List<Lot> lots = new ArrayList<>();
        public final List<Collision.Box> colliders = new ArrayList<>();
        Tile(int cx, int cz) { this.cx = cx; this.cz = cz; }
        MeshBuf buf(Layer l) { return mesh.computeIfAbsent(l, k -> new MeshBuf()); }
    }

    private static float uvScale(Layer l) {
        return switch (l) { case ASPHALT -> 4f; case SIDEWALK -> 3.5f / 6.0f; case GRASS -> 6f; case VERGE -> 3f; case CURB -> 1.5f; case STONE -> 2f; case WATER -> 8.5f; case GRAVEL -> 1.5f; default -> 1f; };
    }

    /** Builder for one tile. All coordinates are world coordinates. */
    private static final class B {
        final Tile t; final float ox, oz; final int cx, cz;
        B(int cx, int cz) { this.cx = cx; this.cz = cz; ox = cx * WorldGrid.TILE; oz = cz * WorldGrid.TILE; t = new Tile(cx, cz); }

        short vtx(MeshBuf m, Layer l, float[] p, float nx, float ny, float nz) {
            float s = uvScale(l);
            float u = Math.abs(ny) > .5f ? p[0] / s : (p[0] + p[2]) / s, w = Math.abs(ny) > .5f ? p[2] / s : p[1] / s;
            return m.add(p[0], p[1], p[2], nx, ny, nz, u, w);
        }
        /** Mapped decal quad with (0,0) to (1,1) UVs for round light pools and organic puddles (CCW facing UP) */
        void decalQuad(Layer l, float x, float y, float z, float rw, float rl) {
            MeshBuf m = t.buf(l);
            short i0 = m.add(x - rw, y, z - rl, 0, 1, 0, 0, 0);
            short i1 = m.add(x + rw, y, z - rl, 0, 1, 0, 1, 0);
            short i2 = m.add(x + rw, y, z + rl, 0, 1, 0, 1, 1);
            short i3 = m.add(x - rw, y, z + rl, 0, 1, 0, 0, 1);
            m.tri(i0, i2, i1); m.tri(i0, i3, i2);
        }
        /** Quad given in any winding; n is the desired outward normal. */
        void quad(Layer l, float[] a, float[] b, float[] c, float[] d, float nx, float ny, float nz) {
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = d[0] - a[0], vy = d[1] - a[1], vz = d[2] - a[2];
            float cxn = uy * vz - uz * vy, cyn = uz * vx - ux * vz, czn = ux * vy - uy * vx;
            if (cxn * nx + cyn * ny + czn * nz < 0) { float[] tmp = b; b = d; d = tmp; }
            MeshBuf m = t.buf(l);
            short i0 = vtx(m, l, a, nx, ny, nz), i1 = vtx(m, l, b, nx, ny, nz), i2 = vtx(m, l, c, nx, ny, nz), i3 = vtx(m, l, d, nx, ny, nz);
            m.tri(i0, i1, i2); m.tri(i0, i2, i3);
        }
        void poly(Layer l, List<float[]> pts) {   // convex / star-shaped fan, facing up
            MeshBuf m = t.buf(l);
            short[] ids = new short[pts.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = vtx(m, l, pts.get(i), 0, 1, 0);
            for (int i = 1; i + 1 < ids.length; i++) {
                float[] a = pts.get(0), b = pts.get(i), c = pts.get(i + 1);
                float cr = (b[0] - a[0]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[0] - a[0]);
                // winding seen from +Y must be CCW: cross.y = (c-a) x (b-a) ... pick the order that faces up
                if (((b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2])) > 0) m.tri(ids[0], ids[i], ids[i + 1]);
                else m.tri(ids[0], ids[i + 1], ids[i]);
            }
        }
        static float[] p(float x, float y, float z) { return new float[]{x, y, z}; }
        void top(Layer l, float x0, float x1, float z0, float z1, float y) {
            quad(l, p(x0, y, z0), p(x0, y, z1), p(x1, y, z1), p(x1, y, z0), 0, 1, 0);
        }
        /** Vertical wall between two ground points (x0,z0)-(x1,z1) from yLo to yHi, normal (nx,nz). */
        void wall(Layer l, float x0, float z0, float x1, float z1, float yLo0, float yHi0, float yLo1, float yHi1, float nx, float nz) {
            quad(l, p(x0, yLo0, z0), p(x1, yLo1, z1), p(x1, yHi1, z1), p(x0, yHi0, z0), nx, 0, nz);
        }
        float elev(float z) { return WorldGrid.elevation(ox, z); }   // avenue elevation (depends on z + avenue only)
        List<Float> zsteps(float za, float zb) {
            List<Float> out = new ArrayList<>(); int n = Math.max(1, (int) Math.ceil((zb - za) / 8f));
            for (int i = 0; i <= n; i++) out.add(za + (zb - za) * i / n); return out;
        }
        /** Horizontal strip along the avenue following elevation: x in [xa,xb] (world), top at elev+dy. */
        void avenueTop(Layer l, float xa, float xb, float za, float zb, float dy) {
            List<Float> zs = zsteps(za, zb);
            for (int i = 0; i + 1 < zs.size(); i++) {
                float z0 = zs.get(i), z1 = zs.get(i + 1);
                quad(l, p(xa, elev(z0) + dy, z0), p(xa, elev(z1) + dy, z1), p(xb, elev(z1) + dy, z1), p(xb, elev(z0) + dy, z0), 0, 1, 0);
            }
        }
        /** Strip ramping from (elev+dy0) at x=xa down to yEnd at x=xb. */
        void avenueRamp(Layer l, float xa, float xb, float za, float zb, float dy0, float yEnd) {
            List<Float> zs = zsteps(za, zb);
            for (int i = 0; i + 1 < zs.size(); i++) {
                float z0 = zs.get(i), z1 = zs.get(i + 1);
                quad(l, p(xa, elev(z0) + dy0, z0), p(xa, elev(z1) + dy0, z1), p(xb, yEnd, z1), p(xb, yEnd, z0), 0, 1, 0);
            }
        }
        void avenueWall(Layer l, float x, float za, float zb, float dyLo, float dyHi, float nx) {
            List<Float> zs = zsteps(za, zb);
            for (int i = 0; i + 1 < zs.size(); i++) {
                float z0 = zs.get(i), z1 = zs.get(i + 1);
                wall(l, x, z0, x, z1, elev(z0) + dyLo, elev(z0) + dyHi, elev(z1) + dyLo, elev(z1) + dyHi, nx, 0);
            }
        }
    }

    private static int hash(int a, int b, int c) {
        long h = a * 73856093L ^ b * 19349663L ^ c * 83492791L; h ^= (h >>> 13); h *= 0x5bd1e995L; h ^= (h >>> 15);
        return (int) (Math.abs(h) % Integer.MAX_VALUE);
    }

    // ------------------------------------------------------------------ tile
    public static Tile build(int cx, int cz, float railZ) {
        B b = new B(cx, cz);
        WorldGrid.Config cfg = WorldGrid.builtConfig(cx, cz);
        if (WorldGrid.isRiverRow(cz)) { river(b); }
        else {
            terrain(b, cfg);
            for (int s = -1; s <= 1; s += 2) avenueSide(b, s, s > 0 ? cfg.hasEast() : cfg.hasWest());
            asphalt(b, cfg);
            for (int s = -1; s <= 1; s += 2) if (s > 0 ? cfg.hasEast() : cfg.hasWest()) branch(b, s);
            paint(b, cfg);
            lots(b, cfg);
            furniture(b, cfg);
        }
        if (!Float.isNaN(railZ) && railZ >= b.oz - HALF && railZ < b.oz + HALF) rail(b, railZ);
        return b.t;
    }

    private static void terrain(B b, WorldGrid.Config cfg) {
        float[] xs = {b.ox - HALF, b.ox - RAMP_OUT, b.ox + RAMP_OUT, b.ox + HALF}, zs = {b.oz - HALF, b.oz - RAMP_OUT, b.oz + RAMP_OUT, b.oz + HALF};
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            if (i == 1) continue;
            if (j == 1 && ((i == 0 && cfg.hasWest()) || (i == 2 && cfg.hasEast()))) continue;
            b.top(Layer.GRASS, xs[i], xs[i + 1], zs[j], zs[j + 1], 0f);
        }
    }

    /** One side (s = +1 east / -1 west) of the avenue: curb step, sidewalk, ramp to the lawn, corners. */
    private static void avenueSide(B b, int s, boolean branch) {
        float xa = b.ox + s * CURB_FACE, xc = b.ox + s * (CURB_FACE + CURB_W), xo = b.ox + s * WALK_OUT, xr = b.ox + s * RAMP_OUT;
        float[][] spans = branch ? new float[][]{{b.oz - HALF, b.oz - WALK_OUT}, {b.oz + WALK_OUT, b.oz + HALF}} : new float[][]{{b.oz - HALF, b.oz + HALF}};
        for (float[] sp : spans) {
            b.avenueWall(Layer.CURB, xa, sp[0], sp[1], 0f, STEP, -s);
            b.avenueTop(Layer.CURB, Math.min(xa, xc), Math.max(xa, xc), sp[0], sp[1], STEP);
            b.avenueTop(Layer.SIDEWALK, Math.min(xc, xo), Math.max(xc, xo), sp[0], sp[1], STEP);
            // ramp from the sidewalk top down to the lawn: no hard seam
            float[] rz = branch ? new float[]{sp[0] == b.oz - HALF ? sp[0] : b.oz + RAMP_OUT, sp[0] == b.oz - HALF ? b.oz - RAMP_OUT : sp[1]} : sp;
            b.avenueRamp(Layer.VERGE, Math.min(xo, xr), Math.max(xo, xr), rz[0], rz[1], STEP, 0f);
            if (s > 0) { /* ramp quads face up regardless of x ordering (quad() corrects the winding) */ }
        }
        if (!branch) return;
        for (int t = -1; t <= 1; t += 2) corner(b, s, t);
    }

    private static void corner(B b, int s, int t) {
        // arc centre (8,8) in local mirrored coordinates, from (5,8) to (8,5)
        float c = CURB_FACE + CORNER_R;
        List<float[]> arc = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            double phi = Math.PI + (Math.PI / 2) * i / 8.0;
            float lx = c + (float) Math.cos(phi) * CORNER_R, lz = c + (float) Math.sin(phi) * CORNER_R;   // local, both >= 5
            arc.add(new float[]{b.ox + s * lx, 0, b.oz + t * lz});
        }
        // sidewalk corner polygon (top at STEP)
        List<float[]> poly = new ArrayList<>();
        for (float[] p : arc) poly.add(B.p(p[0], STEP, p[2]));
        poly.add(B.p(b.ox + s * WALK_OUT, STEP, b.oz + t * CURB_FACE));
        poly.add(B.p(b.ox + s * WALK_OUT, STEP, b.oz + t * WALK_OUT));
        poly.add(B.p(b.ox + s * CURB_FACE, STEP, b.oz + t * WALK_OUT));
        b.poly(Layer.SIDEWALK, poly);
        // curb faces + curb top stripe along the arc
        for (int i = 0; i < 8; i++) {
            float[] p0 = arc.get(i), p1 = arc.get(i + 1);
            float mx = (p0[0] + p1[0]) / 2 - (b.ox + s * c), mz = (p0[2] + p1[2]) / 2 - (b.oz + t * c);
            float len = (float) Math.hypot(mx, mz); float nx = mx / len, nz = mz / len;   // outward from sidewalk toward the road
            b.wall(Layer.CURB, p0[0], p0[2], p1[0], p1[2], 0, STEP, 0, STEP, nx, nz);
            float ix = -nx * CURB_W, iz = -nz * CURB_W;
            b.quad(Layer.CURB, B.p(p0[0], STEP + .002f, p0[2]), B.p(p1[0], STEP + .002f, p1[2]), B.p(p1[0] + ix, STEP + .002f, p1[2] + iz), B.p(p0[0] + ix, STEP + .002f, p0[2] + iz), 0, 1, 0);
        }
        // straight curb faces where the corner meets the street edges (x=5, z in [8,8.5] and z=5, x in [8,8.5])
        b.wall(Layer.CURB, b.ox + s * CURB_FACE, b.oz + t * CORNER_R * 0 + t * (CURB_FACE + CORNER_R), b.ox + s * CURB_FACE, b.oz + t * WALK_OUT, 0, STEP, 0, STEP, -s, 0);
        b.wall(Layer.CURB, b.ox + s * (CURB_FACE + CORNER_R), b.oz + t * CURB_FACE, b.ox + s * WALK_OUT, b.oz + t * CURB_FACE, 0, STEP, 0, STEP, 0, -t);
        // ramp pieces beside the corner
        b.top(Layer.VERGE, Math.min(b.ox + s * WALK_OUT, b.ox + s * RAMP_OUT), Math.max(b.ox + s * WALK_OUT, b.ox + s * RAMP_OUT),
            Math.min(b.oz + t * WALK_OUT, b.oz + t * RAMP_OUT), Math.max(b.oz + t * WALK_OUT, b.oz + t * RAMP_OUT), 0.02f);
        // road fillet (asphalt) fan: (5,5) then the arc
        List<float[]> fan = new ArrayList<>();
        fan.add(B.p(b.ox + s * CURB_FACE, 0f, b.oz + t * CURB_FACE));
        for (float[] p : arc) fan.add(B.p(p[0], 0f, p[2]));
        b.poly(Layer.ASPHALT, fan);
    }

    private static void asphalt(B b, WorldGrid.Config cfg) {
        b.avenueTop(Layer.ASPHALT, b.ox - CURB_FACE, b.ox + CURB_FACE, b.oz - HALF, b.oz + HALF, 0f);

        // Water puddles / glossy rain-water spots on the road surface and sidewalks
        if (!WorldGrid.isRiverRow(b.cz)) {
            boolean junction = cfg.hasEast() || cfg.hasWest();
            for (float zl = -66f; zl <= 66f; zl += 20f) {
                if (junction && Math.abs(zl) < 14f) continue;
                int h = hash(b.cx, b.cz, (int) (zl * 7));
                float pz = b.oz + zl + (h % 5 - 2);
                // Alternate between left wheel rut, right wheel rut, and center dip
                int lanePick = Math.abs(h) % 3;
                float px = b.ox + (lanePick == 0 ? -2.2f : lanePick == 1 ? 2.2f : 0.0f);
                float rw = 1.4f + (Math.abs(h * 13) % 10) / 14f; // 1.4m to 2.1m half width
                float rl = 2.4f + (Math.abs(h * 31) % 15) / 8f;  // 2.4m to 4.2m half length
                float ey = b.elev(pz) + 0.038f;
                b.decalQuad(Layer.PUDDLE, px, ey, pz, rw, rl);
            }
            // Sidewalk water spots and wet puddle reflections
            for (float zl = -64f; zl <= 64f; zl += 18f) {
                if (junction && Math.abs(zl) < 14f) continue;
                int hW = hash(b.cx, b.cz, (int) (zl * 11 + 3));
                int hE = hash(b.cx, b.cz, (int) (zl * 13 + 7));
                // West sidewalk (5.0m..8.5m from road center, center 6.6m)
                float pzW = b.oz + zl + (hW % 3);
                float pxW = b.ox - 6.6f + (hW % 3) * 0.22f;
                float rwW = 0.9f + (Math.abs(hW) % 5) * 0.12f;
                float rlW = 1.4f + (Math.abs(hW) % 6) * 0.18f;
                float eyW = b.elev(pzW) + STEP + 0.024f;
                b.decalQuad(Layer.PUDDLE, pxW, eyW, pzW, rwW, rlW);

                // East sidewalk
                float pzE = b.oz + zl + (hE % 3);
                float pxE = b.ox + 6.6f + (hE % 3) * 0.22f;
                float rwE = 0.9f + (Math.abs(hE) % 5) * 0.12f;
                float rlE = 1.4f + (Math.abs(hE) % 6) * 0.18f;
                float eyE = b.elev(pzE) + STEP + 0.024f;
                b.decalQuad(Layer.PUDDLE, pxE, eyE, pzE, rwE, rlE);
            }
            if (junction) {
                // Cross street puddles near cross-walks
                if (cfg.hasEast()) {
                    float px = b.ox + 26f, pz = b.oz - 1.8f;
                    b.decalQuad(Layer.PUDDLE, px, 0.038f, pz, 1.8f, 2.8f);
                }
                if (cfg.hasWest()) {
                    float px = b.ox - 26f, pz = b.oz + 1.8f;
                    b.decalQuad(Layer.PUDDLE, px, 0.038f, pz, 1.8f, 2.8f);
                }
            }
        }
    }

    /** Cross street towards side s (+1 east, -1 west): asphalt, both sidewalks, curb, ramps. */
    private static void branch(B b, int s) {
        float xa = b.ox + s * CURB_FACE, xe = b.ox + s * HALF;
        b.top(Layer.ASPHALT, Math.min(xa, xe), Math.max(xa, xe), b.oz - CURB_FACE, b.oz + CURB_FACE, 0f);
        float xs0 = b.ox + s * WALK_OUT;
        for (int t = -1; t <= 1; t += 2) {
            float zc = b.oz + t * CURB_FACE, zk = b.oz + t * (CURB_FACE + CURB_W), zo = b.oz + t * WALK_OUT, zr = b.oz + t * RAMP_OUT;
            float x0 = Math.min(xs0, xe), x1 = Math.max(xs0, xe);
            b.wall(Layer.CURB, x0, zc, x1, zc, 0, STEP, 0, STEP, 0, -t);
            b.top(Layer.CURB, x0, x1, Math.min(zc, zk), Math.max(zc, zk), STEP);
            b.top(Layer.SIDEWALK, x0, x1, Math.min(zk, zo), Math.max(zk, zo), STEP);
            b.quad(Layer.VERGE, B.p(x0, STEP, zo), B.p(x1, STEP, zo), B.p(x1, 0f, zr), B.p(x0, 0f, zr), 0, 1, 0);
        }
    }

    private static void paint(B b, WorldGrid.Config cfg) {
        float y = .012f;
        boolean junction = cfg.hasEast() || cfg.hasWest();
        // avenue centre dashes (12 m period, follow elevation) and solid edge lines
        for (float z = b.oz - HALF + 2; z < b.oz + HALF - 6; z += 12) {
            if (junction && Math.abs(z + 2.25f - b.oz) < 11) continue;
            float z1 = z + 4.5f, zm = (z + z1) / 2;
            b.quad(Layer.PAINT, B.p(b.ox - .08f, b.elev(z) + y, z), B.p(b.ox - .08f, b.elev(zm) + y, zm), B.p(b.ox + .08f, b.elev(zm) + y, zm), B.p(b.ox + .08f, b.elev(z) + y, z), 0, 1, 0);
            b.quad(Layer.PAINT, B.p(b.ox - .08f, b.elev(zm) + y, zm), B.p(b.ox - .08f, b.elev(z1) + y, z1), B.p(b.ox + .08f, b.elev(z1) + y, z1), B.p(b.ox + .08f, b.elev(zm) + y, zm), 0, 1, 0);
        }
        float[][] edgeSpans = junction ? new float[][]{{b.oz - HALF, b.oz - 9}, {b.oz + 9, b.oz + HALF}} : new float[][]{{b.oz - HALF, b.oz + HALF}};
        for (float[] sp : edgeSpans) for (int s = -1; s <= 1; s += 2) {
            float xm = b.ox + s * 4.5f;
            List<Float> zs = b.zsteps(sp[0], sp[1]);
            for (int i = 0; i + 1 < zs.size(); i++) {
                float z0 = zs.get(i), z1 = zs.get(i + 1);
                b.quad(Layer.PAINT, B.p(xm - .06f, b.elev(z0) + y, z0), B.p(xm - .06f, b.elev(z1) + y, z1), B.p(xm + .06f, b.elev(z1) + y, z1), B.p(xm + .06f, b.elev(z0) + y, z0), 0, 1, 0);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            if (!(s > 0 ? cfg.hasEast() : cfg.hasWest())) continue;
            // cross-street dashes
            for (float x = 14; x < HALF - 6; x += 12) {
                float xa = b.ox + s * x, xb = b.ox + s * (x + 4.5f);
                b.top(Layer.PAINT, Math.min(xa, xb), Math.max(xa, xb), b.oz - .08f, b.oz + .08f, y);
            }
            // zebra crossing across the cross street, just outside the avenue
            for (int k = -3; k <= 3; k++) {
                float zz = b.oz + k * 1.3f;
                float xa = b.ox + s * 6.2f, xb = b.ox + s * 8.0f;
                b.top(Layer.PAINT, Math.min(xa, xb), Math.max(xa, xb), zz - .28f, zz + .28f, y + .002f);
            }
        }
        if (junction) for (int t = -1; t <= 1; t += 2) for (int k = -3; k <= 3; k++) {   // zebra across the avenue
            float xx = b.ox + k * 1.25f, zc = b.oz + t * 6.8f;
            b.top(Layer.PAINT, xx - .27f, xx + .27f, zc - 1.2f, zc + 1.2f, y + .002f);
        }
    }

    private static void lots(B b, WorldGrid.Config cfg) {
        if (WorldGrid.isParkTile(b.cx, b.cz)) return;
        for (int s = -1; s <= 1; s += 2) {
            boolean branch = s > 0 ? cfg.hasEast() : cfg.hasWest();
            for (int i = 0; i < 7; i++) {
                float zl = -66 + 22 * i;
                if (branch && Math.abs(zl) - LOT_ALONG / 2 < RAMP_OUT + 2) continue;
                int h = hash(b.cx * 2 + (s > 0 ? 1 : 0), b.cz, i);
                if (h % 11 == 0) continue;                                    // occasional empty lot (garden)
                float zc = b.oz + zl, xDoor = b.ox + s * LOT_FRONT, xc = b.ox + s * (LOT_FRONT + LOT_DEPTH / 2);
                float yaw = s > 0 ? -90f : 90f;
                b.t.lots.add(new Lot(xc, zc, yaw, LOT_ALONG / 2, LOT_DEPTH / 2, h, xDoor));
                b.t.colliders.add(new Collision.Box(xc, zc, LOT_DEPTH / 2, LOT_ALONG / 2, 0, "building"));
                // paved walkway from the sidewalk edge to the door (ramp across the verge, then flat)
                float yS = b.elev(zc) + STEP, w = 1.2f;
                float xo = b.ox + s * WALK_OUT, xr = b.ox + s * RAMP_OUT;
                b.quad(Layer.SIDEWALK, B.p(xo, yS, zc - w), B.p(xo, yS, zc + w), B.p(xr, .03f, zc + w), B.p(xr, .03f, zc - w), 0, 1, 0);
                b.top(Layer.SIDEWALK, Math.min(xr, xDoor), Math.max(xr, xDoor), zc - w, zc + w, .03f);
                float xp = b.ox + s * (LOT_FRONT - 2.2f);
                b.top(Layer.SIDEWALK, Math.min(xp, xDoor), Math.max(xp, xDoor), zc - 2.4f, zc + 2.4f, .035f);

                // Add manicured bushes and shrubs in building front gardens (verge 13m+, well behind sidewalk)
                b.t.props.add(new Prop("shrub", b.ox + s * 13.0f, 0f, zc - 3.8f, (h + 37) % 360, 0.90f + (h % 20) / 100f, h));
                b.t.props.add(new Prop("shrub", b.ox + s * 13.0f, 0f, zc + 3.8f, (h + 83) % 360, 0.95f + (h % 25) / 100f, h + 1));
                b.t.props.add(new Prop("shrub", b.ox + s * 15.0f, 0f, zc - 6.5f, (h + 120) % 360, 0.85f + (h % 20) / 100f, h + 2));
                b.t.props.add(new Prop("shrub", b.ox + s * 15.0f, 0f, zc + 6.5f, (h + 190) % 360, 0.85f + (h % 25) / 100f, h + 3));
                b.t.props.add(new Prop("shrub", b.ox + s * (LOT_FRONT - 1.0f), 0f, zc - 4.5f, (h + 60) % 360, 0.90f, h + 4));
                b.t.props.add(new Prop("shrub", b.ox + s * (LOT_FRONT - 1.0f), 0f, zc + 4.5f, (h + 140) % 360, 0.90f, h + 5));
            }
        }
    }

    private static boolean nearWalkway(B b, int s, float z) {
        for (Lot l : b.t.lots) if (Math.signum(l.x() - b.ox) == s && Math.abs(l.z() - z) < LOT_ALONG / 2 + .5f) return true;
        return false;
    }

    private static void furniture(B b, WorldGrid.Config cfg) {
        boolean park = WorldGrid.isParkTile(b.cx, b.cz);
        for (int s = -1; s <= 1; s += 2) {
            boolean branch = s > 0 ? cfg.hasEast() : cfg.hasWest();
            for (float zl : new float[]{-60, -20, 20, 60}) {
                if (branch && Math.abs(zl) < 14) continue;
                float z = b.oz + zl, x = b.ox + s * 7.6f, y = b.elev(z) + STEP;
                // Focus lamp heads directly inward over the road (East=180 deg, West=0°)
                b.t.lamps.add(new Prop("street-lamp", x, y, z, s > 0 ? 180f : 0f, 1f, 0));
            }
            if (branch) for (float al : new float[]{32, 64}) for (int t = -1; t <= 1; t += 2) {
                float x = b.ox + s * al, z = b.oz + t * 7.4f;
                // Focus cross-street lamps inward over the cross-street road
                b.t.lamps.add(new Prop("street-lamp", x, STEP, z, t > 0 ? 90f : -90f, 1f, 0));
            }
            // street trees placed securely on the verge (13.5m), away from sidewalk (ends at 8.5m) and road (5.0m)
            for (int i = 0; i < 7; i++) {
                float zl = -72 + 22 * i + (hash(b.cx, b.cz, i * 3 + s) % 5 - 2);
                if (Math.abs(zl) < 22 || WorldGrid.isRiverRow(b.cz)) continue;
                float z = b.oz + zl;
                if (nearWalkway(b, s, z)) continue;
                b.t.trees.add(new Prop("tree", b.ox + s * 13.5f, 0f, z, hash(b.cx, i, s) % 360, 0.85f + (hash(i, b.cz, s) % 15) / 100f, hash(b.cx + i, b.cz, s)));
            }
        }
        if (park) {
            // 1. South-West: Grand Lake (spacious landscaped lake with gazebo)
            buildWaterBody(b, b.ox - 48f, b.oz - 38f, 22f, 16f, "gazebo", 12);

            // 2. North-West: Tranquil Pond (ornamental pond with fountain)
            buildWaterBody(b, b.ox - 46f, b.oz + 38f, 17f, 14f, "fountain", 10);

            // 3. South-East: Mirror Lake (visible directly from the avenue)
            buildWaterBody(b, b.ox + 48f, b.oz - 38f, 22f, 16f, "gazebo", 12);

            // 4. North-East: Lotus Pond (visible directly from the avenue)
            buildWaterBody(b, b.ox + 46f, b.oz + 38f, 17f, 14f, "fountain", 10);

            // 50 clumps of bushes and wildflowers throughout park grounds and along the shores
            for (int k = 0; k < 50; k++) {
                int s = (k % 2 == 0) ? -1 : 1;
                float gx = b.ox + s * (20f + (hash(b.cx, k, b.cz) % 54));
                float gz = b.oz + (hash(k, b.cz, b.cx) % 136 - 68);
                if (Math.abs(gz - b.oz) < 10f) continue; // Keep clear of cross-street road
                if (Math.abs(Math.abs(gx - b.ox) - 48f) < 23f && Math.abs(Math.abs(gz - b.oz) - 38f) < 17f) continue; // Not inside water
                b.t.props.add(new Prop("shrub", gx, 0f, gz, hash(k, 31, b.cx) % 360, 0.85f + (hash(k, 5, b.cz) % 35) / 100f, k));
            }

            // 44 natural rock outcroppings and boulder formations distributed across the park grounds
            for (int r = 0; r < 44; r++) {
                int s = (r % 2 == 0) ? -1 : 1;
                float rx = b.ox + s * (19f + (hash(b.cx, r * 13, b.cz) % 55));
                float rz = b.oz + (hash(r * 17, b.cz, b.cx) % 140 - 70);
                if (Math.abs(rz - b.oz) < 11f) continue; // Clear of cross street
                // Not inside the 4 ponds
                if (Math.abs(Math.abs(rx - b.ox) - 48f) < 23f && Math.abs(Math.abs(rz - b.oz) - 38f) < 17f) continue;

                int rTier = hash(r, 41, b.cx) % 4;
                float scale = (rTier == 0) ? 0.35f + (hash(r, 7, b.cz) % 15) / 100f
                            : (rTier == 1) ? 0.52f + (hash(r, 11, b.cz) % 18) / 100f
                            : (rTier == 2) ? 0.72f + (hash(r, 13, b.cz) % 18) / 100f
                            : 0.92f + (hash(r, 17, b.cz) % 18) / 100f; // Max scale ~1.10m, no oversized rocks
                float yaw = hash(r, b.cx, b.cz) % 360;
                float pitch = (hash(r, 29, b.cx) % 38) - 19f;
                float roll = (hash(r, 31, b.cz) % 38) - 19f;
                float ry = 0f;
                b.t.props.add(new Prop("rock", rx, ry, rz, yaw, pitch, roll, scale, r));

                // Small decorative satellite stone beside tier 3 accent rocks
                if (rTier == 3) {
                    float satX = rx + ((hash(r, 5, 2) % 20) - 10) * 0.15f;
                    float satZ = rz + ((hash(r, 7, 3) % 20) - 10) * 0.15f;
                    float satScale = 0.32f + (hash(r, 9, 4) % 18) / 100f;
                    b.t.props.add(new Prop("rock", satX, 0f, satZ, (yaw + 75) % 360, -pitch * 0.7f, roll * 0.8f, satScale, r + 100));
                }
            }

            // 92 diverse shade trees across all park quadrants and groves
            for (int i = 0; i < 92; i++) {
                int s = (i % 2 == 0) ? -1 : 1;
                float x = b.ox + s * (17f + (hash(b.cx, b.cz, i * 7) % 58));
                float z = b.oz + (hash(i * 11, b.cz, b.cx) % 140 - 70);
                if (Math.abs(z - b.oz) < 11.5f) continue; // Keep clear of cross street
                if (Math.abs(Math.abs(x - b.ox) - 48f) < 24f && Math.abs(Math.abs(z - b.oz) - 38f) < 18f) continue;
                float treeScale = 0.75f + (hash(i, b.cz, 7) % 75) / 100f; // 0.75m to 1.50m
                b.t.trees.add(new Prop("tree", x, 0f, z, hash(i, b.cx, b.cz) % 360, treeScale, i));
            }
        }
        // traffic lights on the corner verge of every cross street, focused over approach lanes
        if (cfg.hasEast() || cfg.hasWest()) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    float sigX = b.ox + sx * 8.8f;
                    float sigZ = b.oz + sz * 8.8f;
                    // Focus mast arm inward over the approach lane facing oncoming traffic
                    float yaw;
                    if (sx > 0 && sz < 0) yaw = 180f;       // South-East: Avenue Northbound approach
                    else if (sx < 0 && sz > 0) yaw = 0f;    // North-West: Avenue Southbound approach
                    else if (sx < 0 && sz < 0) yaw = -90f;  // South-West: Cross-street Eastbound approach
                    else yaw = 90f;                         // North-East: Cross-street Westbound approach
                    b.t.signals.add(new Prop("traffic-light", sigX, STEP, sigZ, yaw, 1f, 0));
                }
            }
        }
    }

    private static void buildWaterBody(B b, float cx, float cz, float rw, float rl, String decor, int stoneCount) {
        b.top(Layer.WATER, cx - rw, cx + rw, cz - rl, cz + rl, WATER_Y);
        // Grassy banks sloping gracefully into the water
        b.quad(Layer.VERGE, B.p(cx - rw - 3.5f, .02f, cz - rl - 3.5f), B.p(cx + rw + 3.5f, .02f, cz - rl - 3.5f),
                            B.p(cx + rw, WATER_Y, cz - rl), B.p(cx - rw, WATER_Y, cz - rl), 0, 1, 0);
        b.quad(Layer.VERGE, B.p(cx - rw - 3.5f, .02f, cz + rl + 3.5f), B.p(cx - rw, WATER_Y, cz + rl),
                            B.p(cx + rw, WATER_Y, cz + rl), B.p(cx + rw + 3.5f, .02f, cz + rl + 3.5f), 0, 1, 0);
        b.quad(Layer.VERGE, B.p(cx - rw - 3.5f, .02f, cz - rl - 3.5f), B.p(cx - rw, WATER_Y, cz - rl),
                            B.p(cx - rw, WATER_Y, cz + rl), B.p(cx - rw - 3.5f, .02f, cz + rl + 3.5f), 0, 1, 0);
        b.quad(Layer.VERGE, B.p(cx + rw, WATER_Y, cz - rl), B.p(cx + rw + 3.5f, .02f, cz - rl - 3.5f),
                            B.p(cx + rw + 3.5f, .02f, cz + rl + 3.5f), B.p(cx + rw, WATER_Y, cz + rl), 0, 1, 0);

        // Natural shoreline boulder clusters and emergent water rocks around the pond
        int rocksToPlace = Math.max(26, stoneCount * 2);
        for (int k = 0; k < rocksToPlace; k++) {
            double angle = k * (Math.PI * 2.0 / rocksToPlace) + ((hash(k, (int) Math.abs(cx), 53) % 20) - 10) * 0.02;
            int tier = hash(k, (int) Math.abs(cz), 11) % 4;
            float scale, radOffset, ry;
            float pitch = (hash(k, 17, (int) Math.abs(cx)) % 36) - 18f;
            float roll = (hash(k, 29, (int) Math.abs(cz)) % 36) - 18f;
            float yaw = hash(k, (int) Math.abs(cx), 7) % 360;

            if (tier == 0) {
                // Tier 0: Small river pebbles right at the water line
                scale = 0.30f + (hash(k, 13, 9) % 15) / 100f;
                radOffset = -0.5f + (hash(k, 2, 8) % 15) / 10f; // slightly emergent from water
                ry = WATER_Y + 0.08f;
            } else if (tier == 1 || tier == 2) {
                // Tier 1 & 2: Medium shoreline stones along grassy bank
                scale = 0.52f + (hash(k, 13, 9) % 20) / 100f;
                radOffset = 1.2f + (hash(k, 4, 6) % 25) / 10f;
                ry = 0f;
            } else {
                // Tier 3: Scenic accent pond stones (capped to ~1.0m max, no giant monoliths)
                scale = 0.82f + (hash(k, 13, 9) % 22) / 100f;
                radOffset = 2.0f + (hash(k, 7, 3) % 20) / 10f;
                ry = 0f;
            }
            float rx = cx + (float) Math.cos(angle) * (rw + radOffset);
            float rz = cz + (float) Math.sin(angle) * (rl + radOffset);
            b.t.props.add(new Prop("rock", rx, ry, rz, yaw, pitch, roll, scale, k));
        }

        // Benches overlooking the water
        float benchX = cx + (cx > b.ox ? -rw - 4.5f : rw + 4.5f);
        b.t.props.add(new Prop("bench", benchX, 0f, cz, cx > b.ox ? -90f : 90f, 1f, 0));

        // Gazebo or fountain feature
        if ("gazebo".equals(decor)) {
            float gzX = cx - (cx > b.ox ? -rw * 0.5f : rw * 0.5f);
            float gzZ = cz - (cz > b.oz ? -rl * 0.5f : rl * 0.5f);
            b.t.props.add(new Prop("gazebo", gzX, 0f, gzZ, 45f, 1.15f, 0));
        } else if ("fountain".equals(decor)) {
            b.t.props.add(new Prop("fountain", cx, 0f, cz, 0f, 1.1f, 0));
        }
    }

    private static void river(B b) {
        float x0 = b.ox - HALF, x1 = b.ox + HALF, z0 = b.oz - HALF, z1 = b.oz + HALF, y = WATER_Y;
        b.top(Layer.WATER, x0, x1, z0, z1, y);
        // earth banks where the river meets the neighbouring land (facing into the river)
        b.wall(Layer.VERGE, x0, z0, b.ox - WALK_OUT, z0, y, 0, y, 0, 0, 1);
        b.wall(Layer.VERGE, b.ox + WALK_OUT, z0, x1, z0, y, 0, y, 0, 0, 1);
        b.wall(Layer.VERGE, x0, z1, b.ox - WALK_OUT, z1, y, 0, y, 0, 0, -1);
        b.wall(Layer.VERGE, b.ox + WALK_OUT, z1, x1, z1, y, 0, y, 0, 0, -1);
        // bridge deck: asphalt, raised sidewalks with the 0.12 m curb step, stone parapets, slab sides
        b.top(Layer.ASPHALT, b.ox - CURB_FACE, b.ox + CURB_FACE, z0, z1, 0f);

        // Bridge road lane markings: center dashed lines and outer boundary lines
        float py = 0.025f;
        for (float z = z0 + 2; z < z1 - 6; z += 12) {
            b.top(Layer.PAINT, b.ox - .08f, b.ox + .08f, z, z + 4.5f, py);
        }
        for (int s = -1; s <= 1; s += 2) {
            float xm = b.ox + s * 4.5f;
            b.top(Layer.PAINT, xm - .06f, xm + .06f, z0, z1, py);
        }
        for (int s = -1; s <= 1; s += 2) {
            float xa = b.ox + s * CURB_FACE, xo = b.ox + s * WALK_OUT, xc = b.ox + s * (CURB_FACE + CURB_W);
            b.wall(Layer.CURB, xa, z0, xa, z1, 0, STEP, 0, STEP, -s, 0);
            b.top(Layer.CURB, Math.min(xa, xc), Math.max(xa, xc), z0, z1, STEP);
            b.top(Layer.SIDEWALK, Math.min(xc, xo), Math.max(xc, xo), z0, z1, STEP);
            b.wall(Layer.STONE, xo, z0, xo, z1, -.6f, 0.9f, -.6f, 0.9f, s, 0);          // parapet / slab side
            b.top(Layer.STONE, Math.min(xo - s * .25f, xo), Math.max(xo - s * .25f, xo), z0, z1, 0.9f);
            b.wall(Layer.STONE, xo - s * .25f, z0, xo - s * .25f, z1, STEP, 0.9f, STEP, 0.9f, -s, 0);
            b.t.colliders.add(new Collision.Box(xo - s * .12f, b.oz, .15f, HALF, 0, "parapet"));
        }
        b.quad(Layer.STONE, B.p(b.ox - WALK_OUT, -.6f, z0), B.p(b.ox + WALK_OUT, -.6f, z0), B.p(b.ox + WALK_OUT, -.6f, z1), B.p(b.ox - WALK_OUT, -.6f, z1), 0, -1, 0);
        for (float z : new float[]{-60, -20, 20, 60}) for (int s = -1; s <= 1; s += 2)
            b.t.lamps.add(new Prop("street-lamp", b.ox + s * 7.6f, STEP, b.oz + z, s > 0 ? 180f : 0f, 1f, 0));

        // Massive stone bridge support piers descending into the river water
        for (float pz : new float[]{b.oz - 48f, b.oz - 16f, b.oz + 16f, b.oz + 48f}) {
            float pw = 2.4f, pl = 3.6f;
            b.quad(Layer.STONE, B.p(b.ox - pw, -2.5f, pz - pl), B.p(b.ox + pw, -2.5f, pz - pl),
                                B.p(b.ox + pw, -0.6f, pz - pl), B.p(b.ox - pw, -0.6f, pz - pl), 0, 0, -1);
            b.quad(Layer.STONE, B.p(b.ox - pw, -0.6f, pz + pl), B.p(b.ox + pw, -0.6f, pz + pl),
                                B.p(b.ox + pw, -2.5f, pz + pl), B.p(b.ox - pw, -2.5f, pz + pl), 0, 0, 1);
            b.quad(Layer.STONE, B.p(b.ox - pw, -2.5f, pz - pl), B.p(b.ox - pw, -0.6f, pz - pl),
                                B.p(b.ox - pw, -0.6f, pz + pl), B.p(b.ox - pw, -2.5f, pz + pl), -1, 0, 0);
            b.quad(Layer.STONE, B.p(b.ox + pw, -2.5f, pz + pl), B.p(b.ox + pw, -0.6f, pz + pl),
                                B.p(b.ox + pw, -0.6f, pz - pl), B.p(b.ox + pw, -2.5f, pz - pl), 1, 0, 0);
        }
    }

    private static void rail(B b, float z) {
        float x0 = b.ox - HALF, x1 = b.ox + HALF;
        b.top(Layer.GRAVEL, x0, x1, z - 1.9f, z + 1.9f, .03f);
        for (float r : new float[]{-.75f, .75f}) b.top(Layer.STEEL, x0, x1, z + r - .06f, z + r + .06f, .13f);
        for (float x = x0 + 1; x < x1; x += 2) b.top(Layer.WOOD, x - .11f, x + .11f, z - 1.4f, z + 1.4f, .08f);
        // road crossing planks over the avenue
        b.top(Layer.WOOD, b.ox - CURB_FACE, b.ox + CURB_FACE, z - 1.6f, z + 1.6f, b.elev(z) + .045f);
    }

    // ------------------------------------------------------------------ backdrop
    /** Distant mountain placements around {@code (ax,az)}. Never near a street, river or the x=0 avenue corridor. */
    public static List<Prop> mountains(float ax, float az) {
        List<Prop> out = new ArrayList<>();
        int i0 = Math.round(ax / WorldGrid.TILE), j0 = Math.round(az / WorldGrid.TILE);
        for (int i = i0 - 9; i <= i0 + 9; i++) for (int j = j0 - 9; j <= j0 + 9; j++) {
            float x = i * WorldGrid.TILE + WorldGrid.TILE / 2f, z = j * WorldGrid.TILE + WorldGrid.TILE / 2f;   // block centre, 80 m from every avenue
            if (Math.abs(x) < 400) continue;                                  // |X| > 80 m rule, with a wide margin around the playable avenue
            if (hash(i, j, 77) % 3 != 0) continue;
            if (Math.hypot(x - ax, z - az) < 520) continue;                   // always beyond the fog
            if (WorldGrid.isRiverRow(WorldGrid.tile(z - 60)) || WorldGrid.isRiverRow(WorldGrid.tile(z + 60)) || WorldGrid.isRiverRow(WorldGrid.tile(z))) continue;
            if (Math.abs(x - WorldGrid.RIVER_X) < WorldGrid.RIVER_HALF_WIDTH + 70) continue;
            out.add(new Prop("mountain", x, -2f, z, hash(i, j, 5) % 360, 0.8f + (hash(i, j, 9) % 40) / 100f, 0));
        }
        return out;
    }
}
