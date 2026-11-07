package com.foodrush.app.ui;

import android.content.Context;
import android.content.Intent;

import com.foodrush.app.data.Session;
import com.foodrush.app.ui.restaurant.ClaimRestaurantActivity;
import com.foodrush.app.ui.restaurant.KitchenActivity;
import com.foodrush.app.ui.rider.RiderHomeActivity;

/** Sends each kind of account to its own home screen after sign-in. */
public final class RoleRouter {

    private RoleRouter() {
    }

    public static Intent home(Context context) {
        Session session = new Session(context);
        Class<?> target;
        if (session.isRider()) {
            target = RiderHomeActivity.class;
        } else if (session.isRestaurant()) {
            target = session.restaurantId() == null ? ClaimRestaurantActivity.class : KitchenActivity.class;
        } else {
            target = MainActivity.class;
        }
        return new Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
}
