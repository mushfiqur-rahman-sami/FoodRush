package com.foodrush.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.foodrush.app.data.FoodRushContract.Reviews;
import com.foodrush.app.model.Review;

import java.util.ArrayList;
import java.util.List;

/** Blocking calls through the content provider; run them with AppExecutors. */
public final class ReviewRepository {

    private ReviewRepository() {
    }

    public static void save(Context context, Review review) {
        ContentValues values = new ContentValues();
        values.put(Reviews.ID, review.id);
        values.put(Reviews.RESTAURANT_ID, review.restaurantId);
        values.put(Reviews.AUTHOR, review.author);
        values.put(Reviews.RATING, review.rating);
        values.put(Reviews.TEXT, review.text);
        values.put(Reviews.CREATED_AT, review.createdAt);
        values.put(Reviews.REPLY, review.reply);
        context.getContentResolver().insert(Reviews.URI, values);
    }

    public static List<Review> forRestaurant(Context context, String restaurantId) {
        List<Review> reviews = new ArrayList<>();
        try (Cursor c = context.getContentResolver().query(Reviews.URI, null, Reviews.RESTAURANT_ID + "=?",
                new String[]{restaurantId}, Reviews.CREATED_AT + " DESC")) {
            while (c != null && c.moveToNext()) {
                reviews.add(new Review(
                        c.getString(c.getColumnIndexOrThrow(Reviews.ID)),
                        c.getString(c.getColumnIndexOrThrow(Reviews.RESTAURANT_ID)),
                        c.getString(c.getColumnIndexOrThrow(Reviews.AUTHOR)),
                        c.getFloat(c.getColumnIndexOrThrow(Reviews.RATING)),
                        c.getString(c.getColumnIndexOrThrow(Reviews.TEXT)),
                        c.getLong(c.getColumnIndexOrThrow(Reviews.CREATED_AT)),
                        c.getString(c.getColumnIndexOrThrow(Reviews.REPLY))));
            }
        }
        return reviews;
    }
}
