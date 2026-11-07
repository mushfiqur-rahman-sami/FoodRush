package com.foodrush.app.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.foodrush.app.order.OrderStore;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Which riders are online right now, kept in Firebase Realtime Database under ridersOnline/{uid}.
 * Firebase removes a rider's entry by itself when their phone loses the connection.
 */
public final class RiderPresence {

    private static final String TAG = "RiderPresence";
    private static final String URL = "https://foodrush-4962f-default-rtdb.asia-southeast1.firebasedatabase.app";
    private static final String NODE = "ridersOnline";

    public interface CountListener {
        void onCount(int riders);
    }

    private RiderPresence() {
    }

    private static DatabaseReference riders() {
        return FirebaseDatabase.getInstance(URL).getReference(NODE);
    }

    /** Marks the rider online; the entry disappears on its own if the app or network goes away. */
    public static void goOnline(Context context, String uid, String name) {
        if (!Cloud.isOn(context) || uid == null) {
            return;
        }
        try {
            DatabaseReference me = riders().child(uid);
            me.onDisconnect().removeValue();
            Map<String, Object> value = new HashMap<>();
            value.put("name", name == null ? "" : name);
            value.put("since", ServerValue.TIMESTAMP);
            me.setValue(value);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not share online status", e);
        }
    }

    public static void goOffline(Context context, String uid) {
        if (!Cloud.isOn(context) || uid == null) {
            return;
        }
        try {
            DatabaseReference me = riders().child(uid);
            me.onDisconnect().cancel();
            me.removeValue();
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not clear online status", e);
        }
    }

    /** Live count of online riders; returns null when Firebase isn't set up. */
    public static OrderStore.Subscription watchCount(Context context, CountListener listener) {
        if (!Cloud.isOn(context)) {
            return null;
        }
        try {
            DatabaseReference ref = riders();
            ValueEventListener watcher = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    listener.onCount((int) snapshot.getChildrenCount());
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Online riders not readable: " + error.getMessage());
                    listener.onCount(-1);
                }
            };
            ref.addValueEventListener(watcher);
            return () -> ref.removeEventListener(watcher);
        } catch (RuntimeException e) {
            Log.w(TAG, "Realtime Database not available", e);
            return null;
        }
    }
}
