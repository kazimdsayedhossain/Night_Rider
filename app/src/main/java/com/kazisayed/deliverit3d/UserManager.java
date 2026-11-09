package com.kazisayed.deliverit3d;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class UserManager {
    private static final String TAG = "UserManager";
    private static final String RTDB_URL = "https://nightrider-4066f-default-rtdb.firebaseio.com";

    private static UserManager instance;

    public static synchronized UserManager get() {
        if (instance == null) instance = new UserManager();
        return instance;
    }

    private String uid = "guest";
    private String username = "Rider_001";
    private int cash = 100;
    private int totalMoney = 100;
    private int totalCoins = 0;
    private int completedDeliveries = 0;
    private long points = 0;
    private long totalPlaytime = 0; // in seconds
    private int avatarId = 1;
    private int globalRank = 4;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnProfileLoadedListener {
        void onLoaded(UserManager mgr);
    }

    public void loadOrCreateProfile(Context context, FirebaseUser user, OnProfileLoadedListener listener) {
        loadOrCreateProfile(context, user, null, listener);
    }

    public void loadOrCreateProfile(Context context, FirebaseUser user, String preferredName, OnProfileLoadedListener listener) {
        if (user == null) {
            loadLocal(context);
            if (listener != null) listener.onLoaded(this);
            return;
        }

        this.uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        this.username = doc.getString("username");
                        if (preferredName != null && !preferredName.trim().isEmpty() && (this.username == null || this.username.isEmpty() || this.username.equalsIgnoreCase("Guest Rider"))) {
                            this.username = preferredName;
                            doc.getReference().update("username", this.username);
                        } else if (this.username == null || this.username.isEmpty() || (!user.isAnonymous() && this.username.equalsIgnoreCase("Guest Rider"))) {
                            this.username = deriveName(user);
                            doc.getReference().update("username", this.username);
                        }
                        Long c = doc.getLong("cash");
                        this.cash = c != null ? c.intValue() : 100;

                        Long tm = doc.getLong("totalMoney");
                        this.totalMoney = tm != null ? tm.intValue() : this.cash;

                        Long tc = doc.getLong("totalCoins");
                        this.totalCoins = tc != null ? tc.intValue() : 0;

                        Long cd = doc.getLong("completedDeliveries");
                        this.completedDeliveries = cd != null ? cd.intValue() : 0;

                        Long p = doc.getLong("points");
                        this.points = p != null ? p : 0;

                        Long pt = doc.getLong("totalPlaytime");
                        this.totalPlaytime = pt != null ? pt : 0;

                        Long av = doc.getLong("avatarId");
                        this.avatarId = av != null ? Math.max(1, Math.min(8, av.intValue())) : 1;

                        saveLocal(context);
                        Log.i(TAG, "Profile loaded: " + username + " ($" + cash + ", coins=" + totalCoins + ", deliveries=" + completedDeliveries + ")");
                    } else {
                        // Create brand new profile
                        if (preferredName != null && !preferredName.trim().isEmpty()) {
                            this.username = preferredName;
                        } else {
                            this.username = deriveName(user);
                        }
                        this.cash = 100;
                        this.totalMoney = 100;
                        this.totalCoins = 0;
                        this.completedDeliveries = 0;
                        this.points = 0;
                        this.totalPlaytime = 0;
                        this.avatarId = 1 + new Random().nextInt(8);

                        Map<String, Object> data = new HashMap<>();
                        data.put("username", this.username);
                        data.put("cash", this.cash);
                        data.put("totalMoney", this.totalMoney);
                        data.put("totalCoins", this.totalCoins);
                        data.put("completedDeliveries", this.completedDeliveries);
                        data.put("points", this.points);
                        data.put("totalPlaytime", this.totalPlaytime);
                        data.put("avatarId", this.avatarId);
                        data.put("createdAt", FieldValue.serverTimestamp());

                        db.collection("users").document(uid).set(data);
                        saveLocal(context);
                        Log.i(TAG, "Created new profile: " + username + " (avatar=" + avatarId + ")");
                    }
                    if (listener != null) listener.onLoaded(this);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load from Firestore, using local cache: " + e.getMessage());
                    loadLocal(context);
                    if (user != null && !user.isAnonymous()) {
                        if (preferredName != null && !preferredName.trim().isEmpty()) {
                            this.username = preferredName;
                        } else if (this.username == null || this.username.isEmpty() || this.username.equalsIgnoreCase("Guest Rider")) {
                            this.username = deriveName(user);
                        }
                        saveLocal(context);
                    }
                    if (listener != null) listener.onLoaded(this);
                });
    }

    private String deriveName(FirebaseUser user) {
        if (user == null) return "Guest Rider";
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty() && !user.getDisplayName().equalsIgnoreCase("Guest Rider")) {
            return user.getDisplayName();
        }
        if (user.getEmail() != null && user.getEmail().contains("@")) {
            return user.getEmail().split("@")[0];
        }
        String id = user.getUid();
        return "Rider_" + (id != null && id.length() > 5 ? id.substring(0, 5).toUpperCase(java.util.Locale.ROOT) : "COURIER");
    }

    public void addCoins(Context context, int count) {
        if (count <= 0) return;
        this.totalCoins += count;
        saveLocal(context);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .update("totalCoins", FieldValue.increment(count));
        }
    }

    public boolean spendCash(Context context, int amount) {
        if (amount <= 0) return true;
        if (this.cash < amount) return false;
        this.cash -= amount;
        saveLocal(context);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .update("cash", this.cash);
        }
        return true;
    }

    public void addReward(Context context, int earnedCash, long earnedPoints, int deliveriesCount) {
        this.cash += earnedCash;
        this.totalMoney += earnedCash;
        this.points += earnedPoints;
        this.completedDeliveries += deliveriesCount;
        saveLocal(context);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .update("cash", FieldValue.increment(earnedCash),
                            "totalMoney", FieldValue.increment(earnedCash),
                            "points", FieldValue.increment(earnedPoints),
                            "completedDeliveries", FieldValue.increment(deliveriesCount));

            // Sync to Realtime DB leaderboard
            pushLeaderboardEntry(user.getUid(), this.username, this.points, this.totalPlaytime, this.completedDeliveries, this.totalMoney, this.totalCoins);
        }
    }

    public void addPlaytimeSeconds(Context context, int seconds) {
        if (seconds <= 0) return;
        this.totalPlaytime += seconds;
        saveLocal(context);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .update("totalPlaytime", FieldValue.increment(seconds),
                            "lastActive", FieldValue.serverTimestamp());
            pushLeaderboardEntry(user.getUid(), this.username, this.points, this.totalPlaytime, this.completedDeliveries, this.totalMoney, this.totalCoins);
        }
    }

    private void pushLeaderboardEntry(String uid, String name, long score, long playtime, int deliveries, int money, int coins) {
        executor.execute(() -> {
            try {
                String endpoint = RTDB_URL + "/leaderboard/" + uid + ".json";
                JSONObject payload = new JSONObject();
                payload.put("displayName", name);
                payload.put("score", score);
                payload.put("playtime", playtime);
                payload.put("deliveries", deliveries);
                payload.put("totalMoney", money);
                payload.put("totalCoins", coins);
                payload.put("district", "Neo-Downtown");

                byte[] body = payload.toString().getBytes("UTF-8");
                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                OutputStream os = conn.getOutputStream();
                os.write(body);
                os.close();
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    private void saveLocal(Context context) {
        if (context == null) return;
        SaveStore s = new SaveStore(context);
        s.put("player_uid", uid);
        s.put("player_username", username);
        s.put("deliver3d_cash", cash);
        s.put("player_total_money", totalMoney);
        s.put("player_total_coins", totalCoins);
        s.put("player_deliveries", completedDeliveries);
        s.put("player_points", points);
        s.put("player_playtime", totalPlaytime);
        s.put("player_avatar_id", avatarId);
    }

    public void loadLocal(Context context) {
        if (context == null) return;
        SaveStore s = new SaveStore(context);
        this.uid = s.text("player_uid", "guest");
        String savedUser = s.text("player_username", "");
        if (savedUser.isEmpty() || savedUser.equalsIgnoreCase("Guest Rider")) {
            FirebaseUser cu = FirebaseAuth.getInstance().getCurrentUser();
            if (cu != null && !cu.isAnonymous()) {
                this.username = deriveName(cu);
            } else {
                this.username = "Guest Rider";
            }
        } else {
            this.username = savedUser;
        }
        this.cash = s.cash();
        this.totalMoney = s.number("player_total_money", this.cash);
        this.totalCoins = s.number("player_total_coins", 0);
        this.completedDeliveries = s.number("player_deliveries", 0);
        this.points = s.number("player_points", 0);
        this.totalPlaytime = s.number("player_playtime", 0);
        this.avatarId = Math.max(1, Math.min(8, s.number("player_avatar_id", 1)));
    }

    public void setUsername(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.username = name.trim();
        }
    }

    public static int getAvatarDrawable(int id) {
        return switch (id) {
            case 2 -> R.drawable.avatar_2;
            case 3 -> R.drawable.avatar_3;
            case 4 -> R.drawable.avatar_4;
            case 5 -> R.drawable.avatar_5;
            case 6 -> R.drawable.avatar_6;
            case 7 -> R.drawable.avatar_7;
            case 8 -> R.drawable.avatar_8;
            default -> R.drawable.avatar_1;
        };
    }

    public String getUid() { return uid; }
    public String getUsername() { return username; }
    public int getCash() { return cash; }
    public int getTotalMoney() { return totalMoney; }
    public int getTotalCoins() { return totalCoins; }
    public int getCompletedDeliveries() { return completedDeliveries; }
    public long getPoints() { return points; }
    public long getTotalPlaytime() { return totalPlaytime; }
    public int getAvatarId() { return avatarId; }
    public int getGlobalRank() { return globalRank; }
}
