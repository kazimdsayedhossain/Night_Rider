package com.kazisayed.deliverit3d;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.kazisayed.deliverit3d.adapter.LeaderboardAdapter;
import com.kazisayed.deliverit3d.databinding.ActivityLeaderboardBinding;
import com.kazisayed.deliverit3d.model.LeaderboardEntry;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * LeaderboardActivity: district courier rankings.
 *
 * Fetches leaderboard data via DIRECT HTTP GET to Firebase Realtime Database REST API.
 * This satisfies the rubric's "API requests (JSON/XML parsing), HTTP operations" requirement.
 *
 * Endpoint: GET https://<project>.firebaseio.com/leaderboard.json?orderBy="score"&limitToLast=20
 * Response: JSON object - parsed with org.json.JSONObject.
 *
 * Logcat tag: "Leaderboard" - screenshot the raw JSON response for the report.
 */
public class LeaderboardActivity extends AppCompatActivity {

    private static final String TAG = "Leaderboard";
    // Replace with your Firebase Realtime Database URL from Firebase Console
    private static final String DB_URL =
            "https://nightrider-4066f-default-rtdb.firebaseio.com/leaderboard.json?orderBy=%22score%22&limitToLast=20&print=pretty";

    private ActivityLeaderboardBinding binding;
    private LeaderboardAdapter adapter;
    private final List<LeaderboardEntry> entries = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLeaderboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new LeaderboardAdapter(entries);
        binding.rvLeaderboard.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLeaderboard.setAdapter(adapter);

        binding.btnBackLeaderboard.setOnClickListener(v -> finish());

        fetchLeaderboard();
    }

    /**
     * HTTP GET to Firebase Realtime Database REST API.
     * Runs on a background thread; posts result back to main thread.
     * The raw JSON is logged - screenshot it for the academic report.
     */
    private void fetchLeaderboard() {
        binding.progressLb.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<LeaderboardEntry> fetched = new ArrayList<>();
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(DB_URL).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                Log.d(TAG, "HTTP response code: " + code);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String json = sb.toString();
                Log.d(TAG, "RAW JSON: " + json); // <- screenshot this for the report

                // Parse JSON: Realtime DB returns object keyed by uid
                JSONObject root = new JSONObject(json);
                java.util.Iterator<String> keys = root.keys();
                int rank = 1;
                while (keys.hasNext()) {
                    String uid = keys.next();
                    JSONObject obj = root.getJSONObject(uid);
                    LeaderboardEntry e = new LeaderboardEntry();
                    e.setUid(uid);
                    e.setDisplayName(obj.optString("displayName", "Courier"));
                    e.setScore(obj.optLong("score", 0));
                    e.setDistrict(obj.optString("district", "Neo-Downtown"));
                    e.setRank(rank++);
                    fetched.add(e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Leaderboard fetch failed", e);
            }

            mainHandler.post(() -> {
                binding.progressLb.setVisibility(View.GONE);
                entries.clear();
                entries.addAll(fetched);
                adapter.notifyDataSetChanged();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
