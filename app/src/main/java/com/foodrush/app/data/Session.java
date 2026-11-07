package com.foodrush.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.foodrush.app.model.LatLon;

/** Small user-level settings: who is signed in and as what, delivery address, payment and app options. */
public final class Session {

    public static final String ROLE_CUSTOMER = "customer";
    public static final String ROLE_RIDER = "rider";
    public static final String ROLE_RESTAURANT = "restaurant";

    public static final String PAYMENT_CASH = "Cash on delivery";

    /** Shibbari Circle, Khulna, until the user picks an address. */
    private static final String DEFAULT_ADDRESS = "Shibbari Circle, KDA Avenue, Khulna";
    private static final float DEFAULT_LAT = 22.822232f;
    private static final float DEFAULT_LON = 89.552929f;

    private final SharedPreferences prefs;

    public Session(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("foodrush", Context.MODE_PRIVATE);
    }

    public boolean isSignedIn() {
        return uid() != null;
    }

    public String uid() {
        return prefs.getString("uid", null);
    }

    public String name() {
        return prefs.getString("name", "");
    }

    public String email() {
        return prefs.getString("email", "");
    }

    public String phone() {
        return prefs.getString("phone", "");
    }

    public String role() {
        return prefs.getString("role", ROLE_CUSTOMER);
    }

    public boolean isCustomer() {
        return ROLE_CUSTOMER.equals(role());
    }

    public boolean isRider() {
        return ROLE_RIDER.equals(role());
    }

    public boolean isRestaurant() {
        return ROLE_RESTAURANT.equals(role());
    }

    /** The restaurant a restaurant account runs; null until it claims or creates one. */
    public String restaurantId() {
        return prefs.getString("restaurant_id", null);
    }

    public void setRestaurantId(String id) {
        prefs.edit().putString("restaurant_id", id).apply();
    }

    public void signIn(String uid, String name, String email, String phone, String role, String restaurantId) {
        prefs.edit().putString("uid", uid).putString("name", name).putString("email", email)
                .putString("phone", phone == null ? "" : phone).putString("role", role == null ? ROLE_CUSTOMER : role)
                .putString("restaurant_id", restaurantId).apply();
    }

    public void updateProfile(String name, String phone) {
        prefs.edit().putString("name", name).putString("phone", phone).apply();
    }

    public void signOut() {
        prefs.edit().remove("uid").remove("name").remove("email").remove("phone").remove("photo")
                .remove("role").remove("restaurant_id").remove("rider_online").apply();
    }

    public String photoPath() {
        return prefs.getString("photo", null);
    }

    public void setPhotoPath(String path) {
        prefs.edit().putString("photo", path).apply();
    }

    public String addressLabel() {
        return prefs.getString("address", DEFAULT_ADDRESS);
    }

    public LatLon addressPoint() {
        return new LatLon(prefs.getFloat("address_lat", DEFAULT_LAT), prefs.getFloat("address_lon", DEFAULT_LON));
    }

    public void setAddress(String label, LatLon point) {
        prefs.edit().putString("address", label).putFloat("address_lat", (float) point.lat)
                .putFloat("address_lon", (float) point.lon).apply();
    }

    public String payment() {
        return prefs.getString("payment", PAYMENT_CASH);
    }

    public void setPayment(String label) {
        prefs.edit().putString("payment", label).apply();
    }

    public String coupon() {
        return prefs.getString("coupon", null);
    }

    public void setCoupon(String code) {
        prefs.edit().putString("coupon", code).apply();
    }

    public boolean riderOnline() {
        return prefs.getBoolean("rider_online", false);
    }

    public void setRiderOnline(boolean online) {
        prefs.edit().putBoolean("rider_online", online).apply();
    }

    /** The rider's daily goal: deliveries (0 for none). */
    public int riderGoalDeliveries() {
        return prefs.getInt("goal_deliveries", 8);
    }

    /** The rider's daily goal: taka earned (0 for none). */
    public int riderGoalTaka() {
        return prefs.getInt("goal_taka", 600);
    }

    public void setRiderGoal(int deliveries, int taka) {
        prefs.edit().putInt("goal_deliveries", deliveries).putInt("goal_taka", taka).apply();
    }

    /** Cash-on-delivery money from orders delivered up to this time has been handed in. */
    public long cashDepositedUntil() {
        return prefs.getLong("cash_deposited_until", 0);
    }

    public void setCashDepositedUntil(long time) {
        prefs.edit().putLong("cash_deposited_until", time).apply();
    }

    /** The PIN the customer gives the rider, kept only on this phone (and in a customer-only cloud doc). */
    public String pin(String orderId) {
        return prefs.getString("pin_" + orderId, null);
    }

    public void setPin(String orderId, String pin) {
        prefs.edit().putString("pin_" + orderId, pin).apply();
    }

    public boolean onboardingDone() {
        return prefs.getBoolean("onboarding_done", false);
    }

    public void setOnboardingDone() {
        prefs.edit().putBoolean("onboarding_done", true).apply();
    }

    public boolean darkMode() {
        return prefs.getBoolean("dark_mode", false);
    }

    public void setDarkMode(boolean on) {
        prefs.edit().putBoolean("dark_mode", on).apply();
    }

    public boolean promoNotifications() {
        return prefs.getBoolean("promo_notifications", true);
    }

    public void setPromoNotifications(boolean on) {
        prefs.edit().putBoolean("promo_notifications", on).apply();
    }

    public boolean notificationPromptShown() {
        return prefs.getBoolean("notification_prompt", false);
    }

    public void setNotificationPromptShown() {
        prefs.edit().putBoolean("notification_prompt", true).apply();
    }

    public String lastPromoCode() {
        return prefs.getString("last_promo", null);
    }

    public void setLastPromoCode(String code) {
        prefs.edit().putString("last_promo", code).apply();
    }
}
