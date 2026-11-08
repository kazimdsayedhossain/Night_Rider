package com.kazisayed.deliverit3d.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkReceiver extends BroadcastReceiver {

    public interface OnNetworkChangeListener {
        void onNetworkChanged(boolean isOnline);
    }

    private final OnNetworkChangeListener listener;

    public NetworkReceiver(OnNetworkChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
        boolean online = info != null && info.isConnected();
        if (listener != null) {
            listener.onNetworkChanged(online);
        }
    }
}
