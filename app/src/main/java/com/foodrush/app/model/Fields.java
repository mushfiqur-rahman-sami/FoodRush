package com.foodrush.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Null-safe reads from Firestore maps, whose numbers arrive as Long or Double. */
public final class Fields {

    private Fields() {
    }

    public static String text(Map<String, Object> m, String key) {
        Object value = m.get(key);
        return value == null ? "" : value.toString();
    }

    /** Null when the field is missing, unlike {@link #text}. */
    public static String nullableText(Map<String, Object> m, String key) {
        Object value = m.get(key);
        return value == null ? null : value.toString();
    }

    public static long number(Map<String, Object> m, String key) {
        Object value = m.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    public static int integer(Map<String, Object> m, String key, int fallback) {
        Object value = m.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    public static double decimal(Map<String, Object> m, String key) {
        Object value = m.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    public static boolean flag(Map<String, Object> m, String key, boolean fallback) {
        Object value = m.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    @SuppressWarnings("unchecked")
    public static List<String> strings(Map<String, Object> m, String key) {
        List<String> list = new ArrayList<>();
        Object value = m.get(key);
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item != null) {
                    list.add(item.toString());
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> maps(Map<String, Object> m, String key) {
        List<Map<String, Object>> list = new ArrayList<>();
        Object value = m.get(key);
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item instanceof Map) {
                    list.add((Map<String, Object>) item);
                }
            }
        }
        return list;
    }
}
