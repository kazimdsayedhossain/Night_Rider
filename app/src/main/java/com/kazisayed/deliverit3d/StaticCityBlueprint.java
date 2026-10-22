package com.kazisayed.deliverit3d;

import com.badlogic.gdx.Gdx;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * StaticCityBlueprint
 * -------------------
 * Houses the authoring layout for the native Deliver It 3D game.
 * Loads assets/city_map.json (identical to the JS game layout), or falls back to
 * the baked 108 building lots along the avenue, lamps every 40m, trees, and park/farm props.
 */
public final class StaticCityBlueprint {

    private StaticCityBlueprint() {}

    public static final class BuildingLot {
        public final String templateId, name;
        public final float x, y, z, yaw, scale, halfWidth, halfLength, height;

        public BuildingLot(String templateId, float x, float y, float z, float yaw,
                           float scale, float halfWidth, float halfLength,
                           float height, String name) {
            this.templateId = templateId;
            this.name = name;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.scale = scale;
            this.halfWidth = halfWidth;
            this.halfLength = halfLength;
            this.height = height;
        }

        @Override public String toString() {
            return "BuildingLot{" + templateId + " @ (" + x + "," + z + ")}";
        }
    }

    public static final class PropPlacement {
        public final String propId;
        public final float x, y, z, yaw, scale;

        public PropPlacement(String propId, float x, float y, float z, float yaw, float scale) {
            this.propId = propId;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.scale = scale;
        }

        @Override public String toString() {
            return "PropPlacement{" + propId + " @ (" + x + "," + z + ")}";
        }
    }

    public static final class RoadSegment {
        public final String id;
        public final float x1, z1, x2, z2, width;
        public final int lanes;

        public RoadSegment(String id, float x1, float z1, float x2, float z2, float width, int lanes) {
            this.id = id; this.x1 = x1; this.z1 = z1; this.x2 = x2; this.z2 = z2; this.width = width; this.lanes = lanes;
        }
    }

    public static final class Intersection {
        public final String id;
        public final float x, z, size;

        public Intersection(String id, float x, float z, float size) {
            this.id = id; this.x = x; this.z = z; this.size = size;
        }
    }

    public static final List<RoadSegment>   ROADS         = new ArrayList<>();
    public static final List<Intersection>  INTERSECTIONS = new ArrayList<>();
    public static final List<BuildingLot>   BUILDINGS     = new ArrayList<>();
    public static final List<PropPlacement> TREES         = new ArrayList<>();
    public static final List<PropPlacement> LIGHTS        = new ArrayList<>();
    public static final List<PropPlacement> PROPS         = new ArrayList<>();
    private static boolean initialized = false;

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        clear();
        boolean loaded = false;
        try {
            if (Gdx.files != null && Gdx.files.internal("city_map.json").exists()) {
                try (InputStream in = Gdx.files.internal("city_map.json").read()) {
                    CityMapJson.load(in, ROADS, INTERSECTIONS, BUILDINGS, TREES, LIGHTS, PROPS);
                    loaded = true;
                    android.util.Log.i("Dispatch", "Loaded city map from city_map.json ("
                            + BUILDINGS.size() + " buildings, "
                            + TREES.size() + " trees, "
                            + LIGHTS.size() + " lamps, "
                            + PROPS.size() + " props)");
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("Dispatch", "Failed reading city_map.json, using fallback: " + t.getMessage());
        }
        if (!loaded) {
            buildFallback();
            android.util.Log.i("Dispatch", "Initialized static fallback city ("
                    + BUILDINGS.size() + " buildings, "
                    + TREES.size() + " trees, "
                    + LIGHTS.size() + " lamps, "
                    + PROPS.size() + " props)");
        }
        initialized = true;
    }

    public static void clear() {
        ROADS.clear();
        INTERSECTIONS.clear();
        BUILDINGS.clear();
        TREES.clear();
        LIGHTS.clear();
        PROPS.clear();
    }

    public static List<BuildingLot> buildingsInTile(int cx, int cz) {
        if (WorldGrid.isRiverRow(cz)) return Collections.emptyList();
        ensureInitialized();
        float minX = cx * WorldGrid.TILE - WorldGrid.TILE / 2f, maxX = minX + WorldGrid.TILE;
        float minZ = cz * WorldGrid.TILE - WorldGrid.TILE / 2f, maxZ = minZ + WorldGrid.TILE;
        List<BuildingLot> res = new ArrayList<>();
        for (BuildingLot b : BUILDINGS) {
            if (b.x >= minX && b.x < maxX && b.z >= minZ && b.z < maxZ) res.add(b);
        }
        return res;
    }

    public static List<PropPlacement> treesInTile(int cx, int cz) {
        ensureInitialized();
        float minX = cx * WorldGrid.TILE - WorldGrid.TILE / 2f, maxX = minX + WorldGrid.TILE;
        float minZ = cz * WorldGrid.TILE - WorldGrid.TILE / 2f, maxZ = minZ + WorldGrid.TILE;
        List<PropPlacement> res = new ArrayList<>();
        for (PropPlacement p : TREES) {
            if (p.x >= minX && p.x < maxX && p.z >= minZ && p.z < maxZ) res.add(p);
        }
        return res;
    }

    public static List<PropPlacement> lightsInTile(int cx, int cz) {
        ensureInitialized();
        float minX = cx * WorldGrid.TILE - WorldGrid.TILE / 2f, maxX = minX + WorldGrid.TILE;
        float minZ = cz * WorldGrid.TILE - WorldGrid.TILE / 2f, maxZ = minZ + WorldGrid.TILE;
        List<PropPlacement> res = new ArrayList<>();
        for (PropPlacement p : LIGHTS) {
            if (p.x >= minX && p.x < maxX && p.z >= minZ && p.z < maxZ) res.add(p);
        }
        return res;
    }

    public static List<PropPlacement> propsInTile(int cx, int cz) {
        ensureInitialized();
        float minX = cx * WorldGrid.TILE - WorldGrid.TILE / 2f, maxX = minX + WorldGrid.TILE;
        float minZ = cz * WorldGrid.TILE - WorldGrid.TILE / 2f, maxZ = minZ + WorldGrid.TILE;
        List<PropPlacement> res = new ArrayList<>();
        for (PropPlacement p : PROPS) {
            if (p.x >= minX && p.x < maxX && p.z >= minZ && p.z < maxZ) res.add(p);
        }
        return res;
    }

    // =====================================================================
    // Hard-coded fallback — matches city_map.json
    // =====================================================================

    private static void buildFallback() {
        buildRoadsFallback();
        buildIntersectionsFallback();
        buildBuildingsFallback();
        buildTreesFallback();
        buildLightsFallback();
        buildPropsFallback();
    }

    private static void buildRoadsFallback() {
        ROADS.add(new RoadSegment("NS_0",      0f, -480f,    0f, 1360f, 10f, 4));
        ROADS.add(new RoadSegment("NS_W160", -160f, -160f, -160f, 1120f, 10f, 2));
        ROADS.add(new RoadSegment("NS_E160",  160f, -160f,  160f, 1120f, 10f, 2));

        float[] ews = {-320f, -160f, 0f, 160f, 320f, 480f, 640f, 800f, 960f, 1120f};
        for (float z : ews) {
            ROADS.add(new RoadSegment("EW_" + (int) z, -160f, z, 160f, z, 10f, 2));
        }
    }

    private static void buildIntersectionsFallback() {
        int[] zs = {-320, -160, 0, 160, 320, 480, 640, 800, 960, 1120};
        for (int z : zs) {
            INTERSECTIONS.add(new Intersection("INT_0_" + z, 0f, z, 14f));
        }
    }

    private static void buildBuildingsFallback() {
        final float YAWHALF = 1.570796f;
        Object[][] blocks = {
            {"b5_green_bakery",     -20.5f, -142f, "b3_brick_cafe",        20.5f, -142f, 7.5f, 8.0f, 28f},
            {"b1_blue_corner_shop", -20.5f, -118f, "b2_pink_tower",        20.5f, -118f, 7.5f, 8.0f, 28f},
            {"b4_music_store",      -20.5f,  -94f, "b6_apartment_mart",    20.5f,  -94f, 7.0f, 8.0f, 26f},
            {"b7_office_tower",     -20.5f,  -70f, "city-core-set",        20.5f,  -70f, 8.0f, 8.0f, 36f},
            {"street-frontage-set", -20.5f,  -46f, "b5_green_bakery",      20.5f,  -46f, 10.5f,10.0f,32f},
            {"b3_brick_cafe",       -20.5f,  -22f, "b1_blue_corner_shop",  20.5f,  -22f, 7.0f, 8.0f, 24f},
            {"b2_pink_tower",       -20.5f,   18f, "b4_music_store",       20.5f,   18f, 7.5f, 8.0f, 32f},
            {"b6_apartment_mart",   -20.5f,   42f, "b7_office_tower",      20.5f,   42f, 8.0f, 8.5f, 30f},
            {"city-core-set",       -20.5f,   66f, "street-frontage-set",  20.5f,   66f, 12.0f,12.0f,40f},
            {"b5_green_bakery",     -20.5f,   90f, "b3_brick_cafe",        20.5f,   90f, 7.5f, 8.0f, 28f},
            {"b1_blue_corner_shop", -20.5f,  114f, "b2_pink_tower",        20.5f,  114f, 7.5f, 8.0f, 28f},
            {"b4_music_store",      -20.5f,  138f, "b6_apartment_mart",    20.5f,  138f, 7.0f, 8.0f, 26f},
            {"b7_office_tower",     -20.5f,  178f, "city-core-set",        20.5f,  178f, 8.0f, 8.0f, 36f},
            {"street-frontage-set", -20.5f,  202f, "b5_green_bakery",      20.5f,  202f, 10.5f,10.0f,32f},
            {"b3_brick_cafe",       -20.5f,  226f, "b1_blue_corner_shop",  20.5f,  226f, 7.0f, 8.0f, 24f},
            {"b2_pink_tower",       -20.5f,  250f, "b4_music_store",       20.5f,  250f, 7.5f, 8.0f, 32f},
            {"b6_apartment_mart",   -20.5f,  274f, "b7_office_tower",      20.5f,  274f, 8.0f, 8.5f, 30f},
            {"city-core-set",       -20.5f,  298f, "street-frontage-set",  20.5f,  298f, 12.0f,12.0f,40f},
            {"b5_green_bakery",     -20.5f,  338f, "b3_brick_cafe",        20.5f,  338f, 7.5f, 8.0f, 28f},
            {"b1_blue_corner_shop", -20.5f,  362f, "b2_pink_tower",        20.5f,  362f, 7.5f, 8.0f, 28f},
            {"b4_music_store",      -20.5f,  386f, "b6_apartment_mart",    20.5f,  386f, 7.0f, 8.0f, 26f},
            {"b7_office_tower",     -20.5f,  410f, "city-core-set",        20.5f,  410f, 8.0f, 8.0f, 36f},
            {"street-frontage-set", -20.5f,  434f, "b5_green_bakery",      20.5f,  434f, 10.5f,10.0f,32f},
            {"b3_brick_cafe",       -20.5f,  458f, "b1_blue_corner_shop",  20.5f,  458f, 7.0f, 8.0f, 24f},
            {"b2_pink_tower",       -20.5f,  498f, "b4_music_store",       20.5f,  498f, 7.5f, 8.0f, 32f},
            {"b6_apartment_mart",   -20.5f,  522f, "b7_office_tower",      20.5f,  522f, 8.0f, 8.5f, 30f},
            {"city-core-set",       -20.5f,  546f, "street-frontage-set",  20.5f,  546f, 12.0f,12.0f,40f},
            {"b5_green_bakery",     -20.5f,  570f, "b3_brick_cafe",        20.5f,  570f, 7.5f, 8.0f, 28f},
            {"b1_blue_corner_shop", -20.5f,  594f, "b2_pink_tower",        20.5f,  594f, 7.5f, 8.0f, 28f},
            {"b4_music_store",      -20.5f,  618f, "b6_apartment_mart",    20.5f,  618f, 7.0f, 8.0f, 26f},
            {"b7_office_tower",     -20.5f,  658f, "city-core-set",        20.5f,  658f, 8.0f, 8.0f, 36f},
            {"street-frontage-set", -20.5f,  682f, "b5_green_bakery",      20.5f,  682f, 10.5f,10.0f,32f},
            {"b3_brick_cafe",       -20.5f,  706f, "b1_blue_corner_shop",  20.5f,  706f, 7.0f, 8.0f, 24f},
            {"b2_pink_tower",       -20.5f,  730f, "b4_music_store",       20.5f,  730f, 7.5f, 8.0f, 32f},
            {"b6_apartment_mart",   -20.5f,  754f, "b7_office_tower",      20.5f,  754f, 8.0f, 8.5f, 30f},
            {"city-core-set",       -20.5f,  778f, "street-frontage-set",  20.5f,  778f, 12.0f,12.0f,40f},
            {"b5_green_bakery",     -20.5f,  818f, "b3_brick_cafe",        20.5f,  818f, 7.5f, 8.0f, 28f},
            {"b1_blue_corner_shop", -20.5f,  842f, "b2_pink_tower",        20.5f,  842f, 7.5f, 8.0f, 28f},
            {"b4_music_store",      -20.5f,  866f, "b6_apartment_mart",    20.5f,  866f, 7.0f, 8.0f, 26f},
            {"b7_office_tower",     -20.5f,  890f, "city-core-set",        20.5f,  890f, 8.0f, 8.0f, 36f},
            {"street-frontage-set", -20.5f,  914f, "b5_green_bakery",      20.5f,  914f, 10.5f,10.0f,32f},
            {"b3_brick_cafe",       -20.5f,  938f, "b1_blue_corner_shop",  20.5f,  938f, 7.0f, 8.0f, 24f},
            {"b2_pink_tower",       -20.5f,  978f, "b4_music_store",       20.5f,  978f, 7.5f, 8.0f, 32f},
            {"b6_apartment_mart",   -20.5f, 1002f, "b7_office_tower",      20.5f, 1002f, 8.0f, 8.5f, 30f},
            {"city-core-set",       -20.5f, 1026f, "street-frontage-set",  20.5f, 1026f, 12.0f,12.0f,40f},
            {"b5_green_bakery",     -20.5f, 1050f, "b3_brick_cafe",        20.5f, 1050f, 7.5f, 8.0f, 28f},
            {"b1_blue_corner_shop", -20.5f, 1074f, "b2_pink_tower",        20.5f, 1074f, 7.5f, 8.0f, 28f},
            {"b4_music_store",      -20.5f, 1098f, "b6_apartment_mart",    20.5f, 1098f, 7.0f, 8.0f, 26f},
            {"b7_office_tower",     -20.5f, 1138f, "city-core-set",        20.5f, 1138f, 8.0f, 8.0f, 36f},
            {"street-frontage-set", -20.5f, 1162f, "b5_green_bakery",      20.5f, 1162f, 10.5f,10.0f,32f},
            {"b3_brick_cafe",       -20.5f, 1186f, "b1_blue_corner_shop",  20.5f, 1186f, 7.0f, 8.0f, 24f},
            {"b2_pink_tower",       -20.5f, 1210f, "b4_music_store",       20.5f, 1210f, 7.5f, 8.0f, 32f},
            {"b6_apartment_mart",   -20.5f, 1234f, "b7_office_tower",      20.5f, 1234f, 8.0f, 8.5f, 30f},
            {"city-core-set",       -20.5f, 1258f, "street-frontage-set",  20.5f, 1258f, 12.0f,12.0f,40f}
        };
        for (Object[] b : blocks) {
            String lt = (String) b[0]; float lx = (Float) b[1]; float lz = (Float) b[2];
            String rt = (String) b[3]; float rx = (Float) b[4]; float rz = (Float) b[5];
            float hw = (Float) b[6];  float hl = (Float) b[7]; float h  = (Float) b[8];

            BUILDINGS.add(new BuildingLot(lt, lx, 0f, lz, YAWHALF, 1f, hw, hl, h, prettyName(lt)));
            BUILDINGS.add(new BuildingLot(rt, rx, 0f, rz, -YAWHALF, 1f, hw, hl, h, prettyName(rt)));
        }
    }

    private static void buildTreesFallback() {
        float[] zBands = {22f, 70f, 118f, 502f, 550f, 598f, 982f, 1030f, 1078f};
        for (float z : zBands) {
            TREES.add(new PropPlacement("trees-pack", -13.5f, 0f, z, 0f, 0.85f));
            TREES.add(new PropPlacement("trees-pack",  13.5f, 0f, z, 0f, 0.85f));
        }

        float[] xs = {-150f, -115f, -80f};
        float[] zs = {400f, 435f, 470f, 505f, 540f, 575f};
        float[] yaws = {120f, 80f, 40f, 270f, 15f, 120f, 60f, 310f, 200f,
                        210f, 245f, 280f, 0f, 180f, 0f, 150f, 115f, 80f};
        int y = 0;
        for (float z : zs) {
            for (float x : xs) {
                TREES.add(new PropPlacement("trees-pack", x, 0f, z,
                        yaws[y % yaws.length], 1.0f));
                y++;
            }
        }
    }

    private static void buildLightsFallback() {
        for (float z = -80f; z <= 1280f; z += 40f) {
            // Skip any lamp at or near cross-street intersections (multiples of 160m)
            float distToJunction = Math.abs(z - Math.round(z / 160.0f) * 160.0f);
            if (distToJunction < 14f) continue;
            // Focus lamp heads inward directly over the roadway (East: 180° = PI, West: 0°)
            LIGHTS.add(new PropPlacement("street-lamp",  8.6f, 0f, z, (float) Math.PI, 1f));
            LIGHTS.add(new PropPlacement("street-lamp", -8.6f, 0f, z,  0f, 1f));
        }
    }

    private static void buildPropsFallback() {
        PROPS.add(new PropPlacement("fountain", -85f, 0f, 500f, 0f, 1.2f));
        PROPS.add(new PropPlacement("gazebo",  -115f, 0f, 500f, 0f, 1.2f));
        PROPS.add(new PropPlacement("bench",    -85f, 0f, 488f, 0f, 1f));
        PROPS.add(new PropPlacement("bench",    -85f, 0f, 512f, 0f, 1f));

        PROPS.add(new PropPlacement("barn",      90f, 0f, -320f, 0f, 1.1f));
        PROPS.add(new PropPlacement("silo",     110f, 0f, -335f, 0f, 1.1f));
        PROPS.add(new PropPlacement("windmill",  65f, 0f, -280f, 0f, 1.1f));
        PROPS.add(new PropPlacement("hay-bale",  78f, 0f, -310f, 0f, 1.0f));

        PROPS.add(new PropPlacement("finish", 0f, 0f, 1200f, 0f, 1f));
    }

    private static String prettyName(String templateId) {
        return switch (templateId) {
            case "b1_blue_corner_shop" -> "Blue Corner Supermart";
            case "b2_pink_tower" -> "Skyline Tower Hotel";
            case "b3_brick_cafe" -> "Brick Cafe & Roastery";
            case "b4_music_store" -> "Neon Music & Records";
            case "b5_green_bakery" -> "Green Bakery & Pastries";
            case "b6_apartment_mart" -> "Grand Plaza Apartments";
            case "b7_office_tower" -> "Corporate Financial Center";
            case "city-core-set" -> "Midtown Commercial Complex";
            case "street-frontage-set" -> "Historic Frontage Block";
            default -> templateId;
        };
    }

    // =====================================================================
    // Tiny zero-dependency JSON reader
    // =====================================================================

    private static final class CityMapJson {

        static void load(InputStream in,
                         List<RoadSegment>  roads,
                         List<Intersection> intersections,
                         List<BuildingLot>  buildings,
                         List<PropPlacement> trees,
                         List<PropPlacement> lights,
                         List<PropPlacement> props) throws Exception {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject root = new Parser(text).parseObject();

            List<JsonValue> roadArr = root.getArray("roads");
            if (roadArr != null) {
                for (JsonValue v : roadArr) {
                    JsonObject o = v.asObject();
                    roads.add(new RoadSegment(
                        o.getString("id"),
                        o.getFloat("x1"), o.getFloat("z1"),
                        o.getFloat("x2"), o.getFloat("z2"),
                        o.getFloat("width", 10f),
                        o.getInt("lanes", 2)));
                }
            }
            List<JsonValue> intArr = root.getArray("intersections");
            if (intArr != null) {
                for (JsonValue v : intArr) {
                    JsonObject o = v.asObject();
                    intersections.add(new Intersection(
                        o.getString("id"), o.getFloat("x"), o.getFloat("z"),
                        o.getFloat("size", 14f)));
                }
            }
            List<JsonValue> bldArr = root.getArray("buildings");
            if (bldArr != null) {
                for (JsonValue v : bldArr) {
                    JsonObject o = v.asObject();
                    buildings.add(new BuildingLot(
                        o.getString("templateId"),
                        o.getFloat("x"), o.getFloat("y"), o.getFloat("z"),
                        o.getFloat("yaw"), o.getFloat("scale", 1f),
                        o.getFloat("halfWidth", 7.5f),
                        o.getFloat("halfLength", 8f),
                        o.getFloat("height", 28f),
                        o.getString("name")));
                }
            }
            addProps(trees,  root.getArray("trees"),  "trees-pack");
            addProps(lights, root.getArray("lights"), "street-lamp");
            addProps(props,  root.getArray("props"),  "bench");
        }

        private static void addProps(List<PropPlacement> dst, List<JsonValue> arr, String fallback) {
            if (arr == null) return;
            for (JsonValue v : arr) {
                JsonObject o = v.asObject();
                String id = o.getString("propId", fallback);
                float x = o.getFloat("x"), y = o.getFloat("y"), z = o.getFloat("z");
                float yaw = o.getFloat("yaw");
                float scale = o.getFloat("scale", 1f);
                if ("street-lamp".equals(id)) {
                    // Filter out any lamp within 14m of an intersection (prevents lamps standing in intersecting cross-street roads!)
                    float distToJunction = Math.abs(z - Math.round(z / 160.0f) * 160.0f);
                    if (distToJunction < 14.0f) continue;
                    // Also guard against any road position
                    if (WorldGrid.road(x, z)) continue;
                    // Focus street lamps directly inward onto the road (East=180°, West=0°)
                    yaw = x > 0 ? (float) Math.PI : 0f;
                } else if ("trees-pack".equals(id) || "tree".equals(id)) {
                    // Move street verge trees securely away from sidewalk (ends at 8.5m) to 13.5m
                    if (Math.abs(x) > 10f && Math.abs(x) < 16f) {
                        x = Math.signum(x) * 13.5f;
                    }
                }
                dst.add(new PropPlacement(id, x, y, z, yaw, scale));
            }
        }

        interface JsonValue { JsonObject asObject(); }

        static final class JsonObject implements JsonValue {
            final Map<String, JsonValue> map = new LinkedHashMap<>();
            public JsonObject asObject() { return this; }

            String getString(String k) { return getString(k, ""); }
            String getString(String k, String def) {
                JsonValue v = map.get(k);
                return v instanceof JsonString ? ((JsonString) v).value : def;
            }
            float getFloat(String k) { return getFloat(k, 0f); }
            float getFloat(String k, float def) {
                JsonValue v = map.get(k);
                return v instanceof JsonNumber ? (float) ((JsonNumber) v).value : def;
            }
            int getInt(String k, int def) {
                JsonValue v = map.get(k);
                return v instanceof JsonNumber ? (int) ((JsonNumber) v).value : def;
            }
            List<JsonValue> getArray(String k) {
                JsonValue v = map.get(k);
                if (v instanceof JsonArray) return ((JsonArray) v).items;
                return null;
            }
        }
        static final class JsonArray implements JsonValue {
            final List<JsonValue> items = new ArrayList<>();
            public JsonObject asObject() { return null; }
        }
        static final class JsonString implements JsonValue {
            final String value;
            JsonString(String v) { value = v; }
            public JsonObject asObject() { return null; }
        }
        static final class JsonNumber implements JsonValue {
            final double value;
            JsonNumber(double v) { value = v; }
            public JsonObject asObject() { return null; }
        }

        static final class Parser {
            private final String s;
            private int i = 0;
            Parser(String s) { this.s = s; }

            JsonObject parseObject() { return (JsonObject) parseValue(); }

            private JsonValue parseValue() {
                skipWs();
                char c = s.charAt(i);
                if (c == '{') return parseObj();
                if (c == '[') return parseArr();
                if (c == '"') return new JsonString(parseString());
                if (c == 't') { i += 4; return new JsonNumber(1); }
                if (c == 'f') { i += 5; return new JsonNumber(0); }
                if (c == 'n') { i += 4; return new JsonNumber(0); }
                return parseNumber();
            }

            private JsonObject parseObj() {
                JsonObject obj = new JsonObject();
                i++; skipWs();
                if (s.charAt(i) == '}') { i++; return obj; }
                while (true) {
                    skipWs();
                    String key = parseString();
                    skipWs();
                    if (s.charAt(i) != ':') throw new IllegalStateException("expected ':' at " + i);
                    i++;
                    obj.map.put(key, parseValue());
                    skipWs();
                    char c = s.charAt(i++);
                    if (c == '}') return obj;
                    if (c != ',') throw new IllegalStateException("expected ',' at " + (i - 1));
                }
            }

            private JsonArray parseArr() {
                JsonArray arr = new JsonArray();
                i++; skipWs();
                if (s.charAt(i) == ']') { i++; return arr; }
                while (true) {
                    arr.items.add(parseValue());
                    skipWs();
                    char c = s.charAt(i++);
                    if (c == ']') return arr;
                    if (c != ',') throw new IllegalStateException("expected ',' at " + (i - 1));
                }
            }

            private String parseString() {
                if (s.charAt(i) != '"') throw new IllegalStateException("expected '\"' at " + i);
                i++;
                StringBuilder sb = new StringBuilder();
                while (true) {
                    char c = s.charAt(i++);
                    if (c == '"') return sb.toString();
                    if (c == '\\') {
                        char e = s.charAt(i++);
                        switch (e) {
                            case '"' -> sb.append('"');
                            case '\\' -> sb.append('\\');
                            case '/' -> sb.append('/');
                            case 'b' -> sb.append('\b');
                            case 'f' -> sb.append('\f');
                            case 'n' -> sb.append('\n');
                            case 'r' -> sb.append('\r');
                            case 't' -> sb.append('\t');
                            case 'u' -> {
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                                i += 4;
                            }
                            default -> throw new IllegalStateException("bad escape \\" + e);
                        }
                    } else {
                        sb.append(c);
                    }
                }
            }

            private JsonNumber parseNumber() {
                int start = i;
                while (i < s.length() && "0123456789+-.eE".indexOf(s.charAt(i)) >= 0) i++;
                return new JsonNumber(Double.parseDouble(s.substring(start, i)));
            }

            private void skipWs() {
                while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            }
        }
    }
}
