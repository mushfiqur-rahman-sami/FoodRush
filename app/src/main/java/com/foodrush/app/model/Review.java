package com.foodrush.app.model;

import java.util.HashMap;
import java.util.Map;

public class Review {
    public final String id;
    public final String restaurantId;
    public final String author;
    public final float rating;
    public final String text;
    public final long createdAt;
    /** The restaurant's answer; empty when it has not replied. */
    public final String reply;

    public Review(String id, String restaurantId, String author, float rating, String text, long createdAt, String reply) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.author = author;
        this.rating = rating;
        this.text = text;
        this.createdAt = createdAt;
        this.reply = reply == null ? "" : reply;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("restaurantId", restaurantId);
        m.put("author", author);
        m.put("rating", (double) rating);
        m.put("text", text);
        m.put("createdAt", createdAt);
        m.put("reply", reply);
        return m;
    }

    public static Review from(String id, Map<String, Object> m) {
        return new Review(id, Fields.text(m, "restaurantId"), Fields.text(m, "author"), (float) Fields.decimal(m, "rating"),
                Fields.text(m, "text"), Fields.number(m, "createdAt"), Fields.text(m, "reply"));
    }
}
