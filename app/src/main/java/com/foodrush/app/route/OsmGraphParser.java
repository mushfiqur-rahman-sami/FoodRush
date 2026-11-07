package com.foodrush.app.route;

import com.foodrush.app.model.LatLon;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link RoadGraph} from OpenStreetMap XML, as returned by the Overpass API.
 *
 * Only junctions become graph nodes: points shared by two roads, plus road ends. The points in
 * between are kept as each edge's bends. That shrinks the graph several times over, which keeps
 * Dijkstra fast on a phone, without changing any shortest path.
 */
public final class OsmGraphParser {

    private OsmGraphParser() {
    }

    private static final class Way {
        final List<Long> refs = new ArrayList<>();
        String highway;
        String oneway;
        String junction;
        String name;
    }

    /** Typical motorbike riding speed in Bangladeshi traffic, km/h, by road class. */
    static double speedKmh(String highway) {
        if (highway == null) {
            return 15;
        }
        switch (highway.replace("_link", "")) {
            case "motorway":
                return 50;
            case "trunk":
                return 38;
            case "primary":
                return 28;
            case "secondary":
                return 24;
            case "tertiary":
                return 21;
            case "unclassified":
            case "residential":
            case "road":
                return 17;
            default:
                return 12;
        }
    }

    public static RoadGraph parse(XmlPullParser parser) throws XmlPullParserException, IOException {
        Map<Long, double[]> points = new HashMap<>();
        List<Way> ways = new ArrayList<>();
        Way current = null;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("node".equals(tag)) {
                    String lat = parser.getAttributeValue(null, "lat");
                    String lon = parser.getAttributeValue(null, "lon");
                    if (lat != null && lon != null) {
                        points.put(Long.parseLong(parser.getAttributeValue(null, "id")),
                                new double[]{Double.parseDouble(lat), Double.parseDouble(lon)});
                    }
                } else if ("way".equals(tag)) {
                    current = new Way();
                } else if (current != null && "nd".equals(tag)) {
                    current.refs.add(Long.parseLong(parser.getAttributeValue(null, "ref")));
                } else if (current != null && "tag".equals(tag)) {
                    String key = parser.getAttributeValue(null, "k");
                    String value = parser.getAttributeValue(null, "v");
                    if ("highway".equals(key)) {
                        current.highway = value;
                    } else if ("oneway".equals(key)) {
                        current.oneway = value;
                    } else if ("junction".equals(key)) {
                        current.junction = value;
                    } else if ("name".equals(key) || ("name:en".equals(key) && current.name == null)) {
                        current.name = value;
                    }
                }
            } else if (event == XmlPullParser.END_TAG && "way".equals(parser.getName()) && current != null) {
                if (current.highway != null && current.refs.size() > 1) {
                    ways.add(current);
                }
                current = null;
            }
        }
        return build(points, ways);
    }

    private static RoadGraph build(Map<Long, double[]> points, List<Way> ways) {
        // A point is a junction if two roads use it, or a road starts or ends there.
        Map<Long, Integer> uses = new HashMap<>();
        for (Way way : ways) {
            for (int i = 0; i < way.refs.size(); i++) {
                int extra = (i == 0 || i == way.refs.size() - 1) ? 2 : 1;
                uses.merge(way.refs.get(i), extra, Integer::sum);
            }
        }

        RoadGraph graph = new RoadGraph();
        for (Way way : ways) {
            int direction = direction(way);
            double metersPerMinute = speedKmh(way.highway) * 1000 / 60;
            Long from = null;
            double meters = 0;
            List<Double> bends = new ArrayList<>();
            double[] previous = null;
            for (Long ref : way.refs) {
                double[] point = points.get(ref);
                if (point == null) {
                    // The extract cut this road; restart after the gap.
                    from = null;
                    previous = null;
                    bends.clear();
                    meters = 0;
                    continue;
                }
                if (previous != null) {
                    meters += LatLon.meters(previous[0], previous[1], point[0], point[1]);
                }
                previous = point;
                boolean junction = uses.getOrDefault(ref, 0) >= 2;
                if (from == null) {
                    if (junction) {
                        from = ref;
                        meters = 0;
                        bends.clear();
                    }
                    continue;
                }
                if (!junction) {
                    bends.add(point[0]);
                    bends.add(point[1]);
                    continue;
                }
                connect(graph, points, way, from, ref, meters, toArray(bends), metersPerMinute, direction);
                from = ref;
                meters = 0;
                bends.clear();
            }
        }
        graph.keepLargestComponent();
        return graph;
    }

    /** 1 forward only, -1 backward only, 0 both ways. Roundabouts are one-way by definition. */
    private static int direction(Way way) {
        if ("-1".equals(way.oneway)) {
            return -1;
        }
        if ("yes".equals(way.oneway) || "true".equals(way.oneway) || "1".equals(way.oneway)
                || "roundabout".equals(way.junction) || "motorway".equals(way.highway)) {
            return 1;
        }
        return 0;
    }

    private static void connect(RoadGraph graph, Map<Long, double[]> points, Way way, long from, long to,
                                double meters, double[] bends, double metersPerMinute, int direction) {
        if (from == to) {
            return;
        }
        String a = String.valueOf(from);
        String b = String.valueOf(to);
        ensureNode(graph, a, points.get(from), way.name);
        ensureNode(graph, b, points.get(to), way.name);
        double minutes = meters / metersPerMinute;
        if (direction >= 0) {
            graph.addEdge(a, b, minutes, meters, bends);
        }
        if (direction <= 0) {
            graph.addEdge(b, a, minutes, meters, RoadGraph.reverseVia(bends));
        }
    }

    private static void ensureNode(RoadGraph graph, String id, double[] point, String roadName) {
        if (graph.node(id) == null) {
            graph.addNode(new RoadGraph.Node(id, roadName == null ? "" : roadName, point[0], point[1]));
        }
    }

    private static double[] toArray(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
