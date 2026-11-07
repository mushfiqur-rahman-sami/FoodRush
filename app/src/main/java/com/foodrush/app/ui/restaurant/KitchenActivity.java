package com.foodrush.app.ui.restaurant;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.PopupMenu;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.foodrush.app.R;
import com.foodrush.app.adapter.JobAdapter;
import com.foodrush.app.adapter.OrderAdapter;
import com.foodrush.app.data.AuthRepository;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityKitchenBinding;
import com.foodrush.app.databinding.ItemKitchenOrderBinding;
import com.foodrush.app.model.Order;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.order.OrderLogic;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.service.KitchenService;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.ui.HelpActivity;
import com.foodrush.app.ui.LoginActivity;
import com.foodrush.app.ui.ProfileActivity;
import com.foodrush.app.ui.SettingsActivity;
import com.foodrush.app.util.PriceCalculator;
import com.foodrush.app.util.States;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** The restaurant's home: new orders to accept, food to cook, and what has been sent out. */
public class KitchenActivity extends BaseActivity {

    private ActivityKitchenBinding binding;
    private Session session;
    private String restaurantId;
    private Restaurant restaurant;
    private JobAdapter adapter;
    private OrderStore.Subscription subscription;
    private List<Order> orders = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new Session(this);
        restaurantId = session.restaurantId();
        if (restaurantId == null) {
            startActivity(new Intent(this, ClaimRestaurantActivity.class));
            finish();
            return;
        }
        binding = ActivityKitchenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.workTabActive.setText(R.string.kitchen_tab_new);
        binding.workTabOther.setText(R.string.kitchen_tab_cooking);
        binding.workTabDone.setText(R.string.kitchen_tab_done);
        binding.workTabs.setOnCheckedStateChangeListener((group, ids) -> render());
        binding.workSwitch.setText(R.string.open_for_orders);
        binding.workMenu.setOnClickListener(this::showMenu);
        binding.workStats.setOnClickListener(v -> startActivity(new Intent(this, SalesDashboardActivity.class)));

        adapter = new JobAdapter(this::bindCard);
        binding.workList.setLayoutManager(new LinearLayoutManager(this));
        binding.workList.setAdapter(adapter);
        States.loading(binding.workState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withPermission(Manifest.permission.POST_NOTIFICATIONS, R.string.permission_notifications_kitchen, () -> {
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (restaurantId == null) {
            return;
        }
        subscription = OrderStore.watchRestaurant(this, session.uid(), list -> {
            orders = list;
            render();
        });
        MenuRepository.get(this).restaurant(restaurantId, new MenuRepository.Listener<Restaurant>() {
            @Override
            public void onResult(Restaurant result) {
                restaurant = result;
                showRestaurant();
            }

            @Override
            public void onError() {
                showMessage(getString(R.string.load_error));
            }
        });
    }

    @Override
    protected void onStop() {
        if (subscription != null) {
            subscription.remove();
            subscription = null;
        }
        super.onStop();
    }

    private void showRestaurant() {
        binding.workTitle.setText(restaurant.name);
        binding.workSubtitle.setText(restaurant.paused ? getString(R.string.busy_paused_label)
                : restaurant.busyMinutes > 0 ? getString(R.string.busy_extra_label, restaurant.busyMinutes)
                : restaurant.areaLabel());
        binding.workSwitch.setOnCheckedChangeListener(null);
        binding.workSwitch.setChecked(restaurant.open);
        binding.workSwitch.setOnCheckedChangeListener((button, checked) -> setOpen(checked));
        if (restaurant.open) {
            KitchenService.start(this);
        }
    }

    private void setOpen(boolean open) {
        restaurant.open = open;
        MenuRepository.get(this).saveRestaurant(restaurant, new MenuRepository.Listener<Void>() {
            @Override
            public void onResult(Void result) {
                showMessage(getString(open ? R.string.now_open : R.string.now_closed));
            }

            @Override
            public void onError() {
                showMessage(getString(R.string.save_failed));
            }
        });
        if (open) {
            KitchenService.start(this);
        } else {
            KitchenService.stop(this);
        }
    }

    private void render() {
        int tab = binding.workTabs.getCheckedChipId();
        List<Order> shown = new ArrayList<>();
        int todayCount = 0;
        int todaySales = 0;
        for (Order order : orders) {
            Order.Kitchen k = order.kitchen(restaurantId);
            if (k == null) {
                continue;
            }
            if (DateUtils.isToday(order.placedAt) && order.status != Order.CANCELLED) {
                todayCount++;
                todaySales += k.subtotal;
            }
            boolean isNew = order.isActive() && k.status == Order.Kitchen.NEW;
            boolean cooking = order.isActive() && !isNew && !k.pickedUp;
            if (tab == R.id.work_tab_active && isNew || tab == R.id.work_tab_other && cooking
                    || tab == R.id.work_tab_done && !isNew && !cooking) {
                shown.add(order);
            }
        }
        binding.workStats.setText(getString(R.string.kitchen_stats_tap, todayCount, PriceCalculator.format(todaySales)));
        adapter.submit(shown);
        if (shown.isEmpty()) {
            States.empty(binding.workState, tab == R.id.work_tab_active ? R.string.kitchen_empty_new
                    : tab == R.id.work_tab_other ? R.string.kitchen_empty_cooking : R.string.kitchen_empty_done);
        } else {
            States.hide(binding.workState);
        }
    }

    private void bindCard(ItemKitchenOrderBinding card, Order order) {
        Order.Kitchen k = order.kitchen(restaurantId);
        card.jobTitle.setText(getString(R.string.order_title, order.shortId(),
                DateUtils.getRelativeTimeSpanString(order.placedAt)));
        card.jobAmount.setText(PriceCalculator.format(k.subtotal));
        card.jobItems.setText(itemsFor(order, k));
        card.jobStatus.setText(order.isActive() ? OrderAdapter.kitchenLabel(this, k.status)
                : getString(OrderAdapter.stageLabel(order.status)));
        String rider = order.hasRealRider() || order.hasSimRider() ? getString(R.string.rider_named, order.riderName)
                : getString(R.string.rider_not_yet);
        String meta = getString(R.string.kitchen_meta, order.customerName, order.payment, rider);
        if (order.scheduledFor > 0) {
            meta = getString(R.string.scheduled_kitchen, DateFormat.getTimeFormat(this).format(new Date(order.scheduledFor)),
                    DateFormat.getTimeFormat(this).format(new Date(OrderLogic.releaseAt(order)))) + "\n" + meta;
        }
        card.jobMeta.setText(meta);
        if (!order.isActive() || k.pickedUp) {
            return;
        }
        switch (k.status) {
            case Order.Kitchen.NEW:
                action(card.jobPrimary, R.string.accept, () -> setStatus(order, Order.Kitchen.ACCEPTED));
                action(card.jobSecondary, R.string.reject, () -> confirmReject(order));
                break;
            case Order.Kitchen.ACCEPTED:
                action(card.jobPrimary, R.string.start_cooking, () -> setStatus(order, Order.Kitchen.PREPARING));
                break;
            case Order.Kitchen.PREPARING:
                action(card.jobPrimary, R.string.food_ready, () -> setStatus(order, Order.Kitchen.READY));
                break;
            default:
                card.jobStatus.setText(R.string.waiting_pickup);
                break;
        }
    }

    /** With several restaurants on one order, each line ends with its restaurant's name. */
    private static String itemsFor(Order order, Order.Kitchen k) {
        if (order.kitchens.size() < 2) {
            return order.items;
        }
        List<String> mine = new ArrayList<>();
        for (String line : order.items.split("\n")) {
            if (line.endsWith(" · " + k.name)) {
                mine.add(line.substring(0, line.length() - k.name.length() - 3));
            }
        }
        return String.join("\n", mine);
    }

    private static void action(com.google.android.material.button.MaterialButton button, int label, Runnable onClick) {
        button.setVisibility(View.VISIBLE);
        button.setText(label);
        button.setOnClickListener(v -> onClick.run());
    }

    private void setStatus(Order order, int status) {
        OrderStore.mutate(this, order.id, o -> OrderLogic.setKitchenStatus(o, restaurantId, status,
                System.currentTimeMillis()), changed -> {
            if (!changed) {
                showMessage(getString(R.string.order_changed_elsewhere));
            }
        });
    }

    private void confirmReject(Order order) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reject_title)
                .setMessage(R.string.reject_text)
                .setPositiveButton(R.string.reject, (d, w) -> setStatus(order, Order.Kitchen.REJECTED))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Rush days: add cooking time, or pause new orders without closing. */
    private void chooseBusyMode() {
        if (restaurant == null) {
            return;
        }
        String[] options = {getString(R.string.busy_normal), getString(R.string.busy_15), getString(R.string.busy_30),
                getString(R.string.busy_pause)};
        int current = restaurant.paused ? 3 : restaurant.busyMinutes >= 30 ? 2 : restaurant.busyMinutes >= 15 ? 1 : 0;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.busy_title)
                .setSingleChoiceItems(options, current, (dialog, which) -> {
                    restaurant.paused = which == 3;
                    restaurant.busyMinutes = which == 1 ? 15 : which == 2 ? 30 : 0;
                    MenuRepository.get(this).saveRestaurant(restaurant, new MenuRepository.Listener<Void>() {
                        @Override
                        public void onResult(Void result) {
                            showRestaurant();
                            showMessage(options[which]);
                        }

                        @Override
                        public void onError() {
                            showMessage(getString(R.string.save_failed));
                        }
                    });
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.kitchen, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.kitchen_sales) {
                startActivity(new Intent(this, SalesDashboardActivity.class));
            } else if (id == R.id.kitchen_busy) {
                chooseBusyMode();
            } else if (id == R.id.kitchen_menu_editor) {
                startActivity(new Intent(this, MenuEditorActivity.class));
            } else if (id == R.id.kitchen_profile) {
                startActivity(new Intent(this, RestaurantProfileActivity.class));
            } else if (id == R.id.kitchen_coupons) {
                startActivity(new Intent(this, CouponsActivity.class));
            } else if (id == R.id.kitchen_reviews) {
                startActivity(new Intent(this, RestaurantReviewsActivity.class));
            } else if (id == R.id.kitchen_account) {
                startActivity(new Intent(this, ProfileActivity.class));
            } else if (id == R.id.kitchen_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.kitchen_help) {
                startActivity(new Intent(this, HelpActivity.class));
            } else if (id == R.id.kitchen_logout) {
                KitchenService.stop(this);
                new AuthRepository(this).logout();
                startActivity(new Intent(this, LoginActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            }
            return true;
        });
        menu.show();
    }
}
