package com.kazisayed.deliverit3d.model;

import java.io.Serializable;

public class LeaderboardEntry implements Serializable {
    private String uid;
    private String displayName;
    private long score;
    private String district;
    private int rank;

    public LeaderboardEntry() {}

    public LeaderboardEntry(String uid, String displayName, long score, String district, int rank) {
        this.uid = uid;
        this.displayName = displayName;
        this.score = score;
        this.district = district;
        this.rank = rank;
    }

    public String getUid() { return uid != null ? uid : ""; }
    public void setUid(String uid) { this.uid = uid; }

    public String getDisplayName() { return displayName != null ? displayName : "Courier"; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public long getScore() { return score; }
    public void setScore(long score) { this.score = score; }

    public String getDistrict() { return district != null ? district : "Neo-Downtown"; }
    public void setDistrict(String district) { this.district = district; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
}
