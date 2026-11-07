package com.foodrush.app.data;

import android.net.Uri;
import android.provider.BaseColumns;

/** Public shape of {@link com.foodrush.app.provider.FoodRushProvider}: authority, URIs and columns. */
public final class FoodRushContract {

    public static final String AUTHORITY = "com.foodrush.app.provider";
    private static final Uri BASE = Uri.parse("content://" + AUTHORITY);

    private FoodRushContract() {
    }

    public static final class Cart implements BaseColumns {
        public static final String TABLE = "cart";
        public static final Uri URI = Uri.withAppendedPath(BASE, TABLE);
        public static final String DISH_ID = "dish_id";
        public static final String NAME = "name";
        public static final String IMAGE = "image";
        public static final String RESTAURANT_ID = "restaurant_id";
        public static final String SIZE = "size";
        public static final String EXTRAS = "extras";
        public static final String UNIT_PRICE = "unit_price";
        public static final String QUANTITY = "quantity";
        public static final String PREP_MINUTES = "prep_minutes";
    }

    /** The whole dish is kept as JSON, so a saved dish still opens when the phone is offline. */
    public static final class Favourites implements BaseColumns {
        public static final String TABLE = "favourites";
        public static final Uri URI = Uri.withAppendedPath(BASE, TABLE);
        public static final String DISH_ID = "dish_id";
        public static final String NAME = "name";
        public static final String RESTAURANT_ID = "restaurant_id";
        public static final String DATA = "data";
    }

    public static final class Searches implements BaseColumns {
        public static final String TABLE = "searches";
        public static final Uri URI = Uri.withAppendedPath(BASE, TABLE);
        public static final String QUERY = "query";
        public static final String SEARCHED_AT = "searched_at";
    }

    /** A local copy of every order this phone has seen; DATA holds the full order as JSON. */
    public static final class Orders {
        public static final String TABLE = "orders";
        public static final Uri URI = Uri.withAppendedPath(BASE, TABLE);
        public static final String ID = "id";
        public static final String CUSTOMER_UID = "customer_uid";
        public static final String RIDER_UID = "rider_uid";
        public static final String STATUS = "status";
        public static final String PLACED_AT = "placed_at";
        public static final String DATA = "data";
    }

    public static final class Reviews {
        public static final String TABLE = "reviews";
        public static final Uri URI = Uri.withAppendedPath(BASE, TABLE);
        public static final String ID = "id";
        public static final String RESTAURANT_ID = "restaurant_id";
        public static final String AUTHOR = "author";
        public static final String RATING = "rating";
        public static final String TEXT = "text";
        public static final String CREATED_AT = "created_at";
        public static final String REPLY = "reply";
    }
}
