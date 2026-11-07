package com.foodrush.app.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Single-source shortest paths with a binary heap: O((V + E) log V).
 *
 * Fits this app because travel times are never negative and each question starts from one point.
 * Streets can be one-way, so straight-line distance would pick routes a rider cannot drive.
 */
public final class Dijkstra {

    private Dijkstra() {
    }

    public static final class Result {
        private final Map<String, Double> minutes;
        private final Map<String, String> previous;
        private final String source;

        Result(String source, Map<String, Double> minutes, Map<String, String> previous) {
            this.source = source;
            this.minutes = minutes;
            this.previous = previous;
        }

        /** Travel minutes from the source, or {@link Double#POSITIVE_INFINITY} when unreachable. */
        public double minutesTo(String nodeId) {
            Double value = minutes.get(nodeId);
            return value == null ? Double.POSITIVE_INFINITY : value;
        }

        /** Junctions from the source to the target inclusive; empty when unreachable. */
        public List<String> pathTo(String nodeId) {
            if (!minutes.containsKey(nodeId)) {
                return Collections.emptyList();
            }
            List<String> path = new ArrayList<>();
            for (String at = nodeId; at != null; at = previous.get(at)) {
                path.add(at);
            }
            Collections.reverse(path);
            return path.get(0).equals(source) ? path : Collections.<String>emptyList();
        }
    }

    private static final class Entry implements Comparable<Entry> {
        final String nodeId;
        final double minutes;

        Entry(String nodeId, double minutes) {
            this.nodeId = nodeId;
            this.minutes = minutes;
        }

        @Override
        public int compareTo(Entry other) {
            return Double.compare(minutes, other.minutes);
        }
    }

    public static Result run(RoadGraph graph, String source) {
        if (graph.node(source) == null) {
            throw new IllegalArgumentException("Unknown source node: " + source);
        }
        Map<String, Double> best = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<Entry> queue = new PriorityQueue<>();
        best.put(source, 0.0);
        queue.add(new Entry(source, 0.0));

        while (!queue.isEmpty()) {
            Entry current = queue.poll();
            // A junction can sit in the queue several times; only its best entry counts.
            if (current.minutes > best.get(current.nodeId)) {
                continue;
            }
            for (RoadGraph.Edge edge : graph.edgesFrom(current.nodeId)) {
                double candidate = current.minutes + edge.minutes;
                Double known = best.get(edge.to);
                if (known == null || candidate < known) {
                    best.put(edge.to, candidate);
                    previous.put(edge.to, current.nodeId);
                    queue.add(new Entry(edge.to, candidate));
                }
            }
        }
        return new Result(source, best, previous);
    }
}
