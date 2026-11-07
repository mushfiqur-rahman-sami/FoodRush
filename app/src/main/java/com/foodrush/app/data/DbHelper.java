package com.foodrush.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.foodrush.app.data.FoodRushContract.Cart;
import com.foodrush.app.data.FoodRushContract.Favourites;
import com.foodrush.app.data.FoodRushContract.Orders;
import com.foodrush.app.data.FoodRushContract.Reviews;
import com.foodrush.app.data.FoodRushContract.Searches;

public class DbHelper extends SQLiteOpenHelper {

    private static final String NAME = "foodrush.db";
    private static final int VERSION = 3;

    /** Device-only accounts, used when Firebase is not configured. Not exposed by the provider. */
    static final String USERS = "users";

    private static DbHelper instance;

    public static synchronized DbHelper get(Context context) {
        if (instance == null) {
            instance = new DbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DbHelper(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAppTables(db);
        db.execSQL("CREATE TABLE " + USERS + " (id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, "
                + "salt TEXT NOT NULL, hash TEXT NOT NULL)");
    }

    private void createAppTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + Cart.TABLE + " (" + Cart._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Cart.DISH_ID + " TEXT NOT NULL, " + Cart.NAME + " TEXT NOT NULL, " + Cart.IMAGE + " TEXT, "
                + Cart.RESTAURANT_ID + " TEXT NOT NULL, " + Cart.SIZE + " TEXT NOT NULL, "
                + Cart.EXTRAS + " TEXT NOT NULL, " + Cart.UNIT_PRICE + " INTEGER NOT NULL, "
                + Cart.QUANTITY + " INTEGER NOT NULL, " + Cart.PREP_MINUTES + " INTEGER NOT NULL DEFAULT 15)");
        db.execSQL("CREATE TABLE " + Favourites.TABLE + " (" + Favourites._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Favourites.DISH_ID + " TEXT NOT NULL UNIQUE, " + Favourites.NAME + " TEXT NOT NULL, "
                + Favourites.RESTAURANT_ID + " TEXT, " + Favourites.DATA + " TEXT NOT NULL)");
        db.execSQL("CREATE TABLE " + Searches.TABLE + " (" + Searches._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Searches.QUERY + " TEXT NOT NULL UNIQUE, " + Searches.SEARCHED_AT + " INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE " + Orders.TABLE + " (" + Orders.ID + " TEXT PRIMARY KEY, "
                + Orders.CUSTOMER_UID + " TEXT, " + Orders.RIDER_UID + " TEXT, " + Orders.STATUS + " INTEGER NOT NULL, "
                + Orders.PLACED_AT + " INTEGER NOT NULL, " + Orders.DATA + " TEXT NOT NULL)");
        db.execSQL("CREATE TABLE " + Reviews.TABLE + " (" + Reviews.ID + " TEXT PRIMARY KEY, "
                + Reviews.RESTAURANT_ID + " TEXT NOT NULL, " + Reviews.AUTHOR + " TEXT NOT NULL, "
                + Reviews.RATING + " REAL NOT NULL, " + Reviews.TEXT + " TEXT NOT NULL, "
                + Reviews.CREATED_AT + " INTEGER NOT NULL, " + Reviews.REPLY + " TEXT NOT NULL DEFAULT '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + USERS + " (id TEXT PRIMARY KEY, name TEXT NOT NULL, "
                    + "email TEXT NOT NULL UNIQUE, salt TEXT NOT NULL, hash TEXT NOT NULL)");
        }
        if (oldVersion < 3) {
            // Version 3 moved to Bangladesh: prices in taka and new menus, so old carts, saved dishes,
            // orders and reviews no longer match anything. Accounts are kept.
            for (String table : new String[]{Cart.TABLE, Favourites.TABLE, Searches.TABLE, Orders.TABLE, Reviews.TABLE}) {
                db.execSQL("DROP TABLE IF EXISTS " + table);
            }
            createAppTables(db);
        }
    }
}
