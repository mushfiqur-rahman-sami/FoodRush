package com.foodrush.app.ui.restaurant;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.foodrush.app.R;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivitySalesDashboardBinding;
import com.foodrush.app.model.Order;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.order.Earnings;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.ui.DashboardViews;
import com.foodrush.app.util.PriceCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The restaurant's sales dashboard: sales by period with a 7-day chart, order count and average,
 * best-selling dishes, accepted vs rejected orders, cooking time and rating.
 */
public class SalesDashboardActivity extends BaseActivity {

    private static final int DAYS = 7;

    private ActivitySalesDashboardBinding binding;
    private Session session;
    private String restaurantId;
    private Restaurant restaurant;
    private OrderStore.Subscription subscription;
    private List<Order> orders = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySalesDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.salesBar, R.string.sales_dashboard_title);
        session = new Session(this);
        restaurantId = session.restaurantId();
        if (restaurantId == null) {
            finish();
            return;
        }
        MenuRepository.get(this).restaurant(restaurantId, new MenuRepository.Listener<Restaurant>() {
            @Override
            public void onResult(Restaurant result) {
                restaurant = result;
                render();
            }

            @Override
            public void onError() {
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (restaurantId == null) {
            return;
        }
        subscription = OrderStore.watchRestaurant(this, session.uid(), list -> {
            List<Order> mine = new ArrayList<>();
            for (Order o : list) {
                if (o.kitchen(restaurantId) != null) {
                    mine.add(o);
                }
            }
            orders = mine;
            render();
        });
        render();
    }

    @Override
    protected void onStop() {
        if (subscription != null) {
            subscription.remove();
            subscription = null;
        }
        super.onStop();
    }

    private void render() {
        long now = System.currentTimeMillis();
        List<Order> sold = new ArrayList<>();
        int rejected = 0;
        int cancelled = 0;
        for (Order o : orders) {
            Order.Kitchen k = o.kitchen(restaurantId);
            if (k.status == Order.Kitchen.REJECTED) {
                rejected++;
            } else if (o.status == Order.CANCELLED) {
                cancelled++;
            } else {
                sold.add(o);
            }
        }
        Earnings.Amount sales = Earnings.restaurantSales(restaurantId);
        Earnings.When when = Earnings.placed();

        binding.salesTiles.removeAllViews();
        DashboardViews.addTile(binding.salesTiles,
                PriceCalculator.format(Earnings.sum(sold, sales, when, Earnings.startOfDay(now))),
                getString(R.string.period_today));
        DashboardViews.addTile(binding.salesTiles,
                PriceCalculator.format(Earnings.sum(sold, sales, when, Earnings.daysAgo(now, 7))),
                getString(R.string.period_week));
        DashboardViews.addTile(binding.salesTiles,
                PriceCalculator.format(Earnings.sum(sold, sales, when, Earnings.startOfMonth(now))),
                getString(R.string.period_month));
        int all = Earnings.sum(sold, sales, when, 0);
        DashboardViews.addTile(binding.salesTiles, String.valueOf(sold.size()), getString(R.string.stat_orders));
        binding.salesChart.setData(Earnings.byDay(sold, sales, when, DAYS, now), DashboardViews.dayLabels(this, now, DAYS));

        binding.salesTop.removeAllViews();
        List<Map.Entry<String, Integer>> top = Earnings.topDishes(orders, restaurantId, 5);
        binding.salesTopEmpty.setVisibility(top.isEmpty() ? View.VISIBLE : View.GONE);
        int rank = 1;
        for (Map.Entry<String, Integer> dish : top) {
            TextView row = new TextView(this);
            row.setText(getString(R.string.top_dish_line, rank++, dish.getKey(), dish.getValue()));
            row.setTextSize(14);
            row.setTypeface(ResourcesCompat.getFont(this, R.font.poppins_regular));
            row.setTextColor(getColor(R.color.text_primary));
            int pad = (int) (6 * getResources().getDisplayMetrics().density);
            row.setPadding(0, pad, 0, pad);
            binding.salesTop.addView(row);
        }

        binding.salesStats.removeAllViews();
        DashboardViews.addTile(binding.salesStats,
                PriceCalculator.format(sold.isEmpty() ? 0 : all / sold.size()), getString(R.string.stat_avg_order));
        DashboardViews.addTile(binding.salesStats, getString(R.string.minutes_short,
                        Earnings.averageMinutes(sold, o -> o.acceptedAt, o -> kitchenReadyAt(o))),
                getString(R.string.stat_avg_cooking));
        DashboardViews.addTile(binding.salesStats, getString(R.string.accepted_vs_rejected, sold.size(), rejected),
                getString(R.string.stat_accepted_rejected));
        DashboardViews.addTile(binding.salesStats, String.valueOf(cancelled), getString(R.string.stat_cancelled));
        if (restaurant != null) {
            DashboardViews.addTile(binding.salesStats,
                    String.format(Locale.US, "%.1f★", restaurant.rating),
                    getResources().getQuantityString(R.plurals.ratings_count, restaurant.ratingCount, restaurant.ratingCount));
            DashboardViews.addTile(binding.salesStats, PriceCalculator.format(all), getString(R.string.period_all));
        }
    }

    private long kitchenReadyAt(Order o) {
        Order.Kitchen k = o.kitchen(restaurantId);
        return k == null ? 0 : k.readyAt;
    }
}
