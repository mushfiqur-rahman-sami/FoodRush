package com.foodrush.app.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.foodrush.app.R;
import com.foodrush.app.data.Cloud;
import com.foodrush.app.data.Session;
import com.foodrush.app.model.Order;
import com.foodrush.app.order.OrderLogic;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.ui.restaurant.KitchenActivity;
import com.foodrush.app.util.NotificationHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs while a restaurant is open for orders, so a new order rings the kitchen even when the app is
 * in the background. Without a paid push server, a foreground service is the free way to do this.
 */
public class KitchenService extends Service {

    private static final String TAG = "KitchenService";

    private OrderStore.Subscription subscription;
    private final Set<String> announced = new HashSet<>();
    private MediaPlayer alarm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<Order> latest = new java.util.ArrayList<>();
    private Runnable recheck;

    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context, new Intent(context, KitchenService.class));
        } catch (IllegalStateException e) {
            Log.w(TAG, "Could not start order alerts now", e);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, KitchenService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Session session = new Session(this);
        if (!Cloud.isOn(this) || session.restaurantId() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;
        ServiceCompat.startForeground(this, NotificationHelper.KITCHEN_FOREGROUND_ID,
                NotificationHelper.work(this, KitchenActivity.class, getString(R.string.kitchen_open_title),
                        getString(R.string.kitchen_open_text)).build(), type);
        if (subscription == null) {
            String restaurantId = session.restaurantId();
            subscription = OrderStore.watchRestaurant(this, session.uid(), orders -> {
                latest = orders;
                onOrders(orders, restaurantId);
            });
            // A scheduled order starts ringing when its cooking time comes, without any new change.
            recheck = () -> {
                onOrders(latest, restaurantId);
                handler.postDelayed(recheck, 60_000);
            };
            handler.postDelayed(recheck, 60_000);
        }
        return START_NOT_STICKY;
    }

    private void onOrders(List<Order> orders, String restaurantId) {
        boolean anyWaiting = false;
        long now = System.currentTimeMillis();
        for (Order order : orders) {
            Order.Kitchen kitchen = order.kitchen(restaurantId);
            boolean waiting = kitchen != null && kitchen.status == Order.Kitchen.NEW && order.isActive();
            // Each waiting order notifies once, including any already waiting when alerts start.
            if (waiting && announced.add(order.id)) {
                NotificationHelper.newKitchenOrder(this, order);
            }
            // Scheduled orders only ring once it's time to start cooking them.
            anyWaiting |= waiting && !OrderLogic.waitingForSchedule(order, now);
        }
        if (anyWaiting) {
            startAlarm();
        } else {
            stopAlarm();
        }
    }

    /** Rings in a loop until every new order has been accepted or rejected, so none is missed. */
    private void startAlarm() {
        if (alarm != null) {
            return;
        }
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (sound == null) {
            sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, sound);
            player.setLooping(true);
            player.prepare();
            player.start();
            alarm = player;
        } catch (Exception e) {
            Log.w(TAG, "Could not play the order alarm", e);
        }
    }

    private void stopAlarm() {
        if (alarm != null) {
            alarm.stop();
            alarm.release();
            alarm = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopAlarm();
        if (subscription != null) {
            subscription.remove();
        }
        super.onDestroy();
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        // Android 15 caps dataSync services at six hours a day; opening the kitchen screen restarts it.
        stopSelf();
    }
}
