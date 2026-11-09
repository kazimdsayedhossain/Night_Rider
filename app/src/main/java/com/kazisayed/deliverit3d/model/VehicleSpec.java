package com.kazisayed.deliverit3d.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VehicleSpec implements Serializable {

    public static class PartUpgrade implements Serializable {
        public final String name;
        public final String benefit;
        public final int[] costs;
        public final String[] tierBenefits;

        public PartUpgrade(String name, String benefit, int[] costs, String[] tierBenefits) {
            this.name = name;
            this.benefit = benefit;
            this.costs = costs;
            this.tierBenefits = tierBenefits;
        }

        public int getCost(int tier) {
            if (tier >= 3) return -1;
            return (tier >= 0 && tier < costs.length) ? costs[tier] : 500;
        }

        public String getBenefit(int tier) {
            if (tier <= 0) return "Stock / Standard";
            int idx = Math.min(tier - 1, tierBenefits.length - 1);
            return tierBenefits[idx];
        }

        public String getNextBenefit(int tier) {
            if (tier >= 3) return "Maximum Tier Reached";
            return tierBenefits[Math.min(tier, tierBenefits.length - 1)];
        }
    }

    private String id;
    private String name;
    private String type;
    private String stats;
    private int price;
    private boolean unlocked;
    private float baseSpeedKmh;
    private int baseCapacity;
    private int capacityPerTier;
    private float grip;

    private PartUpgrade speedPart;
    private PartUpgrade capacityPart;
    private PartUpgrade utilityPart;

    public VehicleSpec() {}

    public VehicleSpec(String id, String name, String type, String stats, int price, boolean unlocked,
                       float baseSpeedKmh, int baseCapacity, int capacityPerTier, float grip,
                       PartUpgrade speedPart, PartUpgrade capacityPart, PartUpgrade utilityPart) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.stats = stats;
        this.price = price;
        this.unlocked = unlocked;
        this.baseSpeedKmh = baseSpeedKmh;
        this.baseCapacity = baseCapacity;
        this.capacityPerTier = capacityPerTier;
        this.grip = grip;
        this.speedPart = speedPart;
        this.capacityPart = capacityPart;
        this.utilityPart = utilityPart;
    }

    public String getId() { return id != null ? id : "bike"; }
    public String getName() { return name != null ? name : "Fixie Messenger"; }
    public String getType() { return type != null ? type : "Bicycle"; }
    public String getStats() { return stats != null ? stats : ""; }
    public int getPrice() { return price; }
    public boolean isUnlocked() { return unlocked; }
    public float getBaseSpeedKmh() { return baseSpeedKmh > 0 ? baseSpeedKmh : 30f; }
    public int getBaseCapacity() { return baseCapacity > 0 ? baseCapacity : 4; }
    public int getCapacityPerTier() { return capacityPerTier > 0 ? capacityPerTier : 2; }
    public float getGrip() { return grip > 0 ? grip : 1.0f; }

    public PartUpgrade getSpeedPart() { return speedPart; }
    public PartUpgrade getCapacityPart() { return capacityPart; }
    public PartUpgrade getUtilityPart() { return utilityPart; }

    public String getSpeedPartName() { return speedPart != null ? speedPart.name : "Drivetrain"; }
    public String getCapacityPartName() { return capacityPart != null ? capacityPart.name : "Cargo Deck"; }
    public String getUtilityPartName() { return utilityPart != null ? utilityPart.name : "Tires & Handling"; }

    public String getSpeedPartDesc() { return speedPart != null ? speedPart.benefit : ""; }
    public String getCapacityPartDesc() { return capacityPart != null ? capacityPart.benefit : ""; }
    public String getUtilityPartDesc() { return utilityPart != null ? utilityPart.benefit : ""; }

    public static List<VehicleSpec> all() {
        List<VehicleSpec> list = new ArrayList<>();

        // 1. Fixie Messenger (Default bicycle: Base Speed = 30 KM/H)
        list.add(new VehicleSpec(
                "bike", "Fixie Messenger", "Bicycle",
                "Base Speed: 30 km/h | Agile Courier Bicycle | Free Default",
                0, true, 30.0f, 4, 2, 1.05f,
                new PartUpgrade(
                        "Shimano Alfine Drivetrain",
                        "High-cadence hollow-tech crankset boosting top speed & cadence.",
                        new int[]{100, 220, 450},
                        new String[]{"+12% Speed (34 km/h)", "+24% Speed (37 km/h)", "+36% Speed (41 km/h)"}
                ),
                new PartUpgrade(
                        "Porteur Front Cargo Deck",
                        "Reinforced alloy front deck with courier pannier bags.",
                        new int[]{80, 180, 350},
                        new String[]{"6 Orders Capacity (+2)", "8 Orders Capacity (+4)", "10 Orders Capacity (+6)"}
                ),
                new PartUpgrade(
                        "Continental Slick Tires",
                        "All-weather compound preventing slips on wet roads & sharp turns.",
                        new int[]{70, 150, 300},
                        new String[]{"+15% Wet Grip", "+30% Wet Cornering", "+50% Traction Stability"}
                )
        ));

        // 2. Vespa Sprint (Urban Motor Scooter)
        list.add(new VehicleSpec(
                "scooter", "Vespa Sprint", "Motor Scooter",
                "Base Speed: 48 km/h | Lightweight Urban Cruiser",
                450, false, 48.0f, 6, 2, 1.15f,
                new PartUpgrade(
                        "Polini Hi-Speed Variator & Exhaust",
                        "Tuned variator rollers and low-restriction carbon exhaust pipe.",
                        new int[]{150, 320, 650},
                        new String[]{"+12% Speed (54 km/h)", "+24% Speed (60 km/h)", "+36% Speed (66 km/h)"}
                ),
                new PartUpgrade(
                        "Heavy-Duty Rear Courier Top-Box",
                        "Fiberglass cargo carrier with dual reinforced sub-frame mounts.",
                        new int[]{120, 260, 520},
                        new String[]{"8 Orders Capacity (+2)", "10 Orders Capacity (+4)", "12 Orders Capacity (+6)"}
                ),
                new PartUpgrade(
                        "Brembo ABS Disc Brakes",
                        "Shortens braking distance and refunds red-light stop penalty.",
                        new int[]{110, 240, 480},
                        new String[]{"25% Shorter Braking", "50% Shorter Braking", "Instant Brake Authority"}
                )
        ));

        // 3. Bobber 750 (Cruiser Motorcycle)
        list.add(new VehicleSpec(
                "superbike", "Bobber 750", "Cruiser Motorcycle",
                "Base Speed: 85 km/h | High-Torque V-Twin Cruiser",
                1400, false, 85.0f, 8, 2, 0.95f,
                new PartUpgrade(
                        "Stage-1 Carburetor & Free-Flow Headers",
                        "Enlarged air jets and direct dual exhaust headers for raw torque.",
                        new int[]{300, 650, 1200},
                        new String[]{"+14% Speed (97 km/h)", "+28% Speed (109 km/h)", "+42% Speed (121 km/h)"}
                ),
                new PartUpgrade(
                        "Reinforced Leather Saddlebag System",
                        "Weather-treated saddlebags with heavy gauge rivets and inner dividers.",
                        new int[]{240, 500, 950},
                        new String[]{"10 Orders Capacity (+2)", "12 Orders Capacity (+4)", "14 Orders Capacity (+6)"}
                ),
                new PartUpgrade(
                        "Heavy Front Fork & Crash Bars",
                        "Heavy damping forks deflecting traffic bumps and reducing impact penalty.",
                        new int[]{220, 480, 900},
                        new String[]{"-30% Collision Damage", "-60% Collision Damage", "Deflects Traffic Scrapes"}
                )
        ));

        // 4. V7 Night Rider (Roadster Motorcycle)
        list.add(new VehicleSpec(
                "guzzi", "V7 Night Rider", "Roadster Motorcycle",
                "Base Speed: 105 km/h | High-Velocity Syndicate Roadster",
                3200, false, 105.0f, 10, 2, 1.05f,
                new PartUpgrade(
                        "Dyno-Tuned Race ECU & Titanium Exhaust",
                        "Remapped fuel curves, aggressive ignition, and lightweight titanium pipes.",
                        new int[]{500, 1100, 2200},
                        new String[]{"+15% Speed (120 km/h)", "+30% Speed (136 km/h)", "+45% Speed (152 km/h)"}
                ),
                new PartUpgrade(
                        "Dual Hard-Shell Touring Panniers",
                        "Lockable aerodynamic composite cases with compression straps.",
                        new int[]{400, 850, 1700},
                        new String[]{"12 Orders Capacity (+2)", "14 Orders Capacity (+4)", "16 Orders Capacity (+6)"}
                ),
                new PartUpgrade(
                        "Carbon Windscreen & Aero Fairings",
                        "Cuts high-speed drag and extends Fever Mode duration by +3.5s.",
                        new int[]{380, 800, 1600},
                        new String[]{"+1.5s Fever Duration", "+3.0s Fever Duration", "+5.0s Extended Fever"}
                )
        ));

        // 5. Barracuda 440 (Muscle Car)
        list.add(new VehicleSpec(
                "barracuda", "Barracuda 440", "Muscle Car",
                "Base Speed: 135 km/h | Heavy Courier Muscle Machine",
                7500, false, 135.0f, 12, 3, 0.90f,
                new PartUpgrade(
                        "Roots Positive-Displacement Supercharger",
                        "Belt-driven supercharger blower forcing immense air into 440ci V8.",
                        new int[]{950, 2100, 4200},
                        new String[]{"+16% Speed (156 km/h)", "+32% Speed (178 km/h)", "+48% Speed (200 km/h)"}
                ),
                new PartUpgrade(
                        "Reinforced Courier Trunk Bay",
                        "Heavy rear springs and partitioned modular parcel sorting bay.",
                        new int[]{800, 1700, 3400},
                        new String[]{"15 Orders Capacity (+3)", "18 Orders Capacity (+6)", "21 Orders Capacity (+9)"}
                ),
                new PartUpgrade(
                        "Heavy Steel Push Bar Bumper",
                        "Reinforced steel bull bar plowing through traffic without breaking combo.",
                        new int[]{750, 1600, 3200},
                        new String[]{"Preserves Combo on Bump", "1.5x Near-Miss Zone", "Double Near-Miss Bonus"}
                )
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

    public static float getBaseSpeedKmh(String id) {
        return findById(id).getBaseSpeedKmh();
    }

    public static float getBaseSpeed(String id) {
        // In m/s world units
        return findById(id).getBaseSpeedKmh() / 3.6f;
    }

    public static float getBaseAccel(String id) {
        VehicleSpec spec = findById(id);
        return spec.getBaseSpeedKmh() / 30.0f;
    }

    public static float getGrip(String id) {
        return findById(id).getGrip();
    }

    public static int getBaseCapacity(String id) {
        return findById(id).getBaseCapacity();
    }

    public static int getCapacityForTier(String id, int tier) {
        VehicleSpec spec = findById(id);
        int clampedTier = Math.max(0, Math.min(3, tier));
        return spec.getBaseCapacity() + (clampedTier * spec.getCapacityPerTier());
    }

    public static float getSpeedMultiplier(String id, int tier) {
        int clampedTier = Math.max(0, Math.min(3, tier));
        return 1.0f + (clampedTier * 0.12f);
    }

    public static float getAccelMultiplier(String id, int tier) {
        int clampedTier = Math.max(0, Math.min(3, tier));
        return 1.0f + (clampedTier * 0.15f);
    }

    public static int getSpeedUpgradeCost(String id, int currentTier) {
        VehicleSpec spec = findById(id);
        return spec.speedPart != null ? spec.speedPart.getCost(currentTier) : -1;
    }

    public static int getCapacityUpgradeCost(String id, int currentTier) {
        VehicleSpec spec = findById(id);
        return spec.capacityPart != null ? spec.capacityPart.getCost(currentTier) : -1;
    }

    public static int getUtilityUpgradeCost(String id, int currentTier) {
        VehicleSpec spec = findById(id);
        return spec.utilityPart != null ? spec.utilityPart.getCost(currentTier) : -1;
    }
}
