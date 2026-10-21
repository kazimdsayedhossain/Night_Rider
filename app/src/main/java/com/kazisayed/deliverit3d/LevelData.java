package com.kazisayed.deliverit3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of levels.js generateFixedDistrictLevel(): the same seeded generator (mulberry32),
 * the same free-zone logic and the same difficulty curves, so a level plays identically
 * to the web build.  Pure Java (no libGDX).
 */
public final class LevelData {
    public static final int LEVEL_LENGTH = 1200, CUSTOMERS_PER_LEVEL = 8, RAILROAD_Z = 880, RAILROAD_MIN_LEVEL = 3;
    private static final float JUNCTION_MARGIN = 26, EDGE_MARGIN = 8, MIN_ZONE_LENGTH = 30, FINISH_CLEARANCE = 20,
        RAIL_CLEARANCE = 24, BRIDGE_PADDING = 8;
    private static final float[] COIN_LANES = {-2.2f, 0f, 2.2f};

    public record Hub(String id, String name, String orderType, String item, float x, float z, String side) {}
    public static final Hub[] HUBS = {
        new Hub("bakery", "Green Bakery & Pastries", "Fresh Croissants & Bagels", "Croissants", -8.5f, 10f, "left"),
        new Hub("cafe", "Brick Cafe & Roastery", "Artisanal Espresso & Sandwiches", "Espresso", 8.5f, 10f, "right"),
        new Hub("music", "Neon Music & Records", "Fragile Vinyls & Audio Gear", "Vinyl", -8.5f, 10f, "left"),
        new Hub("market", "Blue Corner Supermart", "Groceries & Provisions", "Groceries", 8.5f, 10f, "right"),
        new Hub("express", "Express Central Logistics", "Priority Parcels & Cargo", "Parcel", -8.5f, 10f, "left")
    };
    private static final String[] NAMES = {"Maya", "Lucas", "Elena", "Liam", "Sophia", "Oliver", "Aria", "Noah", "Chloe", "Ethan", "Zoe", "Mason", "Isabella", "Aiden", "Emma"};
    private static final String[] DISTRICTS = {"Commercial Avenue", "Historic Promenade", "Neon Metropolis", "Riverfront Boulevard", "Midtown Express Grid"};
    private static final int[] SHIRTS = {0xf39c12, 0xe74c3c, 0x2ecc71, 0x3498db, 0x9b59b6, 0x1abc9c, 0xf1c40f, 0xe67e22, 0x34495e, 0x00d2d3, 0xff6b81, 0x54a0ff, 0x5f27cd, 0xff9f43, 0x10ac84};

    public static class Point implements Rules.Spot {
        public float x, z; public String name; public boolean collected;
        Point(float x, float z, String n) { this.x = x; this.z = z; name = n; }
        public float x() { return x; } public float z() { return z; } public String label() { return name; } public boolean open() { return !collected; }
    }
    public static final class Customer extends Point {
        public final int reward, shirt; public final String side, item;
        public final Rules.Approach approach = new Rules.Approach();
        public float celebrateTime;
        Customer(float x, float z, String n, int r, int shirt, String side, String item) { super(x, z, n); reward = r; this.shirt = shirt; this.side = side; this.item = item; }
    }
    public static final class Depot extends Point { Depot(float x, float z, String n) { super(x, z, n); } }
    public static final class Coin extends Point { Coin(float x, float z) { super(x, z, "Street bonus"); } }
    public record Junction(float z, boolean hasEast, boolean hasWest, boolean signalized) {}
    private record Zone(int block, float start, float end) {}

    public final int number, quota, timeLimit, timeCap, inventory, length;
    public final float speed, comboWindow, finishX = 0, finishZ, finishYaw = 0, startX = 0, startZ = 0, startYaw = 0;
    public final String district, name;
    public final Hub hub;
    public final Rules.TrafficProfile traffic;
    public final boolean railroad;
    public final float trainInterval, trainSpeed;
    public final int[] checkpoints;
    public final List<Junction> junctions = new ArrayList<>();
    public final List<Customer> customers = new ArrayList<>();
    public final List<Depot> depots = new ArrayList<>();
    public final List<Coin> coins = new ArrayList<>();

    public LevelData(int level) {
        number = level; length = LEVEL_LENGTH; finishZ = LEVEL_LENGTH;
        Rules.Random rng = new Rules.Random(level * 7919 + 13);
        hub = HUBS[(level - 1) % HUBS.length];
        quota = Rules.parcelQuotaFor(level);
        double top = Rules.playerTopSpeedFor(level);
        speed = (float) top;
        timeLimit = Rules.timeLimitFor(level, length, top);
        timeCap = timeLimit + Rules.TIME_CAP_HEADROOM;
        comboWindow = Rules.comboWindowFor(level);
        checkpoints = Rules.checkpointsFor(length);
        inventory = Math.min(quota, Rules.CAPACITY);
        railroad = level >= RAILROAD_MIN_LEVEL;
        trainInterval = (float) Math.max(12, 20 - level * 0.8);
        trainSpeed = (float) (28 + Math.min(level * 1.5, 12));
        traffic = Rules.trafficProfileFor(level);
        district = DISTRICTS[(level - 1) % DISTRICTS.length];
        name = hub.name() + " · Level " + level;

        for (int block = 0; block * WorldGrid.TILE <= length + WorldGrid.TILE; block++) {
            WorldGrid.Config c = WorldGrid.intersectionConfig(0, block);
            boolean signal = c.hasEast() || c.hasWest();   // identical to levels.js (river rows carry no built signal; see TrafficSim)
            junctions.add(new Junction(block * WorldGrid.TILE, c.hasEast(), c.hasWest(), signal));
        }

        List<Zone> zones = freeZones(length, railroad ? RAILROAD_Z : Float.NaN);
        List<Zone> chosen = new ArrayList<>(zones);
        while (chosen.size() > CUSTOMERS_PER_LEVEL) {
            int s = 0;
            for (int i = 1; i < chosen.size(); i++)
                if (chosen.get(i).end - chosen.get(i).start < chosen.get(s).end - chosen.get(s).start) s = i;
            chosen.remove(s);
        }
        float[] customerZs = new float[chosen.size()];
        for (int i = 0; i < chosen.size(); i++) {
            Zone zn = chosen.get(i);
            customerZs[i] = round1(zn.start + EDGE_MARGIN + (float) rng.next() * (zn.end - zn.start - 2 * EDGE_MARGIN));
        }
        for (int idx = 0; idx < customerZs.length; idx++) {
            String side = idx % 2 == 0 ? "right" : "left";
            if (idx > 0 && rng.next() < 0.3) side = side.equals("right") ? "left" : "right";
            customers.add(new Customer(side.equals("right") ? 7.5f : -7.5f, customerZs[idx],
                NAMES[(idx + level) % NAMES.length], 60 + level * 20 + idx * 10,
                SHIRTS[(idx + level) % SHIRTS.length], side, hub.item()));
        }
        int[] depotBlocks = {2, 5};
        for (int idx = 0; idx < 2; idx++) {
            final int block = depotBlocks[idx];
            List<Zone> cand = new ArrayList<>();
            for (Zone zn : zones) if (zn.block == block) cand.add(zn);
            if (cand.isEmpty()) continue;
            cand.sort((a, b) -> Float.compare(b.end - b.start, a.end - a.start));
            float z = round1(bestSpot(cand.get(0), customerZs));
            int side = (idx + level) % 2 == 0 ? -1 : 1;
            depots.add(new Depot(side * 5.8f, z, idx == 0 ? "Depot A" : "Depot B"));
        }
        buildCoinTrails(rng);
    }

    public Junction junctionAt(int row) {
        return row >= 0 && row < junctions.size() ? junctions.get(row) : null;
    }

    private static float round1(float v) { return (float) (Math.floor(v * 10.0 + 0.5) / 10.0); }

    private static List<Zone> freeZones(int length, float railZ) {
        List<Zone> zones = new ArrayList<>();
        for (int block = 0; block * WorldGrid.TILE < length; block++) {
            float start = block * WorldGrid.TILE + JUNCTION_MARGIN;
            float end = Math.min((block + 1) * WorldGrid.TILE - JUNCTION_MARGIN, length - FINISH_CLEARANCE);
            if (end - start >= MIN_ZONE_LENGTH) zones.add(new Zone(block, start, end));
        }
        List<float[]> blocked = new ArrayList<>();
        for (int row : WorldGrid.RIVER_ROWS) { float[] s = WorldGrid.riverSpan(row); blocked.add(new float[]{s[0] - BRIDGE_PADDING, s[1] + BRIDGE_PADDING}); }
        if (!Float.isNaN(railZ)) blocked.add(new float[]{railZ - RAIL_CLEARANCE, railZ + RAIL_CLEARANCE});
        for (float[] span : blocked) {
            List<Zone> next = new ArrayList<>();
            for (Zone z : zones) {
                if (span[1] <= z.start || span[0] >= z.end) { next.add(z); continue; }
                if (span[0] - z.start > 0) next.add(new Zone(z.block, z.start, span[0]));
                if (z.end - span[1] > 0) next.add(new Zone(z.block, span[1], z.end));
            }
            zones = next;
        }
        List<Zone> out = new ArrayList<>();
        for (Zone z : zones) if (z.end - z.start >= MIN_ZONE_LENGTH) out.add(z);
        return out;
    }

    private static float bestSpot(Zone zone, float[] others) {
        float bestZ = (zone.start + zone.end) / 2, bestGap = -1;
        for (float z = zone.start + EDGE_MARGIN; z <= zone.end - EDGE_MARGIN; z += 2) {
            float gap = Float.POSITIVE_INFINITY;
            for (float o : others) gap = Math.min(gap, Math.abs(o - z));
            if (gap > bestGap) { bestGap = gap; bestZ = z; }
        }
        return bestZ;
    }

    private void buildCoinTrails(Rules.Random rng) {
        double z = 40 + rng.next() * 20;
        while (z < length - 60) {
            int trail = 3 + (int) Math.floor(rng.next() * 2);
            int lane = (int) Math.floor(rng.next() * COIN_LANES.length);
            for (int i = 0; i < trail; i++) {
                double cz = z + i * 7;
                if (cz >= length - 40) break;
                boolean onJunction = false;
                for (Junction j : junctions) if (Math.abs(j.z() - cz) < 14) onJunction = true;
                if (!onJunction) coins.add(new Coin(COIN_LANES[lane], (float) (Math.floor(cz * 10 + 0.5) / 10)));
                if (rng.next() < 0.4) lane = Math.max(0, Math.min(COIN_LANES.length - 1, lane + (rng.next() < 0.5 ? -1 : 1)));
            }
            z += trail * 7 + 80 + rng.next() * 60;
        }
    }
}
