package com.foodrush.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import com.foodrush.app.util.Connectivity;

/**
 * Registered at runtime by each screen in onStart and removed in onStop. Dynamic registration is
 * required here: since API 26 most implicit broadcasts never reach manifest-declared receivers.
 */
public class ConnectivityReceiver extends BroadcastReceiver {

    public interface Listener {
        void onConnectivityChanged(boolean online, boolean airplaneMode);
    }

    private final Listener listener;

    public ConnectivityReceiver(Listener listener) {
        this.listener = listener;
    }

    @SuppressWarnings("deprecation")
    public void register(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean airplane = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        listener.onConnectivityChanged(Connectivity.isOnline(context), airplane);
    }
}
