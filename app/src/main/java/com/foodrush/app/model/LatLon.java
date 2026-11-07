package com.foodrush.app.model;

import java.util.Locale;

/** A point on the map. Distances use the haversine formula, accurate to well under 1% at city scale. */
public final class LatLon {

    private static final double EARTH_METERS = 6_371_000;

    public final double lat;
    public final double lon;

    public LatLon(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double metersTo(LatLon other) {
        return meters(lat, lon, other.lat, other.lon);
    }

    public static double meters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Short text form used inside stored routes: "lat,lon". */
    public String encode() {
        return String.format(Locale.US, "%.5f,%.5f", lat, lon);
    }

    public static LatLon decode(String text) {
        String[] parts = text.split(",");
        return new LatLon(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LatLon)) {
            return false;
        }
        LatLon other = (LatLon) o;
        return Double.compare(lat, other.lat) == 0 && Double.compare(lon, other.lon) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(lat) * 31 + Double.hashCode(lon);
    }
}
