package com.foodrush.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.foodrush.app.data.FoodRushContract.Orders;
import com.foodrush.app.model.Order;
import com.foodrush.app.util.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * Local copy of orders, through the content provider. Without Firebase this is the only store;
 * with it, {@link com.foodrush.app.order.OrderStore} copies every order it sees here. Blocking calls.
 */
public final class OrderRepository {

    private OrderRepository() {
    }

    public static void save(Context context, Order order) {
        ContentValues values = new ContentValues();
        values.put(Orders.ID, order.id);
        values.put(Orders.CUSTOMER_UID, order.customerUid);
        values.put(Orders.RIDER_UID, order.riderUid);
        values.put(Orders.STATUS, order.status);
        values.put(Orders.PLACED_AT, order.placedAt);
        values.put(Orders.DATA, Json.write(order.toMap()));
        context.getContentResolver().insert(Orders.URI, values);
    }

    public static Order find(Context context, String id) {
        if (id == null) {
            return null;
        }
        try (Cursor c = context.getContentResolver().query(Uri.withAppendedPath(Orders.URI, id), null, null, null, null)) {
            return c != null && c.moveToFirst() ? read(c) : null;
        }
    }

    public static List<Order> forCustomer(Context context, String uid) {
        return list(context, Orders.CUSTOMER_UID + "=?", new String[]{uid});
    }

    public static List<Order> forRider(Context context, String uid) {
        return list(context, Orders.RIDER_UID + "=?", new String[]{uid});
    }

    /** Orders this customer is still waiting for. */
    public static List<Order> activeForCustomer(Context context, String uid) {
        return list(context, Orders.CUSTOMER_UID + "=? AND " + Orders.STATUS + " NOT IN (?, ?)",
                new String[]{uid, String.valueOf(Order.CANCELLED), String.valueOf(Order.DELIVERED)});
    }

    private static List<Order> list(Context context, String selection, String[] args) {
        List<Order> orders = new ArrayList<>();
        try (Cursor c = context.getContentResolver().query(Orders.URI, null, selection, args, Orders.PLACED_AT + " DESC")) {
            while (c != null && c.moveToNext()) {
                orders.add(read(c));
            }
        }
        return orders;
    }

    private static Order read(Cursor c) {
        return Order.from(Json.read(c.getString(c.getColumnIndexOrThrow(Orders.DATA))));
    }
}
