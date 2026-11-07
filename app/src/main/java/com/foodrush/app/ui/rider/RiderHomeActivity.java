package com.foodrush.app.ui.rider;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.PopupMenu;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.foodrush.app.R;
import com.foodrush.app.adapter.JobAdapter;
import com.foodrush.app.adapter.OrderAdapter;
import com.foodrush.app.data.AuthRepository;
import com.foodrush.app.data.Cloud;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityKitchenBinding;
import com.foodrush.app.databinding.ItemKitchenOrderBinding;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Order;
import com.foodrush.app.order.OrderLogic;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.route.RoutePlanner;
import com.foodrush.app.service.RiderService;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.ui.HelpActivity;
import com.foodrush.app.ui.LoginActivity;
import com.foodrush.app.ui.ProfileActivity;
import com.foodrush.app.ui.SettingsActivity;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.PriceCalculator;
import com.foodrush.app.util.States;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The rider's home: go online, see delivery jobs nearby (the quickest one by road, worked out with
 * Dijkstra, is marked Recommended), the current delivery, history and earnings.
 */
public class RiderHomeActivity extends BaseActivity {

    private ActivityKitchenBinding binding;
    private Session session;
    private JobAdapter adapter;
    private OrderStore.Subscription mine;
    private OrderStore.Subscription open;
    private ListenerRegistration profile;
    private List<Order> myOrders = new ArrayList<>();
    private List<Order> openOrders = new ArrayList<>();
    /** Road minutes from the rider to each job's first pickup, by order id. */
    private final Map<String, Double> pickupMinutes = new HashMap<>();
    private String recommendedId;
    private String routedFor;
    private String rating = "–";
    private boolean tabChosen;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** The GPS position comes from the service; refresh the map and job times now and then. */
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 10_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new Session(this);
        binding = ActivityKitchenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.workTitle.setText(getString(R.string.rider_hello, session.name()));
        binding.workSwitch.setText(R.string.online);
        binding.workTabActive.setText(R.string.rider_tab_current);
        binding.workTabOther.setText(R.string.rider_tab_available);
        binding.workTabDone.setText(R.string.rider_tab_history);
        binding.workTabs.setOnCheckedStateChangeListener((group, ids) -> render());
        binding.workMenu.setOnClickListener(this::showMenu);
        binding.workStats.setOnClickListener(v -> startActivity(new Intent(this, RiderDashboardActivity.class)));
        binding.workSwitch.setChecked(session.riderOnline());
        binding.workSwitch.setOnCheckedChangeListener((button, checked) -> setOnline(checked));

        binding.workMap.setVisibility(View.VISIBLE);
        binding.workMap.setTileSource(TileSourceFactory.MAPNIK);
        binding.workMap.setMultiTouchControls(true);
        binding.workMap.setTilesScaledToDpi(true);
        binding.workMap.getController().setZoom(14.0);
        LatLon start = session.addressPoint();
        binding.workMap.getController().setCenter(new GeoPoint(start.lat, start.lon));

        adapter = new JobAdapter(this::bindCard);
        binding.workList.setLayoutManager(new LinearLayoutManager(this));
        binding.workList.setAdapter(adapter);
        States.loading(binding.workState);

        if (!Cloud.isOn(this)) {
            States.empty(binding.workState, R.string.role_needs_firebase);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!Cloud.isOn(this)) {
            return;
        }
        if (session.riderOnline()) {
            withLocation(() -> RiderService.start(this));
        }
        mine = OrderStore.watchRider(this, session.uid(), list -> {
            myOrders = list;
            if (!tabChosen) {
                tabChosen = true;
                binding.workTabs.check(hasActive() ? R.id.work_tab_active
                        : session.riderOnline() ? R.id.work_tab_other : R.id.work_tab_done);
            }
            render();
        });
        open = OrderStore.watchUnassigned(this, list -> {
            openOrders = list;
            render();
        });
        profile = Cloud.db().collection(Cloud.RIDERS).document(session.uid()).addSnapshotListener((doc, e) -> {
            if (doc != null && doc.exists()) {
                Long sum = doc.getLong("ratingSum");
                Long count = doc.getLong("ratingCount");
                rating = count == null || count == 0 ? "–"
                        : String.format(Locale.US, "%.1f★ (%d)", sum / (double) count, count);
                render();
            }
        });
        handler.postDelayed(refresh, 10_000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.workMap.onResume();
    }

    @Override
    protected void onPause() {
        binding.workMap.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(refresh);
        if (mine != null) {
            mine.remove();
        }
        if (open != null) {
            open.remove();
        }
        if (profile != null) {
            profile.remove();
        }
        super.onStop();
    }

    private boolean hasActive() {
        for (Order order : myOrders) {
            if (order.isActive()) {
                return true;
            }
        }
        return false;
    }

    /** Location for sharing the rider's position; notifications for job alerts. */
    private void withLocation(Runnable then) {
        withPermission(Manifest.permission.ACCESS_FINE_LOCATION, R.string.permission_location_rider, () -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withPermission(Manifest.permission.POST_NOTIFICATIONS, R.string.permission_notifications_rider, then);
            } else {
                then.run();
            }
        });
    }

    private void setOnline(boolean online) {
        if (online) {
            withLocation(() -> {
                session.setRiderOnline(true);
                RiderService.start(this);
                showMessage(getString(R.string.now_online));
                binding.workTabs.check(R.id.work_tab_other);
                render();
            });
        } else {
            if (hasActive()) {
                showMessage(getString(R.string.finish_delivery_first));
                binding.workSwitch.setChecked(true);
                return;
            }
            session.setRiderOnline(false);
            RiderService.stop(this);
            render();
        }
    }

    private List<Order> nearbyJobs(LatLon here) {
        List<Order> jobs = new ArrayList<>();
        for (Order order : openOrders) {
            if (!order.isActive() || order.status < Order.ACCEPTED || order.kitchens.isEmpty()) {
                continue;
            }
            if (here == null || here.metersTo(order.kitchens.get(0).location()) <= RiderService.JOB_RADIUS_METERS) {
                jobs.add(order);
            }
        }
        return jobs;
    }

    private void render() {
        LatLon here = RiderService.lastFix();
        int tab = binding.workTabs.getCheckedChipId();
        List<Order> shown = new ArrayList<>();
        if (tab == R.id.work_tab_active) {
            for (Order order : myOrders) {
                if (order.isActive()) {
                    shown.add(order);
                }
            }
        } else if (tab == R.id.work_tab_other) {
            if (session.riderOnline()) {
                shown.addAll(nearbyJobs(here));
                routeJobs(here, shown);
                shown.sort((a, b) -> Double.compare(minutesTo(a), minutesTo(b)));
            }
        } else {
            for (Order order : myOrders) {
                if (!order.isActive()) {
                    shown.add(order);
                }
            }
        }
        renderStats();
        renderMap(here, tab == R.id.work_tab_other ? shown : new ArrayList<>());
        adapter.submit(shown);
        if (!shown.isEmpty()) {
            States.hide(binding.workState);
        } else if (tab == R.id.work_tab_other && !session.riderOnline()) {
            States.empty(binding.workState, R.string.go_online_first);
        } else {
            States.empty(binding.workState, tab == R.id.work_tab_active ? R.string.rider_empty_current
                    : tab == R.id.work_tab_other ? R.string.rider_empty_available : R.string.rider_empty_history);
        }
    }

    private double minutesTo(Order order) {
        Double minutes = pickupMinutes.get(order.id);
        return minutes == null ? Double.MAX_VALUE : minutes;
    }

    /** One Dijkstra run from the rider gives the road time to every job's pickup at once. */
    private void routeJobs(LatLon here, List<Order> jobs) {
        if (here == null || jobs.isEmpty()) {
            return;
        }
        StringBuilder key = new StringBuilder(here.encode());
        for (Order job : jobs) {
            key.append(job.id);
        }
        if (key.toString().equals(routedFor)) {
            return;
        }
        routedFor = key.toString();
        List<LatLon> pickups = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Order job : jobs) {
            pickups.add(job.kitchens.get(0).location());
            ids.add(job.id);
        }
        AppExecutors.load(() -> new RoutePlanner(this).minutesFrom(here, pickups), minutes -> {
            String best = null;
            for (int i = 0; i < ids.size(); i++) {
                pickupMinutes.put(ids.get(i), minutes[i]);
                if (best == null || minutes[i] < pickupMinutes.get(best)) {
                    best = ids.get(i);
                }
            }
            recommendedId = best;
            render();
        });
    }

    private void renderStats() {
        int todayEarned = 0;
        int todayCount = 0;
        int weekEarned = 0;
        Calendar weekStart = Calendar.getInstance();
        weekStart.add(Calendar.DAY_OF_YEAR, -6);
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        for (Order order : myOrders) {
            if (order.status != Order.DELIVERED) {
                continue;
            }
            int earned = order.fee + order.tip;
            if (DateUtils.isToday(order.deliveredAt)) {
                todayEarned += earned;
                todayCount++;
            }
            if (order.deliveredAt >= weekStart.getTimeInMillis()) {
                weekEarned += earned;
            }
        }
        binding.workStats.setText(getString(R.string.rider_stats_tap, PriceCalculator.format(todayEarned), todayCount,
                PriceCalculator.format(weekEarned), rating));
        binding.workSubtitle.setText(session.riderOnline() ? R.string.rider_status_online : R.string.rider_status_offline);
    }

    private void renderMap(LatLon here, List<Order> jobs) {
        binding.workMap.getOverlays().clear();
        if (here != null) {
            Marker me = new Marker(binding.workMap);
            me.setPosition(new GeoPoint(here.lat, here.lon));
            me.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_map_rider));
            me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            me.setTitle(getString(R.string.you_are_here));
            binding.workMap.getOverlays().add(me);
            binding.workMap.getController().setCenter(me.getPosition());
        }
        for (Order job : jobs) {
            Order.Kitchen k = job.kitchens.get(0);
            Marker marker = new Marker(binding.workMap);
            marker.setPosition(new GeoPoint(k.lat, k.lon));
            marker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_map_restaurant));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(k.name);
            binding.workMap.getOverlays().add(marker);
        }
        binding.workMap.invalidate();
    }

    private void bindCard(ItemKitchenOrderBinding card, Order order) {
        card.jobTitle.setText(restaurantsOf(order));
        card.jobAmount.setText(PriceCalculator.format(order.fee + order.tip));
        card.jobItems.setText(getString(R.string.deliver_to, order.address));
        card.jobMeta.setText(getString(R.string.job_meta, order.distanceKm, order.itemCount,
                DateUtils.getRelativeTimeSpanString(order.placedAt)));
        if (!order.isActive() || order.hasRealRider() && !session.uid().equals(order.riderUid)) {
            card.jobStatus.setText(OrderAdapter.stageLabel(order.status));
            return;
        }
        if (session.uid().equals(order.riderUid)) {
            card.jobStatus.setText(OrderAdapter.stageLabel(order.status));
            show(card.jobPrimary, R.string.open_delivery);
            card.jobPrimary.setOnClickListener(v -> openDelivery(order));
            card.getRoot().setOnClickListener(v -> openDelivery(order));
            return;
        }
        Double minutes = pickupMinutes.get(order.id);
        String pickup = minutes == null ? getString(R.string.eta_working)
                : getString(R.string.pickup_minutes, (int) Math.ceil(minutes));
        card.jobStatus.setText(order.id.equals(recommendedId) ? getString(R.string.recommended_job, pickup) : pickup);
        show(card.jobPrimary, R.string.accept_job);
        card.jobPrimary.setOnClickListener(v -> accept(order));
    }

    private static void show(View button, int label) {
        button.setVisibility(View.VISIBLE);
        ((com.google.android.material.button.MaterialButton) button).setText(label);
    }

    private static String restaurantsOf(Order order) {
        List<String> names = new ArrayList<>();
        for (Order.Kitchen k : order.kitchens) {
            names.add(k.name);
        }
        return String.join(" + ", names);
    }

    private void accept(Order order) {
        if (hasActive()) {
            showMessage(getString(R.string.one_job_at_a_time));
            return;
        }
        String uid = session.uid();
        String name = session.name();
        OrderStore.mutate(this, order.id, o -> OrderLogic.riderAccept(o, uid, name, System.currentTimeMillis()),
                changed -> {
                    if (!changed) {
                        new MaterialAlertDialogBuilder(this)
                                .setMessage(R.string.job_taken)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                        return;
                    }
                    OrderStore.setRiderPhone(order.id, session.phone());
                    openDelivery(order);
                });
    }

    private void openDelivery(Order order) {
        startActivity(new Intent(this, RiderDeliveryActivity.class)
                .putExtra(RiderDeliveryActivity.EXTRA_ORDER_ID, order.id));
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 5, 0, R.string.rider_dashboard_title);
        menu.getMenu().add(0, 1, 0, R.string.nav_profile);
        menu.getMenu().add(0, 2, 1, R.string.nav_settings);
        menu.getMenu().add(0, 3, 2, R.string.nav_help);
        menu.getMenu().add(0, 4, 3, R.string.nav_logout);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 5:
                    startActivity(new Intent(this, RiderDashboardActivity.class));
                    break;
                case 1:
                    startActivity(new Intent(this, ProfileActivity.class));
                    break;
                case 2:
                    startActivity(new Intent(this, SettingsActivity.class));
                    break;
                case 3:
                    startActivity(new Intent(this, HelpActivity.class));
                    break;
                default:
                    if (hasActive()) {
                        showMessage(getString(R.string.finish_delivery_first));
                        break;
                    }
                    RiderService.stop(this);
                    new AuthRepository(this).logout();
                    startActivity(new Intent(this, LoginActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    break;
            }
            return true;
        });
        menu.show();
    }
}
