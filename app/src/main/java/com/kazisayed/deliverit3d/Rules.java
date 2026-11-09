package com.kazisayed.deliverit3d;

import java.util.List;

/** Pure-Java port of gameRules.js (no libGDX, fully deterministic). */
public final class Rules {
    private Rules() {}

    public static final int CAPACITY = 4;
    public static final float DELIVERY_RADIUS = 8.6f;
    public static final float DELIVERY_BEST_POSSIBLE = 4.0f;
    public static final float DELIVERY_PASS_MARGIN = 0.45f;
    public static final float DELIVERY_STOP_SPEED = 1.4f;
    public static final float NEAR_MISS_COMBO_EXTEND = 2.0f;
    public static final int FEVER_AT = 3;
    public static final int TIME_CAP_HEADROOM = 35;
    public static final int CHECKPOINT_TIME = 5;
    public static final int CHECKPOINT_SCORE = 250;
    public static final int SCORE_NEAR_MISS = 60, SCORE_COIN = 5, SCORE_RED_LIGHT_OBEYED = 100, SCORE_TIME_PER_SECOND = 10;

    // ---- small math helpers used by the game loop
    public static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
        public static float wrap(float a) { return (float) Math.atan2(Math.sin(a), Math.cos(a)); }
    public static float lerpAngle(float a, float b, float t) { return a + wrap(b - a) * t; }

    // ---- delivery ratings
    public record Rating(String id, String label, float maxDist, int cash, int score, int time) {}
    public static final Rating[] RATINGS = {
        new Rating("PERFECT", "PERFECT!", 4.2f, 150, 350, 5),
        new Rating("GREAT", "GREAT!", 6.4f, 100, 200, 4),
        new Rating("GOOD", "GOOD!", Float.POSITIVE_INFINITY, 50, 100, 3)
    };
    public static Rating rate(float closest) {
        for (Rating r : RATINGS) if (closest <= r.maxDist()) return r;
        return RATINGS[RATINGS.length - 1];
    }

    /** Tracks one customer's approach (DeliveryApproach). */
    public static final class Approach {
        public record Result(boolean fire, float best, boolean left) {}
        public float best = Float.POSITIVE_INFINITY;
        public boolean active, warned;
        public void reset() { best = Float.POSITIVE_INFINITY; active = false; warned = false; }
        public Result update(float distance, float speed) {
            if (distance > DELIVERY_RADIUS) {
                boolean was = active; float b = best;
                best = Float.POSITIVE_INFINITY; active = false;
                return new Result(was, b, was);
            }
            active = true;
            best = Math.min(best, distance);
            boolean passed = distance > best + DELIVERY_PASS_MARGIN;
            boolean optimal = best <= DELIVERY_BEST_POSSIBLE;
            boolean stopped = speed < DELIVERY_STOP_SPEED;
            return new Result(passed || optimal || stopped, best, false);
        }
    }

    // ---- combo
    private static final int[] TIER_COUNT = {1, 2, 3, 5, 7};
    private static final float[] TIER_MULT = {1f, 1.25f, 2f, 2.5f, 3f};
    public static float multiplier(int count) {
        float m = 1;
        for (int i = 0; i < TIER_COUNT.length; i++) if (count >= TIER_COUNT[i]) m = TIER_MULT[i];
        return m;
    }
    public static float comboWindowFor(int level) { return (float) Math.max(11, 16 - 0.3 * (level - 1)); }

    public static final class Combo {
        public record Hit(int count, float multiplier, boolean fever, boolean feverStarted) {}
        public record Drop(String reason, int lost, boolean wasFever) {}
        public float window = 16, timeLeft;
        public int count, best;
        public void reset() { count = 0; timeLeft = 0; best = 0; }
        public boolean active() { return count > 0; }
        public boolean fever() { return count >= FEVER_AT; }
        public float multiplier() { return active() ? Rules.multiplier(count) : 1f; }
        public float fraction() { return active() ? clamp(timeLeft / window, 0, 1) : 0; }
        public Hit hit() {
            boolean was = fever();
            count++; best = Math.max(best, count); timeLeft = window;
            return new Hit(count, multiplier(), fever(), !was && fever());
        }
        public boolean extend(float seconds) {
            if (!active()) return false;
            timeLeft = Math.min(window, timeLeft + seconds); return true;
        }
        public Drop tick(float dt) {
            if (!active()) return null;
            timeLeft -= dt;
            return timeLeft <= 0 ? drop("expired") : null;
        }
        public Drop drop(String reason) {
            if (!active()) return null;
            Drop d = new Drop(reason, count, fever());
            count = 0; timeLeft = 0; return d;
        }
    }

    // ---- difficulty curves (doubles so results match JS exactly)
    public static double playerTopSpeedFor(int level) { return (14 + Math.min(level * 0.6, 8)) * 1.60; }
    public static int parcelQuotaFor(int level) { return Math.min(6, 3 + (level - 1) / 3); }
    public static int timeLimitFor(int level, double length, double topSpeed) {
        double req = Math.min(0.92, 0.80 + 0.0125 * (level - 1));
        return (int) Math.floor(length / (topSpeed * 0.9 * req) + 0.5);
    }
    public static int[] checkpointsFor(int length) {
        return new int[]{(int) Math.floor(length * 0.25 + 0.5), (int) Math.floor(length * 0.5 + 0.5), (int) Math.floor(length * 0.75 + 0.5)};
    }
    public static int checkpointCash(int level) { return 100 + level * 10; }

    public record TrafficProfile(int lanePool, float crossIntervalScale, float crossSpeedBonus) {}
    public static TrafficProfile trafficProfileFor(int level) {
        return new TrafficProfile(8 + Math.min(2, (level - 1) / 2),
            (float) Math.max(0.85, 1.3 - 0.04 * (level - 1)),
            (float) Math.min(3.5, 0.3 * (level - 1)));
    }

    // ---- scoring / rewards
    public static int starsFor(float timeRemaining, float timeLimit, int bestCombo) {
        float f = timeRemaining / Math.max(1, timeLimit);
        if (f >= 0.25f || (bestCombo >= 5 && f >= 0.12f)) return 3;
        if (f >= 0.12f) return 2;
        return 1;
    }
    public record FinishRewards(int baseReward, int timeBonus, int finishReward) {}
    public static FinishRewards finishRewards(int level, float seconds) {
        int base = 200 + level * 50, bonus = Math.max(0, (int) Math.floor(seconds)) * 5;
        return new FinishRewards(base, bonus, base + bonus);
    }

    public record Target(float x, float z, String name, String kind, float distance) {}
    public interface Spot { float x(); float z(); String label(); boolean open(); }
    public static Target navigationTarget(float px, float pz, List<? extends Spot> customers, List<? extends Spot> depots,
                                          int inventory, int delivered, int quota, float finishZ) {
        Target best = null;
        if (delivered >= quota) {
            best = new Target(0, finishZ, "Finish arch", "finish", (float) Math.hypot(0 - px, finishZ - pz));
        } else {
            List<? extends Spot> list = inventory <= 0 ? depots : customers;
            String kind = inventory <= 0 ? "depot" : "delivery";
            for (Spot s : list) {
                if (!s.open()) continue;
                float d = (float) Math.hypot(s.x() - px, s.z() - pz);
                if (best == null || d < best.distance()) best = new Target(s.x(), s.z(), s.label(), kind, d);
            }
        }
        return best;
    }

    /** mulberry32 - bit-exact with the JS generator. */
    public static final class Random {
        private int a;
        public Random(int seed) { a = seed; }
        public double next() {
            a += 0x6D2B79F5;
            int t = a;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + ((t ^ (t >>> 7)) * (t | 61));
            return (((long) (t ^ (t >>> 14))) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }
}
