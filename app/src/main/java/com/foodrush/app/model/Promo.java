package com.foodrush.app.model;

import java.util.HashMap;
import java.util.Map;

/**
 * A coupon. Platform-wide promos come from promotions.xml; restaurant coupons live in Firestore
 * under coupons/{CODE} and only discount that restaurant's part of the cart.
 */
public class Promo {
    public final String code;
    public final String title;
    public final String subtitle;
    public final int percent;
    /** Flat taka off; 0 when the promo is a percentage. */
    public final int flat;
    public final boolean freeDelivery;
    public final int minOrder;
    /** Null for platform promos. */
    public final String restaurantId;
    /** 0 when it never expires. */
    public final long expiresAt;
    public final boolean active;

    public Promo(String code, String title, String subtitle, int percent, int flat, boolean freeDelivery,
                 int minOrder, String restaurantId, long expiresAt, boolean active) {
        this.code = code;
        this.title = title;
        this.subtitle = subtitle;
        this.percent = percent;
        this.flat = flat;
        this.freeDelivery = freeDelivery;
        this.minOrder = minOrder;
        this.restaurantId = restaurantId;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public boolean usableAt(long now) {
        return active && (expiresAt == 0 || now < expiresAt);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("title", title);
        m.put("subtitle", subtitle);
        m.put("percent", percent);
        m.put("flat", flat);
        m.put("freeDelivery", freeDelivery);
        m.put("minOrder", minOrder);
        m.put("restaurantId", restaurantId);
        m.put("expiresAt", expiresAt);
        m.put("active", active);
        return m;
    }

    public static Promo from(Map<String, Object> m) {
        return new Promo(Fields.text(m, "code"), Fields.text(m, "title"), Fields.text(m, "subtitle"),
                Fields.integer(m, "percent", 0), Fields.integer(m, "flat", 0), Fields.flag(m, "freeDelivery", false),
                Fields.integer(m, "minOrder", 0), Fields.nullableText(m, "restaurantId"), Fields.number(m, "expiresAt"),
                Fields.flag(m, "active", true));
    }
}
