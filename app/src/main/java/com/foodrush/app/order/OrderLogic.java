package com.foodrush.app.order;

import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Order;
import com.foodrush.app.route.Polylines;

import java.util.List;

/**
 * The order state machine, with no Android code so it is unit-tested directly.
 *
 * Real people move an order along: the restaurant accepts and cooks, a rider takes it, picks it up
 * and delivers it with the customer's PIN. When nobody answers in time, the customer's own phone
 * steps in and simulates the missing person, so an order always completes, even with one phone.
 */
public final class OrderLogic {

    /** How long a restaurant with an owner account has to accept before the demo kitchen takes over. */
    public static final long OWNER_WAIT_MS = 2 * 60_000;
    /** How long after placing the order real riders get before the demo rider takes it. */
    public static final long RIDER_WAIT_MS = 2 * 60_000;
    public static final double ARRIVING_METERS = 300;

    static final long SIM_ACCEPT_MS = 6_000;
    static final long SIM_COOK_MS = 14_000;
    static final long SIM_READY_MS = 40_000;
    static final long SIM_PICKUP_MS = 8_000;
    /** Demo riders move faster than real time: 6 seconds per road minute, within these limits. */
    static final int SIM_SECONDS_PER_ROAD_MINUTE = 6;
    static final int MIN_SIM_TRAVEL_SECONDS = 40;
    static final int MAX_SIM_TRAVEL_SECONDS = 180;

    private OrderLogic() {
    }

    /** Overall status from the kitchens and the rider. */
    public static int derive(Order o) {
        if (o.status == Order.CANCELLED) {
            return Order.CANCELLED;
        }
        if (o.deliveredAt > 0) {
            return Order.DELIVERED;
        }
        if (o.kitchens.isEmpty()) {
            return o.status;
        }
        boolean allPicked = true;
        int lowest = Integer.MAX_VALUE;
        for (Order.Kitchen k : o.kitchens) {
            if (k.status == Order.Kitchen.REJECTED) {
                return Order.CANCELLED;
            }
            allPicked &= k.pickedUp;
            lowest = Math.min(lowest, k.status);
        }
        if (allPicked) {
            return o.arriving ? Order.ARRIVING : Order.PICKED_UP;
        }
        return lowest;
    }

    public static boolean allPickedUp(Order o) {
        for (Order.Kitchen k : o.kitchens) {
            if (!k.pickedUp) {
                return false;
            }
        }
        return !o.kitchens.isEmpty();
    }

    private static boolean refresh(Order o, long now) {
        boolean changed = false;
        int lowest = Integer.MAX_VALUE;
        for (Order.Kitchen k : o.kitchens) {
            lowest = Math.min(lowest, k.status);
        }
        if (lowest >= Order.Kitchen.ACCEPTED && o.acceptedAt == 0) {
            o.acceptedAt = now;
            changed = true;
        }
        if (lowest >= Order.Kitchen.READY && o.readyAt == 0) {
            o.readyAt = now;
            changed = true;
        }
        int status = derive(o);
        if (status != o.status) {
            o.status = status;
            changed = true;
        }
        return changed;
    }

    /**
     * When the kitchen should start, so a scheduled order arrives on time: the wanted time minus
     * cooking and riding time. Orders for as soon as possible start when placed.
     */
    public static long releaseAt(Order o) {
        if (o.scheduledFor <= 0) {
            return o.placedAt;
        }
        int prep = 15;
        for (Order.Kitchen k : o.kitchens) {
            prep = Math.max(prep, k.prepMinutes);
        }
        long lead = (long) ((prep + Math.ceil(o.travelMinutes)) * 60_000);
        return Math.max(o.placedAt, o.scheduledFor - lead);
    }

    /** True while a scheduled order is waiting for its start time. */
    public static boolean waitingForSchedule(Order o, long now) {
        return o.status == Order.PLACED && o.scheduledFor > 0 && now < releaseAt(o);
    }

    static int simKitchenStage(long elapsed) {
        if (elapsed < SIM_ACCEPT_MS) {
            return Order.Kitchen.NEW;
        }
        if (elapsed < SIM_COOK_MS) {
            return Order.Kitchen.ACCEPTED;
        }
        if (elapsed < SIM_READY_MS) {
            return Order.Kitchen.PREPARING;
        }
        return Order.Kitchen.READY;
    }

    public static int simTravelSeconds(Order o) {
        int seconds = (int) Math.ceil(o.travelMinutes * SIM_SECONDS_PER_ROAD_MINUTE);
        return Math.max(MIN_SIM_TRAVEL_SECONDS, Math.min(MAX_SIM_TRAVEL_SECONDS, seconds));
    }

    /**
     * Applies every demo step due by {@code now}. With {@code realPeople} false (no Firebase, so no
     * other phones), nobody is waited for. Returns true when the order changed.
     */
    public static boolean simulate(Order o, long now, boolean realPeople) {
        if (!o.isActive()) {
            return false;
        }
        boolean changed = false;
        long release = releaseAt(o);
        for (Order.Kitchen k : o.kitchens) {
            boolean unattended = !realPeople || k.ownerUid == null || k.ownerUid.isEmpty();
            if (k.simStartedAt == 0 && k.status == Order.Kitchen.NEW && now >= release
                    && (unattended || now - release >= OWNER_WAIT_MS)) {
                k.simStartedAt = now;
                changed = true;
            }
            if (k.simStartedAt > 0) {
                int target = simKitchenStage(now - k.simStartedAt);
                if (target > k.status) {
                    k.status = target;
                    if (target == Order.Kitchen.READY) {
                        k.readyAt = now;
                    }
                    changed = true;
                }
            }
        }
        changed |= refresh(o, now);

        if (o.riderUid == null && o.status >= Order.READY) {
            long due = realPeople ? Math.max(release + RIDER_WAIT_MS, o.readyAt + 15_000) : o.readyAt + 3_000;
            if (now >= due) {
                o.riderUid = Order.RIDER_SIM;
                o.riderName = "FoodRush rider";
                o.assignedAt = now;
                changed = true;
            }
        }
        if (o.hasSimRider()) {
            if (!allPickedUp(o) && now - o.assignedAt >= SIM_PICKUP_MS) {
                for (Order.Kitchen k : o.kitchens) {
                    if (k.status >= Order.Kitchen.READY) {
                        k.pickedUp = true;
                    }
                }
                if (allPickedUp(o)) {
                    o.pickedUpAt = now;
                }
                changed = true;
            }
            if (o.pickedUpAt > 0) {
                List<LatLon> route = o.routePoints();
                double length = Polylines.length(route);
                double travelled = simRiderMeters(o, now);
                if (!o.arriving && length - travelled <= ARRIVING_METERS) {
                    o.arriving = true;
                    changed = true;
                }
                if (travelled >= length) {
                    o.deliveredAt = now;
                    changed = true;
                }
            }
        }
        changed |= refresh(o, now);
        return changed;
    }

    /** How far along the planned route the demo rider is. */
    public static double simRiderMeters(Order o, long now) {
        if (o.pickedUpAt == 0) {
            return 0;
        }
        double fraction = (now - o.pickedUpAt) / 1000.0 / simTravelSeconds(o);
        return Math.min(1, Math.max(0, fraction)) * Polylines.length(o.routePoints());
    }

    /** Where to draw the rider, or null when there is no rider yet. */
    public static LatLon riderPosition(Order o, long now) {
        List<LatLon> route = o.routePoints();
        if (o.hasSimRider() && !route.isEmpty()) {
            return Polylines.pointAt(route, simRiderMeters(o, now));
        }
        if (o.hasRealRider() && o.riderAt > 0) {
            return new LatLon(o.riderLat, o.riderLon);
        }
        return null;
    }

    /** Road meters still to ride, or -1 before pickup. */
    public static double metersLeft(Order o, long now) {
        if (o.pickedUpAt == 0 || !o.isActive()) {
            return -1;
        }
        List<LatLon> route = o.routePoints();
        double length = Polylines.length(route);
        if (o.hasSimRider()) {
            return Math.max(0, length - simRiderMeters(o, now));
        }
        LatLon rider = riderPosition(o, now);
        if (rider == null || route.isEmpty()) {
            return length;
        }
        return Math.max(rider.metersTo(o.customer()), length - Polylines.progress(route, rider));
    }

    /** Estimated minutes until the food arrives, for the customer's screen. */
    public static int minutesLeft(Order o, long now) {
        if (!o.isActive()) {
            return 0;
        }
        double meters = metersLeft(o, now);
        double routeMeters = Math.max(1, o.distanceKm * 1000);
        if (meters >= 0) {
            return (int) Math.max(1, Math.ceil(meters / routeMeters * o.travelMinutes));
        }
        long elapsedMinutes = (now - o.placedAt) / 60_000;
        return (int) Math.max(Math.ceil(o.travelMinutes) + 1, o.etaMinutes - elapsedMinutes);
    }

    // ---- Actions by real people ----

    public static boolean setKitchenStatus(Order o, String restaurantId, int status, long now) {
        Order.Kitchen k = o.kitchen(restaurantId);
        if (k == null || !o.isActive() || k.pickedUp || status <= k.status && status != Order.Kitchen.REJECTED) {
            return false;
        }
        k.status = status;
        if (status == Order.Kitchen.READY) {
            k.readyAt = now;
        }
        if (status == Order.Kitchen.REJECTED) {
            o.status = Order.CANCELLED;
            o.cancelReason = k.name + " could not take this order";
            if (o.riderUid == null) {
                o.riderUid = Order.RIDER_NONE;
            }
            return true;
        }
        refresh(o, now);
        return true;
    }

    public static boolean riderAccept(Order o, String riderUid, String riderName, long now) {
        if (o.riderUid != null || !o.isActive()) {
            return false;
        }
        o.riderUid = riderUid;
        o.riderName = riderName;
        o.assignedAt = now;
        return true;
    }

    public static boolean riderPickedUp(Order o, String restaurantId, long now) {
        Order.Kitchen k = o.kitchen(restaurantId);
        if (k == null || k.pickedUp || k.status < Order.Kitchen.READY || !o.isActive()) {
            return false;
        }
        k.pickedUp = true;
        if (allPickedUp(o)) {
            o.pickedUpAt = now;
        }
        refresh(o, now);
        return true;
    }

    /** Records a real rider's GPS fix; marks the order arriving once within 300 m of the customer. */
    public static boolean riderMoved(Order o, LatLon where, long now) {
        if (!o.isActive()) {
            return false;
        }
        o.riderLat = where.lat;
        o.riderLon = where.lon;
        o.riderAt = now;
        if (o.pickedUpAt > 0 && !o.arriving && where.metersTo(o.customer()) <= ARRIVING_METERS) {
            o.arriving = true;
        }
        refresh(o, now);
        return true;
    }

    public static boolean deliver(Order o, long now) {
        if (!o.isActive() || o.pickedUpAt == 0) {
            return false;
        }
        o.deliveredAt = now;
        refresh(o, now);
        return true;
    }

    public static boolean canCustomerCancel(Order o) {
        if (!o.isActive() || o.status > Order.ACCEPTED) {
            return false;
        }
        for (Order.Kitchen k : o.kitchens) {
            if (k.status > Order.Kitchen.ACCEPTED || k.pickedUp) {
                return false;
            }
        }
        return true;
    }

    public static boolean cancel(Order o, String reason) {
        if (!canCustomerCancel(o)) {
            return false;
        }
        o.status = Order.CANCELLED;
        o.cancelReason = reason;
        if (o.riderUid == null) {
            o.riderUid = Order.RIDER_NONE;
        }
        return true;
    }
}
