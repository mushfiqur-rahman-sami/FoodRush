package com.foodrush.app.order;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.foodrush.app.model.Order;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EarningsTest {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = Earnings.startOfDay(System.currentTimeMillis()) + 12 * 60 * 60 * 1000;

    private static Order delivered(long at, int fee, int tip, int stars, String payment, int total) {
        Order o = new Order();
        o.id = "o" + at;
        o.status = Order.DELIVERED;
        o.pickedUpAt = at - 20 * 60_000;
        o.deliveredAt = at;
        o.placedAt = at - 45 * 60_000;
        o.fee = fee;
        o.tip = tip;
        o.riderRating = stars;
        o.payment = payment;
        o.total = total;
        Order.Kitchen k = new Order.Kitchen();
        k.restaurantId = "r1";
        k.name = "Kacchi Ghor";
        k.subtotal = total - fee;
        o.kitchens.add(k);
        return o;
    }

    private final List<Order> orders = Arrays.asList(
            delivered(NOW - 60_000, 60, 20, 5, "Cash on delivery", 500),
            delivered(NOW - 2 * 60_000, 50, 0, 4, "bKash ••678", 400),
            delivered(NOW - DAY, 70, 50, 5, "Cash on delivery", 600),
            delivered(NOW - 3 * DAY, 40, 0, 0, "Cash on delivery", 300),
            delivered(NOW - 40 * DAY, 90, 0, 2, "Cash on delivery", 900));

    @Test
    public void riderEarningsByPeriod() {
        Earnings.Amount earning = Earnings.riderEarning();
        Earnings.When when = Earnings.delivered();
        assertEquals(130, Earnings.sum(orders, earning, when, Earnings.startOfDay(NOW)));
        assertEquals(290, Earnings.sum(orders, earning, when, Earnings.daysAgo(NOW, 7)));
        assertEquals(380, Earnings.sum(orders, earning, when, 0));
        assertEquals(2, Earnings.count(orders, when, Earnings.startOfDay(NOW)));
    }

    @Test
    public void lastSevenDaysEndWithToday() {
        int[] days = Earnings.byDay(orders, Earnings.riderEarning(), Earnings.delivered(), 7, NOW);
        assertArrayEquals(new int[]{0, 0, 0, 40, 0, 120, 130}, days);
    }

    @Test
    public void combinedRatingIgnoresUnratedDeliveries() {
        Earnings.Ratings ratings = Earnings.riderRatings(orders);
        assertEquals(4, ratings.count);
        assertEquals(4.0f, ratings.average, 0.001f);
        assertArrayEquals(new int[]{0, 1, 0, 1, 2}, ratings.counts);
    }

    @Test
    public void cashInHandCountsOnlyCashSinceLastDeposit() {
        assertEquals(500 + 600 + 300 + 900, Earnings.cashInHand(orders, 0));
        assertEquals(500, Earnings.cashInHand(orders, NOW - 90_000));
    }

    @Test
    public void averageDeliveryTime() {
        assertEquals(20, Earnings.averageMinutes(orders, o -> o.pickedUpAt, o -> o.deliveredAt));
    }

    @Test
    public void topDishesFromLinesAndOldText() {
        Order withLines = delivered(NOW, 50, 0, 0, "Cash on delivery", 1000);
        Order.Line kacchi = new Order.Line();
        kacchi.name = "Mutton Kacchi Biryani";
        kacchi.restaurantId = "r1";
        kacchi.quantity = 3;
        withLines.lines.add(kacchi);
        Order oldStyle = delivered(NOW, 50, 0, 0, "Cash on delivery", 500);
        oldStyle.items = "2 × Borhani (Glass)\n1 × Mutton Kacchi Biryani (Full) + Egg";
        List<Map.Entry<String, Integer>> top = Earnings.topDishes(Arrays.asList(withLines, oldStyle), "r1", 5);
        assertEquals("Mutton Kacchi Biryani", top.get(0).getKey());
        assertEquals(4, (int) top.get(0).getValue());
        assertEquals("Borhani", top.get(1).getKey());
        assertEquals(2, (int) top.get(1).getValue());
    }
}
