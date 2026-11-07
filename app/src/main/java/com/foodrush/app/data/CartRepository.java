package com.foodrush.app.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.foodrush.app.data.FoodRushContract.Cart;
import com.foodrush.app.model.CartItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Blocking calls through the content provider; run them with AppExecutors. */
public final class CartRepository {

    private CartRepository() {
    }

    /** Adds to an identical line (same dish, size, add-ons) when there is one. */
    public static void add(Context context, CartItem item) {
        ContentResolver resolver = context.getContentResolver();
        String selection = Cart.DISH_ID + "=? AND " + Cart.SIZE + "=? AND " + Cart.EXTRAS + "=?";
        String[] args = {item.dishId, item.size, item.extras};
        try (Cursor c = resolver.query(Cart.URI, new String[]{Cart._ID, Cart.QUANTITY}, selection, args, null)) {
            if (c != null && c.moveToFirst()) {
                setQuantity(context, c.getLong(0), c.getInt(1) + item.quantity);
                return;
            }
        }
        ContentValues values = new ContentValues();
        values.put(Cart.DISH_ID, item.dishId);
        values.put(Cart.NAME, item.name);
        values.put(Cart.IMAGE, item.image);
        values.put(Cart.RESTAURANT_ID, item.restaurantId);
        values.put(Cart.SIZE, item.size);
        values.put(Cart.EXTRAS, item.extras);
        values.put(Cart.UNIT_PRICE, item.unitPrice);
        values.put(Cart.QUANTITY, item.quantity);
        values.put(Cart.PREP_MINUTES, item.prepMinutes);
        resolver.insert(Cart.URI, values);
    }

    public static void setQuantity(Context context, long rowId, int quantity) {
        if (quantity <= 0) {
            remove(context, rowId);
            return;
        }
        ContentValues values = new ContentValues();
        values.put(Cart.QUANTITY, quantity);
        context.getContentResolver().update(ContentUris.withAppendedId(Cart.URI, rowId), values, null, null);
    }

    public static void remove(Context context, long rowId) {
        context.getContentResolver().delete(ContentUris.withAppendedId(Cart.URI, rowId), null, null);
    }

    public static void clear(Context context) {
        context.getContentResolver().delete(Cart.URI, null, null);
    }

    public static List<CartItem> all(Context context) {
        List<CartItem> items = new ArrayList<>();
        try (Cursor c = context.getContentResolver().query(Cart.URI, null, null, null, Cart._ID)) {
            while (c != null && c.moveToNext()) {
                items.add(new CartItem(
                        c.getLong(c.getColumnIndexOrThrow(Cart._ID)),
                        c.getString(c.getColumnIndexOrThrow(Cart.DISH_ID)),
                        c.getString(c.getColumnIndexOrThrow(Cart.NAME)),
                        c.getString(c.getColumnIndexOrThrow(Cart.IMAGE)),
                        c.getString(c.getColumnIndexOrThrow(Cart.RESTAURANT_ID)),
                        c.getString(c.getColumnIndexOrThrow(Cart.SIZE)),
                        c.getString(c.getColumnIndexOrThrow(Cart.EXTRAS)),
                        c.getInt(c.getColumnIndexOrThrow(Cart.UNIT_PRICE)),
                        c.getInt(c.getColumnIndexOrThrow(Cart.QUANTITY)),
                        c.getInt(c.getColumnIndexOrThrow(Cart.PREP_MINUTES))));
            }
        }
        return items;
    }

    public static Set<String> restaurantIds(Context context) {
        Set<String> ids = new LinkedHashSet<>();
        try (Cursor c = context.getContentResolver().query(Cart.URI, new String[]{Cart.RESTAURANT_ID}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                ids.add(c.getString(0));
            }
        }
        return ids;
    }

    public static int count(Context context) {
        int total = 0;
        try (Cursor c = context.getContentResolver().query(Cart.URI, new String[]{Cart.QUANTITY}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                total += c.getInt(0);
            }
        }
        return total;
    }
}
