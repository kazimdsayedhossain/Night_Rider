package com.kazisayed.deliverit3d;

import java.util.*;

/**
 * Pure-Java port of the TrafficManager in traffic.js: pooled avenue cars (lane locked at
 * roadX +/- 2.2 m), dynamic cross-street spawners, signal / headway / conflict / player
 * reaction logic, OBB collisions and near-miss detection.
 *
 * Spawn/despawn follows the audit: vehicles are only ever created beyond
 * {@code spawnDistance} (= fog far + 35 m) from the player and are recycled once they are more
 * than {@code despawnBehind} (45 m) behind the camera, so nothing pops in or out of view.
 */
public final class TrafficSim {
    public static final String[] TYPES = {"sports", "bus", "motorcycle", "police", "muscle_car", "bicycle", "scooter", "truck"};
    private static final int MAX_CROSS = 4;
    private static final float LANE = 2.2f;

    /** Set to false to disable all traffic vehicles on the road. */
    public static boolean ENABLE_VEHICLES = true;

    public record Profile(float cruise, float max, float accel, float brake, float gap, float headway, float horn, float yield) {}
    private static final Map<String, Profile> PROFILES = new HashMap<>();
    static {
        PROFILES.put("police", new Profile(17.55f, 24.3f, 9.5f, 24f, 7.5f, .9f, .78f, .55f));
        PROFILES.put("sports", new Profile(18.9f, 27.0f, 10.5f, 25f, 7.0f, .82f, .46f, .52f));
        PROFILES.put("muscle_car", new Profile(17.55f, 24.3f, 9.2f, 23f, 8.0f, .9f, .54f, .55f));
        PROFILES.put("bus", new Profile(10.8f, 14.85f, 4.2f, 13f, 12f, 1.6f, .32f, 1.1f));
        PROFILES.put("motorcycle", new Profile(16.2f, 24.3f, 9.8f, 22f, 6.0f, .8f, .58f, .65f));
        PROFILES.put("scooter", new Profile(10.8f, 16.2f, 5.8f, 16f, 6.0f, .9f, .46f, .75f));
        PROFILES.put("bicycle", new Profile(6.48f, 9.72f, 2.7f, 8.5f, 4.5f, 1.2f, .2f, 1f));
        PROFILES.put("truck", new Profile(9.45f, 12.82f, 3.6f, 12f, 14f, 1.8f, .42f, .9f));
    }
    private static float[] extents(String t) {
        return switch (t) {
            case "bus" -> new float[]{1.3f, 4.6f};
            case "truck" -> new float[]{1.25f, 4.9f};
            case "motorcycle" -> new float[]{.45f, 1.1f};
            case "scooter" -> new float[]{.45f, 1.0f};
            case "bicycle" -> new float[]{.35f, .9f};
            case "police", "muscle_car" -> new float[]{.95f, 2.4f};
            default -> new float[]{.9f, 2.3f};
        };
    }

    public static final class Vehicle {
        public String type; public Profile profile; public Object model;
        public float x, z, yaw, pitch, speed, cruise, prevSpeed;
        public float halfWidth, halfLength;
        public int dir, poolSlot;
        public boolean cross;            // true: travels along x on a cross street
        public float laneX, roadX, spawnSegmentX, laneZ;
        public boolean rewardedNearMiss, stoppedAtLight, remove;
        public float hornCooldown, stuckTime, age, brakeLight;
        public final Collision.Box box = new Collision.Box(0, 0, 1, 1, 0, "traffic");
        void sync() { box.x = x; box.z = z; box.yaw = yaw; box.halfWidth = halfWidth; box.halfLength = halfLength; }
    }
    public interface Listener { void spawned(Vehicle v); void removed(Vehicle v); void horn(Vehicle v, float intensity); }

    private static final class Spawner { String key; float segmentX, z, interval, speed, timer; int dir, spawnIndex; }
    private record Light(float x, float z, int state, int crossState) {}   // 0 green, 1 yellow, 2 red

    public final List<Vehicle> lane = new ArrayList<>(), cross = new ArrayList<>();
    public float spawnDistance = 245f;      // fog far (210) + 35 m
    public float despawnBehind = 45f + 6f;  // 45 m behind the camera (camera trails the rider by ~6 m)
    public boolean night = true;
    private final Rules.TrafficProfile profile;
    private final int length;
    private final Listener listener;
    private final Map<String, Spawner> spawners = new HashMap<>();
    private float time, laneRoadX;
    private final Random random = new Random(1337);

    public TrafficSim(LevelData level, Listener listener) {
        this.profile = level.traffic; this.length = level.length; this.listener = listener;
        if (ENABLE_VEHICLES) {
            for (int i = 0; i < profile.lanePool(); i++) {
                String type = TYPES[i % TYPES.length];
                boolean fwd = i % 2 == 0;
                float startZ = (fwd ? 220f : 280f) + (i / 2) * 75f;
                Vehicle v = make(type, fwd ? 1 : -1, i, 0.94f + ((i + 1) * 37 % 13) / 100f);
                v.cross = false; v.roadX = 0; v.laneX = fwd ? LANE : -LANE; v.x = v.laneX; v.z = startZ;
                v.yaw = fwd ? 0 : (float) Math.PI; v.sync();
                lane.add(v); listener.spawned(v);
            }
        }
    }

    private Vehicle make(String type, int dir, int slot, float speedScale) {
        Vehicle v = new Vehicle();
        v.type = type; v.profile = PROFILES.get(type); v.dir = dir; v.poolSlot = slot;
        v.cruise = Math.min(v.profile.max(), v.profile.cruise() * speedScale);
        v.speed = v.prevSpeed = v.cruise;
        float[] e = extents(type); v.halfWidth = e[0]; v.halfLength = e[1];
        return v;
    }

    // ------------------------------------------------------------ signals
    private static int phase(double t, double off, boolean crossAxis) {
        double p = (((t + off) % 13.2) + 13.2) % 13.2;
        if (crossAxis) p = (p + 6.6) % 13.2;
        return p < 5.6 ? 0 : p < 6.6 ? 1 : 2;
    }
    public static int signalState(int cx, int cz, double t, boolean crossAxis) {
        double off = Math.abs((long) cx * 19 + (long) cz * 29) % 13.2;
        return phase(t, off, crossAxis);
    }
    /** 0 green / 1 yellow / 2 red for the avenue at junction row {@code row} (x = 0 avenue). */
    public int avenueSignal(int row) { return signalState(0, row, time, false); }
    public float time() { return time; }

    private List<Light> lightsNear(float px, float pz) {
        List<Light> out = new ArrayList<>();
        int ccx = WorldGrid.tile(px), ccz = WorldGrid.tile(pz);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int cx = ccx + dx, cz = ccz + dz;
            if (WorldGrid.isRiverRow(cz)) continue;
            WorldGrid.Config c = WorldGrid.intersectionConfig(cx, cz);
            if (!(c.hasEast() || c.hasWest())) continue;
            out.add(new Light(cx * WorldGrid.TILE, cz * WorldGrid.TILE, signalState(cx, cz, time, false), signalState(cx, cz, time, true)));
        }
        return out;
    }

    // ------------------------------------------------------------ main update
    public void update(float dt, float px, float pz, float playerSpeedZ, float playerYaw) {
        if (!ENABLE_VEHICLES) {
            if (!lane.isEmpty() || !cross.isEmpty()) {
                for (Vehicle v : lane) listener.removed(v);
                for (Vehicle v : cross) listener.removed(v);
                lane.clear();
                cross.clear();
            }
            return;
        }
        time += dt;
        syncSpawners(px, pz);
        recenterLanes(px);
        List<Light> lights = lightsNear(px, pz);

        for (Spawner s : spawners.values()) {
            if (Math.abs(pz - s.z) >= 115 || Math.abs(px - s.segmentX) >= 130) continue;
            s.timer += dt;
            float laneZ = s.z + (s.dir > 0 ? -LANE : LANE);
            boolean queued = false;
            for (Vehicle c : cross) if (c.spawnSegmentX == s.segmentX && c.dir == s.dir && Math.abs(c.laneZ - laneZ) < 1) queued = true;
            if (cross.size() < MAX_CROSS && s.timer >= s.interval && !queued) { s.timer = 0; spawnCross(s, px, pz); }
        }
        for (Vehicle c : cross) {
            c.age += dt; hornDown(c, dt);
            drive(c, dt, lights, leader(c, cross), px, pz, playerSpeedZ, playerYaw);
            double d = Math.hypot(c.x - px, c.z - pz);
            if (d > 140 || (c.age > 20 && d > 65)) c.remove = true;
        }
        for (Iterator<Vehicle> it = cross.iterator(); it.hasNext();) {
            Vehicle c = it.next(); if (c.remove) { listener.removed(c); it.remove(); }
        }
        for (Vehicle c : lane) {
            hornDown(c, dt);
            drive(c, dt, lights, leader(c, lane), px, pz, playerSpeedZ, playerYaw);
            // recycle when behind camera or far ahead of view
            if (c.z < pz - despawnBehind || c.z > pz + 180f) recycle(c, px, pz);
        }
        resolve();
    }

    private static void hornDown(Vehicle v, float dt) { v.hornCooldown = Math.max(0, v.hornCooldown - dt); }

    private void syncSpawners(float px, float pz) {
        int ccx = WorldGrid.tile(px), ccz = WorldGrid.tile(pz);
        Set<String> keep = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int cx = ccx + dx, cz = ccz + dz;
            if (Math.abs(cz * WorldGrid.TILE) > length + WorldGrid.TILE) continue;
            for (int dir = -1; dir <= 1; dir += 2) {
                String key = cx + "_" + cz + "_" + dir; keep.add(key);
                if (spawners.containsKey(key)) continue;
                Spawner s = new Spawner();
                s.key = key; s.segmentX = cx * WorldGrid.TILE; s.z = cz * WorldGrid.TILE; s.dir = dir;
                s.interval = (9.5f + (Math.abs(cx * 17 + cz * 31 + dir * 7) % 5) * 1.5f) * profile.crossIntervalScale();
                s.speed = (12 + (Math.abs(cx * 23 + cz * 41) % 6) + profile.crossSpeedBonus()) * 1.35f;
                s.timer = 1 + (Math.abs(cx * 11 + cz * 19 + dir) % 4);
                s.spawnIndex = Math.abs(cx * 47 + cz * 83 + dir * 13);
                spawners.put(key, s);
            }
        }
        spawners.keySet().retainAll(keep);
    }

    private void recenterLanes(float px) {
        float roadX = WorldGrid.tile(px) * WorldGrid.TILE;
        if (roadX == laneRoadX) return;
        laneRoadX = roadX;
        for (Vehicle c : lane) { c.roadX = roadX; c.laneX = roadX + (c.dir > 0 ? LANE : -LANE); c.x = c.laneX; c.sync(); }
    }

    private void spawnCross(Spawner s, float px, float pz) {
        s.spawnIndex++;
        String type = TYPES[s.spawnIndex % TYPES.length];
        float laneZ = s.z + (s.dir > 0 ? -LANE : LANE);
        // Spawn 165m to 195m away from the avenue, well outside camera view in distant fog
        float dist = 165f + (s.spawnIndex % 4) * 10f;
        float startX = s.segmentX + (s.dir > 0 ? -dist : dist);
        if (Math.hypot(startX - px, laneZ - pz) < 140f) return;
        if (!WorldGrid.road(startX, laneZ)) return;
        float scale = Math.min(1.18f, 0.92f + s.speed / 70f) * (0.94f + (s.spawnIndex * 37 % 13) / 100f);
        Vehicle v = make(type, s.dir, s.spawnIndex, scale);
        v.cross = true; v.x = startX; v.z = laneZ; v.laneZ = laneZ; v.spawnSegmentX = s.segmentX;
        v.yaw = s.dir > 0 ? (float) Math.PI / 2 : -(float) Math.PI / 2; v.sync();
        cross.add(v); listener.spawned(v);
    }

    private void recycle(Vehicle old, float px, float pz) {
        float aveX = WorldGrid.tile(px) * WorldGrid.TILE;
        float laneX = aveX + (old.dir > 0 ? LANE : -LANE);
        String type = TYPES[random.nextInt(TYPES.length)];
        Vehicle v = make(type, old.dir, old.poolSlot, 0.94f + ((old.poolSlot + (int) time) * 37 % 13) / 100f);
        float z = safeSpawnZ(old, v, pz, laneX);
        if (!WorldGrid.road(laneX, z)) return;
        listener.removed(old);
        v.cross = false; v.roadX = aveX; v.laneX = laneX; v.x = laneX; v.z = z; v.yaw = old.dir > 0 ? 0 : (float) Math.PI; v.sync();
        lane.set(lane.indexOf(old), v);
        listener.spawned(v);
    }

    private float safeSpawnZ(Vehicle old, Vehicle fresh, float pz, float laneX) {
        float baseLead = old.dir < 0 ? 175f : 160f;
        float slot = (old.poolSlot % 6) * 16f;
        for (int a = 0; a < 12; a++) {
            float cand = pz + baseLead + slot + a * 14f;
            if (cand >= length - 25f) {
                if (old.dir < 0 && pz < length - 80f) cand = length - 30f - a * 12f;
                else cand = pz - despawnBehind - 25f - a * 14f;
            }
            if (Math.abs(cand - pz) < 140f) continue; // Never spawn closer than 140m (hidden in distant fog)
            boolean blocked = false;
            for (Vehicle o : lane) {
                if (o == old || Math.abs(o.laneX - laneX) > .35f) continue;
                if (Math.abs(o.z - cand) < fresh.halfLength + o.halfLength + fresh.profile.gap() + 16f) { blocked = true; break; }
            }
            if (!blocked && WorldGrid.road(laneX, cand)) return cand;
        }
        return pz + baseLead + slot;
    }

    // ------------------------------------------------------------ driving
    private float pos(Vehicle v) { return v.cross ? v.x : v.z; }

    private record Leader(Vehicle v, float gap) {}
    private Leader leader(Vehicle car, List<Vehicle> list) {
        Leader best = null; float nearest = Float.MAX_VALUE;
        for (Vehicle o : list) {
            if (o == car || o.dir != car.dir) continue;
            boolean same = car.cross ? Math.abs(o.laneZ - car.laneZ) < .9f : (Math.abs(o.laneX - car.laneX) < .35f && o.roadX == car.roadX);
            float gap = (pos(o) - pos(car)) * car.dir;
            if (!same || gap <= 0 || gap >= nearest) continue;
            nearest = gap; best = new Leader(o, gap - car.halfLength - o.halfLength);
        }
        return best;
    }

    private float followLimit(Vehicle c, Leader l, float target) {
        if (l == null) return target;
        float desired = c.profile.gap() + c.speed * c.profile.headway();
        if (l.gap() >= desired) return target;
        return Math.min(target, Math.max(0, l.v().speed + (l.gap() - desired) * 1.35f));
    }

    private float signalLimit(Vehicle c, List<Light> lights, float target) {
        float stopOffset = 5f + c.halfLength + .4f, nearest = Float.MAX_VALUE; Light sig = null;
        for (Light l : lights) {
            if (c.cross) {
                if (Math.abs(l.z() - c.z) >= 4.5f) continue;
                float s = (l.x() - c.x) * c.dir; if (s > 0 && s < nearest) { nearest = s; sig = l; }
            } else {
                if (Math.abs(l.x() - c.roadX) > 4.5f) continue;
                float s = (l.z() - c.z) * c.dir; if (s > 0 && s < nearest) { nearest = s; sig = l; }
            }
        }
        c.stoppedAtLight = false;
        if (sig == null) return target;
        float toLine = nearest - stopOffset;
        if (toLine <= 0 || toLine > 75) return target;
        float braking = c.speed * c.speed / (2 * c.profile.brake()) + 1.1f;
        int st = c.cross ? sig.crossState() : sig.state();
        if (st == 2 || (st == 1 && toLine > braking)) {
            target = Math.min(target, (float) Math.sqrt(Math.max(0, 2 * c.profile.brake() * Math.max(0, toLine - .25f))));
            c.stoppedAtLight = toLine < .8f || target < .6f;
        }
        return target;
    }

    private float conflictLimit(Vehicle c, float target) {
        float ix, iz, toCenter;
        if (c.cross) { ix = c.spawnSegmentX; iz = c.laneZ; toCenter = (ix - c.x) * c.dir; }
        else { ix = c.roadX; iz = Math.round(c.z / (float) WorldGrid.TILE) * WorldGrid.TILE; toCenter = (iz - c.z) * c.dir; }
        if (toCenter <= .4f || toCenter > 30) return target;
        float stopDist = toCenter - 7.5f; boolean blocked = false;
        for (List<Vehicle> list : List.of(lane, cross)) for (Vehicle o : list)
            if (o != c && Math.abs(o.x - ix) < 7.2f && Math.abs(o.z - iz) < 7.2f) {
                // If the conflicting vehicle is also stopped, yield cleanly to avoid deadlock
                if (o.speed < 0.6f && (c.stuckTime > o.stuckTime || c.poolSlot < o.poolSlot)) continue;
                blocked = true;
            }
        // Cross cars only yield to MOVING avenue cars; if avenue car is stopped at a red light (speed <= 1.2f), cross cars GO
        if (!blocked && c.cross) for (Vehicle l : lane) {
            if (l.speed > 1.2f) {
                float t = (iz - l.z) * l.dir;
                if (t > -1 && t < 24) { blocked = true; break; }
            }
        }
        if (!blocked) return target;
        if (stopDist <= .6f) return 0;
        return Math.min(target, (float) Math.sqrt(Math.max(0, 2 * c.profile.brake() * Math.max(0, stopDist - .3f))));
    }

    private float obstacleLimit(Vehicle c, float target) {
        for (List<Vehicle> list : List.of(lane, cross)) for (Vehicle o : list) {
            if (o == c) continue;
            boolean perp = c.cross != o.cross;
            float oForward = perp ? o.halfWidth : o.halfLength, oLateral = perp ? o.halfLength + .35f : o.halfWidth;
            float fwd = c.cross ? (o.x - c.x) * c.dir : (o.z - c.z) * c.dir;
            float lat = c.cross ? Math.abs(o.z - c.z) : Math.abs(o.x - c.x);
            if (lat < c.halfWidth + oLateral + .35f && fwd > 0 && fwd < 35) {
                float gap = fwd - c.halfLength - oForward, desired = c.profile.gap() + c.speed * c.profile.headway() + 4.0f;
                if (gap <= .8f) return 0;
                if (gap < desired) target = Math.min(target, (float) Math.sqrt(Math.max(0, 2 * c.profile.brake() * Math.max(0, gap - .5f))));
            }
        }
        return target;
    }

    private float reactToPlayer(Vehicle c, float px, float pz, float target) {
        float fwd = c.cross ? (px - c.x) * c.dir : (pz - c.z) * c.dir;
        float lat = c.cross ? Math.abs(pz - c.z) : Math.abs(px - c.x);
        if (fwd > 36 || fwd < -1.5f || lat > 2.2f) return target;
        if (fwd <= 2.2f) return 0;
        float brakingDist = c.speed * c.speed / (2 * c.profile.brake());
        if (fwd < brakingDist + 4.2f) target = Math.min(target, (float) Math.sqrt(2 * c.profile.brake() * Math.max(0, fwd - 4.2f)));
        if (fwd < 6.5f && c.profile.yield() > .95f) target = Math.min(target, c.cruise * .72f);
        return target;
    }

    private void honk(Vehicle c, float intensity) {
        // Disabled background ambient traffic honking to prevent continuous bell sound
    }

    private void drive(Vehicle c, float dt, List<Light> lights, Leader leader, float px, float pz, float playerSpeedZ, float playerYaw) {
        float target = c.cruise;
        target = Math.min(target, signalLimit(c, lights, c.cruise));
        target = followLimit(c, leader, target);
        target = conflictLimit(c, target);
        target = obstacleLimit(c, target);
        if (!c.cross) {
            boolean aligned = c.dir > 0 ? Math.cos(playerYaw) > .35 : Math.cos(playerYaw) < -.35;
            float gapP = (pz - c.z) * c.dir;
            if (aligned && Math.abs(px - c.laneX) < 1.35f && gapP > 0 && gapP < 45) {
                float safe = c.profile.gap() + c.speed * c.profile.headway(), gap = gapP - c.halfLength - .8f;
                target = Math.min(target, Math.max(0, playerSpeedZ + (gap - safe) * 1.2f));
            }
        }
        if (c.speed < 0.5f) c.stuckTime += dt;
        else c.stuckTime = Math.max(0, c.stuckTime - dt * 1.5f);
        if (c.stuckTime > 12.0f && c.cross) { c.remove = true; return; }
        target = reactToPlayer(c, px, pz, target);
        float rate = target < c.speed ? c.profile.brake() : c.profile.accel();
        c.speed = c.speed < target ? Math.min(target, c.speed + rate * dt) : Math.max(target, c.speed - rate * dt);
        c.brakeLight = (c.speed < c.prevSpeed - .08f || c.stoppedAtLight) ? 1 : 0; c.prevSpeed = c.speed;
        if (c.cross) {
            float nx = c.x + c.dir * c.speed * dt;
            if (WorldGrid.road(nx, c.z)) c.x = nx; else c.remove = true;
            c.pitch = 0;
        } else {
            float nz = c.z + c.dir * c.speed * dt;
            if (WorldGrid.road(c.x, nz)) { c.z = nz; c.pitch = -WorldGrid.slope(c.x, nz) * c.dir; } else c.remove = true;
        }
        c.sync();
    }

    // ------------------------------------------------------------ collisions
    private void resolve() {
        List<Vehicle> all = new ArrayList<>(lane); all.addAll(cross);
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            Vehicle a = all.get(i), b = all.get(j);
            float dx = a.x - b.x, dz = a.z - b.z;
            if (dx * dx + dz * dz > 100) continue;
            Collision.Contact c = Collision.contact(a.box, b.box, 0);
            if (c == null) continue;
            float sep = c.depth() + .02f;
            boolean la = Math.abs(Math.sin(a.yaw)) < .3, lb = Math.abs(Math.sin(b.yaw)) < .3;
            a.x += (la ? c.x() * .15f : c.x()) * sep * .5f; a.z += (la ? c.z() : c.z() * .15f) * sep * .5f;
            b.x -= (lb ? c.x() * .15f : c.x()) * sep * .5f; b.z -= (lb ? c.z() : c.z() * .15f) * sep * .5f;
            a.speed = Math.max(0, a.speed * .4f); b.speed = Math.max(0, b.speed * .4f); a.sync(); b.sync();
        }
    }

    /** First vehicle touching the rider's OBB, or null. */
    public Vehicle hit(float x, float z, float yaw) {
        Collision.Box p = new Collision.Box(x, z, .38f, 1.02f, yaw, "cycle");
        for (List<Vehicle> list : List.of(lane, cross)) for (Vehicle v : list)
            if (Math.abs(v.x - x) < 8 && Math.abs(v.z - z) < 8 && Collision.contact(p, v.box, 0) != null) return v;
        return null;
    }

    /** Graze (<0.9 m) with a vehicle that is closing on the rider; each vehicle pays out once. */
    public boolean nearMiss(float x, float z, float yaw, float vx, float vz, float minClosing) {
        Collision.Box p = new Collision.Box(x, z, .38f, 1.02f, yaw, "cycle");
        for (List<Vehicle> list : List.of(cross, lane)) for (Vehicle c : list) {
            if (c.rewardedNearMiss || Math.abs(c.x - x) > 7 || Math.abs(c.z - z) > 9) continue;
            if (Collision.overlap(p, c.box, 0)) { c.rewardedNearMiss = true; continue; }
            if (!Collision.overlap(p, c.box, .9f)) continue;
            float cvx = c.cross ? c.dir * c.speed : 0, cvz = c.cross ? 0 : c.dir * c.speed;
            if (Math.hypot(vx - cvx, vz - cvz) < minClosing) continue;
            c.rewardedNearMiss = true; return true;
        }
        return false;
    }

    /** Honk all vehicles near a position (player horn): they slow down. */
    public void startle(float x, float z) {
        for (List<Vehicle> list : List.of(lane, cross)) for (Vehicle v : list)
            if (Math.hypot(v.x - x, v.z - z) < 22) v.speed = Math.max(0, v.speed - 4.5f);
    }
}
