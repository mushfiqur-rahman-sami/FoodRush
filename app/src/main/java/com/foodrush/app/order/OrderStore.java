package com.foodrush.app.order;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.foodrush.app.data.Cloud;
import com.foodrush.app.data.FoodRushContract;
import com.foodrush.app.data.OrderRepository;
import com.foodrush.app.model.Order;
import com.foodrush.app.util.AppExecutors;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Where orders live. With Firebase, Firestore is the truth and every order seen is copied into the
 * local content provider; changes go through transactions so the customer's, restaurant's and
 * rider's phones never overwrite each other. Without Firebase, the content provider is the only store.
 * Callbacks run on the main thread.
 */
public final class OrderStore {

    private static final String TAG = "OrderStore";

    public interface Mutation {
        /** Changes the order in place; return false to leave it untouched. */
        boolean apply(Order order);
    }

    public interface Done {
        void onDone(boolean changed);
    }

    public interface Subscription {
        void remove();
    }

    private OrderStore() {
    }

    public static boolean cloud(Context context) {
        return Cloud.isOn(context);
    }

    private static DocumentReference ref(String id) {
        return Cloud.db().collection(Cloud.ORDERS).document(id);
    }

    public static void place(Context context, Order order, String pin, String customerPhone, Done done) {
        Context app = context.getApplicationContext();
        if (!cloud(app)) {
            AppExecutors.load(() -> {
                OrderRepository.save(app, order);
                return true;
            }, done::onDone);
            return;
        }
        FirebaseFirestore db = Cloud.db();
        WriteBatch batch = db.batch();
        batch.set(ref(order.id), order.toMap());
        Map<String, Object> contact = new HashMap<>();
        contact.put("customerPhone", customerPhone);
        contact.put("customerName", order.customerName);
        contact.put("riderPhone", "");
        batch.set(ref(order.id).collection(Cloud.PRIVATE).document("contact"), contact);
        Map<String, Object> secret = new HashMap<>();
        secret.put("pin", pin);
        batch.set(ref(order.id).collection(Cloud.PRIVATE).document("pin"), secret);
        batch.commit()
                .addOnSuccessListener(v -> AppExecutors.load(() -> {
                    OrderRepository.save(app, order);
                    return true;
                }, done::onDone))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "place failed", e);
                    done.onDone(false);
                });
    }

    /** Read-change-write. In the cloud only the fields that changed are sent, inside a transaction. */
    public static void mutate(Context context, String orderId, Mutation mutation, Done done) {
        Context app = context.getApplicationContext();
        Done callback = done == null ? changed -> { } : done;
        if (!cloud(app)) {
            AppExecutors.load(() -> {
                Order order = OrderRepository.find(app, orderId);
                if (order == null || !mutation.apply(order)) {
                    return false;
                }
                OrderRepository.save(app, order);
                return true;
            }, callback::onDone);
            return;
        }
        DocumentReference ref = ref(orderId);
        Order[] saved = new Order[1];
        Cloud.db().runTransaction(tx -> {
            DocumentSnapshot snapshot = tx.get(ref);
            if (!snapshot.exists() || snapshot.getData() == null) {
                return false;
            }
            Order before = Order.from(snapshot.getData());
            Order after = before.copy();
            if (!mutation.apply(after)) {
                return false;
            }
            Map<String, Object> changes = diff(before.toMap(), after.toMap());
            if (changes.isEmpty()) {
                return false;
            }
            tx.update(ref, changes);
            saved[0] = after;
            return true;
        }).addOnSuccessListener(changed -> {
            if (Boolean.TRUE.equals(changed) && saved[0] != null) {
                Order order = saved[0];
                AppExecutors.io(() -> OrderRepository.save(app, order));
            }
            callback.onDone(Boolean.TRUE.equals(changed));
        }).addOnFailureListener(e -> {
            Log.w(TAG, "mutate failed", e);
            callback.onDone(false);
        });
    }

    static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new HashMap<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            if (!Objects.equals(before.get(entry.getKey()), entry.getValue())) {
                changes.put(entry.getKey(), entry.getValue());
            }
        }
        return changes;
    }

    /** A plain write for frequent, uncontested fields such as the rider's position. */
    public static void update(String orderId, Map<String, Object> fields) {
        ref(orderId).update(fields).addOnFailureListener(e -> Log.w(TAG, "update failed", e));
    }

    // ---- Watching ----

    public static Subscription watch(Context context, String orderId, Consumer<Order> onOrder) {
        Context app = context.getApplicationContext();
        if (!cloud(app)) {
            Uri uri = Uri.withAppendedPath(FoodRushContract.Orders.URI, orderId);
            Runnable reload = () -> AppExecutors.load(() -> OrderRepository.find(app, orderId), order -> {
                if (order != null) {
                    onOrder.accept(order);
                }
            });
            return observe(app, uri, reload);
        }
        ListenerRegistration registration = ref(orderId).addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "watch failed", e);
            } else if (snapshot != null && snapshot.exists() && snapshot.getData() != null) {
                Order order = Order.from(snapshot.getData());
                AppExecutors.io(() -> OrderRepository.save(app, order));
                onOrder.accept(order);
            }
        });
        return registration::remove;
    }

    public static Subscription watchCustomer(Context context, String uid, Consumer<List<Order>> onOrders) {
        Context app = context.getApplicationContext();
        if (!cloud(app)) {
            return observe(app, FoodRushContract.Orders.URI, () -> AppExecutors.load(
                    () -> OrderRepository.forCustomer(app, uid), onOrders::accept));
        }
        return watchQuery(app, Cloud.db().collection(Cloud.ORDERS).whereEqualTo("customerUid", uid), onOrders);
    }

    /** Orders of the restaurants this account owns, newest first. Cloud only. */
    public static Subscription watchRestaurant(Context context, String ownerUid, Consumer<List<Order>> onOrders) {
        return watchQuery(context.getApplicationContext(),
                Cloud.db().collection(Cloud.ORDERS).whereArrayContains("ownerUids", ownerUid), onOrders);
    }

    /** Orders no rider has taken yet. Cloud only. */
    public static Subscription watchUnassigned(Context context, Consumer<List<Order>> onOrders) {
        return watchQuery(context.getApplicationContext(),
                Cloud.db().collection(Cloud.ORDERS).whereEqualTo("riderUid", null), onOrders);
    }

    public static Subscription watchRider(Context context, String riderUid, Consumer<List<Order>> onOrders) {
        return watchQuery(context.getApplicationContext(),
                Cloud.db().collection(Cloud.ORDERS).whereEqualTo("riderUid", riderUid), onOrders);
    }

    private static Subscription watchQuery(Context app, Query query, Consumer<List<Order>> onOrders) {
        ListenerRegistration registration = query.addSnapshotListener((result, e) -> {
            if (e != null) {
                Log.w(TAG, "query failed", e);
                return;
            }
            if (result == null) {
                return;
            }
            List<Order> orders = new ArrayList<>();
            for (QueryDocumentSnapshot doc : result) {
                orders.add(Order.from(doc.getData()));
            }
            Collections.sort(orders, (a, b) -> Long.compare(b.placedAt, a.placedAt));
            AppExecutors.io(() -> {
                for (Order order : orders) {
                    OrderRepository.save(app, order);
                }
            });
            onOrders.accept(orders);
        });
        return registration::remove;
    }

    private static Subscription observe(Context app, Uri uri, Runnable reload) {
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                reload.run();
            }
        };
        app.getContentResolver().registerContentObserver(uri, true, observer);
        reload.run();
        return () -> app.getContentResolver().unregisterContentObserver(observer);
    }

    // ---- Private details ----

    /** Phone numbers of the people on an order: customerPhone, customerName, riderPhone. */
    public static void contact(String orderId, Consumer<Map<String, Object>> onResult) {
        ref(orderId).collection(Cloud.PRIVATE).document("contact").get()
                .addOnSuccessListener(doc -> onResult.accept(doc.getData() == null ? new HashMap<>() : doc.getData()))
                .addOnFailureListener(e -> onResult.accept(new HashMap<>()));
    }

    public static void setRiderPhone(String orderId, String phone) {
        Map<String, Object> update = new HashMap<>();
        update.put("riderPhone", phone);
        ref(orderId).collection(Cloud.PRIVATE).document("contact").set(update, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "rider phone failed", e));
    }

    /** The delivery PIN, readable only by the customer. */
    public static void pin(String orderId, Consumer<String> onResult) {
        ref(orderId).collection(Cloud.PRIVATE).document("pin").get()
                .addOnSuccessListener(doc -> onResult.accept(doc.getString("pin")))
                .addOnFailureListener(e -> onResult.accept(null));
    }
}
