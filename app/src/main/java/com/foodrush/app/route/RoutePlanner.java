package com.foodrush.app.route;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import com.foodrush.app.model.LatLon;
import com.foodrush.app.network.ApiClient;
import com.foodrush.app.network.GeoApi;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Real road routing anywhere in Bangladesh, for free.
 *
 * For each question the app downloads only the roads in a box around the points involved, from
 * OpenStreetMap's Overpass service, builds a graph with {@link OsmGraphParser} and runs
 * {@link Dijkstra}. Downloads are cached on the phone for a month. If Overpass is down, the public
 * OSRM router is the backup, and if that fails too the app falls back to a straight-line estimate.
 * Every method here blocks, so call them off the main thread.
 */
public final class RoutePlanner {

    public static final String SOURCE_DIJKSTRA = "dijkstra";
    public static final String SOURCE_OSRM = "osrm";
    public static final String SOURCE_ESTIMATE = "estimate";

    private static final String TAG = "RoutePlanner";
    private static final String[] OVERPASS_SERVERS = {
            "https://overpass-api.de/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
    };
    private static final String ALL_ROADS = "motorway|trunk|primary|secondary|tertiary|unclassified|residential"
            + "|living_street|service|road|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link";
    private static final String MAIN_ROADS = "motorway|trunk|primary|secondary|tertiary|unclassified"
            + "|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link";
    /** Boxes wider than this (degrees, about 6.5 km) download main roads only, to stay small. */
    private static final double DETAIL_SPAN = 0.06;
    private static final double GRID = 0.02;
    private static final double MARGIN = 0.012;
    private static final long CACHE_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final int MAX_CACHE_FILES = 12;
    /** Straight-line estimate: roads wind about 40% longer, at an average 17 km/h in traffic. */
    private static final double DETOUR = 1.4;
    private static final double ESTIMATE_METERS_PER_MINUTE = 17000 / 60.0;

    /** Give up on Overpass after this long and let OSRM or the estimate answer instead. */
    private static final long DOWNLOAD_BUDGET_MILLIS = 45_000;
    /** Finished delivery routes are reused this long; a road route for longer than a backup one. */
    private static final long ROUTE_CACHE_MILLIS = 15 * 60_000;
    private static final long BACKUP_ROUTE_CACHE_MILLIS = 3 * 60_000;
    /** How long a waiting screen gives OSRM after Dijkstra has run out of time. */
    private static final long OSRM_GRACE_MILLIS = 4_000;

    /** One lock per road area, so a download for the home screen never holds up checkout. */
    private static final Map<String, Object> AREA_LOCKS = new HashMap<>();
    /** The few most recent road graphs, so screens asking about the same area share one. */
    private static final Map<String, DeliveryPlanner> PLANNERS = new LinkedHashMap<String, DeliveryPlanner>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, DeliveryPlanner> eldest) {
            return size() > 3;
        }
    };
    private static final Map<String, CachedRoute> ROUTES = new HashMap<>();
    private static final Map<String, Future<Result>> IN_FLIGHT = new HashMap<>();
    private static final ExecutorService WORKERS = Executors.newCachedThreadPool();

    private static final class CachedRoute {
        final Result result;
        final long at;

        CachedRoute(Result result, long at) {
            this.result = result;
            this.at = at;
        }

        boolean fresh(long now) {
            long ttl = SOURCE_DIJKSTRA.equals(result.source) ? ROUTE_CACHE_MILLIS : BACKUP_ROUTE_CACHE_MILLIS;
            return now - at < ttl;
        }
    }

    private final Context app;

    public RoutePlanner(Context context) {
        this.app = context.getApplicationContext();
    }

    public static final class Result {
        public final List<LatLon> points;
        public final List<Integer> pickupOrder;
        public final double minutes;
        public final double meters;
        public final String source;

        Result(List<LatLon> points, List<Integer> pickupOrder, double minutes, double meters, String source) {
            this.points = points;
            this.pickupOrder = pickupOrder;
            this.minutes = minutes;
            this.meters = meters;
            this.source = source;
        }
    }

    public static double estimateMinutes(LatLon a, LatLon b) {
        return a.metersTo(b) * DETOUR / ESTIMATE_METERS_PER_MINUTE;
    }

    static String routeKey(List<LatLon> pickups, LatLon customer) {
        StringBuilder key = new StringBuilder();
        for (LatLon p : pickups) {
            key.append(p.encode()).append(';');
        }
        return key.append('>').append(customer.encode()).toString();
    }

    /** A recent route for exactly these stops, or null. */
    public static Result cached(List<LatLon> pickups, LatLon customer) {
        synchronized (ROUTES) {
            CachedRoute route = ROUTES.get(routeKey(pickups, customer));
            return route != null && route.fresh(System.currentTimeMillis()) ? route.result : null;
        }
    }

    /**
     * Starts working out the best route in the background, or joins the same work already running
     * (the cart starts it, checkout waits for it). Finished routes are cached.
     */
    public Future<Result> planAsync(List<LatLon> pickups, LatLon customer) {
        String key = routeKey(pickups, customer);
        synchronized (ROUTES) {
            CachedRoute route = ROUTES.get(key);
            if (route != null && route.fresh(System.currentTimeMillis())) {
                return CompletableFuture.completedFuture(route.result);
            }
            Future<Result> running = IN_FLIGHT.get(key);
            if (running != null) {
                return running;
            }
            List<LatLon> stops = new ArrayList<>(pickups);
            Future<Result> job = WORKERS.submit(() -> {
                Result result = plan(stops, customer);
                synchronized (ROUTES) {
                    ROUTES.put(key, new CachedRoute(result, System.currentTimeMillis()));
                    IN_FLIGHT.remove(key);
                }
                return result;
            });
            IN_FLIGHT.put(key, job);
            return job;
        }
    }

    /**
     * The best route available within {@code budgetMillis}. Dijkstra on real roads gets the whole
     * budget; OSRM is asked at the same time and answers if Dijkstra is still downloading roads; a
     * straight-line estimate is the last resort. Blocking: call off the main thread.
     */
    public Result planWithin(List<LatLon> pickups, LatLon customer, long budgetMillis) {
        Future<Result> best = planAsync(pickups, customer);
        if (best.isDone()) {
            return get(best, 0);
        }
        List<Integer> order = nearestNextOrder(pickups, customer);
        Future<Result> backup = WORKERS.submit(() -> osrm(pickups, order, customer));
        Result result = get(best, budgetMillis);
        if (result != null) {
            backup.cancel(true);
            return result;
        }
        result = get(backup, OSRM_GRACE_MILLIS);
        if (result == null) {
            result = estimate(pickups, order, customer);
        }
        synchronized (ROUTES) {
            // Keep it briefly, until the real road route (still running) replaces it.
            if (!ROUTES.containsKey(routeKey(pickups, customer))) {
                ROUTES.put(routeKey(pickups, customer), new CachedRoute(result, System.currentTimeMillis()));
            }
        }
        return result;
    }

    private static Result get(Future<Result> future, long millis) {
        try {
            return millis <= 0 ? future.get() : future.get(millis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | java.util.concurrent.CancellationException e) {
            Log.w(TAG, "Route work failed", e);
            return null;
        }
    }

    /** Pickups (restaurants) then the customer, with a real road route when one can be found. */
    public Result plan(List<LatLon> pickups, LatLon customer) {
        List<LatLon> all = new ArrayList<>(pickups);
        all.add(customer);
        try {
            DeliveryPlanner planner = plannerFor(all);
            DeliveryPlanner.Plan plan = planner.plan(pickups, customer);
            if (plan != null) {
                return new Result(Polylines.simplify(plan.points, 8), plan.pickupOrder, plan.minutes, plan.meters,
                        SOURCE_DIJKSTRA);
            }
        } catch (IOException | XmlPullParserException | RuntimeException e) {
            Log.w(TAG, "Road network unavailable, trying OSRM", e);
        }
        List<Integer> order = nearestNextOrder(pickups, customer);
        Result osrm = osrm(pickups, order, customer);
        return osrm != null ? osrm : estimate(pickups, order, customer);
    }

    /** Riding minutes from each source to the customer: Dijkstra when possible, else an estimate. */
    public double[] minutesToCustomer(LatLon customer, List<LatLon> sources) {
        List<LatLon> all = new ArrayList<>(sources);
        all.add(customer);
        double[] minutes = null;
        try {
            minutes = plannerFor(all).minutesToCustomer(customer, sources);
        } catch (IOException | XmlPullParserException | RuntimeException e) {
            Log.w(TAG, "Using estimates for restaurant times", e);
        }
        return fillEstimates(minutes, sources, customer);
    }

    /** Riding minutes from a rider to each target, from one Dijkstra run. */
    public double[] minutesFrom(LatLon origin, List<LatLon> targets) {
        List<LatLon> all = new ArrayList<>(targets);
        all.add(origin);
        double[] minutes = null;
        try {
            minutes = plannerFor(all).minutesFrom(origin, targets);
        } catch (IOException | XmlPullParserException | RuntimeException e) {
            Log.w(TAG, "Using estimates for rider times", e);
        }
        return fillEstimates(minutes, targets, origin);
    }

    private static double[] fillEstimates(double[] minutes, List<LatLon> points, LatLon other) {
        double[] out = minutes == null ? new double[points.size()] : minutes;
        if (minutes == null) {
            Arrays.fill(out, Double.POSITIVE_INFINITY);
        }
        for (int i = 0; i < out.length; i++) {
            if (Double.isInfinite(out[i])) {
                out[i] = estimateMinutes(points.get(i), other);
            }
        }
        return out;
    }

    private static List<Integer> nearestNextOrder(List<LatLon> pickups, LatLon customer) {
        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < pickups.size(); i++) {
            remaining.add(i);
        }
        int start = remaining.get(0);
        for (int i : remaining) {
            if (pickups.get(i).metersTo(customer) > pickups.get(start).metersTo(customer)) {
                start = i;
            }
        }
        List<Integer> order = new ArrayList<>();
        order.add(start);
        remaining.remove(Integer.valueOf(start));
        while (!remaining.isEmpty()) {
            LatLon at = pickups.get(order.get(order.size() - 1));
            int next = remaining.get(0);
            for (int i : remaining) {
                if (at.metersTo(pickups.get(i)) < at.metersTo(pickups.get(next))) {
                    next = i;
                }
            }
            order.add(next);
            remaining.remove(Integer.valueOf(next));
        }
        return order;
    }

    private Result osrm(List<LatLon> pickups, List<Integer> order, LatLon customer) {
        StringBuilder coordinates = new StringBuilder();
        for (int i : order) {
            coordinates.append(String.format(Locale.US, "%.6f,%.6f;", pickups.get(i).lon, pickups.get(i).lat));
        }
        coordinates.append(String.format(Locale.US, "%.6f,%.6f", customer.lon, customer.lat));
        try {
            retrofit2.Response<GeoApi.RouteResult> response = ApiClient.osrm(app).route(coordinates.toString()).execute();
            GeoApi.RouteResult body = response.body();
            if (!response.isSuccessful() || body == null || body.routes == null || body.routes.isEmpty()) {
                return null;
            }
            GeoApi.Route route = body.routes.get(0);
            List<LatLon> points = new ArrayList<>();
            for (List<Double> pair : route.geometry.coordinates) {
                points.add(new LatLon(pair.get(1), pair.get(0)));
            }
            // OSRM's car speeds are for empty roads; riders in city traffic are slower.
            double minutes = Math.max(route.seconds / 60 * 1.6, route.meters / ESTIMATE_METERS_PER_MINUTE);
            return new Result(Polylines.simplify(points, 8), order, minutes, route.meters, SOURCE_OSRM);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "OSRM failed", e);
            return null;
        }
    }

    private static Result estimate(List<LatLon> pickups, List<Integer> order, LatLon customer) {
        List<LatLon> points = new ArrayList<>();
        for (int i : order) {
            points.add(pickups.get(i));
        }
        points.add(customer);
        double meters = Polylines.length(points) * DETOUR;
        return new Result(points, order, meters / ESTIMATE_METERS_PER_MINUTE, meters, SOURCE_ESTIMATE);
    }

    // ---- Road network download and cache ----

    private DeliveryPlanner plannerFor(List<LatLon> points) throws IOException, XmlPullParserException {
        double south = Double.MAX_VALUE;
        double west = Double.MAX_VALUE;
        double north = -Double.MAX_VALUE;
        double east = -Double.MAX_VALUE;
        for (LatLon p : points) {
            south = Math.min(south, p.lat);
            north = Math.max(north, p.lat);
            west = Math.min(west, p.lon);
            east = Math.max(east, p.lon);
        }
        // Round outward to a grid so nearby questions share one download.
        south = Math.floor((south - MARGIN) / GRID) * GRID;
        west = Math.floor((west - MARGIN) / GRID) * GRID;
        north = Math.ceil((north + MARGIN) / GRID) * GRID;
        east = Math.ceil((east + MARGIN) / GRID) * GRID;
        boolean detailed = Math.max(north - south, east - west) <= DETAIL_SPAN;
        String key = String.format(Locale.US, "%s_%.2f_%.2f_%.2f_%.2f", detailed ? "all" : "main", south, west, north, east);
        Object lock;
        synchronized (AREA_LOCKS) {
            lock = AREA_LOCKS.computeIfAbsent(key, k -> new Object());
        }
        // Two screens asking about the same area share one download; different areas don't wait.
        synchronized (lock) {
            synchronized (PLANNERS) {
                DeliveryPlanner known = PLANNERS.get(key);
                if (known != null) {
                    return known;
                }
            }
            File file = download(key, String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", south, west, north, east),
                    detailed ? ALL_ROADS : MAIN_ROADS);
            RoadGraph graph;
            try (InputStream in = new FileInputStream(file)) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(in, "UTF-8");
                graph = OsmGraphParser.parse(parser);
            }
            if (graph.size() < 2) {
                throw new IOException("No roads in " + key);
            }
            DeliveryPlanner planner = new DeliveryPlanner(graph);
            synchronized (PLANNERS) {
                PLANNERS.put(key, planner);
            }
            return planner;
        }
    }

    private File download(String key, String box, String roadTypes) throws IOException {
        File dir = new File(app.getCacheDir(), "roads");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("No cache folder");
        }
        File file = new File(dir, key + ".osm");
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_MILLIS && file.length() > 0) {
            return file;
        }
        String query = "[out:xml][timeout:40];way[\"highway\"~\"^(" + roadTypes + ")$\"](" + box + ")->.w;"
                + ".w out body qt;node(w.w);out skel qt;";
        IOException last = null;
        long started = System.currentTimeMillis();
        for (String server : OVERPASS_SERVERS) {
            if (System.currentTimeMillis() - started > DOWNLOAD_BUDGET_MILLIS) {
                break;
            }
            Request request = new Request.Builder().url(server)
                    .post(new FormBody.Builder().add("data", query).build()).build();
            try (Response response = ApiClient.slowHttp(app).newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    last = new IOException("Overpass HTTP " + response.code());
                    continue;
                }
                File temp = new File(dir, key + ".part");
                try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                if (!temp.renameTo(file)) {
                    throw new IOException("Could not save road data");
                }
                trimCache(dir);
                return file;
            } catch (IOException e) {
                last = e;
            }
        }
        if (file.exists()) {
            // A stale copy beats no roads at all.
            return file;
        }
        throw last == null ? new IOException("Overpass unavailable") : last;
    }

    private static void trimCache(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".osm"));
        if (files == null || files.length <= MAX_CACHE_FILES) {
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - MAX_CACHE_FILES; i++) {
            if (!files[i].delete()) {
                Log.w(TAG, "Could not delete " + files[i]);
            }
        }
    }
}
