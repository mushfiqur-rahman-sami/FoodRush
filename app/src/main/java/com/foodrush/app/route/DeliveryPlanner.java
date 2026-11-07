package com.foodrush.app.route;

import com.foodrush.app.model.LatLon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Turns road-network shortest paths into the times, distances and routes the app shows. */
public final class DeliveryPlanner {

    /** Farther than this from any road junction counts as off the network. */
    static final double MAX_SNAP_METERS = 1500;
    /** Walking or riding pace for the bit between an address and its nearest junction. */
    private static final double OFF_ROAD_METERS_PER_MINUTE = 200;

    private final RoadGraph graph;
    private final RoadGraph reversed;

    public DeliveryPlanner(RoadGraph graph) {
        this.graph = graph;
        this.reversed = graph.reversed();
    }

    public static final class Plan {
        /** Map points in riding order: first pickup, the other pickups, then the customer. */
        public final List<LatLon> points;
        /** Indexes into the pickups list, in the order the rider visits them. */
        public final List<Integer> pickupOrder;
        public final double minutes;
        public final double meters;

        public Plan(List<LatLon> points, List<Integer> pickupOrder, double minutes, double meters) {
            this.points = points;
            this.pickupOrder = pickupOrder;
            this.minutes = minutes;
            this.meters = meters;
        }
    }

    private RoadGraph.Node snap(LatLon point) {
        RoadGraph.Node node = graph.nearestNode(point.lat, point.lon);
        if (node == null || RoadGraph.metersBetween(point.lat, point.lon, node.lat, node.lon) > MAX_SNAP_METERS) {
            return null;
        }
        return node;
    }

    private static double offRoadMinutes(LatLon point, RoadGraph.Node node) {
        return RoadGraph.metersBetween(point.lat, point.lon, node.lat, node.lon) / OFF_ROAD_METERS_PER_MINUTE;
    }

    /**
     * Riding minutes from each source to the customer. One run over the reversed graph answers this
     * for every source at once, and respects one-way streets in the riding direction. Unreachable
     * sources get {@link Double#POSITIVE_INFINITY}.
     */
    public double[] minutesToCustomer(LatLon customer, List<LatLon> sources) {
        double[] minutes = new double[sources.size()];
        java.util.Arrays.fill(minutes, Double.POSITIVE_INFINITY);
        RoadGraph.Node target = snap(customer);
        if (target == null) {
            return minutes;
        }
        Dijkstra.Result result = Dijkstra.run(reversed, target.id);
        for (int i = 0; i < sources.size(); i++) {
            RoadGraph.Node from = snap(sources.get(i));
            if (from != null) {
                minutes[i] = result.minutesTo(from.id) + offRoadMinutes(sources.get(i), from)
                        + offRoadMinutes(customer, target);
            }
        }
        return minutes;
    }

    /** Riding minutes from one origin (a rider) to many targets (restaurants), with one Dijkstra run. */
    public double[] minutesFrom(LatLon origin, List<LatLon> targets) {
        double[] minutes = new double[targets.size()];
        java.util.Arrays.fill(minutes, Double.POSITIVE_INFINITY);
        RoadGraph.Node start = snap(origin);
        if (start == null) {
            return minutes;
        }
        Dijkstra.Result result = Dijkstra.run(graph, start.id);
        for (int i = 0; i < targets.size(); i++) {
            RoadGraph.Node to = snap(targets.get(i));
            if (to != null) {
                minutes[i] = result.minutesTo(to.id) + offRoadMinutes(origin, start) + offRoadMinutes(targets.get(i), to);
            }
        }
        return minutes;
    }

    /**
     * Route through every pickup, then to the customer. Starts at the pickup farthest from the
     * customer and always rides to the nearest remaining one. That is a heuristic, not the
     * guaranteed-best visiting order, but each leg is an exact shortest path and carts rarely
     * span more than a few restaurants. Returns null when some point is off the network.
     */
    public Plan plan(List<LatLon> pickups, LatLon customer) {
        RoadGraph.Node customerNode = snap(customer);
        if (customerNode == null || pickups.isEmpty()) {
            return null;
        }
        List<RoadGraph.Node> pickupNodes = new ArrayList<>();
        for (LatLon pickup : pickups) {
            RoadGraph.Node node = snap(pickup);
            if (node == null) {
                return null;
            }
            pickupNodes.add(node);
        }

        double[] toCustomer = minutesToCustomer(customer, pickups);
        Set<Integer> remaining = new LinkedHashSet<>();
        int start = 0;
        for (int i = 0; i < pickups.size(); i++) {
            remaining.add(i);
            if (toCustomer[i] > toCustomer[start]) {
                start = i;
            }
        }
        if (Double.isInfinite(toCustomer[start])) {
            return null;
        }
        remaining.remove(start);

        List<LatLon> points = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        points.add(pickups.get(start));
        order.add(start);
        double minutes = offRoadMinutes(pickups.get(start), pickupNodes.get(start));
        double meters = 0;
        int at = start;
        while (!remaining.isEmpty()) {
            Dijkstra.Result leg = Dijkstra.run(graph, pickupNodes.get(at).id);
            int next = -1;
            for (int candidate : remaining) {
                if (next < 0 || leg.minutesTo(pickupNodes.get(candidate).id) < leg.minutesTo(pickupNodes.get(next).id)) {
                    next = candidate;
                }
            }
            List<String> path = leg.pathTo(pickupNodes.get(next).id);
            if (path.isEmpty()) {
                return null;
            }
            points.addAll(graph.geometry(path));
            points.add(pickups.get(next));
            minutes += leg.minutesTo(pickupNodes.get(next).id) + 2 * offRoadMinutes(pickups.get(next), pickupNodes.get(next));
            meters += graph.meters(path);
            order.add(next);
            remaining.remove(next);
            at = next;
        }
        Dijkstra.Result last = Dijkstra.run(graph, pickupNodes.get(at).id);
        List<String> path = last.pathTo(customerNode.id);
        if (path.isEmpty()) {
            return null;
        }
        points.addAll(graph.geometry(path));
        points.add(customer);
        minutes += last.minutesTo(customerNode.id) + offRoadMinutes(customer, customerNode);
        meters += graph.meters(path);
        return new Plan(points, order, minutes, meters);
    }
}
