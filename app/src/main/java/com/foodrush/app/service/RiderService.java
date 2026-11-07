package com.foodrush.app.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.foodrush.app.R;
import com.foodrush.app.call.CommsWatcher;
import com.foodrush.app.data.Cloud;
import com.foodrush.app.data.RiderPresence;
import com.foodrush.app.data.Session;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Order;
import com.foodrush.app.order.OrderLogic;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.ui.rider.RiderHomeActivity;
import com.foodrush.app.util.NotificationHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs while a rider is online: shares the phone's GPS position (every few seconds while delivering),
 * announces new delivery jobs nearby and keeps chat and call alerts alive. Foreground with the
 * "location" type, started from the rider's screen, so no background-location permission is needed.
 */
public class RiderService extends Service {

    private static final String TAG = "RiderService";
    private static final long FIX_MILLIS = 5_000;
    private static final float FIX_METERS = 10;
    private static final long ORDER_WRITE_MILLIS = 5_000;
    private static final long RIDER_WRITE_MILLIS = 15_000;
    /** Jobs farther than this from the rider are not announced. */
    public static final double JOB_RADIUS_METERS = 8_000;

    private static volatile LatLon lastFix;

    private FusedLocationProviderClient location;
    private OrderStore.Subscription mine;
    private OrderStore.Subscription open;
    private CommsWatcher comms;
    private Order active;
    private final Set<String> announced = new HashSet<>();
    private long lastOrderWrite;
    private long lastRiderWrite;
    private boolean arrivingSent;
    private String uid;

    private final LocationCallback callback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult result) {
            Location fix = result.getLastLocation();
            if (fix != null) {
                onFix(new LatLon(fix.getLatitude(), fix.getLongitude()));
            }
        }
    };

    /** The rider's latest GPS position, or null before the first fix. */
    @Nullable
    public static LatLon lastFix() {
        return lastFix;
    }

    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context, new Intent(context, RiderService.class));
        } catch (IllegalStateException e) {
            Log.w(TAG, "Could not go online now", e);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, RiderService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED || !Cloud.isOn(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0;
        ServiceCompat.startForeground(this, NotificationHelper.RIDER_FOREGROUND_ID,
                NotificationHelper.work(this, RiderHomeActivity.class, getString(R.string.rider_online_title),
                        getString(R.string.rider_online_text)).build(), type);
        if (uid == null) {
            uid = new Session(this).uid();
            startLocation();
            mine = OrderStore.watchRider(this, uid, this::onMyOrders);
            open = OrderStore.watchUnassigned(this, this::onOpenOrders);
            comms = new CommsWatcher(this, uid);
            comms.start();
            setOnline(true);
        }
        return START_NOT_STICKY;
    }

    /** Started only after the screen confirmed the location permission. */
    @SuppressLint("MissingPermission")
    private void startLocation() {
        location = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, FIX_MILLIS)
                .setMinUpdateDistanceMeters(FIX_METERS)
                .build();
        location.requestLocationUpdates(request, callback, Looper.getMainLooper());
        location.getLastLocation().addOnSuccessListener(fix -> {
            if (fix != null) {
                onFix(new LatLon(fix.getLatitude(), fix.getLongitude()));
            }
        });
    }

    private void onFix(LatLon fix) {
        lastFix = fix;
        long now = System.currentTimeMillis();
        if (now - lastRiderWrite >= RIDER_WRITE_MILLIS) {
            lastRiderWrite = now;
            Map<String, Object> rider = new HashMap<>();
            rider.put("online", true);
            rider.put("lat", fix.lat);
            rider.put("lon", fix.lon);
            rider.put("at", now);
            Cloud.db().collection(Cloud.RIDERS).document(uid).set(rider, SetOptions.merge());
        }
        if (active == null) {
            return;
        }
        if (now - lastOrderWrite >= ORDER_WRITE_MILLIS) {
            lastOrderWrite = now;
            Map<String, Object> position = new HashMap<>();
            position.put("riderLat", fix.lat);
            position.put("riderLon", fix.lon);
            position.put("riderAt", now);
            OrderStore.update(active.id, position);
        }
        if (active.pickedUpAt > 0 && !active.arriving && !arrivingSent
                && fix.metersTo(active.customer()) <= OrderLogic.ARRIVING_METERS) {
            arrivingSent = true;
            OrderStore.mutate(this, active.id, o -> OrderLogic.riderMoved(o, fix, System.currentTimeMillis()), null);
        }
    }

    private void onMyOrders(List<Order> orders) {
        Order current = null;
        List<String> chats = new ArrayList<>();
        for (Order order : orders) {
            if (order.isActive()) {
                if (current == null) {
                    current = order;
                }
                chats.add(order.id);
            }
        }
        if (current == null || active == null || !current.id.equals(active.id)) {
            arrivingSent = false;
        }
        active = current;
        comms.watchChats(chats);
    }

    private void onOpenOrders(List<Order> orders) {
        LatLon here = lastFix;
        int fresh = 0;
        for (Order order : orders) {
            if (!order.isActive() || order.status < Order.ACCEPTED || order.kitchens.isEmpty()) {
                continue;
            }
            LatLon pickup = order.kitchens.get(0).location();
            if (here != null && here.metersTo(pickup) > JOB_RADIUS_METERS) {
                continue;
            }
            if (announced.add(order.id)) {
                fresh++;
            }
        }
        if (fresh > 0 && active == null) {
            NotificationHelper.newDelivery(this, fresh);
        }
    }

    private void setOnline(boolean online) {
        Map<String, Object> rider = new HashMap<>();
        rider.put("online", online);
        rider.put("name", new Session(this).name());
        rider.put("at", System.currentTimeMillis());
        Cloud.db().collection(Cloud.RIDERS).document(uid).set(rider, SetOptions.merge());
        if (online) {
            RiderPresence.goOnline(this, uid, new Session(this).name());
        } else {
            RiderPresence.goOffline(this, uid);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (location != null) {
            location.removeLocationUpdates(callback);
        }
        if (mine != null) {
            mine.remove();
        }
        if (open != null) {
            open.remove();
        }
        if (comms != null) {
            comms.stop();
        }
        if (uid != null) {
            setOnline(false);
        }
        super.onDestroy();
    }
}
