package com.foodrush.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.foodrush.app.R;
import com.foodrush.app.adapter.OrderAdapter;
import com.foodrush.app.data.CartRepository;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityListBinding;
import com.foodrush.app.model.CartItem;
import com.foodrush.app.model.Dish;
import com.foodrush.app.model.Order;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.util.States;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The customer's orders. Without Firebase the list comes from the content provider and refreshes
 * through a ContentObserver; with it, from a live Firestore query (also copied into the provider).
 */
public class OrdersActivity extends BaseActivity {

    private ActivityListBinding binding;
    private OrderAdapter adapter;
    private OrderStore.Subscription subscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.listBar, R.string.orders_title);

        adapter = new OrderAdapter(order -> startActivity(new Intent(this, TrackingActivity.class)
                .putExtra(TrackingActivity.EXTRA_ORDER_ID, order.id)));
        adapter.setReorderListener(this::reorder);
        binding.listItems.setLayoutManager(new LinearLayoutManager(this));
        binding.listItems.setAdapter(adapter);
        States.loading(binding.listState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        subscription = OrderStore.watchCustomer(this, new Session(this).uid(), this::show);
    }

    @Override
    protected void onStop() {
        if (subscription != null) {
            subscription.remove();
            subscription = null;
        }
        super.onStop();
    }

    /** Puts a past order's dishes back in the cart, at today's menu, skipping anything sold out. */
    private void reorder(Order order) {
        Set<String> restaurantIds = new LinkedHashSet<>();
        for (Order.Line line : order.lines) {
            restaurantIds.add(line.restaurantId);
        }
        MenuRepository.get(this).dishesOf(restaurantIds, false, new MenuRepository.Listener<List<Dish>>() {
            @Override
            public void onResult(List<Dish> available) {
                Map<String, Dish> byId = new HashMap<>();
                for (Dish dish : available) {
                    byId.put(dish.id, dish);
                }
                List<CartItem> items = new ArrayList<>();
                int missing = 0;
                for (Order.Line line : order.lines) {
                    Dish dish = byId.get(line.dishId);
                    if (dish == null) {
                        missing++;
                        continue;
                    }
                    items.add(new CartItem(0, dish.id, dish.name, dish.image, dish.restaurantId, line.size,
                            line.extras, line.unitPrice, line.quantity, dish.prepMinutes));
                }
                if (items.isEmpty()) {
                    showMessage(getString(R.string.reorder_none));
                    return;
                }
                int skipped = missing;
                AppExecutors.load(() -> {
                    for (CartItem item : items) {
                        CartRepository.add(OrdersActivity.this, item);
                    }
                    return true;
                }, done -> {
                    if (skipped > 0) {
                        Toast.makeText(OrdersActivity.this, getResources().getQuantityString(
                                R.plurals.reorder_skipped, skipped, skipped), Toast.LENGTH_LONG).show();
                    }
                    startActivity(new Intent(OrdersActivity.this, CartActivity.class));
                });
            }

            @Override
            public void onError() {
                showMessage(getString(R.string.load_error));
            }
        });
    }

    private void show(List<Order> orders) {
        adapter.submit(orders);
        if (orders.isEmpty()) {
            States.empty(binding.listState, R.string.orders_empty);
        } else {
            States.hide(binding.listState);
        }
    }
}
