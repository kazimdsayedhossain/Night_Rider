package com.kazisayed.deliverit3d.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.kazisayed.deliverit3d.GameActivity;
import com.kazisayed.deliverit3d.R;
import java.util.Locale;

public class ShiftTrackerService extends Service {

    public static final String ACTION_START_SHIFT = "com.kazisayed.deliverit3d.START_SHIFT";
    public static final String ACTION_STOP_SHIFT = "com.kazisayed.deliverit3d.STOP_SHIFT";

    public static final String EXTRA_ROUTE_NAME = "extra_route";
    public static final String EXTRA_TOTAL_SECONDS = "extra_seconds";

    private static final String CHANNEL_ID = "shift_tracker_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int secondsRemaining = 120;
    private String routeName = "Downtown Circuit";

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (secondsRemaining > 0) {
                secondsRemaining -= 5;
                updateNotification();
                handler.postDelayed(this, 5000);
            } else {
                stopSelf();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP_SHIFT.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            routeName = intent.getStringExtra(EXTRA_ROUTE_NAME);
            if (routeName == null) routeName = "Active Route";
            secondsRemaining = intent.getIntExtra(EXTRA_TOTAL_SECONDS, 120);
        }

        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);

        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 5000);

        return START_NOT_STICKY;
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, GameActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        int mins = secondsRemaining / 60;
        int secs = secondsRemaining % 60;
        String content = String.format(Locale.US, "%s | Time remaining: %d:%02d", routeName, mins, secs);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Night Rider: Shift Active")
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setContentIntent(pi)
                .addAction(R.mipmap.ic_launcher, "Return to Ride", pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Night Rider Shift Tracker",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active delivery route and status while shift is ongoing.");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
