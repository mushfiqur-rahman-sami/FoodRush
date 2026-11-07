package com.foodrush.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.foodrush.app.data.FoodRushContract.Searches;

import java.util.ArrayList;
import java.util.List;

/** Blocking calls through the content provider; run them with AppExecutors. */
public final class SearchHistoryRepository {

    private static final int KEEP = 8;

    private SearchHistoryRepository() {
    }

    public static void record(Context context, String query) {
        ContentValues values = new ContentValues();
        values.put(Searches.QUERY, query.trim());
        values.put(Searches.SEARCHED_AT, System.currentTimeMillis());
        context.getContentResolver().insert(Searches.URI, values);
    }

    public static List<String> recent(Context context) {
        List<String> queries = new ArrayList<>();
        try (Cursor c = context.getContentResolver().query(Searches.URI, new String[]{Searches.QUERY}, null, null,
                Searches.SEARCHED_AT + " DESC")) {
            while (c != null && c.moveToNext() && queries.size() < KEEP) {
                queries.add(c.getString(0));
            }
        }
        return queries;
    }

    public static void clear(Context context) {
        context.getContentResolver().delete(Searches.URI, null, null);
    }
}
