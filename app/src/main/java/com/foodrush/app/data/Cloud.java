package com.foodrush.app.data;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/** Firestore collection names and the "is Firebase set up" switch. */
public final class Cloud {

    public static final String USERS = "users";
    public static final String RESTAURANTS = "restaurants";
    public static final String DISHES = "dishes";
    public static final String PHOTOS = "photos";
    public static final String COUPONS = "coupons";
    public static final String REVIEWS = "reviews";
    public static final String ORDERS = "orders";
    public static final String PRIVATE = "private";
    public static final String MESSAGES = "messages";
    public static final String RIDERS = "riders";
    public static final String CALLS = "calls";
    public static final String META = "meta";

    private Cloud() {
    }

    /** True when the app was built with google-services.json. */
    public static boolean isConfigured(Context context) {
        return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
    }

    /** True when Firebase is set up and someone is signed in to it. */
    public static boolean isOn(Context context) {
        return isConfigured(context) && FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    public static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }
}
