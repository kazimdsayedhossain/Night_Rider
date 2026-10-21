package com.kazisayed.deliverit3d;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import java.util.*;

/**
 * Streams {@link CityLayout} tiles (160 m, the same lattice as the JS city) around the rider.
 * All geometry decisions live in the pure CityLayout class; this class only turns its mesh
 * buffers into LibGDX models, places GLB buildings/props on the lots and culls by distance.
 */
public final class WorldScene implements AutoCloseable {
    private static final int RADIUS = 2;                       // tiles kept around the rider
    private final MeshKit kit;
    private final AssetLibrary assets;
    private final Map<Long, Cell> cells = new HashMap<>();
    private final List<ModelInstance> mountains = new ArrayList<>();
    private float railZ = Float.NaN, mountainAx = Float.NaN, mountainAz = Float.NaN;
    private final Material facade;
    private final Deque<long[]> pending = new ArrayDeque<>();
    private int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;

    public static final class Object3D { ModelInstance near, far; float x, y, z, radius; }
    /** Per-lamp light effects: ground pool, camera-facing halo and wet-road reflection streak (JS createStreetLamp). */
    private static final class LampFx {
        float hx, hz, bulbY, groundY; ModelInstance pool, halo, streak;
    }
    private final Vector3 tmpV = new Vector3();
    private static final class Cell {
        final List<LampFx> lamps = new ArrayList<>();
        int cx, cz; Model geometry; ModelInstance ground; BoundingBox bounds;
        final List<Object3D> objects = new ArrayList<>(); final List<Collision.Box> colliders = new ArrayList<>();
    }

    public WorldScene(MeshKit kit, AssetLibrary assets) {
        this.kit = kit; this.assets = assets;
        this.facade = kit.textured("facade-ceramic-v1.png", 0.9f);
    }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

    /** Level start/restart: the rail crossing only exists on railroad levels, so rebuild tiles. */
    public void configure(float railroadZ) {
        if (Float.compare(railZ, railroadZ) == 0) return;
        railZ = railroadZ; clear();
    }
    private void clear() {
        for (Cell c : cells.values()) if (c.geometry != null) c.geometry.dispose();
        cells.clear(); pending.clear(); lastCx = lastCz = Integer.MIN_VALUE;
    }

    public void stream(float x, float z) {
        int cx = WorldGrid.tile(x), cz = WorldGrid.tile(z);
        if (cx != lastCx || cz != lastCz) {
            lastCx = cx; lastCz = cz;
            List<long[]> want = new ArrayList<>();
            for (int a = cx - RADIUS; a <= cx + RADIUS; a++) for (int b = cz - RADIUS; b <= cz + RADIUS; b++)
                if (!cells.containsKey(key(a, b))) want.add(new long[]{a, b, (long) (Math.pow(a - cx, 2) + Math.pow(b - cz, 2))});
            want.sort(Comparator.comparingLong(w -> w[2]));
            pending.clear(); pending.addAll(want);
            for (Iterator<Cell> it = cells.values().iterator(); it.hasNext();) {
                Cell c = it.next();
                if (Math.abs(c.cx - cx) > RADIUS + 1 || Math.abs(c.cz - cz) > RADIUS + 1) { if (c.geometry != null) c.geometry.dispose(); it.remove(); }
            }
        }
        int budget = cells.size() < 9 ? 3 : 1;
        while (budget-- > 0 && !pending.isEmpty()) {
            long[] p = pending.poll();
            if (!cells.containsKey(key((int) p[0], (int) p[1]))) cells.put(key((int) p[0], (int) p[1]), build((int) p[0], (int) p[1]));
        }
    }

    public boolean ready() { return cells.size() >= 9; }

    private Material material(CityLayout.Layer l) {
        return switch (l) {
            case GRASS -> kit.grass; case VERGE -> kit.earth; case ASPHALT -> kit.asphalt; case SIDEWALK -> kit.sidewalk;
            case CURB, STONE -> kit.stone; case PAINT -> kit.ivory; case WATER -> kit.water; case GRAVEL -> kit.gravel;
            case WOOD -> kit.wood; case STEEL -> kit.steel; case POOL -> kit.lightPool; case PUDDLE -> kit.puddle;
        };
    }

    private Cell build(int cx, int cz) {
        Cell c = new Cell(); c.cx = cx; c.cz = cz;
        float ox = cx * WorldGrid.TILE, oz = cz * WorldGrid.TILE, h = WorldGrid.TILE / 2f;
        c.bounds = new BoundingBox(new Vector3(ox - h, -3, oz - h), new Vector3(ox + h, 70, oz + h));
        CityLayout.Tile tile = CityLayout.build(cx, cz, railZ);

        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        for (Map.Entry<CityLayout.Layer, CityLayout.MeshBuf> e : tile.mesh.entrySet()) {
            CityLayout.MeshBuf m = e.getValue();
            if (m.n == 0) continue;
            MeshPartBuilder part = builder.part(e.getKey().name(), GL20.GL_TRIANGLES, MeshKit.ATTR, material(e.getKey()));
            part.ensureCapacity(m.n, m.ni);
            MeshPartBuilder.VertexInfo vi = new MeshPartBuilder.VertexInfo();
            short[] map = new short[m.n];
            for (int i = 0; i < m.n; i++) {
                int o = i * 8;
                vi.setPos(m.v[o], m.v[o + 1], m.v[o + 2]).setNor(m.v[o + 3], m.v[o + 4], m.v[o + 5]).setUV(m.v[o + 6], m.v[o + 7]);
                map[i] = part.vertex(vi);
            }
            for (int i = 0; i < m.ni; i += 3) part.triangle(map[m.idx[i]], map[m.idx[i + 1]], map[m.idx[i + 2]]);
        }
        c.geometry = builder.end();
        c.ground = new ModelInstance(c.geometry);
        MeshKit.shareMaterials(c.ground, c.geometry);   // wetness / puddle changes must reach every tile
        c.colliders.addAll(tile.colliders);

        List<StaticCityBlueprint.BuildingLot> staticLots = StaticCityBlueprint.buildingsInTile(cx, cz);
        if (!staticLots.isEmpty()) {
            for (StaticCityBlueprint.BuildingLot b : staticLots) {
                AssetLibrary.Template t = assets.getBuilding(b.templateId);
                if (t == null) t = assets.pickBuilding(0);
                if (t == null) continue;
                float fit = b.scale;
                float hw = t != null ? t.width * 0.5f : b.halfWidth;
                float hl = t != null ? t.length * 0.5f : b.halfLength;
                float s = Math.signum(b.x);
                if (s == 0) s = 1;
                // Ensure building front edge is strictly behind sidewalk (8.5m) and verge ramp (11.0m)
                float posX = b.x;
                if (Math.abs(posX) - hw < 13.0f) {
                    posX = s * (13.0f + hw);
                }
                float actualHw = Math.max(b.halfWidth, hw);
                float actualHl = Math.max(b.halfLength, hl);

                Object3D o = new Object3D();
                o.near = t.create(posX, b.y, b.z, b.yaw * MathUtils.radiansToDegrees, fit);
                AssetLibrary.varyWindows(o.near, (int)(posX * 31 + b.z * 17));
                o.x = posX; o.y = b.y + t.height * fit * .5f; o.z = b.z;
                o.radius = Math.max(t.height * fit, Math.max(actualHw, actualHl) * 2);
                o.far = kit.box(facade, posX, o.y, b.z, 2 * actualHw, t.height * fit, 2 * actualHl);
                c.objects.add(o);
                c.colliders.add(new Collision.Box(posX, b.z, actualHw, actualHl, 0, "building"));

                // Add lush bushes and shrubs strictly in the building's garden and along its sides (verge 12.5m+, clear of sidewalk)
                float bx = s * Math.max(12.5f, Math.abs(posX) - actualHw * 0.4f);
                if (assets.shrubs != null && !assets.shrubs.isEmpty()) {
                    for (float dz : new float[]{-actualHl * 0.75f, -actualHl * 0.25f, actualHl * 0.25f, actualHl * 0.75f}) {
                        AssetLibrary.Template st = assets.shrubs.get((int)Math.abs(posX * 7 + dz * 13) % assets.shrubs.size());
                        addProp(c, st, new CityLayout.Prop("shrub", bx, 0f, b.z + dz, (int)(posX * 19 + dz * 31) % 360, 0.95f, 0), 3.5f);
                    }
                }
            }
        } else {
            for (CityLayout.Lot lot : tile.lots) {
                AssetLibrary.Template t = assets.pickBuilding(lot.variant());
                if (t == null) continue;
                float fitXZ = Math.min(1f, Math.min(2 * lot.halfAlong() / Math.max(.1f, t.length), 2 * lot.halfDepth() / Math.max(.1f, t.width)));
                float fitY = Math.max(fitXZ, Math.min(1.4f, 26f / Math.max(1f, t.height * fitXZ)) * fitXZ);
                Object3D o = new Object3D();
                o.near = t.create(lot.x(), 0, lot.z(), lot.yaw(), fitXZ, fitY);
                AssetLibrary.varyWindows(o.near, lot.variant());
                o.x = lot.x(); o.y = t.height * fitY * .5f; o.z = lot.z(); o.radius = Math.max(t.height * fitY, 2 * lot.halfAlong());
                o.far = kit.box(facade, lot.x(), o.y, lot.z(), 2 * lot.halfDepth(), t.height * fitY, 2 * lot.halfAlong());
                c.objects.add(o);

                // Add lush bushes along procedural lot frontage
                if (assets.shrubs != null && !assets.shrubs.isEmpty()) {
                    float s = Math.signum(lot.x());
                    float bx = lot.x() - s * (lot.halfDepth() + 1.8f);
                    for (float dz : new float[]{-lot.halfAlong() * 0.5f, lot.halfAlong() * 0.5f}) {
                        AssetLibrary.Template st = assets.shrubs.get((int)Math.abs(lot.x() * 7 + dz * 13) % assets.shrubs.size());
                        addProp(c, st, new CityLayout.Prop("shrub", bx, 0f, lot.z() + dz, (int)(lot.x() * 19 + dz * 31) % 360, 0.95f, 0), 3.5f);
                    }
                }
            }
        }

        List<StaticCityBlueprint.PropPlacement> staticLamps = StaticCityBlueprint.lightsInTile(cx, cz);
        if (!staticLamps.isEmpty()) {
            for (StaticCityBlueprint.PropPlacement l : staticLamps) {
                if (WorldGrid.road(l.x, l.z)) continue;
                addProp(c, assets.getProp("street-lamp"), new CityLayout.Prop("lamp", l.x, l.y, l.z, l.yaw * MathUtils.radiansToDegrees, l.scale, 0), 8f);
                addLampFx(c, l.x, l.y, l.z, l.yaw * MathUtils.radiansToDegrees, l.scale);
            }
        } else {
            for (CityLayout.Prop p : tile.lamps) {
                if (WorldGrid.road(p.x(), p.z())) continue;
                addProp(c, assets.getProp("street-lamp"), p, 8f);
                addLampFx(c, p.x(), p.y(), p.z(), p.yaw(), p.scale());
            }
        }

        boolean isPark = WorldGrid.isParkTile(cx, cz);
        List<StaticCityBlueprint.PropPlacement> staticTrees = StaticCityBlueprint.treesInTile(cx, cz);
        if (!isPark && !staticTrees.isEmpty()) {
            for (int i = 0; i < staticTrees.size(); i++) {
                StaticCityBlueprint.PropPlacement p = staticTrees.get(i);
                if (!isClearOfRoadAndSidewalk(p.x, p.z)) continue;
                AssetLibrary.Template t = assets.trees.isEmpty() ? null : assets.trees.get(i % assets.trees.size());
                if (t != null) {
                    addProp(c, t, new CityLayout.Prop("tree", p.x, p.y, p.z, p.yaw * MathUtils.radiansToDegrees, p.scale, i), t.height * p.scale + 2);
                    c.colliders.add(Collision.Box.circle(p.x, p.z, .35f, "tree"));
                }
            }
        } else {
            for (CityLayout.Prop p : tile.trees) {
                if (assets.trees.isEmpty()) break;
                if (!isClearOfRoadAndSidewalk(p.x(), p.z())) continue;
                AssetLibrary.Template t = assets.trees.get(p.variant() % assets.trees.size());
                addProp(c, t, p, t.height * p.scale() + 2);
                c.colliders.add(Collision.Box.circle(p.x(), p.z(), .35f, "tree"));
            }
        }

        List<StaticCityBlueprint.PropPlacement> staticProps = StaticCityBlueprint.propsInTile(cx, cz);
        for (StaticCityBlueprint.PropPlacement p : staticProps) {
            AssetLibrary.Template t = assets.getProp(p.propId);
            if (t != null) {
                addProp(c, t, new CityLayout.Prop(p.propId, p.x, p.y, p.z, p.yaw * MathUtils.radiansToDegrees, p.scale, 0), t.height * p.scale + 2);
            }
        }
        for (CityLayout.Prop p : tile.props) {
            if ("shrub".equals(p.kind()) || "grass".equals(p.kind())) {
                if (!assets.shrubs.isEmpty()) {
                    AssetLibrary.Template t = assets.shrubs.get(p.variant() % assets.shrubs.size());
                    addProp(c, t, p, 3.5f);
                }
            } else {
                AssetLibrary.Template t = assets.getProp(p.kind());
                if (t != null) {
                    addProp(c, t, p, t.height * p.scale() + 2);
                    if ("rock".equals(p.kind())) c.colliders.add(Collision.Box.circle(p.x(), p.z(), Math.max(0.35f, 0.95f * p.scale()), "rock"));
                }
            }
        }
        for (CityLayout.Prop p : tile.signals) addProp(c, assets.getProp("traffic-light"), p, 8f);
        return c;
    }

    /** The luminaire head sits 2.4 m out along the lamp's arm (local +x); the pool lies on the road beyond it. */
    private void addLampFx(Cell c, float x, float y, float z, float yawDeg, float scale) {
        AssetLibrary.Template t = assets.getProp("street-lamp");
        if (t == null) return;
        float r = yawDeg * MathUtils.degreesToRadians, cos = (float) Math.cos(r), sin = (float) Math.sin(r);
        LampFx f = new LampFx();
        f.hx = x + 2.4f * scale * cos; f.hz = z - 2.4f * scale * sin;
        f.bulbY = y + 5.75f * scale;
        float px = x + 3.4f * scale * cos, pz = z - 3.4f * scale * sin;
        f.groundY = WorldGrid.elevation(px, pz);
        f.pool = kit.decal(kit.lightPool, px, f.groundY + .045f, pz, 11f, 11f);
        f.halo = kit.billboard(kit.haloMat);
        f.streak = kit.streak(kit.lampStreak);
        c.lamps.add(f);
    }

    private void addProp(Cell c, AssetLibrary.Template t, CityLayout.Prop p, float radius) {
        if (t == null) return;
        Object3D o = new Object3D();
        o.near = t.create(p.x(), p.y(), p.z(), p.yaw(), p.pitch(), p.roll(), p.scale());
        o.x = p.x(); o.y = p.y() + t.height * p.scale() * .5f; o.z = p.z(); o.radius = radius;
        c.objects.add(o);
    }

    public static boolean isClearOfRoadAndSidewalk(float x, float z) {
        int cx = WorldGrid.tile(x), cz = WorldGrid.tile(z);
        float dx = Math.abs(x - cx * WorldGrid.TILE);
        float dz = Math.abs(z - cz * WorldGrid.TILE);
        // Avenue road is |dx| <= 5.0, sidewalk is 5.0..8.5
        // Trees must stay at least 10.5m away from the avenue center
        if (dx < 10.5f) return false;
        // Cross street road is |dz| <= 5.0, sidewalk is 5.0..8.5
        if (dz < 12.0f && (WorldGrid.builtConfig(cx, cz).hasEast() || WorldGrid.builtConfig(cx, cz).hasWest())) return false;
        // Check if on any drivable road surface or river
        if (WorldGrid.road(x, z) || WorldGrid.isRiverRow(cz)) return false;
        return true;
    }

    public void collect(Camera camera, float x, float z, Array<RenderableProvider> out) {
        for (Cell c : cells.values()) {
            if (!camera.frustum.boundsInFrustum(c.bounds)) continue;
            out.add(c.ground);
            if (kit.night() > .02f) collectLamps(camera, c, out);
            for (Object3D o : c.objects) {
                float d = Vector2.dst2(x, z, o.x, o.z);
                if (d > 230 * 230 || !camera.frustum.sphereInFrustum(o.x, o.y, o.z, o.radius)) continue;
                if (o.far != null && d > 205 * 205) out.add(o.far);
                else out.add(o.near);
            }
        }
        if (assets.mountain != null) {
            if (Float.isNaN(mountainAx) || Math.abs(x - mountainAx) > 480 || Math.abs(z - mountainAz) > 480) {
                mountainAx = x; mountainAz = z; mountains.clear();
                for (CityLayout.Prop p : CityLayout.mountains(x, z)) mountains.add(assets.mountain.create(p.x(), p.y(), p.z(), p.yaw(), p.scale()));
            }
            for (ModelInstance m : mountains) out.add(m);
        }
    }

    /** Lamp pools within ~100 m, halos + camera-aimed wet reflection within ~120 m (same ranges as the JS city). */
    private void collectLamps(Camera camera, Cell c, Array<RenderableProvider> out) {
        Vector3 cam = camera.position;
        for (LampFx f : c.lamps) {
            float dx = cam.x - f.hx, dz = cam.z - f.hz, d2 = dx * dx + dz * dz;
            if (d2 > 120f * 120f) continue;
            float dist = (float) Math.sqrt(d2);
            if (dist < 100f) out.add(f.pool);
            // keep the halo's on-screen size soft and centered directly on the 3D luminaire bulb
            MeshKit.faceCamera(f.halo, camera, f.hx, f.bulbY, f.hz, Math.min(2.8f, Math.max(.6f, dist * .09f)));
            out.add(f.halo);
            if (dist < 4f) continue;
            // mirror image of the luminaire meets the road 38 % of the way toward the viewer, smear runs to the viewer
            float fx = f.hx + dx * .38f, fz = f.hz + dz * .38f, len = Math.min(11f, dist * .62f * .9f);
            f.streak.transform.setToTranslation(fx, WorldGrid.elevation(fx, fz) + .05f, fz)
                .rotate(Vector3.Y, MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees).scale(1.5f, 1f, Math.max(2.2f, len));
            out.add(f.streak);
        }
    }

    /** Obstacles only matter off-street (the rider is held on the road); water is deadly. */
    public Collision.Contact collision(float x, float z, float yaw) {
        int cx = WorldGrid.tile(x), cz = WorldGrid.tile(z);
        Cell c = cells.get(key(cx, cz));
        if (c != null) for (Collision.Box b : c.colliders) {
            if (Math.abs(x - b.x) > Math.max(b.halfWidth, b.halfLength) + 3 || Math.abs(z - b.z) > Math.max(b.halfWidth, b.halfLength) + 3) continue;
            Collision.Contact hit = Collision.contact(x, z, yaw, 0.37f, 0.95f, b);
            if (hit != null) return hit;
        }
        if (WorldGrid.water(x, z)) return new Collision.Contact(0, -1, 0.15f);
        return null;
    }

    @Override public void close() { clear(); }
}
