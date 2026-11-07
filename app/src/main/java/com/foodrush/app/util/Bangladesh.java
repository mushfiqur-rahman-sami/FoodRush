package com.foodrush.app.util;

import com.foodrush.app.model.LatLon;

/** Checks that keep addresses and phone numbers within Bangladesh. */
public final class Bangladesh {

    /**
     * Country outline from Natural Earth (public domain), lon/lat pairs. It is coarse, so points
     * within {@link #EDGE_TOLERANCE_METERS} of it also count, which keeps coastal and border towns in.
     */
    private static final double[][] OUTLINE = {
            {92.672721, 22.041239}, {92.652257, 21.324048}, {92.303234, 21.475485}, {92.368554, 20.670883},
            {92.082886, 21.192195}, {92.025215, 21.70157}, {91.834891, 22.182936}, {91.417087, 22.765019},
            {90.496006, 22.805017}, {90.586957, 22.392794}, {90.272971, 21.836368}, {89.847467, 22.039146},
            {89.70205, 21.857116}, {89.418863, 21.966179}, {89.031961, 22.055708}, {88.876312, 22.879146},
            {88.52977, 23.631142}, {88.69994, 24.233715}, {88.084422, 24.501657}, {88.306373, 24.866079},
            {88.931554, 25.238692}, {88.209789, 25.768066}, {88.563049, 26.446526}, {89.355094, 26.014407},
            {89.832481, 25.965082}, {89.920693, 25.26975}, {90.872211, 25.132601}, {91.799596, 25.147432},
            {92.376202, 24.976693}, {91.915093, 24.130414}, {91.46773, 24.072639}, {91.158963, 23.503527},
            {91.706475, 22.985264}, {91.869928, 23.624346}, {92.146035, 23.627499}, {92.672721, 22.041239},
    };
    private static final double EDGE_TOLERANCE_METERS = 15000;

    /** The map is limited to this box: north, east, south, west. */
    public static final double NORTH = 26.75;
    public static final double EAST = 92.75;
    public static final double SOUTH = 20.55;
    public static final double WEST = 88.0;

    private Bangladesh() {
    }

    public static boolean contains(LatLon p) {
        if (p.lat < SOUTH || p.lat > NORTH || p.lon < WEST || p.lon > EAST) {
            return false;
        }
        return insideOutline(p) || distanceToOutline(p) <= EDGE_TOLERANCE_METERS;
    }

    /** Ray casting over the outline. */
    private static boolean insideOutline(LatLon p) {
        boolean inside = false;
        for (int i = 0, j = OUTLINE.length - 1; i < OUTLINE.length; j = i++) {
            double xi = OUTLINE[i][0];
            double yi = OUTLINE[i][1];
            double xj = OUTLINE[j][0];
            double yj = OUTLINE[j][1];
            if ((yi > p.lat) != (yj > p.lat) && p.lon < (xj - xi) * (p.lat - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double distanceToOutline(LatLon p) {
        double best = Double.MAX_VALUE;
        double scale = Math.cos(Math.toRadians(p.lat));
        for (int i = 1; i < OUTLINE.length; i++) {
            double ax = OUTLINE[i - 1][0] * scale;
            double ay = OUTLINE[i - 1][1];
            double bx = OUTLINE[i][0] * scale;
            double by = OUTLINE[i][1];
            double px = p.lon * scale;
            double dx = bx - ax;
            double dy = by - ay;
            double len = dx * dx + dy * dy;
            double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (p.lat - ay) * dy) / len));
            double qx = ax + dx * t;
            double qy = ay + dy * t;
            double meters = Math.hypot(px - qx, p.lat - qy) * 111320;
            best = Math.min(best, meters);
        }
        return best;
    }

    /**
     * Normalises a Bangladeshi mobile number to "+8801XXXXXXXXX", or returns null when it is not
     * one. Accepts "01712345678", "8801712345678" and "+880 1712-345678". Operators use 013 to 019.
     */
    public static String normalizePhone(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.startsWith("880")) {
            digits = digits.substring(3);
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.matches("1[3-9]\\d{8}") ? "+880" + digits : null;
    }
}
