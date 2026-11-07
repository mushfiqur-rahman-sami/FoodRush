package com.foodrush.app.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.foodrush.app.data.DbHelper;
import com.foodrush.app.data.FoodRushContract;
import com.foodrush.app.data.FoodRushContract.Cart;
import com.foodrush.app.data.FoodRushContract.Favourites;
import com.foodrush.app.data.FoodRushContract.Orders;
import com.foodrush.app.data.FoodRushContract.Reviews;
import com.foodrush.app.data.FoodRushContract.Searches;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cart, favourites, recent searches, orders and reviews over SQLite. Exported, but guarded by a
 * signature-level permission in the manifest, so only apps signed with the same key can reach it.
 */
public class
FoodRushProvider extends ContentProvider {

    private static final int DIR_OFFSET = 0;
    private static final int ITEM_OFFSET = 100;

    private static final String[] TABLES = {Cart.TABLE, Favourites.TABLE, Searches.TABLE, Orders.TABLE, Reviews.TABLE};
    private static final String[] KEY_COLUMNS = {Cart._ID, Favourites.DISH_ID, Searches._ID, Orders.ID, Reviews.ID};

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        for (int i = 0; i < TABLES.length; i++) {
            MATCHER.addURI(FoodRushContract.AUTHORITY, TABLES[i], DIR_OFFSET + i);
            MATCHER.addURI(FoodRushContract.AUTHORITY, TABLES[i] + "/*", ITEM_OFFSET + i);
        }
    }

    private DbHelper helper;

    @Override
    public boolean onCreate() {
        helper = DbHelper.get(getContext());
        return true;
    }

    private static int match(Uri uri) {
        int code = MATCHER.match(uri);
        if (code == UriMatcher.NO_MATCH) {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        return code;
    }

    private static boolean isItem(int code) {
        return code >= ITEM_OFFSET;
    }

    private static int tableIndex(int code) {
        return isItem(code) ? code - ITEM_OFFSET : code - DIR_OFFSET;
    }

    private static String withKey(int code, String selection) {
        if (!isItem(code)) {
            return selection;
        }
        String keyClause = KEY_COLUMNS[tableIndex(code)] + " = ?";
        return selection == null ? keyClause : keyClause + " AND (" + selection + ")";
    }

    private static String[] withKeyArgs(int code, Uri uri, String[] args) {
        if (!isItem(code)) {
            return args;
        }
        List<String> all = new ArrayList<>();
        all.add(uri.getLastPathSegment());
        if (args != null) {
            all.addAll(Arrays.asList(args));
        }
        return all.toArray(new String[0]);
    }

    private void notifyChange(Uri uri) {
        Context context = getContext();
        if (context != null) {
            context.getContentResolver().notifyChange(uri, null);
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        int code = match(uri);
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(TABLES[tableIndex(code)]);
        builder.setStrict(true);
        Cursor cursor = builder.query(helper.getReadableDatabase(), projection, withKey(code, selection),
                withKeyArgs(code, uri, selectionArgs), null, null, sortOrder);
        Context context = getContext();
        if (context != null) {
            cursor.setNotificationUri(context.getContentResolver(), uri);
        }
        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        int code = match(uri);
        if (isItem(code)) {
            throw new IllegalArgumentException("Insert needs a table URI: " + uri);
        }
        // REPLACE: favourites and searches are keyed by value, and orders re-sync from Firestore.
        long row = helper.getWritableDatabase().insertWithOnConflict(TABLES[tableIndex(code)], null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        notifyChange(uri);
        return ContentUris.withAppendedId(uri, row);
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        int code = match(uri);
        int rows = helper.getWritableDatabase().update(TABLES[tableIndex(code)], values, withKey(code, selection),
                withKeyArgs(code, uri, selectionArgs));
        if (rows > 0) {
            notifyChange(uri);
        }
        return rows;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int code = match(uri);
        int rows = helper.getWritableDatabase().delete(TABLES[tableIndex(code)], withKey(code, selection),
                withKeyArgs(code, uri, selectionArgs));
        if (rows > 0) {
            notifyChange(uri);
        }
        return rows;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        int code = match(uri);
        String kind = isItem(code) ? "vnd.android.cursor.item/" : "vnd.android.cursor.dir/";
        return kind + "vnd.foodrush." + TABLES[tableIndex(code)];
    }
}
