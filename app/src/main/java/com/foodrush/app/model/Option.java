package com.foodrush.app.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A size (e.g. "Half", 220 tk) or an add-on (e.g. "Extra egg", +20 tk). Prices are whole taka. */
public final class Option {

    public final String name;
    public final int price;

    public Option(String name, int price) {
        this.name = name;
        this.price = price;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("price", price);
        return m;
    }

    public static List<Map<String, Object>> toMaps(List<Option> options) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Option option : options) {
            list.add(option.toMap());
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static List<Option> fromMaps(Object value) {
        List<Option> options = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) item;
                    Object price = m.get("price");
                    options.add(new Option(String.valueOf(m.get("name")),
                            price instanceof Number ? ((Number) price).intValue() : 0));
                }
            }
        }
        return options;
    }

    /** "Half:220,Full:350" form used by the bundled XML. */
    public static List<Option> parse(String text) {
        List<Option> options = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return options;
        }
        for (String part : text.split(",")) {
            int colon = part.lastIndexOf(':');
            if (colon > 0) {
                options.add(new Option(part.substring(0, colon).trim(), Integer.parseInt(part.substring(colon + 1).trim())));
            }
        }
        return options;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Option && ((Option) o).name.equals(name) && ((Option) o).price == price;
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + price;
    }
}
