package com.foodrush.app.order;

import com.foodrush.app.model.Order;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sums and averages for the rider and restaurant dashboards. No Android code, so it is unit-tested. */
public final class Earnings {

    private static final Pattern ITEM_LINE = Pattern.compile("^(\\d+) × ([^(+·]+?)\\s*(?:\\(.*)?(?:\\+.*)?(?:·.*)?$");

    private Earnings() {
    }

    /** What one order is worth to the person looking: a rider's fee and tip, or a restaurant's food sales. */
    public interface Amount {
        int of(Order order);
    }

    /** When an order counts: delivery time for riders, order time for restaurants. */
    public interface When {
        long of(Order order);
    }

    public static Amount riderEarning() {
        return o -> o.fee + o.tip;
    }

    public static When delivered() {
        return o -> o.deliveredAt;
    }

    public static Amount restaurantSales(String restaurantId) {
        return o -> {
            Order.Kitchen k = o.kitchen(restaurantId);
            return k == null ? 0 : k.subtotal;
        };
    }

    public static When placed() {
        return o -> o.placedAt;
    }

    public static long startOfDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Midnight starting the last {@code days} days, today included. */
    public static long daysAgo(long now, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfDay(now));
        c.add(Calendar.DAY_OF_YEAR, -(days - 1));
        return c.getTimeInMillis();
    }

    public static long startOfMonth(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfDay(now));
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    public static int sum(List<Order> orders, Amount amount, When when, long from) {
        int total = 0;
        for (Order o : orders) {
            long at = when.of(o);
            if (at > 0 && at >= from) {
                total += amount.of(o);
            }
        }
        return total;
    }

    public static int count(List<Order> orders, When when, long from) {
        int total = 0;
        for (Order o : orders) {
            long at = when.of(o);
            if (at > 0 && at >= from) {
                total++;
            }
        }
        return total;
    }

    /** Totals for each of the last {@code days} days, oldest first. */
    public static int[] byDay(List<Order> orders, Amount amount, When when, int days, long now) {
        int[] totals = new int[days];
        long first = daysAgo(now, days);
        for (Order o : orders) {
            long at = when.of(o);
            if (at <= 0 || at < first) {
                continue;
            }
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(first);
            int index = 0;
            while (index < days - 1) {
                c.add(Calendar.DAY_OF_YEAR, 1);
                if (at < c.getTimeInMillis()) {
                    break;
                }
                index++;
            }
            totals[index] += amount.of(o);
        }
        return totals;
    }

    /** Average whole minutes between two moments of each order, skipping orders missing either. */
    public static int averageMinutes(List<Order> orders, When start, When end) {
        long sum = 0;
        int count = 0;
        for (Order o : orders) {
            long a = start.of(o);
            long b = end.of(o);
            if (a > 0 && b > a) {
                sum += b - a;
                count++;
            }
        }
        return count == 0 ? 0 : (int) Math.round(sum / 60_000.0 / count);
    }

    public static final class Ratings {
        public final float average;
        public final int count;
        /** counts[0] is one star, counts[4] five stars. */
        public final int[] counts;

        Ratings(float average, int count, int[] counts) {
            this.average = average;
            this.count = count;
            this.counts = counts;
        }
    }

    /** The rider's combined rating: the average of every delivery a customer rated. */
    public static Ratings riderRatings(List<Order> orders) {
        int[] counts = new int[5];
        int sum = 0;
        int count = 0;
        for (Order o : orders) {
            if (o.riderRating >= 1 && o.riderRating <= 5) {
                counts[o.riderRating - 1]++;
                sum += o.riderRating;
                count++;
            }
        }
        return new Ratings(count == 0 ? 0f : (float) sum / count, count, counts);
    }

    /** Cash collected at the door that the rider hasn't deposited yet. */
    public static int cashInHand(List<Order> orders, long depositedUntil) {
        int total = 0;
        for (Order o : orders) {
            if (o.status == Order.DELIVERED && o.isCashOnDelivery() && o.deliveredAt > depositedUntil) {
                total += o.total;
            }
        }
        return total;
    }

    /** Best sellers by quantity, most first. Older orders without structured lines are read from their text. */
    public static List<Map.Entry<String, Integer>> topDishes(List<Order> orders, String restaurantId, int limit) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (Order o : orders) {
            if (o.status == Order.CANCELLED || o.kitchen(restaurantId) == null) {
                continue;
            }
            if (!o.lines.isEmpty()) {
                for (Order.Line line : o.lines) {
                    if (restaurantId.equals(line.restaurantId)) {
                        quantities.merge(line.name, line.quantity, Integer::sum);
                    }
                }
            } else if (o.kitchens.size() == 1) {
                for (String text : o.items.split("\n")) {
                    Matcher m = ITEM_LINE.matcher(text.trim());
                    if (m.matches()) {
                        quantities.merge(m.group(2).trim(), Integer.parseInt(m.group(1)), Integer::sum);
                    }
                }
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(quantities.entrySet());
        Collections.sort(sorted, (a, b) -> b.getValue() - a.getValue());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
}
