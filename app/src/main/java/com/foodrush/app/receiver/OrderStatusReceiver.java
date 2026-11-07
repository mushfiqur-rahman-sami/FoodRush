package com.foodrush.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

/**
 * App-internal broadcast from the tracking service to whichever screen is open: "this order moved
 * on". Sent every second while a demo rider drives, so the map can redraw the rider.
 */
public class OrderStatusReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.foodrush.app.ORDER_PROGRESS";
    private static final String EXTRA_ORDER = "order";
    private static final String EXTRA_STATUS = "status";

    public interface Listener {
        void onOrderProgress(String orderId, int status);
    }

    private final Listener listener;

    public OrderStatusReceiver(Listener listener) {
        this.listener = listener;
    }

    public static void send(Context context, String orderId, int status) {
        context.sendBroadcast(new Intent(ACTION)
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_ORDER, orderId)
                .putExtra(EXTRA_STATUS, status));
    }

    public void register(Context context) {
        ContextCompat.registerReceiver(context, this, new IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        listener.onOrderProgress(intent.getStringExtra(EXTRA_ORDER), intent.getIntExtra(EXTRA_STATUS, 0));
    }
}
