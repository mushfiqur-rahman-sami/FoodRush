package com.foodrush.app.route;

import com.foodrush.app.model.LatLon;

import java.util.ArrayList;
import java.util.List;

/** Measuring, walking along and thinning out map lines. */
public final class Polylines {

    private Polylines() {
    }

    public static double length(List<LatLon> line) {
        double total = 0;
        for (int i = 1; i < line.size(); i++) {
            total += line.get(i - 1).metersTo(line.get(i));
        }
        return total;
    }

    /** The point a given distance along the line, clamped to its ends. */
    public static LatLon pointAt(List<LatLon> line, double meters) {
        if (line.isEmpty()) {
            return null;
        }
        double remaining = Math.max(0, meters);
        for (int i = 1; i < line.size(); i++) {
            LatLon a = line.get(i - 1);
            LatLon b = line.get(i);
            double leg = a.metersTo(b);
            if (remaining <= leg && leg > 0) {
                double t = remaining / leg;
                return new LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t);
            }
            remaining -= leg;
        }
        return line.get(line.size() - 1);
    }

    /** How far along the line the point nearest to {@code p} is, in meters from the start. */
    public static double progress(List<LatLon> line, LatLon p) {
        double best = Double.MAX_VALUE;
        double bestAlong = 0;
        double along = 0;
        for (int i = 1; i < line.size(); i++) {
            LatLon a = line.get(i - 1);
            LatLon b = line.get(i);
            double leg = a.metersTo(b);
            double t = projection(a, b, p);
            LatLon q = new LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t);
            double d = q.metersTo(p);
            if (d < best) {
                best = d;
                bestAlong = along + leg * t;
            }
            along += leg;
        }
        return bestAlong;
    }

    private static double projection(LatLon a, LatLon b, LatLon p) {
        double scale = Math.cos(Math.toRadians(a.lat));
        double ax = a.lon * scale;
        double bx = b.lon * scale;
        double px = p.lon * scale;
        double dx = bx - ax;
        double dy = b.lat - a.lat;
        double len = dx * dx + dy * dy;
        if (len == 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, ((px - ax) * dx + (p.lat - a.lat) * dy) / len));
    }

    /** Douglas-Peucker: drops points that bend the line by less than {@code toleranceMeters}. */
    public static List<LatLon> simplify(List<LatLon> line, double toleranceMeters) {
        if (line.size() < 3) {
            return new ArrayList<>(line);
        }
        boolean[] keep = new boolean[line.size()];
        keep[0] = true;
        keep[line.size() - 1] = true;
        mark(line, 0, line.size() - 1, toleranceMeters, keep);
        List<LatLon> out = new ArrayList<>();
        for (int i = 0; i < line.size(); i++) {
            if (keep[i]) {
                out.add(line.get(i));
            }
        }
        return out;
    }

    private static void mark(List<LatLon> line, int first, int last, double tolerance, boolean[] keep) {
        if (last <= first + 1) {
            return;
        }
        double worst = -1;
        int index = -1;
        LatLon a = line.get(first);
        LatLon b = line.get(last);
        for (int i = first + 1; i < last; i++) {
            LatLon p = line.get(i);
            double t = projection(a, b, p);
            LatLon q = new LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t);
            double d = q.metersTo(p);
            if (d > worst) {
                worst = d;
                index = i;
            }
        }
        if (worst > tolerance) {
            keep[index] = true;
            mark(line, first, index, tolerance, keep);
            mark(line, index, last, tolerance, keep);
        }
    }
}
