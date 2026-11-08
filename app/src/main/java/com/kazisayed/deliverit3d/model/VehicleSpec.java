package com.kazisayed.deliverit3d.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VehicleSpec implements Serializable {
    private String id;
    private String name;
    private String type;
    private String stats;
    private boolean unlocked;

    public VehicleSpec() {}

    public VehicleSpec(String id, String name, String type, String stats, boolean unlocked) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.stats = stats;
        this.unlocked = unlocked;
    }

    public String getId() { return id != null ? id : "bike"; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : "Courier Bike"; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type != null ? type : "Bicycle"; }
    public void setType(String type) { this.type = type; }

    public String getStats() { return stats != null ? stats : "Speed: 24 km/h | Handling: Agile"; }
    public void setStats(String stats) { this.stats = stats; }

    public boolean isUnlocked() { return unlocked; }
    public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }

    public static List<VehicleSpec> all() {
        List<VehicleSpec> list = new ArrayList<>();
        list.add(new VehicleSpec("bike", "Fixie Messenger", "Bicycle", "Speed: 28 km/h | Grip: High | Cargo: 15 kg", true));
        list.add(new VehicleSpec("scooter", "Vespa Sprint", "Motor Scooter", "Speed: 45 km/h | Grip: Medium | Cargo: 25 kg", true));
        list.add(new VehicleSpec("superbike", "Bobber 750", "Cruiser", "Speed: 85 km/h | Grip: Firm | Cargo: 35 kg", true));
        list.add(new VehicleSpec("guzzi", "V7 Night Rider", "Roadster", "Speed: 95 km/h | Grip: High | Cargo: 40 kg", true));
        list.add(new VehicleSpec("barracuda", "Barracuda 440", "Muscle Car", "Speed: 130 km/h | Grip: Heavy | Cargo: 100 kg", false));
        return list;
    }
}
