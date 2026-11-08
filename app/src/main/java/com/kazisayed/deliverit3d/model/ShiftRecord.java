package com.kazisayed.deliverit3d.model;

import com.google.firebase.firestore.ServerTimestamp;
import java.io.Serializable;
import java.util.Date;

public class ShiftRecord implements Serializable {
    private String route;
    private int cash;
    private long score;
    private int stars;
    private long timeMs;
    @ServerTimestamp
    private Date date;

    public ShiftRecord() {}

    public ShiftRecord(String route, int cash, long score, int stars, long timeMs, Date date) {
        this.route = route;
        this.cash = cash;
        this.score = score;
        this.stars = stars;
        this.timeMs = timeMs;
        this.date = date;
    }

    public String getRoute() { return route != null ? route : "Neo-Downtown"; }
    public void setRoute(String route) { this.route = route; }

    public int getCash() { return cash; }
    public void setCash(int cash) { this.cash = cash; }

    public long getScore() { return score; }
    public void setScore(long score) { this.score = score; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public long getTimeMs() { return timeMs; }
    public void setTimeMs(long timeMs) { this.timeMs = timeMs; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
}
