package com.foodrush.app.ui.rider;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

import com.foodrush.app.R;
import com.foodrush.app.adapter.OrderAdapter;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityRiderDeliveryBinding;
import com.foodrush.app.databinding.ItemStopBinding;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Order;
import com.foodrush.app.order.OrderLogic;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.service.RiderService;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.ui.ChatActivity;
import com.foodrush.app.util.NotificationHelper;
import com.foodrush.app.util.Pins;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The rider's current delivery: the road route on the map, each restaurant to collect from, the
 * customer, chat and call, turn-by-turn in the phone's maps app, and delivery with the customer's PIN.
 */
public class RiderDeliveryActivity extends BaseActivity {

    public static final String EXTRA_ORDER_ID = "order_id";
    private static final int MAP_PADDING_PX = 100;

    private ActivityRiderDeliveryBinding binding;
    private Session session;
    private Order order;
    private Marker riderMarker;
    private String drawnRoute;
    private OrderStore.Subscription subscription;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable moveRider = new Runnable() {
        @Override
        public void run() {
            renderRider();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRiderDeliveryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.deliveryBar, R.string.delivery_title);
        session = new Session(this);

        binding.deliveryMap.setTileSource(TileSourceFactory.MAPNIK);
        binding.deliveryMap.setMultiTouchControls(true);
        binding.deliveryMap.setTilesScaledToDpi(true);
        binding.deliveryMap.getController().setZoom(15.0);

        binding.deliveryChat.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_ORDER_ID, order.id)));
        // A normal phone call to the customer's number; the in-app call is in the chat screen.
        binding.deliveryCall.setOnClickListener(v -> dialOrderPerson(order.id, "customerPhone"));
        binding.deliveryNavigate.setOnClickListener(v -> navigate());
        binding.deliveryDone.setOnClickListener(v -> askPin());
    }

    @Override
    protected void onStart() {
        super.onStart();
        String id = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (id == null) {
            finish();
            return;
        }
        subscription = OrderStore.watch(this, id, this::show);
        handler.post(moveRider);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.deliveryMap.onResume();
    }

    @Override
    protected void onPause() {
        binding.deliveryMap.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(moveRider);
        if (subscription != null) {
            subscription.remove();
            subscription = null;
        }
        super.onStop();
    }

    private void show(Order latest) {
        order = latest;
        if (!order.route.equals(drawnRoute)) {
            drawRoute();
        }
        boolean mine = session.uid().equals(order.riderUid);
        boolean active = order.isActive() && mine;
        Order.Kitchen next = nextPickup();
        if (!order.isActive()) {
            binding.deliveryStatus.setText(OrderAdapter.stageLabel(order.status));
        } else if (next != null) {
            binding.deliveryStatus.setText(getString(R.string.go_to_pickup, next.name));
        } else {
            binding.deliveryStatus.setText(getString(R.string.go_to_customer, order.customerName));
        }

        binding.deliveryStops.removeAllViews();
        for (Order.Kitchen k : orderedKitchens()) {
            ItemStopBinding row = ItemStopBinding.inflate(getLayoutInflater(), binding.deliveryStops, true);
            row.stopName.setText(k.name);
            row.stopStatus.setText(k.pickedUp ? getString(R.string.picked_up_done)
                    : k.status >= Order.Kitchen.READY ? getString(R.string.food_ready_pickup)
                    : OrderAdapter.kitchenLabel(this, k.status));
            row.stopCall.setVisibility(k.phone.isEmpty() ? View.GONE : View.VISIBLE);
            row.stopCall.setOnClickListener(v -> dial(k.phone));
            row.stopPicked.setVisibility(active && !k.pickedUp ? View.VISIBLE : View.GONE);
            row.stopPicked.setEnabled(k.status >= Order.Kitchen.READY);
            row.stopPicked.setOnClickListener(v -> pickedUp(k));
        }
        binding.deliveryCustomer.setText(getString(R.string.customer_line, order.customerName, order.address));
        binding.deliveryItems.setText(getString(R.string.collect_cash_line, order.items, order.payment,
                com.foodrush.app.util.PriceCalculator.format(order.total)));
        binding.deliveryChat.setEnabled(active);
        binding.deliveryCall.setEnabled(active);
        binding.deliveryNavigate.setEnabled(active);
        binding.deliveryDone.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.deliveryDone.setEnabled(order.pickedUpAt > 0);
        renderRider();
    }

    private List<Order.Kitchen> orderedKitchens() {
        List<Order.Kitchen> list = new ArrayList<>();
        for (String id : order.pickupOrder) {
            Order.Kitchen k = order.kitchen(id);
            if (k != null) {
                list.add(k);
            }
        }
        for (Order.Kitchen k : order.kitchens) {
            if (!list.contains(k)) {
                list.add(k);
            }
        }
        return list;
    }

    private Order.Kitchen nextPickup() {
        for (Order.Kitchen k : orderedKitchens()) {
            if (!k.pickedUp) {
                return k;
            }
        }
        return null;
    }

    private void renderRider() {
        if (order == null) {
            return;
        }
        LatLon here = RiderService.lastFix();
        if (here == null) {
            binding.deliveryDistance.setText(R.string.waiting_for_gps);
            return;
        }
        if (riderMarker != null) {
            riderMarker.setPosition(new GeoPoint(here.lat, here.lon));
            riderMarker.setVisible(true);
            binding.deliveryMap.invalidate();
        }
        Order.Kitchen next = nextPickup();
        LatLon target = next != null ? next.location() : order.customer();
        binding.deliveryDistance.setText(getString(R.string.distance_to_next,
                here.metersTo(target) / 1000));
    }

    private void drawRoute() {
        drawnRoute = order.route;
        binding.deliveryMap.getOverlays().clear();
        List<GeoPoint> points = new ArrayList<>();
        for (LatLon p : order.routePoints()) {
            points.add(new GeoPoint(p.lat, p.lon));
        }
        if (points.size() > 1) {
            Polyline line = new Polyline(binding.deliveryMap);
            line.setPoints(points);
            line.getOutlinePaint().setColor(ContextCompat.getColor(this, R.color.amber_dark));
            line.getOutlinePaint().setStrokeWidth(10f);
            binding.deliveryMap.getOverlays().add(line);
        }
        for (Order.Kitchen k : order.kitchens) {
            addMarker(new GeoPoint(k.lat, k.lon), R.drawable.ic_map_restaurant, k.name, Marker.ANCHOR_BOTTOM);
        }
        addMarker(new GeoPoint(order.lat, order.lon), R.drawable.ic_map_pin, order.customerName, Marker.ANCHOR_BOTTOM);
        riderMarker = addMarker(new GeoPoint(order.lat, order.lon), R.drawable.ic_map_rider,
                getString(R.string.you_are_here), Marker.ANCHOR_CENTER);
        riderMarker.setVisible(false);
        if (points.size() > 1) {
            BoundingBox box = BoundingBox.fromGeoPointsSafe(points);
            binding.deliveryMap.post(() -> binding.deliveryMap.zoomToBoundingBox(box, false, MAP_PADDING_PX));
        } else {
            binding.deliveryMap.getController().setCenter(new GeoPoint(order.lat, order.lon));
        }
        binding.deliveryMap.invalidate();
    }

    private Marker addMarker(GeoPoint point, int icon, String title, float anchorY) {
        Marker marker = new Marker(binding.deliveryMap);
        marker.setPosition(point);
        marker.setIcon(ContextCompat.getDrawable(this, icon));
        marker.setTitle(title);
        marker.setAnchor(Marker.ANCHOR_CENTER, anchorY);
        binding.deliveryMap.getOverlays().add(marker);
        return marker;
    }

    private void pickedUp(Order.Kitchen kitchen) {
        OrderStore.mutate(this, order.id, o -> OrderLogic.riderPickedUp(o, kitchen.restaurantId,
                System.currentTimeMillis()), changed -> {
            if (!changed) {
                showMessage(getString(R.string.not_ready_yet));
            }
        });
    }

    /** Turn-by-turn directions in the phone's own maps app, handed over with a free geo: intent. */
    private void navigate() {
        Order.Kitchen next = nextPickup();
        LatLon target = next != null ? next.location() : order.customer();
        String label = next != null ? next.name : order.customerName;
        startImplicit(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
                target.lat, target.lon, target.lat, target.lon, Uri.encode(label)))));
    }

    private void askPin() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("0000");
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.enter_pin_title)
                .setMessage(R.string.enter_pin_text)
                .setView(input)
                .setPositiveButton(R.string.deliver, (d, w) -> {
                    if (!Pins.matches(order.id, input.getText().toString(), order.pinHash)) {
                        showMessage(getString(R.string.pin_wrong));
                        return;
                    }
                    OrderStore.mutate(this, order.id, o -> OrderLogic.deliver(o, System.currentTimeMillis()), changed -> {
                        if (changed) {
                            NotificationHelper.cancel(this, ("chat" + order.id).hashCode());
                            showMessage(getString(R.string.delivered_thanks,
                                    com.foodrush.app.util.PriceCalculator.format(order.fee)));
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
