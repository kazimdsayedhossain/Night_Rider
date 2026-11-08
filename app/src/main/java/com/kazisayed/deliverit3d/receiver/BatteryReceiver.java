package com.kazisayed.deliverit3d.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BatteryReceiver extends BroadcastReceiver {

    public interface OnBatteryStatusListener {
        void onBatteryLow();
        void onPowerConnected();
    }

    private final OnBatteryStatusListener listener;

    public BatteryReceiver(OnBatteryStatusListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            if (listener != null) listener.onBatteryLow();
        } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            if (listener != null) listener.onPowerConnected();
        }
    }
}
