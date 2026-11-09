package com.kazisayed.deliverit3d.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VehicleSpec implements Serializable {
    private String id;
    private String name;
    private String type;
    private String stats;
    private int price;
    private boolean unlocked;

    private String speedPartName;
    private String speedPartDesc;
    private int[] speedUpgradeCosts; // Length 3

    private String capacityPartName;
    private String capacityPartDesc;
    private int[] capacityUpgradeCosts; // Length 3

    private float baseSpeed;
    private float baseAccel;
    private int baseCapacity;
    private int capacityPerTier;
    private float grip;

    public VehicleSpec() {}

    public VehicleSpec(String id, String name, String type, String stats, int price, boolean unlocked,
                       String speedPartName, String speedPartDesc, int[] speedUpgradeCosts,
                       String capacityPartName, String capacityPartDesc, int[] capacityUpgradeCosts,
                       float baseSpeed, float baseAccel, int baseCapacity, int capacityPerTier, float grip) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.stats = stats;
        this.price = price;
        this.unlocked = unlocked;
        this.speedPartName = speedPartName;
        this.speedPartDesc = speedPartDesc;
        this.speedUpgradeCosts = speedUpgradeCosts;
        this.capacityPartName = capacityPartName;
        this.capacityPartDesc = capacityPartDesc;
        this.capacityUpgradeCosts = capacityUpgradeCosts;
        this.baseSpeed = baseSpeed;
        this.baseAccel = baseAccel;
        this.baseCapacity = baseCapacity;
        this.capacityPerTier = capacityPerTier;
        this.grip = grip;
    }

    public String getId() { return id != null ? id : "bike"; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : "Fixie Messenger"; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type != null ? type : "Bicycle"; }
    public void setType(String type) { this.type = type; }

    public String getStats() { return stats != null ? stats : ""; }
    public void setStats(String stats) { this.stats = stats; }

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }

    public boolean isUnlocked() { return unlocked; }
    public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }

    public String getSpeedPartName() { return speedPartName != null ? speedPartName : "Drivetrain"; }
    public String getSpeedPartDesc() { return speedPartDesc != null ? speedPartDesc : "Upgrades top speed & acceleration"; }
    public int[] getSpeedUpgradeCosts() { return speedUpgradeCosts != null ? speedUpgradeCosts : new int[]{120, 280, 550}; }

    public String getCapacityPartName() { return capacityPartName != null ? capacityPartName : "Cargo Rack"; }
    public String getCapacityPartDesc() { return capacityPartDesc != null ? capacityPartDesc : "Expands pending order capacity"; }
    public int[] getCapacityUpgradeCosts() { return capacityUpgradeCosts != null ? capacityUpgradeCosts : new int[]{100, 220, 420}; }

    public float getBaseSpeed() { return baseSpeed > 0 ? baseSpeed : 1.0f; }
    public float getBaseAccel() { return baseAccel > 0 ? baseAccel : 1.0f; }
    public int getBaseCapacity() { return baseCapacity > 0 ? baseCapacity : 4; }
    public int getCapacityPerTier() { return capacityPerTier > 0 ? capacityPerTier : 2; }
    public float getGrip() { return grip > 0 ? grip : 1.0f; }

    public static List<VehicleSpec> all() {
        List<VehicleSpec> list = new ArrayList<>();
        list.add(new VehicleSpec(
                "bike", "Fixie Messenger", "Bicycle",
                "Speed: 28 km/h | Cargo: 4-10 Orders | Agile Handling",
                0, true,
                "Shimano Alfine Drivetrain",
                "Precision hollow-tech crankset & racing sprocket chain for rapid cadence.",
                new int[]{120, 280, 550},
                "Front Porteur Cargo Deck",
                "Reinforced alloy front rack with quick-release courier pannier bag mount.",
                new int[]{100, 220, 420},
                1.00f, 1.00f, 4, 2, 1.05f
        ));

        list.add(new VehicleSpec(
                "scooter", "Vespa Sprint", "Motor Scooter",
                "Speed: 45 km/h | Cargo: 6-12 Orders | Stable Balance",
                450, false,
                "Polini Variator & Carbon Exhaust",
                "High-rpm tuned variator rollers with low-restriction resonance exhaust.",
                new int[]{180, 380, 750},
                "Heavy-Duty Rear Courier Top-Box",
                "Shock-absorbing fiberglass carrier box with reinforced dual sub-frame brackets.",
                new int[]{150, 320, 600},
                1.18f, 1.15f, 6, 2, 1.15f
        ));

        list.add(new VehicleSpec(
                "superbike", "Bobber 750", "Cruiser",
                "Speed: 85 km/h | Cargo: 8-14 Orders | High Torque",
                1400, false,
                "Stage-1 Carburetor & Free-Flow Pipes",
                "Enlarged air jetting with velocity intake stacks and direct-header dual pipes.",
                new int[]{350, 750, 1400},
                "Reinforced Leather Saddlebag System",
                "Weather-treated saddlebags with heavy gauge rivets and inner cargo dividers.",
                new int[]{280, 580, 1100},
                1.38f, 1.35f, 8, 2, 0.95f
        ));

        list.add(new VehicleSpec(
                "guzzi", "V7 Night Rider", "Roadster",
                "Speed: 100 km/h | Cargo: 10-16 Orders | High Grip",
                3200, false,
                "ECU Fuel Remap & Titanium Pipes",
                "Dyno-tuned fuel injection curve, aggressive ignition timing, and titanium exhaust.",
                new int[]{600, 1250, 2400},
                "Dual Hard-Shell Touring Panniers",
                "Lockable aerodynamic composite hard-cases with integrated compression straps.",
                new int[]{480, 980, 1900},
                1.55f, 1.50f, 10, 2, 1.05f
        ));

        list.add(new VehicleSpec(
                "barracuda", "Barracuda 440", "Muscle Car",
                "Speed: 130 km/h | Cargo: 12-21 Orders | Heavy Power",
                7500, false,
                "Roots Supercharger & Quad Headers",
                "Belt-driven positive displacement supercharger with quad tuned exhaust headers.",
                new int[]{1200, 2500, 4800},
                "Reinforced Courier Trunk Bay",
                "Reinforced rear springs and multi-tiered cargo rack partitioning system.",
                new int[]{950, 1900, 3600},
                1.75f, 1.65f, 12, 3, 0.90f
        ));

        return list;
    }

    public static VehicleSpec findById(String id) {
        if (id == null) id = "bike";
        for (VehicleSpec v : all()) {
            if (v.getId().equalsIgnoreCase(id)) return v;
        }
        return all().get(0);
    }

    public static int getBaseCapacity(String id) {
        return findById(id).getBaseCapacity();
    }

    public static int getCapacityForTier(String id, int tier) {
        VehicleSpec spec = findById(id);
        int clampedTier = Math.max(0, Math.min(3, tier));
        return spec.getBaseCapacity() + (clampedTier * spec.getCapacityPerTier());
    }

    public static float getBaseSpeed(String id) {
        return findById(id).getBaseSpeed();
    }

    public static float getBaseAccel(String id) {
        return findById(id).getBaseAccel();
    }

    public static float getGrip(String id) {
        return findById(id).getGrip();
    }

    public static float getSpeedMultiplier(String id, int tier) {
        int clampedTier = Math.max(0, Math.min(3, tier));
        return 1.0f + (clampedTier * 0.12f); // Tier 1: +12%, Tier 2: +24%, Tier 3: +36%
    }

    public static float getAccelMultiplier(String id, int tier) {
        int clampedTier = Math.max(0, Math.min(3, tier));
        return 1.0f + (clampedTier * 0.15f); // Tier 1: +15%, Tier 2: +30%, Tier 3: +45%
    }

    public static int getSpeedUpgradeCost(String id, int currentTier) {
        if (currentTier >= 3) return -1; // Max tier
        VehicleSpec spec = findById(id);
        int[] costs = spec.getSpeedUpgradeCosts();
        return (currentTier >= 0 && currentTier < costs.length) ? costs[currentTier] : 500;
    }

    public static int getCapacityUpgradeCost(String id, int currentTier) {
        if (currentTier >= 3) return -1; // Max tier
        VehicleSpec spec = findById(id);
        int[] costs = spec.getCapacityUpgradeCosts();
        return (currentTier >= 0 && currentTier < costs.length) ? costs[currentTier] : 400;
    }
}
