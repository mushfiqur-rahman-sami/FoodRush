package com.foodrush.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.foodrush.app.data.OrderRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.model.Order;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.NotificationHelper;

/**
 * Manifest-declared, because the app is not running at boot. Android 15 forbids starting a dataSync
 * foreground service from BOOT_COMPLETED, so this reminds the customer of orders still on the way;
 * opening the notification restarts tracking.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Session session = new Session(context);
        if (!session.isSignedIn() || !session.isCustomer()) {
            return;
        }
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        AppExecutors.io(() -> {
            try {
                for (Order order : OrderRepository.activeForCustomer(app, session.uid())) {
                    NotificationHelper.orderStatus(app, order);
                }
            } finally {
                pending.finish();
            }
        });
    }
}
