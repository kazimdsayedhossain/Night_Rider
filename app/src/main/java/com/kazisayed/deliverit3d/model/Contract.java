package com.kazisayed.deliverit3d.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Contract implements Serializable {
    private String id;
    private String route;
    private int payout;
    private int timeLimitSeconds;
    private String cargo;
    private int difficulty;

    public Contract() {}

    public Contract(String id, String route, int payout, int timeLimitSeconds, String cargo, int difficulty) {
        this.id = id;
        this.route = route;
        this.payout = payout;
        this.timeLimitSeconds = timeLimitSeconds;
        this.cargo = cargo;
        this.difficulty = difficulty;
    }

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    public String getRoute() { return route != null ? route : "Neo-Downtown Circuit"; }
    public void setRoute(String route) { this.route = route; }

    public int getPayout() { return payout; }
    public void setPayout(int payout) { this.payout = payout; }

    public int getTimeLimitSeconds() { return timeLimitSeconds; }
    public void setTimeLimitSeconds(int timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }

    public String getCargo() { return cargo != null ? cargo : "Standard Parcel"; }
    public void setCargo(String cargo) { this.cargo = cargo; }

    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }

    public static List<Contract> defaults() {
        List<Contract> list = new ArrayList<>();
        list.add(new Contract("c1", "Neo-Downtown -> Harbor", 450, 180, "Fragile Electronics", 3));
        list.add(new Contract("c2", "Central Station -> Midtown Plaza", 280, 120, "Priority Documents", 2));
        list.add(new Contract("c3", "Tech District -> Riverfront Pier", 600, 240, "Medical Supplies", 4));
        list.add(new Contract("c4", "Industrial Zone -> Old Town Market", 350, 150, "Heavy Cargo", 3));
        list.add(new Contract("c5", "Skyway Promenade -> City Hall", 200, 90, "Express Courier", 1));
        return list;
    }
}
