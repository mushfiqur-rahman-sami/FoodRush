package com.foodrush.app.route;

import com.foodrush.app.model.LatLon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Directed weighted graph of road junctions. Edge weights are riding minutes. An edge can carry the
 * bends of the road between its two junctions, so drawn routes follow the real street.
 */
public final class RoadGraph {

    public static final class Node {
        public final String id;
        public final String name;
        public final double lat;
        public final double lon;

        public Node(String id, String name, double lat, double lon) {
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static final class Edge {
        public final String to;
        public final double minutes;
        public final double meters;
        /** Bend points between the two junctions as lat,lon pairs, in riding order. Never null. */
        public final double[] via;

        Edge(String to, double minutes, double meters, double[] via) {
            this.to = to;
            this.minutes = minutes;
            this.meters = meters;
            this.via = via == null ? new double[0] : via;
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<Edge>> edges = new LinkedHashMap<>();

    public void addNode(Node node) {
        nodes.put(node.id, node);
        if (!edges.containsKey(node.id)) {
            edges.put(node.id, new ArrayList<>());
        }
    }

    public void addEdge(String from, String to, double minutes) {
        Node a = nodes.get(from);
        Node b = nodes.get(to);
        if (a == null || b == null) {
            throw new IllegalArgumentException("Unknown node in edge " + from + " -> " + to);
        }
        addEdge(from, to, minutes, LatLon.meters(a.lat, a.lon, b.lat, b.lon), null);
    }

    public void addEdge(String from, String to, double minutes, double meters, double[] via) {
        if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
            throw new IllegalArgumentException("Unknown node in edge " + from + " -> " + to);
        }
        if (minutes < 0) {
            throw new IllegalArgumentException("Dijkstra needs non-negative weights: " + minutes);
        }
        edges.get(from).add(new Edge(to, minutes, meters, via));
    }

    public Node node(String id) {
        return nodes.get(id);
    }

    public Collection<Node> nodes() {
        return nodes.values();
    }

    public int size() {
        return nodes.size();
    }

    public List<Edge> edgesFrom(String id) {
        List<Edge> list = edges.get(id);
        return list == null ? Collections.<Edge>emptyList() : list;
    }

    /** The quickest direct edge between two neighbouring junctions, or null. */
    public Edge edge(String from, String to) {
        Edge best = null;
        for (Edge edge : edgesFrom(from)) {
            if (edge.to.equals(to) && (best == null || edge.minutes < best.minutes)) {
                best = edge;
            }
        }
        return best;
    }

    /** Same junctions with every street direction flipped; bends are reversed to match. */
    public RoadGraph reversed() {
        RoadGraph reversed = new RoadGraph();
        for (Node node : nodes.values()) {
            reversed.addNode(node);
        }
        for (Map.Entry<String, List<Edge>> entry : edges.entrySet()) {
            for (Edge edge : entry.getValue()) {
                reversed.addEdge(edge.to, entry.getKey(), edge.minutes, edge.meters, reverseVia(edge.via));
            }
        }
        return reversed;
    }

    static double[] reverseVia(double[] via) {
        double[] out = new double[via.length];
        for (int i = 0; i + 1 < via.length; i += 2) {
            out[via.length - 2 - i] = via[i];
            out[via.length - 1 - i] = via[i + 1];
        }
        return out;
    }

    /**
     * Drops every junction outside the largest connected piece. OpenStreetMap extracts contain small
     * islands (private yards, cut-off service roads) that would trap a snapped address.
     */
    public void keepLargestComponent() {
        Map<String, List<String>> undirected = new HashMap<>();
        for (Map.Entry<String, List<Edge>> entry : edges.entrySet()) {
            for (Edge edge : entry.getValue()) {
                undirected.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(edge.to);
                undirected.computeIfAbsent(edge.to, k -> new ArrayList<>()).add(entry.getKey());
            }
        }
        Set<String> seen = new HashSet<>();
        Set<String> largest = new HashSet<>();
        for (String start : nodes.keySet()) {
            if (seen.contains(start)) {
                continue;
            }
            Set<String> component = new HashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty()) {
                String at = queue.poll();
                component.add(at);
                List<String> next = undirected.get(at);
                if (next == null) {
                    continue;
                }
                for (String n : next) {
                    if (seen.add(n)) {
                        queue.add(n);
                    }
                }
            }
            if (component.size() > largest.size()) {
                largest = component;
            }
        }
        nodes.keySet().retainAll(largest);
        edges.keySet().retainAll(largest);
    }

    /** Closest junction by straight-line distance, used to snap an address onto the network. */
    public Node nearestNode(double lat, double lon) {
        Node best = null;
        double bestMeters = Double.MAX_VALUE;
        for (Node node : nodes.values()) {
            double meters = metersBetween(lat, lon, node.lat, node.lon);
            if (meters < bestMeters) {
                bestMeters = meters;
                best = node;
            }
        }
        return best;
    }

    /** Fast flat-earth distance, good to a fraction of a percent over a few kilometres. */
    public static double metersBetween(double lat1, double lon1, double lat2, double lon2) {
        double dLat = (lat2 - lat1) * 111320;
        double dLon = (lon2 - lon1) * 111320 * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    /** Turns a junction path into map points, including each road's bends. */
    public List<LatLon> geometry(List<String> path) {
        List<LatLon> points = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            Node node = nodes.get(path.get(i));
            if (i > 0) {
                Edge edge = edge(path.get(i - 1), path.get(i));
                if (edge != null) {
                    for (int j = 0; j + 1 < edge.via.length; j += 2) {
                        points.add(new LatLon(edge.via[j], edge.via[j + 1]));
                    }
                }
            }
            if (node != null) {
                points.add(new LatLon(node.lat, node.lon));
            }
        }
        return points;
    }

    /** Road length along a junction path. */
    public double meters(List<String> path) {
        double total = 0;
        for (int i = 1; i < path.size(); i++) {
            Edge edge = edge(path.get(i - 1), path.get(i));
            if (edge != null) {
                total += edge.meters;
            }
        }
        return total;
    }
}
