package com.kazisayed.deliverit3d;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.Array;
import java.util.*;

/**
 * Rendering wrapper around the pure {@link TrafficSim}: owns the model instances, the headlight
 * beam decals, the rail crossing train and forwards horn events to the audio layer.
 */
public final class TrafficSystem {
    public interface HornSink { void horn(String type, float intensity); }

    /** JS vehicle class -> GLB template id (the native build has no pedal-bike traffic model). */
    private static String templateFor(String type) {
        return switch (type) {
            case "sports" -> "nissan"; case "bus" -> "bus"; case "motorcycle" -> "guzzi"; case "police" -> "police";
            case "muscle_car" -> "barracuda"; case "scooter" -> "scooter"; case "bicycle" -> "bicycle"; case "truck" -> "truck"; default -> "cuda";
        };
    }
    private static final class View { ModelInstance model, beam, tail, head, groundRefl, rider; Vector3 riderOffset; float riderScale; AssetLibrary.Template template; boolean braking; }

    public final TrafficSim sim;
    private final AssetLibrary assets;
    private final MeshKit kit;
    private final LevelData level;
    private final Map<TrafficSim.Vehicle, View> views = new IdentityHashMap<>();
    private final ModelInstance[] train = new ModelInstance[4];
    private final Collision.Box[] trainBoxes = new Collision.Box[4];
    private float trainX = -260, trainClock;
    private boolean night = true;
    private HornSink hornSink;

    public TrafficSystem(AssetLibrary assets, MeshKit kit, LevelData level) {
        this.assets = assets; this.kit = kit; this.level = level;
        sim = new TrafficSim(level, new TrafficSim.Listener() {
            @Override public void spawned(TrafficSim.Vehicle v) { attach(v); }
            @Override public void removed(TrafficSim.Vehicle v) { views.remove(v); }
            @Override public void horn(TrafficSim.Vehicle v, float intensity) { if (hornSink != null) hornSink.horn(v.type, intensity); }
        });
        for (int i = 0; i < 4; i++) {
            train[i] = kit.box(i == 0 ? kit.steel : kit.jacket, 0, 2.2f, LevelData.RAILROAD_Z, 17, 3.5f, 2.8f);
            trainBoxes[i] = new Collision.Box(-300, LevelData.RAILROAD_Z, 8.5f, 1.4f, 0, "train");
        }
    }

    public void setHornSink(HornSink sink) { hornSink = sink; }
    public void setNight(boolean value) { night = value; sim.night = value; }

    private static boolean isTwoWheeler(String type) {
        return "motorcycle".equals(type) || "scooter".equals(type) || "bicycle".equals(type);
    }

    private void attach(TrafficSim.Vehicle v) {
        AssetLibrary.Template t = assets.vehicles.get(templateFor(v.type));
        if (t == null) return;
        View view = new View(); view.template = t; view.model = t.create(0, 0, 0, 0, 1);
        // collision box from the real model footprint (width/length of the normalised template)
        v.halfWidth = Math.max(.3f, t.width * .46f); v.halfLength = Math.max(.8f, t.length * .46f);
        boolean twoWheeler = isTwoWheeler(v.type);
        float beamWidth = twoWheeler ? 2.4f : Math.max(3.4f, t.width * 1.8f);
        float beamLength = twoWheeler ? 13.0f : 17.0f;
        view.beam = kit.decal(twoWheeler ? kit.bikeHeadBeam : kit.headBeam, 0, 0, 0, beamWidth, beamLength);
        view.groundRefl = kit.decal(kit.carGroundRefl, 0, 0, 0, t.width * (twoWheeler ? 1.6f : 1.35f), t.length * (twoWheeler ? 1.4f : 1.25f));
        if (!twoWheeler) {   // wet-road smears: red behind (brighter when braking), warm white ahead (JS vehicle streaks)
            view.tail = kit.streak(kit.tailStreak);
            view.head = kit.streak(kit.headStreak);
        }

        // Mount diverse 3D character on traffic vehicle (motorcycles, scooters, bicycles, cars)
        int seed = v.poolSlot * 31 + Math.abs((int) v.x * 17) + Math.abs((int) v.z * 13);
        Model courierModel = assets.pickTrafficCourier(seed);
        if (courierModel != null) {
            view.rider = new ModelInstance(courierModel);
            view.riderScale = 1.0f;
            if ("motorcycle".equals(v.type)) {
                view.riderOffset = new Vector3(0, 0.72f, -0.14f);
                view.riderScale = 0.98f;
            } else if ("scooter".equals(v.type)) {
                view.riderOffset = new Vector3(0, 0.68f, -0.12f);
                view.riderScale = 0.95f;
            } else if ("bicycle".equals(v.type)) {
                view.riderOffset = new Vector3(0, 0.88f, -0.14f);
                view.riderScale = 1.0f;
            } else if ("truck".equals(v.type)) {
                view.riderOffset = new Vector3(-0.48f, 1.22f, 1.45f);
                view.riderScale = 0.90f;
            } else if ("bus".equals(v.type)) {
                view.riderOffset = new Vector3(-0.55f, 0.82f, 2.70f);
                view.riderScale = 0.90f;
            } else {
                // Seated driver in sports, muscle_car, cuda, police
                view.riderOffset = new Vector3(-0.36f, 0.32f, -0.20f);
                view.riderScale = 0.88f;
            }
        }
        views.put(v, view);
    }

    /** Red/yellow/green for the main avenue (x = 0) at junction row {@code row}. */
    public boolean red(int row) {
        LevelData.Junction j = level.junctionAt(row);
        return j != null && j.signalized() && !WorldGrid.isRiverRow(row) && sim.avenueSignal(row) == 2;
    }
    public boolean trainWarning() { return level.railroad && trainClock % level.trainInterval < 8f; }
    public float trainDistance() { return Math.abs(trainX); }
    public void horn(float x, float z) { sim.startle(x, z); }

    public void update(float dt, float px, float pz, float speed, float yaw) {
        sim.update(dt, px, pz, speed * (float) Math.cos(yaw), yaw);
        for (Map.Entry<TrafficSim.Vehicle, View> e : views.entrySet()) {
            TrafficSim.Vehicle v = e.getKey(); View w = e.getValue();
            float y = WorldGrid.elevation(v.x, v.z);
            w.template.placePitched(w.model, v.x, y, v.z, v.yaw * MathUtils.radiansToDegrees, v.pitch);
            if (w.rider != null && w.riderOffset != null) {
                w.rider.transform.idt()
                    .translate(v.x, y, v.z)
                    .rotate(Vector3.Y, v.yaw * MathUtils.radiansToDegrees)
                    .rotate(Vector3.X, -v.pitch * MathUtils.radiansToDegrees)
                    .translate(w.riderOffset)
                    .scale(w.riderScale, w.riderScale, w.riderScale);
                w.rider.calculateTransforms();
            }
            boolean twoWheeler = isTwoWheeler(v.type);
            float beamWidth = twoWheeler ? 2.4f : Math.max(3.4f, w.template.width * 1.8f);
            float beamLength = twoWheeler ? 13.0f : 17.0f;
            float ahead = v.halfLength + (twoWheeler ? 5.8f : 8.2f);
            w.beam.transform.setToTranslation(v.x + (float) Math.sin(v.yaw) * ahead, y + .036f, v.z + (float) Math.cos(v.yaw) * ahead)
                .rotate(Vector3.Y, v.yaw * MathUtils.radiansToDegrees).scale(beamWidth, 1f, beamLength);
            if (w.groundRefl != null) {
                w.groundRefl.transform.setToTranslation(v.x, y + .037f, v.z)
                    .rotate(Vector3.Y, v.yaw * MathUtils.radiansToDegrees)
                    .scale(w.template.width * (twoWheeler ? 1.6f : 1.35f), 1f, w.template.length * (twoWheeler ? 1.4f : 1.25f));
            }
            if (w.tail != null) {
                float sw = w.template.width * 1.1f, sin = (float) Math.sin(v.yaw), cos = (float) Math.cos(v.yaw), deg = v.yaw * MathUtils.radiansToDegrees;
                boolean brake = v.brakeLight > .5f;
                if (brake != w.braking) { w.braking = brake; MeshKit.useMaterial(w.tail, brake ? kit.brakeStreak : kit.tailStreak); }
                w.tail.transform.setToTranslation(v.x - sin * v.halfLength, y + .04f, v.z - cos * v.halfLength).rotate(Vector3.Y, deg + 180f).scale(sw, 1f, brake ? 5.6f : 4.4f);
                w.head.transform.setToTranslation(v.x + sin * v.halfLength, y + .04f, v.z + cos * v.halfLength).rotate(Vector3.Y, deg).scale(sw, 1f, 6.5f);
            }
        }
        if (level.railroad) {
            trainClock += dt;
            trainX = -260f + (trainClock % level.trainInterval) * level.trainSpeed;
            for (int i = 0; i < train.length; i++) {
                trainBoxes[i].x = trainX - i * 19f;
                train[i].transform.setToTranslation(trainBoxes[i].x, 2.2f, LevelData.RAILROAD_Z).scale(17, 3.5f, 2.8f);
            }
        }
    }

    public Collision.Contact collision(float x, float z, float yaw) {
        TrafficSim.Vehicle v = sim.hit(x, z, yaw);
        if (v == null) return null;
        return Collision.contact(x, z, yaw, .37f, .95f, v.box);
    }
    public boolean nearMiss(float x, float z, float yaw, float speed) {
        return sim.nearMiss(x, z, yaw, (float) Math.sin(yaw) * speed, (float) Math.cos(yaw) * speed, 6f);
    }

    public boolean hitsTrain(float x, float z, float yaw) {
        if (!level.railroad || Math.abs(z - LevelData.RAILROAD_Z) > 3.5f) return false;
        for (Collision.Box b : trainBoxes) if (Collision.contact(x, z, yaw, .37f, .95f, b) != null) return true;
        return false;
    }

    public void collect(Camera camera, float x, float z, Array<RenderableProvider> out) {
        for (Map.Entry<TrafficSim.Vehicle, View> e : views.entrySet()) {
            TrafficSim.Vehicle v = e.getKey();
            if (Vector2.dst2(x, z, v.x, v.z) < 190 * 190 && camera.frustum.sphereInFrustum(v.x, 2, v.z, v.halfLength + 8)) {
                View w = e.getValue();
                if (w.groundRefl != null) out.add(w.groundRefl);
                out.add(w.model);
                if (w.rider != null) out.add(w.rider);
                if (night && kit.night() > .02f) {
                    /* w already defined */
                    out.add(w.beam);
                    if (w.tail != null) { out.add(w.tail); out.add(w.head); }
                }
            }
        }
        if (level.railroad && Math.abs(z - LevelData.RAILROAD_Z) < 220)
            for (int i = 0; i < train.length; i++)
                if (camera.frustum.sphereInFrustum(trainBoxes[i].x, 2.2f, LevelData.RAILROAD_Z, 12)) out.add(train[i]);
    }
}
