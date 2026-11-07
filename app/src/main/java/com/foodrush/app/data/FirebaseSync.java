package com.foodrush.app.data;

import android.content.Context;
import android.util.Log;

import com.foodrush.app.model.Review;
import com.foodrush.app.util.AppExecutors;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** User profiles, reviews, ratings and promo topics in Firestore. Every call is a no-op without Firebase. */
public final class FirebaseSync {

    private static final String TAG = "FirebaseSync";

    private FirebaseSync() {
    }

    public static final class Profile {
        public final String name;
        public final String phone;
        public final String role;
        public final String restaurantId;

        Profile(String name, String phone, String role, String restaurantId) {
            this.name = name;
            this.phone = phone;
            this.role = role;
            this.restaurantId = restaurantId;
        }
    }

    public static void saveUser(Context context, String uid, String name, String email, String phone, String role,
                                String restaurantId) {
        if (!Cloud.isOn(context)) {
            return;
        }
        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("email", email);
        user.put("phone", phone);
        user.put("role", role);
        user.put("restaurantId", restaurantId);
        Cloud.db().collection(Cloud.USERS).document(uid).set(user, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "saveUser failed", e));
        if (Session.ROLE_RIDER.equals(role)) {
            Map<String, Object> rider = new HashMap<>();
            rider.put("name", name);
            Cloud.db().collection(Cloud.RIDERS).document(uid).set(rider, SetOptions.merge());
        }
    }

    /** Reads the profile saved at sign-up; null in the callback when it is missing or unreadable. */
    public static void loadUser(String uid, Consumer<Profile> onResult) {
        Cloud.db().collection(Cloud.USERS).document(uid).get()
                .addOnSuccessListener(doc -> onResult.accept(doc.exists() ? new Profile(doc.getString("name"),
                        doc.getString("phone"), doc.getString("role"), doc.getString("restaurantId")) : null))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "loadUser failed", e);
                    onResult.accept(null);
                });
    }

    /** Saves a review and folds its stars into the restaurant's average. */
    public static void saveReview(Context context, Review review) {
        if (!Cloud.isOn(context)) {
            return;
        }
        Cloud.db().collection(Cloud.REVIEWS).document(review.id).set(review.toMap())
                .addOnFailureListener(e -> Log.w(TAG, "saveReview failed", e));
        DocumentReference restaurant = Cloud.db().collection(Cloud.RESTAURANTS).document(review.restaurantId);
        Cloud.db().runTransaction(tx -> {
            DocumentSnapshot doc = tx.get(restaurant);
            if (!doc.exists()) {
                return null;
            }
            Double rating = doc.getDouble("rating");
            Long count = doc.getLong("ratingCount");
            double oldRating = rating == null ? 0 : rating;
            long oldCount = count == null ? 0 : count;
            double newRating = (oldRating * oldCount + review.rating) / (oldCount + 1);
            Map<String, Object> update = new HashMap<>();
            update.put("rating", Math.round(newRating * 10) / 10.0);
            update.put("ratingCount", oldCount + 1);
            tx.update(restaurant, update);
            return null;
        }).addOnFailureListener(e -> Log.w(TAG, "rating update failed", e));
    }

    public static void replyToReview(Review review, String reply, Runnable onDone, Runnable onError) {
        Cloud.db().collection(Cloud.REVIEWS).document(review.id).update("reply", reply)
                .addOnSuccessListener(v -> onDone.run())
                .addOnFailureListener(e -> {
                    Log.w(TAG, "reply failed", e);
                    onError.run();
                });
    }

    /** Copies every review of a restaurant to this device, then runs onDone on the main thread. */
    public static void pullReviews(Context context, String restaurantId, Runnable onDone) {
        if (!Cloud.isOn(context)) {
            onDone.run();
            return;
        }
        Context app = context.getApplicationContext();
        Cloud.db().collection(Cloud.REVIEWS).whereEqualTo("restaurantId", restaurantId).get()
                .addOnSuccessListener(result -> AppExecutors.io(() -> {
                    for (QueryDocumentSnapshot doc : result) {
                        ReviewRepository.save(app, Review.from(doc.getId(), doc.getData()));
                    }
                    AppExecutors.main(onDone);
                }))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "pullReviews failed", e);
                    onDone.run();
                });
    }

    /** Adds a customer's stars to a real rider's running total. */
    public static void rateRider(Context context, String riderUid, int stars) {
        if (!Cloud.isOn(context)) {
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("ratingSum", FieldValue.increment(stars));
        update.put("ratingCount", FieldValue.increment(1));
        Cloud.db().collection(Cloud.RIDERS).document(riderUid).set(update, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "rateRider failed", e));
    }

    public static void subscribeToPromos(Context context) {
        if (Cloud.isConfigured(context)) {
            FirebaseMessaging.getInstance().subscribeToTopic("promos")
                    .addOnFailureListener(e -> Log.w(TAG, "topic subscribe failed", e));
        }
    }
}
