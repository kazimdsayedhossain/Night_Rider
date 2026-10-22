package com.kazisayed.deliverit3d;

/**
 * Pure-Java port of grid.js plus the road/river/elevation queries that city.js exposes
 * (isRoadPosition, constrainToRoad, getRoadElevation, getRoadSlope, getBiomeAt).
 *
 * The city is an infinite lattice: a north-south avenue runs through x = cx * TILE for every
 * cx, cross streets (x = cx*TILE .. (cx+1)*TILE at z = cz*TILE) exist according to
 * {@link #intersectionConfig(int, int)}, and tile rows -4, 4 and 12 are rivers crossed by a
 * bridge on every avenue.  No libGDX types are used so the logic is unit-testable on a JVM.
 */
public final class WorldGrid {
    public static final int TILE = 160;
    public static final float ROAD_WIDTH = 10f;
    /** Drivable half width of every street (matches CityBuilder.isRoadPosition). */
    public static final float ROAD_HALF_WIDTH = 4.75f;
    /** Half width of the asphalt that is actually meshed (10 m wide street). */
    public static final float ASPHALT_HALF = ROAD_WIDTH * 0.5f;
    public static final int[] RIVER_ROWS = {-4, 4, 12};
    public static final float RIVER_X = -960f;
    public static final float RIVER_HALF_WIDTH = 70f;
    public static final float RIVER_HALF_LENGTH = 6400f;
    /** Half width of the river bridge deck (ROAD_WIDTH + 7 m in city.js). */
    public static final float BRIDGE_HALF = 8.5f;

    private WorldGrid() {}

    public record Config(boolean hasEast, boolean hasWest, String type) {}

    public static boolean isRiverRow(int cz) {
        for (int r : RIVER_ROWS) if (r == cz) return true;
        return false;
    }

    /** z-range [from, to] covered by the river/bridge tile of row {@code cz}. */
    public static float[] riverSpan(int cz) {
        return new float[]{cz * TILE - TILE / 2f, cz * TILE + TILE / 2f};
    }

    public static int tile(float v) {
        return (int) Math.floor(v / TILE + 0.5);   // identical to JS Math.round (half up)
    }

    private static boolean hasEastSegment(int x, int z) {
        long hash = Math.abs((long) x * 7919 + (long) z * 6271 + (long) x * z * 31);
        return (hash % 10) < 5;
    }

    /** Port of getIntersectionConfig (grid.js). */
    public static Config intersectionConfig(int cx, int cz) {
        boolean boulevard = ((cz % 3) + 3) % 3 == 0;
        if (boulevard) return new Config(true, true, "cross");
        boolean east = hasEastSegment(cx, cz), west = hasEastSegment(cx - 1, cz);
        String type = east && west ? "cross" : east ? "t_east" : west ? "t_west" : "straight";
        return new Config(east, west, type);
    }

    /** Cross streets that are actually built: river tiles carry only the bridge. All other cross-streets connect continuously across tiles. */
    public static Config builtConfig(int cx, int cz) {
        if (isRiverRow(cz)) return new Config(false, false, "bridge");
        return intersectionConfig(cx, cz);
    }

    /** True when {@code (x, z)} is on drivable asphalt (avenue, bridge deck or cross street). */
    public static boolean road(float x, float z) {
        int cx = tile(x), cz = tile(z);
        float dx = x - cx * TILE, dz = z - cz * TILE;
        if (Math.abs(dx) <= ROAD_HALF_WIDTH) return true;
        if (Math.abs(dz) <= ROAD_HALF_WIDTH) {
            Config c = builtConfig(cx, cz);
            if (dx > 0 && c.hasEast()) return true;
            if (dx < 0 && c.hasWest()) return true;
        }
        return false;
    }

    public static boolean isRiverTile(int cx, int cz) {
        boolean ew = isRiverRow(cz);
        boolean ns = cx == Math.round(RIVER_X / TILE) && Math.abs((long) cz * TILE) < RIVER_HALF_LENGTH;
        return ew || ns;
    }

    public static boolean isParkTile(int cx, int cz) {
        if (isRiverTile(cx, cz)) return false;
        long hash = Math.abs((long) cx * 73 + (long) cz * 131 + (long) cx * cz * 17);
        boolean closeToStart = (cx == -1 || cx == 1) && cz >= -1 && cz <= 4;
        boolean central = cx >= 4 && cx <= 7 && cz >= -10 && cz <= 10;
        return closeToStart || central || hash % 23 == 8;
    }

    public static boolean isLakeTile(int cx, int cz) {
        return (cx == 12 || cx == 13) && (cz == 8 || cz == 9);
    }

    /** Port of getBiomeAt. */
    public static String biome(int cx, int cz) {
        float x = cx * TILE, z = cz * TILE;
        if (isRiverTile(cx, cz)) return "river";
        if (isLakeTile(cx, cz)) return "lake";
        if (isParkTile(cx, cz)) return "park";
        if (Math.abs(x) >= 3200 || Math.abs(z) >= 7000) return "rural";
        if (x > 1280 && z > 640 && z < 5200) return "industrial";
        if (Math.abs(x) < 640 && Math.abs(z) < 2800) return "downtown";
        if (Math.abs(x) < 1760 && Math.abs(z) < 5200) return "suburbs";
        return "rural";
    }

    /** Open water (river rows away from the bridge deck, the north-south river away from any road). */
    public static boolean water(float x, float z) {
        int cx = tile(x), cz = tile(z);
        float dx = x - cx * TILE;
        if (isRiverRow(cz)) return Math.abs(dx) > BRIDGE_HALF;
        return cx == Math.round(RIVER_X / TILE) && Math.abs(x - RIVER_X) < RIVER_HALF_WIDTH
            && Math.abs((long) cz * TILE) < RIVER_HALF_LENGTH && !road(x, z);
    }

    // ---------------------------------------------------------------- elevation

    private static double amplitude(int cx, int blockRow) {
        long seed = Math.abs((long) cx * 37 + (long) blockRow * 59);
        int pattern = (int) (seed % 4);
        return pattern == 0 ? 0.06 : pattern == 1 ? -0.05 : pattern == 2 ? 0.05 : 0.0;
    }

    private static boolean flatBlock(float x, int blockRow) {
        if (isRiverRow(blockRow) || isRiverRow(blockRow + 1)) return true;   // bridges stay level
        return Math.abs(x - RIVER_X) < RIVER_HALF_WIDTH + 15;
    }

    /** Port of getRoadElevation: level at junctions, gentle crest/dip between them. */
    public static float elevation(float x, float z) {
        int cx = tile(x);
        int blockRow = (int) Math.floor(z / (double) TILE);
        if (flatBlock(x, blockRow)) return 0f;
        double u = ((z % TILE) + TILE) % TILE;
        if (u < 18 || u > 142) return 0f;
        double t = (u - 18) / 124.0;
        double wave = (1 - Math.cos(t * Math.PI * 2)) * 0.5;
        return (float) (wave * amplitude(cx, blockRow));
    }

    /** Port of getRoadSlope (d elevation / d z). */
    public static float slope(float x, float z) {
        int cx = tile(x);
        int blockRow = (int) Math.floor(z / (double) TILE);
        if (flatBlock(x, blockRow)) return 0f;
        double u = ((z % TILE) + TILE) % TILE;
        if (u < 18 || u > 142) return 0f;
        double t = (u - 18) / 124.0;
        return (float) (amplitude(cx, blockRow) * (Math.PI / 124) * Math.sin(t * Math.PI * 2));
    }

    /**
     * Port of constrainToRoad: keeps a rider/vehicle on the street it is on.  Returns true when
     * the position had to be moved (the caller bleeds speed).  {@code p} is {x, z}.
     */
    public static boolean constrainToRoad(float[] p) {
        int cx = tile(p[0]), cz = tile(p[1]);
        float ox = cx * TILE, oz = cz * TILE, dx = p[0] - ox, dz = p[1] - oz;
        Config c = builtConfig(cx, cz);
        boolean east = dx > ROAD_HALF_WIDTH && c.hasEast(), west = dx < -ROAD_HALF_WIDTH && c.hasWest();
        float nx = p[0], nz = p[1];
        if (east || west) nz = oz + Math.max(-ROAD_HALF_WIDTH, Math.min(ROAD_HALF_WIDTH, dz));
        else nx = ox + Math.max(-ROAD_HALF_WIDTH, Math.min(ROAD_HALF_WIDTH, dx));
        boolean moved = nx != p[0] || nz != p[1];
        p[0] = nx; p[1] = nz;
        return moved;
    }

    /** Distance along the given axis to the centre line of the nearest avenue. */
    public static float nearestAvenueX(float x) {
        return tile(x) * TILE;
    }

    public static String district(float x, float z) {
        return biome(tile(x), tile(z));
    }
}
