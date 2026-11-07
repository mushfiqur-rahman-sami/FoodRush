package com.foodrush.app.model;

public class CartItem {
    public final long rowId;
    public final String dishId;
    public final String name;
    public final String image;
    public final String restaurantId;
    /** Chosen size label, or empty when the dish has no sizes. */
    public final String size;
    /** Display text such as "Extra egg, Borhani"; empty when there are no add-ons. */
    public final String extras;
    /** Whole taka for one unit, size and add-ons included. */
    public final int unitPrice;
    public final int quantity;
    public final int prepMinutes;

    public CartItem(long rowId, String dishId, String name, String image, String restaurantId, String size,
                    String extras, int unitPrice, int quantity, int prepMinutes) {
        this.rowId = rowId;
        this.dishId = dishId;
        this.name = name;
        this.image = image;
        this.restaurantId = restaurantId;
        this.size = size;
        this.extras = extras;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.prepMinutes = prepMinutes;
    }

    public int lineTotal() {
        return unitPrice * quantity;
    }
}
