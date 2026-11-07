package com.foodrush.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.foodrush.app.data.FoodRushContract.Favourites;
import com.foodrush.app.model.Dish;
import com.foodrush.app.util.Json;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Blocking calls through the content provider; run them with AppExecutors. */
public final class FavouriteRepository {

    private FavouriteRepository() {
    }

    public static void add(Context context, Dish dish) {
        ContentValues values = new ContentValues();
        values.put(Favourites.DISH_ID, dish.id);
        values.put(Favourites.NAME, dish.name);
        values.put(Favourites.RESTAURANT_ID, dish.restaurantId);
        values.put(Favourites.DATA, Json.write(dish.toMap()));
        context.getContentResolver().insert(Favourites.URI, values);
    }

    public static void remove(Context context, String dishId) {
        context.getContentResolver().delete(Uri.withAppendedPath(Favourites.URI, dishId), null, null);
    }

    public static boolean toggle(Context context, Dish dish) {
        if (ids(context).contains(dish.id)) {
            remove(context, dish.id);
            return false;
        }
        add(context, dish);
        return true;
    }

    public static Set<String> ids(Context context) {
        Set<String> ids = new HashSet<>();
        try (Cursor c = context.getContentResolver().query(Favourites.URI, new String[]{Favourites.DISH_ID}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                ids.add(c.getString(0));
            }
        }
        return ids;
    }

    public static List<Dish> all(Context context) {
        List<Dish> dishes = new ArrayList<>();
        try (Cursor c = context.getContentResolver().query(Favourites.URI, null, null, null, Favourites._ID + " DESC")) {
            while (c != null && c.moveToNext()) {
                dishes.add(Dish.from(Json.read(c.getString(c.getColumnIndexOrThrow(Favourites.DATA)))));
            }
        }
        return dishes;
    }
}
