package com.foodrush.app.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A restaurant, from the bundled seed or Firestore. Owners edit most of these fields in the app. */
public class Restaurant {

    public static final int DEFAULT_RADIUS_KM = 8;
    /** Seven entries, Sunday first, like {@link Calendar#DAY_OF_WEEK}: "10:00-23:00", or "" when closed. */
    public static final int DAYS = 7;

    public String id;
    public String name;
    public String description = "";
    public String cuisine = "";
    public String city = "";
    public String area = "";
    public String address = "";
    public double lat;
    public double lon;
    public String phone = "";
    /** Cover photo: "res:..." for bundled photos or a data URL. */
    public String image;
    public String logo;
    public float rating = 4.5f;
    public int ratingCount;
    public String ownerUid;
    public boolean open = true;
    /** Busy mode: extra minutes of cooking time on rush days (0, 15 or 30). */
    public int busyMinutes;
    /** Busy mode: stays open but takes no new orders for now. */
    public boolean paused;
    public List<String> hours = new ArrayList<>(Arrays.asList("10:00-23:00", "10:00-23:00", "10:00-23:00",
            "10:00-23:00", "10:00-23:00", "10:00-23:00", "10:00-23:00"));
    public int minOrder = 150;
    public int radiusKm = DEFAULT_RADIUS_KM;
    public List<String> categories = new ArrayList<>();

    public LatLon location() {
        return new LatLon(lat, lon);
    }

    public String areaLabel() {
        return area.isEmpty() ? city : area + ", " + city;
    }

    /** Owner switch, busy-mode pause and today's opening hours. Ranges past midnight (18:00-02:00) are handled. */
    public boolean isOpenAt(Calendar now) {
        if (!open || paused) {
            return false;
        }
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int today = now.get(Calendar.DAY_OF_WEEK) - 1;
        int yesterday = (today + DAYS - 1) % DAYS;
        int[] range = parseRange(hourAt(today));
        if (range != null) {
            if (range[1] > range[0] ? minute >= range[0] && minute < range[1] : minute >= range[0]) {
                return true;
            }
        }
        int[] late = parseRange(hourAt(yesterday));
        return late != null && late[1] <= late[0] && minute < late[1];
    }

    private String hourAt(int day) {
        return hours != null && day < hours.size() ? hours.get(day) : "";
    }

    /** Minutes since midnight for "HH:mm-HH:mm", or null when closed or malformed. */
    public static int[] parseRange(String text) {
        if (text == null || !text.matches("\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}")) {
            return null;
        }
        String[] parts = text.split("-");
        return new int[]{minutes(parts[0]), minutes(parts[1])};
    }

    private static int minutes(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    public static String formatRange(int openMinute, int closeMinute) {
        return String.format(Locale.US, "%02d:%02d-%02d:%02d", openMinute / 60, openMinute % 60,
                closeMinute / 60, closeMinute % 60);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", description);
        m.put("cuisine", cuisine);
        m.put("city", city);
        m.put("area", area);
        m.put("address", address);
        m.put("lat", lat);
        m.put("lon", lon);
        m.put("phone", phone);
        m.put("image", image);
        m.put("logo", logo);
        m.put("rating", (double) rating);
        m.put("ratingCount", ratingCount);
        m.put("ownerUid", ownerUid);
        m.put("open", open);
        m.put("busyMinutes", busyMinutes);
        m.put("paused", paused);
        m.put("hours", hours);
        m.put("minOrder", minOrder);
        m.put("radiusKm", radiusKm);
        m.put("categories", categories);
        return m;
    }

    public static Restaurant from(Map<String, Object> m) {
        Restaurant r = new Restaurant();
        r.id = Fields.text(m, "id");
        r.name = Fields.text(m, "name");
        r.description = Fields.text(m, "description");
        r.cuisine = Fields.text(m, "cuisine");
        r.city = Fields.text(m, "city");
        r.area = Fields.text(m, "area");
        r.address = Fields.text(m, "address");
        r.lat = Fields.decimal(m, "lat");
        r.lon = Fields.decimal(m, "lon");
        r.phone = Fields.text(m, "phone");
        r.image = Fields.nullableText(m, "image");
        r.logo = Fields.nullableText(m, "logo");
        r.rating = (float) Fields.decimal(m, "rating");
        r.ratingCount = Fields.integer(m, "ratingCount", 0);
        r.ownerUid = Fields.nullableText(m, "ownerUid");
        r.open = Fields.flag(m, "open", true);
        r.busyMinutes = Fields.integer(m, "busyMinutes", 0);
        r.paused = Fields.flag(m, "paused", false);
        List<String> hours = Fields.strings(m, "hours");
        if (hours.size() == DAYS) {
            r.hours = hours;
        }
        r.minOrder = Fields.integer(m, "minOrder", 150);
        r.radiusKm = Fields.integer(m, "radiusKm", DEFAULT_RADIUS_KM);
        r.categories = Fields.strings(m, "categories");
        return r;
    }
}
