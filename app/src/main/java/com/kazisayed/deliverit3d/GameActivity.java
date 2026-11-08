package com.kazisayed.deliverit3d;

import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.kazisayed.deliverit3d.receiver.BatteryReceiver;
import com.kazisayed.deliverit3d.receiver.HeadsetReceiver;
import com.kazisayed.deliverit3d.service.ShiftTrackerService;

public class GameActivity extends MainActivity {

    public static final String EXTRA_CONTRACT_ROUTE = "extra_route";
    public static final String EXTRA_CONTRACT_PAYOUT = "extra_payout";
    public static final String EXTRA_CONTRACT_TIME = "extra_time";
    public static final String EXTRA_CONTRACT_CARGO = "extra_cargo";

    private BatteryReceiver batteryReceiver;
    private HeadsetReceiver headsetReceiver;

    private String contractRoute = "Downtown Circuit";
    private int contractPayout = 350;
    private int contractTime = 120;
    private String contractCargo = "Standard Parcel";

    private final Handler checkerHandler = new Handler(Looper.getMainLooper());
    private boolean shiftResultShown = false;

    private final Runnable completionChecker = new Runnable() {
        @Override
        public void run() {
            checkShiftCompletion();
            if (!shiftResultShown) {
                checkerHandler.postDelayed(this, 100);
            }
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Read extras
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_CONTRACT_ROUTE)) {
                contractRoute = intent.getStringExtra(EXTRA_CONTRACT_ROUTE);
            }
            contractPayout = intent.getIntExtra(EXTRA_CONTRACT_PAYOUT, 350);
            contractTime = intent.getIntExtra(EXTRA_CONTRACT_TIME, 120);
            if (intent.hasExtra(EXTRA_CONTRACT_CARGO)) {
                contractCargo = intent.getStringExtra(EXTRA_CONTRACT_CARGO);
            }
        }

        // Start shift tracker foreground service
        startShiftTrackerService();

        // Register broadcast receivers
        registerReceivers();

        // Begin polling for shift completion
        checkerHandler.postDelayed(completionChecker, 1000);
    }

    private void startShiftTrackerService() {
        Intent serviceIntent = new Intent(this, ShiftTrackerService.class);
        serviceIntent.setAction(ShiftTrackerService.ACTION_START_SHIFT);
        serviceIntent.putExtra(ShiftTrackerService.EXTRA_ROUTE_NAME, contractRoute);
        serviceIntent.putExtra(ShiftTrackerService.EXTRA_TOTAL_SECONDS, contractTime);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopShiftTrackerService() {
        Intent serviceIntent = new Intent(this, ShiftTrackerService.class);
        serviceIntent.setAction(ShiftTrackerService.ACTION_STOP_SHIFT);
        startService(serviceIntent);
    }

    private void registerReceivers() {
        batteryReceiver = new BatteryReceiver(new BatteryReceiver.OnBatteryStatusListener() {
            @Override
            public void onBatteryLow() {
                Toast.makeText(GameActivity.this, "Low Battery: Bike in Eco Mode", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPowerConnected() {
                Toast.makeText(GameActivity.this, "Power Connected: Full Boost", Toast.LENGTH_SHORT).show();
            }
        });
        IntentFilter bFilter = new IntentFilter();
        bFilter.addAction(Intent.ACTION_BATTERY_LOW);
        bFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        registerReceiver(batteryReceiver, bFilter);

        headsetReceiver = new HeadsetReceiver(() -> {
            Toast.makeText(GameActivity.this, "Audio disconnected: Game paused", Toast.LENGTH_SHORT).show();
            command("pause");
        });
        registerReceiver(headsetReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    private void checkShiftCompletion() {
        if (shiftResultShown || game == null) return;
        DispatchGame.Snapshot snap = game.snapshot;
        if (snap != null && snap.state() == DispatchGame.State.RESULT) {
            shiftResultShown = true;
            stopShiftTrackerService();

            Intent resultIntent = new Intent(this, ShiftResultActivity.class);
            resultIntent.putExtra(ShiftResultActivity.EXTRA_ROUTE, contractRoute);
            resultIntent.putExtra(ShiftResultActivity.EXTRA_CASH, contractPayout);
            resultIntent.putExtra(ShiftResultActivity.EXTRA_SCORE, (long)(snap.score() > 0 ? snap.score() : contractPayout * 10L));
            resultIntent.putExtra(ShiftResultActivity.EXTRA_STARS, snap.stars() > 0 ? snap.stars() : (snap.won() ? 3 : 1));
            resultIntent.putExtra(ShiftResultActivity.EXTRA_TIME_MS, (long)(snap.time() * 1000f));
            startActivity(resultIntent);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        checkerHandler.removeCallbacks(completionChecker);
        stopShiftTrackerService();
        if (batteryReceiver != null) {
            try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
        }
        if (headsetReceiver != null) {
            try { unregisterReceiver(headsetReceiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
