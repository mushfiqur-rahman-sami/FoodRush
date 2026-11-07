package com.foodrush.app.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.foodrush.app.model.LatLon;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DijkstraTest {

    private static RoadGraph graph(String... ids) {
        RoadGraph graph = new RoadGraph();
        for (String id : ids) {
            graph.addNode(new RoadGraph.Node(id, id, 0, 0));
        }
        return graph;
    }

    @Test
    public void picksCheaperIndirectRoute() {
        RoadGraph g = graph("a", "b", "c");
        g.addEdge("a", "c", 10);
        g.addEdge("a", "b", 2);
        g.addEdge("b", "c", 3);
        Dijkstra.Result result = Dijkstra.run(g, "a");
        assertEquals(5, result.minutesTo("c"), 1e-9);
        assertEquals(Arrays.asList("a", "b", "c"), result.pathTo("c"));
    }

    @Test
    public void respectsOneWayStreets() {
        RoadGraph g = graph("a", "b");
        g.addEdge("a", "b", 1);
        assertTrue(Double.isInfinite(Dijkstra.run(g, "b").minutesTo("a")));
        assertTrue(Dijkstra.run(g, "b").pathTo("a").isEmpty());
    }

    @Test
    public void sourceIsZeroAndPathOfOne() {
        RoadGraph g = graph("a");
        assertEquals(0, Dijkstra.run(g, "a").minutesTo("a"), 0);
        assertEquals(Collections.singletonList("a"), Dijkstra.run(g, "a").pathTo("a"));
    }

    @Test
    public void laterImprovementReplacesEarlierQueueEntry() {
        RoadGraph g = graph("s", "x", "y", "t");
        g.addEdge("s", "x", 5);
        g.addEdge("s", "y", 1);
        g.addEdge("y", "x", 1);
        g.addEdge("x", "t", 1);
        assertEquals(3, Dijkstra.run(g, "s").minutesTo("t"), 1e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeWeights() {
        RoadGraph g = graph("a", "b");
        g.addEdge("a", "b", -1);
    }

    @Test
    public void reversedGraphGivesSameTimesAsForwardRuns() {
        RoadGraph g = graph("a", "b", "c", "d");
        g.addEdge("a", "b", 1);
        g.addEdge("b", "d", 4);
        g.addEdge("a", "c", 2);
        g.addEdge("c", "d", 1);
        Dijkstra.Result toD = Dijkstra.run(g.reversed(), "d");
        for (String from : new String[]{"a", "b", "c"}) {
            assertEquals(Dijkstra.run(g, from).minutesTo("d"), toD.minutesTo(from), 1e-9);
        }
    }

    @Test
    public void plannerVisitsAllPickupsAndEndsAtCustomer() {
        // Four junctions about 110 m apart on a line; pickups and the customer sit on them.
        RoadGraph g = new RoadGraph();
        g.addNode(new RoadGraph.Node("r1", "", 22.80, 89.550));
        g.addNode(new RoadGraph.Node("mid", "", 22.80, 89.551));
        g.addNode(new RoadGraph.Node("r2", "", 22.80, 89.552));
        g.addNode(new RoadGraph.Node("home", "", 22.80, 89.553));
        g.addEdge("r1", "mid", 1);
        g.addEdge("mid", "r2", 1);
        g.addEdge("r2", "home", 1);
        g.addEdge("mid", "home", 1);
        LatLon r1 = new LatLon(22.80, 89.550);
        LatLon r2 = new LatLon(22.80, 89.552);
        LatLon home = new LatLon(22.80, 89.553);
        DeliveryPlanner.Plan plan = new DeliveryPlanner(g).plan(Arrays.asList(r2, r1), home);
        // r1 is farther from home, so the rider starts there and passes r2 on the way.
        assertEquals(Arrays.asList(1, 0), plan.pickupOrder);
        assertEquals(3, plan.minutes, 1e-9);
        assertEquals(r1, plan.points.get(0));
        assertEquals(home, plan.points.get(plan.points.size() - 1));
    }

    @Test
    public void unreachableCustomerGivesNoPlan() {
        RoadGraph g = new RoadGraph();
        g.addNode(new RoadGraph.Node("a", "", 22.80, 89.550));
        g.addNode(new RoadGraph.Node("b", "", 22.80, 89.551));
        g.addEdge("b", "a", 1);
        assertNull(new DeliveryPlanner(g).plan(Collections.singletonList(new LatLon(22.80, 89.550)),
                new LatLon(22.80, 89.551)));
    }

    @Test
    public void addressesFarFromAnyRoadAreOffTheNetwork() {
        RoadGraph g = new RoadGraph();
        g.addNode(new RoadGraph.Node("a", "", 22.80, 89.550));
        double[] minutes = new DeliveryPlanner(g).minutesToCustomer(new LatLon(22.80, 89.550),
                Collections.singletonList(new LatLon(23.00, 89.550)));
        assertTrue(Double.isInfinite(minutes[0]));
    }
}
