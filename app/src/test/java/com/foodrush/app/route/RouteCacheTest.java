package com.foodrush.app.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.foodrush.app.model.LatLon;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RouteCacheTest {

    private static final LatLon KACCHI_GHOR = new LatLon(22.810322, 89.561359);
    private static final LatLon KAMRUL = new LatLon(22.798140, 89.527350);
    private static final LatLon HOME = new LatLon(22.822232, 89.552929);

    @Test
    public void sameStopsShareOneRoute() {
        assertEquals(RoutePlanner.routeKey(Arrays.asList(KACCHI_GHOR, KAMRUL), HOME),
                RoutePlanner.routeKey(Arrays.asList(new LatLon(22.810322, 89.561359), KAMRUL), HOME));
    }

    @Test
    public void differentStopsOrAddressAreDifferentRoutes() {
        String base = RoutePlanner.routeKey(Collections.singletonList(KACCHI_GHOR), HOME);
        assertNotEquals(base, RoutePlanner.routeKey(Collections.singletonList(KAMRUL), HOME));
        assertNotEquals(base, RoutePlanner.routeKey(Collections.singletonList(KACCHI_GHOR), KAMRUL));
        assertNotEquals(base, RoutePlanner.routeKey(Arrays.asList(KACCHI_GHOR, KAMRUL), HOME));
    }

    @Test
    public void nothingCachedForANewRoute() {
        assertNull(RoutePlanner.cached(Collections.singletonList(new LatLon(23.78, 90.41)), new LatLon(23.79, 90.42)));
    }
}
