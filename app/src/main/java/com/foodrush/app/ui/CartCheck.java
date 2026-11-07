package com.foodrush.app.ui;

import android.content.Context;

import com.foodrush.app.R;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.model.CartItem;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.util.PriceCalculator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/** Cart sums and the "can this be ordered?" checks, shared by the cart and checkout screens. */
final class CartCheck {

    private CartCheck() {
    }

    static int subtotal(List<CartItem> items) {
        int subtotal = 0;
        for (CartItem item : items) {
            subtotal += item.lineTotal();
        }
        return subtotal;
    }

    static int subtotalFor(List<CartItem> items, String restaurantId) {
        int subtotal = 0;
        for (CartItem item : items) {
            if (item.restaurantId.equals(restaurantId)) {
                subtotal += item.lineTotal();
            }
        }
        return subtotal;
    }

    /** The slowest dish sets the kitchen time. */
    static int prepMinutes(List<CartItem> items) {
        int prep = 10;
        for (CartItem item : items) {
            prep = Math.max(prep, item.prepMinutes);
        }
        return prep;
    }

    static List<LatLon> pickups(Map<String, Restaurant> restaurants) {
        List<LatLon> pickups = new ArrayList<>();
        for (Restaurant r : restaurants.values()) {
            pickups.add(r.location());
        }
        return pickups;
    }

    /** Why the order can't be placed yet, or null. */
    static String problem(Context context, List<CartItem> items, Map<String, Restaurant> restaurants, LatLon customer) {
        Calendar now = Calendar.getInstance();
        for (Restaurant r : restaurants.values()) {
            if (!MenuRepository.delivers(r, customer)) {
                return context.getString(R.string.too_far, r.name);
            }
            if (!r.isOpenAt(now)) {
                return context.getString(R.string.restaurant_closed, r.name);
            }
            if (subtotalFor(items, r.id) < r.minOrder) {
                return context.getString(R.string.below_min_order, r.name, PriceCalculator.format(r.minOrder));
            }
        }
        return null;
    }
}
