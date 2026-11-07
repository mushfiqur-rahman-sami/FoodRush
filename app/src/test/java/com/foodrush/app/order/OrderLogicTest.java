package com.foodrush.app.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Order;

import org.junit.Test;

import java.util.Arrays;

public class OrderLogicTest {

    private static final long T0 = 1_000_000L;

    /** One restaurant, a straight 1 km route to the customer. */
    private static Order order(String ownerUid) {
        Order o = new Order();
        o.id = "o1";
        o.customerUid = "c1";
        o.placedAt = T0;
        Order.Kitchen k = new Order.Kitchen();
        k.restaurantId = "r1";
        k.name = "Kacchi Ghor";
        k.lat = 22.800;
        k.lon = 89.550;
        k.ownerUid = ownerUid;
        o.kitchens.add(k);
        o.restaurantIds.add("r1");
        o.lat = 22.809;
        o.lon = 89.550;
        o.route = Order.encodeRoute(Arrays.asList(new LatLon(22.800, 89.550), new LatLon(22.809, 89.550)));
        o.travelMinutes = 4;
        o.distanceKm = 1;
        o.etaMinutes = 25;
        return o;
    }

    @Test
    public void withoutOtherPhonesTheDemoRunsTheWholeOrder() {
        Order o = order(null);
        long t = T0;
        int last = Order.PLACED;
        while (o.isActive() && t < T0 + 10 * 60_000) {
            OrderLogic.simulate(o, t, false);
            assertTrue("status never goes back", o.status >= last);
            last = o.status;
            t += 1000;
        }
        assertEquals(Order.DELIVERED, o.status);
        assertEquals(Order.RIDER_SIM, o.riderUid);
        assertTrue(o.arriving);
    }

    @Test
    public void scheduledOrderWaitsUntilItsStartTime() {
        Order o = order(null);
        // Wanted in an hour; 15 min cooking + 4 min riding, so the kitchen starts 19 min before.
        o.scheduledFor = T0 + 60 * 60_000;
        o.kitchens.get(0).prepMinutes = 15;
        long release = OrderLogic.releaseAt(o);
        assertEquals(o.scheduledFor - 19 * 60_000, release);
        assertTrue(OrderLogic.waitingForSchedule(o, T0 + 10 * 60_000));
        assertFalse(OrderLogic.simulate(o, T0 + 10 * 60_000, false));
        assertEquals(Order.PLACED, o.status);
        assertTrue(OrderLogic.simulate(o, release, false));
        assertFalse(OrderLogic.waitingForSchedule(o, release + OrderLogic.SIM_ACCEPT_MS));
    }

    @Test
    public void realRestaurantGetsTwoMinutesBeforeTheDemoKitchen() {
        Order o = order("owner");
        assertFalse(OrderLogic.simulate(o, T0 + 60_000, true));
        assertEquals(Order.PLACED, o.status);
        assertTrue(OrderLogic.simulate(o, T0 + OrderLogic.OWNER_WAIT_MS, true));
        assertTrue(o.kitchens.get(0).simStartedAt > 0);
    }

    @Test
    public void unownedRestaurantIsSimulatedAtOnce() {
        Order o = order(null);
        assertTrue(OrderLogic.simulate(o, T0, true));
        OrderLogic.simulate(o, T0 + OrderLogic.SIM_READY_MS, true);
        assertEquals(Order.READY, o.status);
        // Real riders still get their two minutes from placement.
        assertNull(o.riderUid);
        OrderLogic.simulate(o, T0 + OrderLogic.RIDER_WAIT_MS, true);
        assertEquals(Order.RIDER_SIM, o.riderUid);
    }

    @Test
    public void restaurantMovesForwardOnly() {
        Order o = order("owner");
        assertTrue(OrderLogic.setKitchenStatus(o, "r1", Order.Kitchen.ACCEPTED, T0 + 1));
        assertEquals(Order.ACCEPTED, o.status);
        assertEquals(T0 + 1, o.acceptedAt);
        assertFalse(OrderLogic.setKitchenStatus(o, "r1", Order.Kitchen.NEW, T0 + 2));
        assertTrue(OrderLogic.setKitchenStatus(o, "r1", Order.Kitchen.READY, T0 + 3));
        assertEquals(Order.READY, o.status);
        assertEquals(T0 + 3, o.readyAt);
    }

    @Test
    public void rejectingCancelsAndHidesFromRiders() {
        Order o = order("owner");
        assertTrue(OrderLogic.setKitchenStatus(o, "r1", Order.Kitchen.REJECTED, T0 + 1));
        assertEquals(Order.CANCELLED, o.status);
        assertEquals(Order.RIDER_NONE, o.riderUid);
        assertFalse(o.isActive());
    }

    @Test
    public void realRiderFlowWithPickupArrivingAndDelivery() {
        Order o = order("owner");
        OrderLogic.setKitchenStatus(o, "r1", Order.Kitchen.PREPARING, T0 + 1);
        assertTrue(OrderLogic.riderAccept(o, "rider1", "Rahim", T0 + 2));
        assertFalse("only one rider", OrderLogic.riderAccept(o, "rider2", "Karim", T0 + 3));
        assertFalse("food not ready", OrderLogic.riderPickedUp(o, "r1", T0 + 4));
        OrderLogic.setKitchenStatus(o, "r1", Order.Kitchen.READY, T0 + 5);
        assertTrue(OrderLogic.riderPickedUp(o, "r1", T0 + 6));
        assertEquals(Order.PICKED_UP, o.status);
        OrderLogic.riderMoved(o, new LatLon(22.804, 89.550), T0 + 7);
        assertEquals(Order.PICKED_UP, o.status);
        OrderLogic.riderMoved(o, new LatLon(22.8075, 89.550), T0 + 8);
        assertEquals(Order.ARRIVING, o.status);
        assertTrue(OrderLogic.deliver(o, T0 + 9));
        assertEquals(Order.DELIVERED, o.status);
        // A real rider is never replaced by the demo.
        assertFalse(OrderLogic.simulate(o, T0 + 10 * 60_000, true));
    }

    @Test
    public void cancelOnlyBeforeCooking() {
        Order o = order("owner");
        assertTrue(OrderLogic.canCustomerCancel(o));
        OrderLogic.setKitchenStatus(o, "r1", Order.Kitchen.PREPARING, T0 + 1);
        assertFalse(OrderLogic.cancel(o, "changed my mind"));
        Order fresh = order("owner");
        assertTrue(OrderLogic.cancel(fresh, "changed my mind"));
        assertEquals(Order.CANCELLED, fresh.status);
    }

    @Test
    public void severalKitchensMustAllBeReady() {
        Order o = order("owner");
        Order.Kitchen second = new Order.Kitchen();
        second.restaurantId = "r2";
        second.name = "Fan Fair";
        o.kitchens.add(second);
        OrderLogic.setKitchenStatus(o, "r1", Order.Kitchen.READY, T0 + 1);
        assertEquals(Order.PLACED, o.status);
        OrderLogic.setKitchenStatus(o, "r2", Order.Kitchen.READY, T0 + 2);
        assertEquals(Order.READY, o.status);
        OrderLogic.riderAccept(o, "rider1", "Rahim", T0 + 3);
        OrderLogic.riderPickedUp(o, "r1", T0 + 4);
        assertEquals(Order.READY, o.status);
        OrderLogic.riderPickedUp(o, "r2", T0 + 5);
        assertEquals(Order.PICKED_UP, o.status);
        assertEquals(T0 + 5, o.pickedUpAt);
    }

    @Test
    public void minutesLeftShrinkAsTheDemoRiderMoves() {
        Order o = order(null);
        long t = T0;
        while (o.pickedUpAt == 0) {
            OrderLogic.simulate(o, t, false);
            t += 1000;
        }
        int early = OrderLogic.minutesLeft(o, t);
        int later = OrderLogic.minutesLeft(o, t + OrderLogic.simTravelSeconds(o) * 600L);
        assertTrue(early > later);
        assertTrue(OrderLogic.metersLeft(o, t) <= 1000 + 1);
    }

    @Test
    public void storeSendsOnlyChangedFields() {
        Order before = order("owner");
        Order after = before.copy();
        OrderLogic.setKitchenStatus(after, "r1", Order.Kitchen.ACCEPTED, T0 + 1);
        java.util.Map<String, Object> changes = OrderStore.diff(before.toMap(), after.toMap());
        assertEquals(new java.util.HashSet<>(Arrays.asList("kitchens", "status", "acceptedAt")), changes.keySet());
    }
}
