package com.kazisayed.deliverit3d;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.kazisayed.deliverit3d.databinding.ActivityMainMenuBinding;
import com.kazisayed.deliverit3d.receiver.NetworkReceiver;

/**
 * MainMenuActivity: Courier HQ hub.
 *
 * Shows the player's cash balance from Firestore (or local LevelDB when offline),
 * and routes to: ContractBoardActivity, GarageActivity, LeaderboardActivity,
 * ShiftLogActivity, ProfileActivity.
 *
 * Also registers NetworkReceiver dynamically to detect offline mode transitions.
 */
public class MainMenuActivity extends AppCompatActivity {

    private ActivityMainMenuBinding binding;
    private NetworkReceiver networkReceiver;
    private FirebaseFirestore db;
    private FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainMenuBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        user = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        setupButtons();
        loadPlayerProfile();
        checkConnectivity();

        // Register network receiver to show/hide the OFFLINE badge dynamically
        networkReceiver = new NetworkReceiver(isOnline -> {
            runOnUiThread(() -> binding.tvOfflineBadge.setVisibility(
                    isOnline ? View.GONE : View.VISIBLE));
        });
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
    }

    private void setupButtons() {
        binding.btnTakeJob.setOnClickListener(v ->
                startActivity(new Intent(this, ContractBoardActivity.class)));

        binding.btnGarage.setOnClickListener(v ->
                startActivity(new Intent(this, GarageActivity.class)));

        binding.btnLeaderboard.setOnClickListener(v ->
                startActivity(new Intent(this, LeaderboardActivity.class)));

        binding.btnShiftLog.setOnClickListener(v ->
                startActivity(new Intent(this, ShiftLogActivity.class)));

        binding.btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void loadPlayerProfile() {
        if (user == null) return;
        String displayName = user.isAnonymous() ? "Guest Courier" :
                (user.getDisplayName() != null ? user.getDisplayName() : "Courier");
        binding.tvCourierName.setText(displayName);

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(this, (DocumentSnapshot doc) -> {
                    if (doc.exists()) {
                        Long cash = doc.getLong("cash");
                        binding.tvCash.setText((cash != null ? cash : 0) + " coins");
                    }
                })
                .addOnFailureListener(e -> {
                    // Offline: show cached value from LevelDB
                    int cachedCash = SaveStore.getCash(getApplicationContext());
                    binding.tvCash.setText(cachedCash + " coins (cached)");
                });
    }

    private void checkConnectivity() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        boolean online = info != null && info.isConnected();
        binding.tvOfflineBadge.setVisibility(online ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkReceiver != null) unregisterReceiver(networkReceiver);
    }
}
