package com.foodrush.app.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.foodrush.app.model.LatLon;

import org.junit.Test;
import org.kxml2.io.KXmlParser;

import java.io.StringReader;
import java.util.List;

public class OsmGraphParserTest {

    /**
     * A T-junction: a two-way main road 1-2-3 with a bend at 2, and a one-way side road 3-4 that
     * may only be ridden from 3 to 4. Node 9 is an unconnected stub that must be dropped.
     */
    private static final String OSM = "<osm>"
            + "<node id='1' lat='22.8000' lon='89.5500'/>"
            + "<node id='2' lat='22.8000' lon='89.5510'/>"
            + "<node id='3' lat='22.8000' lon='89.5520'/>"
            + "<node id='4' lat='22.8010' lon='89.5520'/>"
            + "<node id='8' lat='22.9000' lon='89.6000'/>"
            + "<node id='9' lat='22.9001' lon='89.6001'/>"
            + "<way id='100'><nd ref='1'/><nd ref='2'/><nd ref='3'/>"
            + "<tag k='highway' v='primary'/><tag k='name' v='KDA Avenue'/></way>"
            + "<way id='101'><nd ref='3'/><nd ref='4'/>"
            + "<tag k='highway' v='residential'/><tag k='oneway' v='yes'/></way>"
            + "<way id='102'><nd ref='8'/><nd ref='9'/><tag k='highway' v='service'/></way>"
            + "<way id='103'><nd ref='1'/><nd ref='4'/><tag k='building' v='yes'/></way>"
            + "</osm>";

    private static RoadGraph parse() throws Exception {
        KXmlParser parser = new KXmlParser();
        parser.setInput(new StringReader(OSM));
        return OsmGraphParser.parse(parser);
    }

    @Test
    public void bendsAreFoldedIntoEdges() throws Exception {
        RoadGraph graph = parse();
        // Junctions only: road ends 1, 3 and 4. Node 2 is a bend, kept as edge geometry.
        assertNull(graph.node("2"));
        assertNotNull(graph.node("1"));
        RoadGraph.Edge edge = graph.edge("1", "3");
        assertNotNull(edge);
        assertEquals(2, edge.via.length);
        assertEquals(89.5510, edge.via[1], 1e-9);
    }

    @Test
    public void oneWayRoadsOnlyGoOneWay() throws Exception {
        RoadGraph graph = parse();
        assertNotNull(graph.edge("3", "4"));
        assertNull(graph.edge("4", "3"));
        assertTrue(Double.isInfinite(Dijkstra.run(graph, "4").minutesTo("1")));
        assertTrue(Dijkstra.run(graph, "1").minutesTo("4") > 0);
    }

    @Test
    public void islandsAndNonRoadsAreDropped() throws Exception {
        RoadGraph graph = parse();
        assertNull(graph.node("8"));
        assertNull(graph.node("9"));
        // The building outline 1-4 is not a road, so the path from 1 to 4 must go through 3.
        assertEquals(java.util.Arrays.asList("1", "3", "4"), Dijkstra.run(graph, "1").pathTo("4"));
    }

    @Test
    public void ridingTimeFollowsRoadClassSpeed() throws Exception {
        RoadGraph graph = parse();
        RoadGraph.Edge main = graph.edge("1", "3");
        double expected = main.meters / (OsmGraphParser.speedKmh("primary") * 1000 / 60);
        assertEquals(expected, main.minutes, 1e-9);
        assertTrue(main.meters > 200 && main.meters < 210);
    }

    @Test
    public void geometryIncludesBends() throws Exception {
        RoadGraph graph = parse();
        List<LatLon> points = graph.geometry(Dijkstra.run(graph, "1").pathTo("4"));
        assertEquals(4, points.size());
        assertEquals(new LatLon(22.8000, 89.5510), points.get(1));
    }
}
