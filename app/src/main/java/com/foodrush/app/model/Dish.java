package com.foodrush.app.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A menu item. Prices are whole taka. {@code image} is a bundled photo ("res:dish_kacchi"), a
 * restaurant-uploaded thumbnail as a data URL, or a web URL.
 */
public class Dish {

    public final String id;
    public final String restaurantId;
    public final String name;
    public final String description;
    public final String category;
    public final int price;
    /** 0 when the dish is not discounted. */
    public final int discountPrice;
    public final String image;
    /** True when a full-size photo was uploaded to photos/{id}. */
    public final boolean hasPhoto;
    public final float rating;
    public final int prepMinutes;
    /** 0 mild to 3 very hot. */
    public final int spicy;
    public final boolean available;
    public final List<Option> sizes;
    public final List<Option> addons;

    public Dish(String id, String restaurantId, String name, String description, String category, int price,
                int discountPrice, String image, boolean hasPhoto, float rating, int prepMinutes, int spicy,
                boolean available, List<Option> sizes, List<Option> addons) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.name = name;
        this.description = description == null ? "" : description;
        this.category = category == null ? "" : category;
        this.price = price;
        this.discountPrice = discountPrice;
        this.image = image;
        this.hasPhoto = hasPhoto;
        this.rating = rating;
        this.prepMinutes = prepMinutes;
        this.spicy = spicy;
        this.available = available;
        this.sizes = sizes == null ? new ArrayList<>() : sizes;
        this.addons = addons == null ? new ArrayList<>() : addons;
    }

    public boolean discounted() {
        return discountPrice > 0 && discountPrice < price;
    }

    /** What a customer pays for the base dish today. */
    public int currentPrice() {
        return discounted() ? discountPrice : price;
    }

    /** A size's price after this dish's discount, rounded to 5 tk. */
    public int priceFor(Option size) {
        if (size == null) {
            return currentPrice();
        }
        if (!discounted()) {
            return size.price;
        }
        return Math.round(size.price * (float) discountPrice / price / 5f) * 5;
    }

    /** Lowest price shown on cards: the smallest size, or the dish itself. */
    public int fromPrice() {
        int lowest = currentPrice();
        for (Option size : sizes) {
            lowest = Math.min(lowest, priceFor(size));
        }
        return lowest;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("restaurantId", restaurantId);
        m.put("name", name);
        m.put("description", description);
        m.put("category", category);
        m.put("price", price);
        m.put("discountPrice", discountPrice);
        m.put("image", image);
        m.put("hasPhoto", hasPhoto);
        m.put("rating", (double) rating);
        m.put("prepMinutes", prepMinutes);
        m.put("spicy", spicy);
        m.put("available", available);
        m.put("sizes", Option.toMaps(sizes));
        m.put("addons", Option.toMaps(addons));
        return m;
    }

    public static Dish from(Map<String, Object> m) {
        return new Dish(Fields.text(m, "id"), Fields.text(m, "restaurantId"), Fields.text(m, "name"),
                Fields.text(m, "description"), Fields.text(m, "category"), Fields.integer(m, "price", 0),
                Fields.integer(m, "discountPrice", 0), Fields.nullableText(m, "image"), Fields.flag(m, "hasPhoto", false),
                (float) Fields.decimal(m, "rating"), Fields.integer(m, "prepMinutes", 15), Fields.integer(m, "spicy", 0),
                Fields.flag(m, "available", true), Option.fromMaps(m.get("sizes")), Option.fromMaps(m.get("addons")));
    }
}
