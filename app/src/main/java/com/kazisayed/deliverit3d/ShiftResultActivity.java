package com.kazisayed.deliverit3d;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.kazisayed.deliverit3d.databinding.ActivityShiftResultBinding;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import org.json.JSONObject;

/**
 * ShiftResultActivity: post-run breakdown.
 *
 * Receives shift result data from GameActivity via Intent extras.
 * Saves the shift to Firestore (shifts subcollection).
 * If new high score: HTTP PUT to Firebase Realtime Database REST API to update leaderboard.
 * Also updates player's total cash in Firestore.
 */
public class ShiftResultActivity extends AppCompatActivity {

    private static final String TAG = "ShiftResult";
    // Replace with your Firebase Realtime DB URL
    private static final String DB_URL = "https://nightrider-4066f-default-rtdb.firebaseio.com";

    public static final String EXTRA_ROUTE = "route";
    public static final String EXTRA_CASH = "cash";
    public static final String EXTRA_SCORE = "score";
    public static final String EXTRA_STARS = "stars";
    public static final String EXTRA_TIME_MS = "timeMs";

    private ActivityShiftResultBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShiftResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String route = getIntent().getStringExtra(EXTRA_ROUTE);
        int cash = getIntent().getIntExtra(EXTRA_CASH, 0);
        long score = getIntent().getLongExtra(EXTRA_SCORE, 0);
        int stars = getIntent().getIntExtra(EXTRA_STARS, 1);
        long timeMs = getIntent().getLongExtra(EXTRA_TIME_MS, 0);

        binding.tvResultRoute.setText(route != null ? route : "Unknown Route");
        binding.tvResultStars.setText(starsString(stars));
        binding.tvResultCash.setText(cash + " coins");
        binding.tvResultScore.setText(String.valueOf(score));
        binding.tvResultTime.setText(formatTime(timeMs));

        // Save shift to Firestore + update leaderboard via REST
        saveShift(route, cash, score, stars, timeMs);

        binding.btnPlayAgain.setOnClickListener(v -> {
            startActivity(new Intent(this, ContractBoardActivity.class));
            finish();
        });

        binding.btnMainMenu.setOnClickListener(v -> {
            startActivity(new Intent(this, MainMenuActivity.class));
            finish();
        });
    }

    private void saveShift(String route, int cash, long score, int stars, long timeMs) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Save shift record
        Map<String, Object> shift = new HashMap<>();
        shift.put("route", route);
        shift.put("cash", cash);
        shift.put("score", score);
        shift.put("stars", stars);
        shift.put("timeMs", timeMs);
        shift.put("date", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("users").document(user.getUid())
                .collection("shifts").add(shift)
                .addOnSuccessListener(ref -> Log.d(TAG, "Shift saved: " + ref.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "Shift save failed", e));

        // 2. Update total cash
        db.collection("users").document(user.getUid())
                .update("cash", com.google.firebase.firestore.FieldValue.increment(cash));

        // 3. If high score, PUT to Realtime Database leaderboard via raw HTTP
        long previousBest = SaveStore.getBestScore(getApplicationContext());
        if (score > previousBest) {
            SaveStore.setBestScore(getApplicationContext(), score);
            pushLeaderboardEntry(user.getUid(),
                    user.isAnonymous() ? "Guest" : user.getDisplayName(), score);
        }
    }

    /**
     * HTTP PUT to Firebase Realtime Database REST API - this is the networking requirement.
     * Logs the JSON payload and response code for the academic report screenshot.
     */
    private void pushLeaderboardEntry(String uid, String name, long score) {
        executor.execute(() -> {
            try {
                String endpoint = DB_URL + "/leaderboard/" + uid + ".json";
                JSONObject payload = new JSONObject();
                payload.put("displayName", name != null ? name : "Courier");
                payload.put("score", score);
                payload.put("district", "Neo-Downtown");

                byte[] body = payload.toString().getBytes("UTF-8");
                Log.d(TAG, "PUT " + endpoint + " body=" + payload); // <- screenshot for report

                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                OutputStream os = conn.getOutputStream();
                os.write(body);
                os.close();

                int code = conn.getResponseCode();
                Log.d(TAG, "PUT response: " + code); // <- screenshot for report
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Leaderboard PUT failed", e);
            }
        });
    }

    private String starsString(int stars) {
        return "\u2605".repeat(Math.max(0, stars)) + "\u2606".repeat(Math.max(0, 3 - stars));
    }

    private String formatTime(long ms) {
        long secs = ms / 1000;
        return String.format("%d:%02d", secs / 60, secs % 60);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
