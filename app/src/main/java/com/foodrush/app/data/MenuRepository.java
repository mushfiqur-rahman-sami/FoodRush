package com.foodrush.app.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.foodrush.app.model.Dish;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Promo;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.util.AppExecutors;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Restaurants, dishes and coupons. With Firebase this reads Firestore, uploading the bundled menu the
 * first time; restaurant owners' edits then reach every phone. Without Firebase it serves the bundled
 * menu. Results are cached in memory, and callbacks always run on the main thread.
 */
public final class MenuRepository {

    public interface Listener<T> {
        void onResult(T result);

        void onError();
    }

    private static final String TAG = "MenuRepository";
    private static final int SEED_VERSION = 1;
    /** Firestore "in" queries take at most 30 values. */
    private static final int IN_LIMIT = 30;

    /** Holds only the application context, which lives as long as the app, so nothing leaks. */
    @SuppressLint("StaticFieldLeak")
    private static MenuRepository instance;

    private final Context app;
    private final Map<String, Restaurant> restaurants = new LinkedHashMap<>();
    private final Map<String, Dish> dishes = new HashMap<>();
    private boolean restaurantsLoaded;

    public static synchronized MenuRepository get(Context context) {
        if (instance == null) {
            instance = new MenuRepository(context.getApplicationContext());
        }
        return instance;
    }

    private MenuRepository(Context app) {
        this.app = app;
    }

    private boolean cloud() {
        return Cloud.isOn(app);
    }

    // ---- Restaurants ----

    public synchronized Restaurant restaurant(String id) {
        return id == null ? null : restaurants.get(id);
    }

    public void restaurants(boolean refresh, Listener<List<Restaurant>> listener) {
        synchronized (this) {
            if (restaurantsLoaded && !refresh) {
                List<Restaurant> cached = new ArrayList<>(restaurants.values());
                AppExecutors.main(() -> listener.onResult(cached));
                return;
            }
        }
        if (!cloud()) {
            AppExecutors.io(() -> {
                XmlDataParser.Seed seed = Catalog.get(app).seed;
                remember(seed.restaurants, seed.dishes);
                List<Restaurant> list = new ArrayList<>(seed.restaurants);
                AppExecutors.main(() -> listener.onResult(list));
            });
            return;
        }
        ensureSeeded(() -> Cloud.db().collection(Cloud.RESTAURANTS).get()
                .addOnSuccessListener(result -> {
                    List<Restaurant> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : result) {
                        Restaurant r = Restaurant.from(doc.getData());
                        r.id = doc.getId();
                        list.add(r);
                    }
                    if (list.isEmpty()) {
                        // Seeding was refused (old rules); show the bundled menu read-only.
                        list.addAll(Catalog.get(app).seed.restaurants);
                    }
                    remember(list, null);
                    listener.onResult(list);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "restaurants failed", e);
                    listener.onError();
                }));
    }

    /** Restaurants that deliver to {@code customer}, nearest first. When none do, the nearest few. */
    public static List<Restaurant> deliveringTo(List<Restaurant> all, LatLon customer) {
        List<Restaurant> sorted = new ArrayList<>(all);
        Collections.sort(sorted, Comparator.comparingDouble(r -> r.location().metersTo(customer)));
        List<Restaurant> inRange = new ArrayList<>();
        for (Restaurant r : sorted) {
            if (delivers(r, customer)) {
                inRange.add(r);
            }
        }
        return inRange;
    }

    public static boolean delivers(Restaurant r, LatLon customer) {
        return r.location().metersTo(customer) <= r.radiusKm * 1000.0;
    }

    public void restaurant(String id, Listener<Restaurant> listener) {
        Restaurant cached = restaurant(id);
        if (cached != null || !cloud()) {
            if (cached == null) {
                restaurants(false, new Listener<List<Restaurant>>() {
                    @Override
                    public void onResult(List<Restaurant> result) {
                        Restaurant found = restaurant(id);
                        if (found == null) {
                            listener.onError();
                        } else {
                            listener.onResult(found);
                        }
                    }

                    @Override
                    public void onError() {
                        listener.onError();
                    }
                });
            } else {
                AppExecutors.main(() -> listener.onResult(cached));
            }
            return;
        }
        Cloud.db().collection(Cloud.RESTAURANTS).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || doc.getData() == null) {
                        listener.onError();
                        return;
                    }
                    Restaurant r = Restaurant.from(doc.getData());
                    r.id = doc.getId();
                    remember(Collections.singletonList(r), null);
                    listener.onResult(r);
                })
                .addOnFailureListener(e -> listener.onError());
    }

    public void saveRestaurant(Restaurant restaurant, Listener<Void> listener) {
        Cloud.db().collection(Cloud.RESTAURANTS).document(restaurant.id).set(restaurant.toMap())
                .addOnSuccessListener(v -> {
                    remember(Collections.singletonList(restaurant), null);
                    listener.onResult(null);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "saveRestaurant failed", e);
                    listener.onError();
                });
    }

    // ---- Dishes ----

    public synchronized Dish dish(String id) {
        return dishes.get(id);
    }

    public void dish(String id, Listener<Dish> listener) {
        Dish cached = dish(id);
        if (cached != null) {
            AppExecutors.main(() -> listener.onResult(cached));
            return;
        }
        if (!cloud()) {
            restaurants(false, new Listener<List<Restaurant>>() {
                @Override
                public void onResult(List<Restaurant> result) {
                    Dish found = dish(id);
                    if (found == null) {
                        listener.onError();
                    } else {
                        listener.onResult(found);
                    }
                }

                @Override
                public void onError() {
                    listener.onError();
                }
            });
            return;
        }
        Cloud.db().collection(Cloud.DISHES).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || doc.getData() == null) {
                        listener.onError();
                        return;
                    }
                    Dish dish = Dish.from(doc.getData());
                    remember(null, Collections.singletonList(dish));
                    listener.onResult(dish);
                })
                .addOnFailureListener(e -> listener.onError());
    }

    /** Dishes of these restaurants. Customers get only available ones; owners see everything. */
    public void dishesOf(Collection<String> restaurantIds, boolean includeUnavailable, Listener<List<Dish>> listener) {
        List<String> ids = new ArrayList<>(restaurantIds);
        if (ids.isEmpty()) {
            AppExecutors.main(() -> listener.onResult(new ArrayList<>()));
            return;
        }
        if (!cloud()) {
            AppExecutors.io(() -> {
                List<Dish> list = new ArrayList<>();
                for (Dish dish : Catalog.get(app).seed.dishes) {
                    if (ids.contains(dish.restaurantId) && (includeUnavailable || dish.available)) {
                        list.add(dish);
                    }
                }
                remember(null, list);
                AppExecutors.main(() -> listener.onResult(list));
            });
            return;
        }
        List<Dish> collected = Collections.synchronizedList(new ArrayList<>());
        int[] pending = {(ids.size() + IN_LIMIT - 1) / IN_LIMIT};
        boolean[] failed = {false};
        for (int start = 0; start < ids.size(); start += IN_LIMIT) {
            List<String> chunk = ids.subList(start, Math.min(ids.size(), start + IN_LIMIT));
            Cloud.db().collection(Cloud.DISHES).whereIn("restaurantId", new ArrayList<>(chunk)).get()
                    .addOnSuccessListener(result -> {
                        for (QueryDocumentSnapshot doc : result) {
                            Dish dish = Dish.from(doc.getData());
                            if (includeUnavailable || dish.available) {
                                collected.add(dish);
                            }
                        }
                        if (--pending[0] == 0 && !failed[0]) {
                            List<Dish> list = new ArrayList<>(collected);
                            remember(null, list);
                            listener.onResult(list);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "dishes failed", e);
                        if (!failed[0]) {
                            failed[0] = true;
                            listener.onError();
                        }
                    });
        }
    }

    /**
     * Saves a dish. {@code fullPhoto} is a large data URL stored apart in photos/{id}, so menu lists
     * only load the small thumbnail kept on the dish itself; null keeps the current photo.
     */
    public void saveDish(Dish dish, String fullPhoto, Listener<Void> listener) {
        FirebaseFirestore db = Cloud.db();
        WriteBatch batch = db.batch();
        batch.set(db.collection(Cloud.DISHES).document(dish.id), dish.toMap());
        if (fullPhoto != null) {
            Map<String, Object> photo = new HashMap<>();
            photo.put("restaurantId", dish.restaurantId);
            photo.put("data", fullPhoto);
            batch.set(db.collection(Cloud.PHOTOS).document(dish.id), photo);
        }
        batch.commit()
                .addOnSuccessListener(v -> {
                    remember(null, Collections.singletonList(dish));
                    listener.onResult(null);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "saveDish failed", e);
                    listener.onError();
                });
    }

    public void deleteDish(Dish dish, Listener<Void> listener) {
        FirebaseFirestore db = Cloud.db();
        WriteBatch batch = db.batch();
        batch.delete(db.collection(Cloud.DISHES).document(dish.id));
        batch.delete(db.collection(Cloud.PHOTOS).document(dish.id));
        batch.commit()
                .addOnSuccessListener(v -> {
                    synchronized (this) {
                        dishes.remove(dish.id);
                    }
                    listener.onResult(null);
                })
                .addOnFailureListener(e -> listener.onError());
    }

    /** The full-size photo a restaurant uploaded, as a data URL. */
    public void photo(String dishId, Listener<String> listener) {
        if (!cloud()) {
            AppExecutors.main(listener::onError);
            return;
        }
        Cloud.db().collection(Cloud.PHOTOS).document(dishId).get()
                .addOnSuccessListener(doc -> {
                    String data = doc.getString("data");
                    if (data == null) {
                        listener.onError();
                    } else {
                        listener.onResult(data);
                    }
                })
                .addOnFailureListener(e -> listener.onError());
    }

    // ---- Coupons ----

    public Promo platformPromo(String code) {
        return Catalog.get(app).promo(code);
    }

    /** A platform promo, or a restaurant coupon from Firestore. Not-found arrives as onResult(null). */
    public void findCoupon(String code, Listener<Promo> listener) {
        String clean = code == null ? "" : code.trim().toUpperCase(Locale.US);
        Promo platform = platformPromo(clean);
        if (platform != null || clean.isEmpty() || !cloud()) {
            AppExecutors.main(() -> listener.onResult(platform));
            return;
        }
        Cloud.db().collection(Cloud.COUPONS).document(clean).get()
                .addOnSuccessListener(doc -> listener.onResult(doc.exists() && doc.getData() != null
                        ? Promo.from(doc.getData()) : null))
                .addOnFailureListener(e -> listener.onError());
    }

    public void coupons(String restaurantId, Listener<List<Promo>> listener) {
        if (!cloud()) {
            AppExecutors.main(() -> listener.onResult(new ArrayList<>()));
            return;
        }
        Cloud.db().collection(Cloud.COUPONS).whereEqualTo("restaurantId", restaurantId).get()
                .addOnSuccessListener(result -> {
                    List<Promo> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : result) {
                        list.add(Promo.from(doc.getData()));
                    }
                    Collections.sort(list, (a, b) -> a.code.compareTo(b.code));
                    listener.onResult(list);
                })
                .addOnFailureListener(e -> listener.onError());
    }

    public void saveCoupon(Promo promo, Listener<Void> listener) {
        Cloud.db().collection(Cloud.COUPONS).document(promo.code).set(promo.toMap())
                .addOnSuccessListener(v -> listener.onResult(null))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "saveCoupon failed", e);
                    listener.onError();
                });
    }

    public void deleteCoupon(String code, Listener<Void> listener) {
        Cloud.db().collection(Cloud.COUPONS).document(code).delete()
                .addOnSuccessListener(v -> listener.onResult(null))
                .addOnFailureListener(e -> listener.onError());
    }

    // ---- Seeding and cache ----

    private synchronized void remember(List<Restaurant> newRestaurants, List<Dish> newDishes) {
        if (newRestaurants != null) {
            for (Restaurant r : newRestaurants) {
                restaurants.put(r.id, r);
            }
            restaurantsLoaded = true;
        }
        if (newDishes != null) {
            for (Dish d : newDishes) {
                dishes.put(d.id, d);
            }
        }
    }

    /** Uploads the bundled menu once per Firebase project, then runs {@code next} either way. */
    private void ensureSeeded(Runnable next) {
        FirebaseFirestore db = Cloud.db();
        db.collection(Cloud.META).document("seed").get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    if (doc.exists()) {
                        next.run();
                        return;
                    }
                    AppExecutors.io(() -> {
                        XmlDataParser.Seed seed = Catalog.get(app).seed;
                        WriteBatch batch = db.batch();
                        for (Restaurant r : seed.restaurants) {
                            batch.set(db.collection(Cloud.RESTAURANTS).document(r.id), r.toMap());
                        }
                        for (Dish d : seed.dishes) {
                            batch.set(db.collection(Cloud.DISHES).document(d.id), d.toMap());
                        }
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("version", SEED_VERSION);
                        meta.put("at", System.currentTimeMillis());
                        batch.set(db.collection(Cloud.META).document("seed"), meta);
                        batch.commit()
                                .addOnSuccessListener(v -> next.run())
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "Seeding refused; check the Firestore rules in the README", e);
                                    next.run();
                                });
                    });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Seed check failed", e);
                    next.run();
                });
    }

    /** Restaurants nobody has claimed yet, for new restaurant accounts. */
    public void unclaimedRestaurants(Listener<List<Restaurant>> listener) {
        restaurants(true, new Listener<List<Restaurant>>() {
            @Override
            public void onResult(List<Restaurant> result) {
                List<Restaurant> free = new ArrayList<>();
                for (Restaurant r : result) {
                    if (r.ownerUid == null || r.ownerUid.isEmpty()) {
                        free.add(r);
                    }
                }
                Collections.sort(free, (a, b) -> (a.city + a.name).compareTo(b.city + b.name));
                listener.onResult(free);
            }

            @Override
            public void onError() {
                listener.onError();
            }
        });
    }
}
