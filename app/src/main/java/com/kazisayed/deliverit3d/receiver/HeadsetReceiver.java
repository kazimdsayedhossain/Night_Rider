package com.kazisayed.deliverit3d.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

public class HeadsetReceiver extends BroadcastReceiver {

    public interface OnHeadsetUnpluggedListener {
        void onHeadsetUnplugged();
    }

    private final OnHeadsetUnpluggedListener listener;

    public HeadsetReceiver(OnHeadsetUnpluggedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
            if (listener != null) listener.onHeadsetUnplugged();
        }
    }
}
