package com.kazisayed.deliverit3d.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.kazisayed.deliverit3d.R;

public class BatteryReceiver extends BroadcastReceiver {

    public static final String ALERT_CHANNEL_ID = "night_rider_alerts";
    public static final int BATTERY_NOTIFICATION_ID = 2002;

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
            postBatteryNotification(context, true);
            if (listener != null) listener.onBatteryLow();
        } else if (Intent.ACTION_BATTERY_OKAY.equals(action) || Intent.ACTION_POWER_CONNECTED.equals(action)) {
            postBatteryNotification(context, false);
            if (listener != null) listener.onPowerConnected();
        }
    }

    public static void checkInitialBatteryState(Context context, OnBatteryStatusListener listener) {
        try {
            Intent batteryStatus = context.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                if (!isCharging && scale > 0) {
                    float pct = (level * 100f) / scale;
                    if (pct <= 15f && listener != null) {
                        listener.onBatteryLow();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static void postBatteryNotification(Context context, boolean lowBattery) {
        if (context == null) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        ALERT_CHANNEL_ID,
                        "System & Power Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Alerts for battery status and game engine power saver");
                nm.createNotificationChannel(channel);
            }

            String title = lowBattery ? "Low Battery - Eco Mode (30 FPS)" : "Power Connected - Full Speed (60 FPS)";
            String text = lowBattery ? "Battery is low! Night Rider frame rate reduced to 30 FPS to save energy."
                                     : "Power connected. Restored full 60 FPS performance.";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);

            nm.notify(BATTERY_NOTIFICATION_ID, builder.build());
        } catch (Exception ignored) {}
    }
}
