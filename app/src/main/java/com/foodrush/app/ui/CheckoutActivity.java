package com.foodrush.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.foodrush.app.R;
import com.foodrush.app.data.CartRepository;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityCheckoutBinding;
import com.foodrush.app.databinding.ItemCheckStepBinding;
import com.foodrush.app.model.CartItem;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Order;
import com.foodrush.app.model.Promo;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.route.RoutePlanner;
import com.foodrush.app.service.OrderTrackingService;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.Pins;
import com.foodrush.app.util.PriceCalculator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Checkout. While a scooter animation plays, it checks the restaurants, finds the road route (at
 * most about 10 seconds: Dijkstra on real roads, else OSRM, else an estimate) and works out the
 * bill. Then it shows the route on a map with the final total and a Place order button.
 */
public class CheckoutActivity extends BaseActivity {

    public static final String EXTRA_DELAY_MINUTES = "delay_minutes";

    private static final long ROUTE_BUDGET_MILLIS = 10_000;
    /** Long enough to see the animation even when the route is already known. */
    private static final long MIN_ANIMATION_MILLIS = 2_400;
    private static final long STEP_GAP_MILLIS = 450;
    private static final long DUMMY_PAYMENT_MILLIS = 1500;

    private ActivityCheckoutBinding binding;
    private Session session;
    private MenuRepository menu;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Restaurant> restaurants = new LinkedHashMap<>();
    private List<CartItem> items = new ArrayList<>();
    private RoutePlanner.Result plan;
    private PriceCalculator.Quote quote;
    private Promo promo;
    private int delayMinutes;
    private long startedAt;
    private boolean placing;

    private final ActivityResultLauncher<Intent> paymentPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    session.setPayment(data.getStringExtra(PaymentActivity.EXTRA_LABEL));
                    binding.checkoutPayment.infoText.setText(session.payment());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        session = new Session(this);
        menu = MenuRepository.get(this);
        delayMinutes = getIntent().getIntExtra(EXTRA_DELAY_MINUTES, 0);
        setUpTopBar(binding.checkoutBar, R.string.checkout_summary_title);

        binding.checkoutStepRestaurants.stepText.setText(R.string.checkout_step_restaurants);
        binding.checkoutStepRoute.stepText.setText(R.string.checkout_step_route);
        binding.checkoutStepBill.stepText.setText(R.string.checkout_step_bill);
        waiting(binding.checkoutStepRoute);
        waiting(binding.checkoutStepBill);

        binding.checkoutMap.setTileSource(TileSourceFactory.MAPNIK);
        binding.checkoutMap.setTilesScaledToDpi(true);
        binding.checkoutMap.setMultiTouchControls(true);
        binding.checkoutAddress.infoIcon.setImageResource(R.drawable.ic_pin);
        binding.checkoutTime.infoIcon.setImageResource(R.drawable.ic_clock);
        binding.checkoutPayment.infoIcon.setImageResource(R.drawable.ic_wallet);
        binding.checkoutPayment.getRoot().setOnClickListener(v ->
                paymentPicker.launch(new Intent(this, PaymentActivity.class)));
        binding.checkoutAddress.getRoot().setOnClickListener(v -> finish());
        binding.checkoutTime.getRoot().setOnClickListener(v -> finish());
        binding.checkoutPlace.setOnClickListener(v -> placeOrder());

        startedAt = SystemClock.elapsedRealtime();
        loadCart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.checkoutMap.onResume();
    }

    @Override
    protected void onPause() {
        binding.checkoutMap.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---- Step 1: restaurants ----

    private void loadCart() {
        AppExecutors.load(() -> CartRepository.all(this), loaded -> {
            items = loaded;
            if (items.isEmpty()) {
                finish();
                return;
            }
            menu.restaurants(false, new MenuRepository.Listener<List<Restaurant>>() {
                @Override
                public void onResult(List<Restaurant> all) {
                    restaurants.clear();
                    for (CartItem item : items) {
                        Restaurant r = menu.restaurant(item.restaurantId);
                        if (r != null) {
                            restaurants.put(r.id, r);
                        }
                    }
                    String problem = CartCheck.problem(CheckoutActivity.this, items, restaurants, session.addressPoint());
                    if (problem != null || restaurants.isEmpty()) {
                        stopWith(problem != null ? problem : getString(R.string.load_error));
                        return;
                    }
                    later(() -> {
                        done(binding.checkoutStepRestaurants);
                        working(binding.checkoutStepRoute);
                        findRoute();
                    });
                }

                @Override
                public void onError() {
                    stopWith(getString(R.string.load_error));
                }
            });
        });
    }

    // ---- Step 2: the road route ----

    private void findRoute() {
        List<LatLon> pickups = CartCheck.pickups(restaurants);
        LatLon customer = session.addressPoint();
        AppExecutors.load(() -> new RoutePlanner(this).planWithin(pickups, customer, ROUTE_BUDGET_MILLIS), result -> {
            if (isFinishing()) {
                return;
            }
            plan = result;
            later(() -> {
                done(binding.checkoutStepRoute);
                working(binding.checkoutStepBill);
                findCoupon();
            });
        });
    }

    // ---- Step 3: the bill ----

    private void findCoupon() {
        String code = session.coupon();
        if (code == null) {
            billAndShow();
            return;
        }
        menu.findCoupon(code, new MenuRepository.Listener<Promo>() {
            @Override
            public void onResult(Promo found) {
                promo = found != null && found.usableAt(System.currentTimeMillis()) ? found : null;
                billAndShow();
            }

            @Override
            public void onError() {
                billAndShow();
            }
        });
    }

    private void billAndShow() {
        int subtotal = CartCheck.subtotal(items);
        int fee = PriceCalculator.deliveryFee(plan.meters / 1000);
        boolean promoFits = promo != null && (promo.restaurantId == null || restaurants.containsKey(promo.restaurantId));
        if (promoFits) {
            int eligible = promo.restaurantId == null ? subtotal : CartCheck.subtotalFor(items, promo.restaurantId);
            quote = PriceCalculator.quote(subtotal, eligible, fee, promo.percent, promo.flat, promo.freeDelivery,
                    promo.minOrder);
        } else {
            promo = null;
            quote = PriceCalculator.plain(subtotal, fee);
        }
        long shown = SystemClock.elapsedRealtime() - startedAt;
        handler.postDelayed(() -> {
            done(binding.checkoutStepBill);
            handler.postDelayed(this::showSummary, STEP_GAP_MILLIS);
        }, Math.max(STEP_GAP_MILLIS, MIN_ANIMATION_MILLIS - shown));
    }

    private void later(Runnable step) {
        handler.postDelayed(step, STEP_GAP_MILLIS);
    }

    private static void waiting(ItemCheckStepBinding step) {
        step.stepProgress.setVisibility(View.INVISIBLE);
        step.stepDone.setVisibility(View.GONE);
        step.stepText.setAlpha(0.5f);
    }

    private static void working(ItemCheckStepBinding step) {
        step.stepProgress.setVisibility(View.VISIBLE);
        step.stepDone.setVisibility(View.GONE);
        step.stepText.setAlpha(1f);
    }

    private static void done(ItemCheckStepBinding step) {
        step.stepProgress.setVisibility(View.GONE);
        step.stepDone.setVisibility(View.VISIBLE);
        step.stepText.setAlpha(1f);
    }

    /** A cart problem (closed, too far, below minimum): explain and go back to the cart. */
    private void stopWith(String message) {
        binding.checkoutAnimation.stop();
        new MaterialAlertDialogBuilder(this)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.back_to_cart, (d, w) -> finish())
                .show();
    }

    // ---- Summary ----

    private void showSummary() {
        binding.checkoutAnimation.stop();
        binding.checkoutLoading.setVisibility(View.GONE);
        binding.checkoutSummary.setVisibility(View.VISIBLE);

        binding.checkoutAddress.infoText.setText(session.addressLabel());
        binding.checkoutPayment.infoText.setText(session.payment());
        int low = prepMinutes() + (int) Math.ceil(plan.minutes);
        if (delayMinutes > 0) {
            binding.checkoutTime.infoText.setText(delayMinutes == 30 ? R.string.eta_scheduled_30 : R.string.eta_scheduled_60);
        } else {
            binding.checkoutTime.infoText.setText(getString(R.string.eta_asap,
                    String.format(Locale.US, "%d-%d min", low, low + 5)));
        }
        binding.checkoutRoute.setText(getString(R.string.route_summary,
                String.format(Locale.US, "%.1f", plan.meters / 1000), routeSourceLabel(plan.source)));

        List<String> lines = new ArrayList<>();
        for (CartItem item : items) {
            lines.add(String.format(Locale.US, "%d × %s%s", item.quantity, item.name,
                    item.size.isEmpty() ? "" : " (" + item.size + ")"));
        }
        binding.checkoutItems.setText(String.join("\n", lines));
        binding.checkoutSubtotal.setText(PriceCalculator.format(quote.subtotal));
        binding.checkoutDiscountRow.setVisibility(quote.discount > 0 ? View.VISIBLE : View.GONE);
        binding.checkoutDiscount.setText(getString(R.string.minus_amount, PriceCalculator.format(quote.discount)));
        binding.checkoutVat.setText(PriceCalculator.format(quote.vat));
        binding.checkoutFee.setText(PriceCalculator.format(quote.fee));
        binding.checkoutTotal.setText(PriceCalculator.format(quote.total));
        binding.checkoutPlace.setText(getString(R.string.place_order_total, PriceCalculator.format(quote.total)));
        drawRoute();
    }

    private String routeSourceLabel(String source) {
        switch (source) {
            case RoutePlanner.SOURCE_DIJKSTRA:
                return getString(R.string.route_source_dijkstra);
            case RoutePlanner.SOURCE_OSRM:
                return getString(R.string.route_source_osrm);
            default:
                return getString(R.string.route_source_estimate);
        }
    }

    private void drawRoute() {
        List<GeoPoint> points = new ArrayList<>();
        for (LatLon p : plan.points) {
            points.add(new GeoPoint(p.lat, p.lon));
        }
        Polyline line = new Polyline(binding.checkoutMap);
        line.setPoints(points);
        line.getOutlinePaint().setColor(ContextCompat.getColor(this, R.color.amber_dark));
        line.getOutlinePaint().setStrokeWidth(9f);
        binding.checkoutMap.getOverlays().add(line);
        for (Restaurant r : restaurants.values()) {
            addMarker(new GeoPoint(r.lat, r.lon), R.drawable.ic_map_restaurant, r.name);
        }
        LatLon home = session.addressPoint();
        addMarker(new GeoPoint(home.lat, home.lon), R.drawable.ic_map_pin, getString(R.string.marker_you));
        if (points.size() > 1) {
            BoundingBox box = BoundingBox.fromGeoPointsSafe(points);
            binding.checkoutMap.post(() -> binding.checkoutMap.zoomToBoundingBox(box, false, 90));
        } else {
            binding.checkoutMap.getController().setZoom(15.0);
            binding.checkoutMap.getController().setCenter(new GeoPoint(home.lat, home.lon));
        }
        binding.checkoutMap.invalidate();
    }

    private void addMarker(GeoPoint point, int icon, String title) {
        Marker marker = new Marker(binding.checkoutMap);
        marker.setPosition(point);
        marker.setIcon(ContextCompat.getDrawable(this, icon));
        marker.setTitle(title);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        binding.checkoutMap.getOverlays().add(marker);
    }

    /** Cooking time: the slowest dish, plus any extra minutes a busy restaurant has added. */
    private int prepMinutes() {
        int busy = 0;
        for (Restaurant r : restaurants.values()) {
            busy = Math.max(busy, r.busyMinutes);
        }
        return CartCheck.prepMinutes(items) + busy;
    }

    // ---- Placing the order ----

    private void placeOrder() {
        if (placing || plan == null || quote == null) {
            return;
        }
        placing = true;
        binding.checkoutPlace.setEnabled(false);
        if (Session.PAYMENT_CASH.equals(session.payment())) {
            submitOrder();
            return;
        }
        // Dummy payment: a short pause standing in for a gateway. Nothing is charged.
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.processing_payment)
                .setCancelable(false)
                .show();
        handler.postDelayed(() -> {
            if (!isDestroyed()) {
                dialog.dismiss();
                submitOrder();
            }
        }, DUMMY_PAYMENT_MILLIS);
    }

    private void submitOrder() {
        String pin = Pins.newPin();
        Order order = buildOrder(pin);
        session.setPin(order.id, pin);
        OrderStore.place(this, order, pin, session.phone(), ok -> {
            if (!ok) {
                placing = false;
                binding.checkoutPlace.setEnabled(true);
                showMessage(getString(R.string.order_failed));
                return;
            }
            AppExecutors.io(() -> CartRepository.clear(this));
            session.setCoupon(null);
            OrderTrackingService.startIfNeeded(this);
            startActivity(new Intent(this, OrderPlacedActivity.class)
                    .putExtra(OrderPlacedActivity.EXTRA_ORDER_ID, order.id));
            setResult(RESULT_OK);
            finish();
        });
    }

    private Order buildOrder(String pin) {
        List<String> lines = new ArrayList<>();
        int count = 0;
        for (CartItem item : items) {
            Restaurant r = restaurants.get(item.restaurantId);
            StringBuilder line = new StringBuilder(String.format(Locale.US, "%d × %s", item.quantity, item.name));
            if (!item.size.isEmpty()) {
                line.append(" (").append(item.size).append(')');
            }
            if (!item.extras.isEmpty()) {
                line.append(" + ").append(item.extras);
            }
            if (restaurants.size() > 1 && r != null) {
                line.append(" · ").append(r.name);
            }
            lines.add(line.toString());
            count += item.quantity;
        }
        Order order = new Order();
        order.id = UUID.randomUUID().toString();
        order.customerUid = session.uid();
        order.customerName = session.name();
        order.placedAt = System.currentTimeMillis();
        order.items = String.join("\n", lines);
        order.itemCount = count;
        order.subtotal = quote.subtotal;
        order.discount = quote.discount;
        order.vat = quote.vat;
        order.fee = quote.fee;
        order.total = quote.total;
        order.coupon = promo == null ? "" : promo.code;
        order.payment = session.payment();
        order.address = session.addressLabel();
        order.lat = session.addressPoint().lat;
        order.lon = session.addressPoint().lon;
        List<Restaurant> list = new ArrayList<>(restaurants.values());
        int prep = prepMinutes();
        for (Restaurant r : list) {
            Order.Kitchen kitchen = new Order.Kitchen();
            kitchen.restaurantId = r.id;
            kitchen.name = r.name;
            kitchen.lat = r.lat;
            kitchen.lon = r.lon;
            kitchen.phone = r.phone;
            kitchen.ownerUid = r.ownerUid;
            kitchen.subtotal = CartCheck.subtotalFor(items, r.id);
            kitchen.prepMinutes = prep;
            order.kitchens.add(kitchen);
            order.restaurantIds.add(r.id);
            if (r.ownerUid != null && !r.ownerUid.isEmpty() && !order.ownerUids.contains(r.ownerUid)) {
                order.ownerUids.add(r.ownerUid);
            }
        }
        for (int i : plan.pickupOrder) {
            order.pickupOrder.add(list.get(i).id);
        }
        order.route = Order.encodeRoute(plan.points);
        order.routeSource = plan.source;
        order.travelMinutes = plan.minutes;
        order.distanceKm = plan.meters / 1000;
        order.etaMinutes = Math.max(prep + (int) Math.ceil(plan.minutes), delayMinutes);
        order.pinHash = Pins.hash(order.id, pin);
        order.scheduledFor = delayMinutes > 0 ? order.placedAt + delayMinutes * 60_000L : 0;
        for (CartItem item : items) {
            Order.Line line = new Order.Line();
            line.dishId = item.dishId;
            line.name = item.name;
            line.restaurantId = item.restaurantId;
            line.quantity = item.quantity;
            line.unitPrice = item.unitPrice;
            line.size = item.size;
            line.extras = item.extras;
            line.prepMinutes = item.prepMinutes;
            order.lines.add(line);
        }
        return order;
    }
}
