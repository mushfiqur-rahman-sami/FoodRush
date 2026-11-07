package com.foodrush.app.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the app's Firestore-shaped maps to JSON text for SQLite, and back. */
public final class Json {

    private Json() {
    }

    public static String write(Map<String, Object> map) {
        return new JSONObject(map).toString();
    }

    public static Map<String, Object> read(String text) {
        try {
            return toMap(new JSONObject(text));
        } catch (JSONException e) {
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, unwrap(object.get(key)));
        }
        return map;
    }

    private static Object unwrap(Object value) throws JSONException {
        if (value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject) {
            return toMap((JSONObject) value);
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                list.add(unwrap(array.get(i)));
            }
            return list;
        }
        return value;
    }
}
