package com.foodrush.app.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.foodrush.app.call.CommsWatcher;
import com.foodrush.app.data.OrderRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.model.Order;
import com.foodrush.app.order.OrderLogic;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.receiver.OrderStatusReceiver;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.NotificationHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The customer's side of an order, foreground while any order is active. It watches the order for
 * changes from the restaurant and rider, notifies on each new stage, runs the demo restaurant or
 * rider when no person answers in time, and keeps chat and call alerts alive. It is also a bound
 * service, so the tracking screen can read the latest copy straight away.
 */
public class OrderTrackingService extends Service {

    private static final String TAG = "OrderTrackingService";
    private static final long TICK_MILLIS = 1000;

    public final class LocalBinder extends Binder {
        public OrderTrackingService service() {
            return OrderTrackingService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Order> latest = new ConcurrentHashMap<>();
    private final Map<String, Integer> notifiedStatus = new ConcurrentHashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private OrderStore.Subscription subscription;
    private CommsWatcher comms;
    private boolean cloud;
    private boolean ticking;
    /** True once the first list of orders has arrived, so an empty list really means "nothing active". */
    private boolean loaded;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            advance();
            handler.postDelayed(this, TICK_MILLIS);
        }
    };

    /** Android allows this only while the app is in the foreground, so callers invoke it from a screen. */
    public static void startIfNeeded(Context context) {
        Session session = new Session(context);
        if (!session.isSignedIn() || !session.isCustomer()) {
            return;
        }
        String uid = session.uid();
        AppExecutors.load(() -> !OrderRepository.activeForCustomer(context, uid).isEmpty(), hasActive -> {
            if (hasActive) {
                try {
                    ContextCompat.startForegroundService(context, new Intent(context, OrderTrackingService.class));
                } catch (IllegalStateException e) {
                    Log.w(TAG, "Could not start tracking now", e);
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;
        ServiceCompat.startForeground(this, NotificationHelper.FOREGROUND_ID,
                NotificationHelper.foreground(this, null).build(), type);
        Session session = new Session(this);
        if (subscription == null && session.isSignedIn()) {
            cloud = OrderStore.cloud(this);
            subscription = OrderStore.watchCustomer(this, session.uid(), this::onOrders);
            if (cloud) {
                comms = new CommsWatcher(this, session.uid());
                comms.start();
            }
        }
        if (!ticking) {
            ticking = true;
            handler.post(tick);
        }
        // Everything is worked out from timestamps, so nothing is lost if the system stops the service.
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Nullable
    public Order latest(String orderId) {
        return latest.get(orderId);
    }

    private void onOrders(List<Order> orders) {
        loaded = true;
        List<String> withRider = new ArrayList<>();
        latest.clear();
        for (Order order : orders) {
            if (!order.isActive() && order.deliveredAt > 0 && System.currentTimeMillis() - order.deliveredAt > 60_000) {
                continue;
            }
            latest.put(order.id, order);
            Integer before = notifiedStatus.put(order.id, order.status);
            if (before != null && before != order.status) {
                NotificationHelper.orderStatus(this, order);
            }
            if (order.isActive() && order.hasRealRider()) {
                withRider.add(order.id);
            }
            OrderStatusReceiver.send(this, order.id, order.status);
        }
        if (comms != null) {
            comms.watchChats(withRider);
        }
    }

    private void advance() {
        long now = System.currentTimeMillis();
        boolean anyActive = false;
        for (Order order : latest.values()) {
            if (!order.isActive()) {
                continue;
            }
            anyActive = true;
            // Try the demo steps on a copy first, so the store is only touched when one is due.
            Order trial = order.copy();
            if (OrderLogic.simulate(trial, now, cloud) && inFlight.add(order.id)) {
                OrderStore.mutate(this, order.id, o -> OrderLogic.simulate(o, System.currentTimeMillis(), cloud),
                        changed -> inFlight.remove(order.id));
            }
            if (order.hasSimRider() && order.pickedUpAt > 0) {
                OrderStatusReceiver.send(this, order.id, order.status);
            }
        }
        if (loaded && !anyActive) {
            stopTracking();
            return;
        }
        Order first = null;
        for (Order order : latest.values()) {
            if (order.isActive()) {
                first = order;
                break;
            }
        }
        if (first != null) {
            NotificationHelper.post(this, NotificationHelper.FOREGROUND_ID, NotificationHelper.foreground(this, first));
        }
    }

    private void stopTracking() {
        handler.removeCallbacks(tick);
        ticking = false;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (subscription != null) {
            subscription.remove();
        }
        if (comms != null) {
            comms.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        // Android 15 caps dataSync services at six hours a day; screens restart tracking when opened.
        stopTracking();
    }
}
