package com.foodrush.app.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.ContactsContract;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.foodrush.app.R;
import com.foodrush.app.data.FirebaseSync;
import com.foodrush.app.data.OrderRepository;
import com.foodrush.app.data.ReviewRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityTrackingBinding;
import com.foodrush.app.databinding.DialogRateBinding;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Order;
import com.foodrush.app.model.Review;
import com.foodrush.app.order.OrderLogic;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.receiver.OrderStatusReceiver;
import com.foodrush.app.service.OrderTrackingService;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.NotificationHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Live order screen. The planned road route is drawn on the map; the rider's marker follows the real
 * rider's GPS (sent from their phone every few seconds) or the demo rider along the route.
 */
public class TrackingActivity extends BaseActivity {

    public static final String EXTRA_ORDER_ID = "order_id";
    private static final int MAP_PADDING_PX = 120;
    private static final long REDRAW_MILLIS = 1000;

    private ActivityTrackingBinding binding;
    private Session session;
    private Order order;
    private String drawnRoute;
    private Marker riderMarker;
    private OrderStore.Subscription subscription;
    private boolean bound;
    private boolean framed;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable redraw = new Runnable() {
        @Override
        public void run() {
            if (order != null) {
                renderMovement();
            }
            handler.postDelayed(this, REDRAW_MILLIS);
        }
    };

    private final OrderStatusReceiver receiver = new OrderStatusReceiver((orderId, status) -> {
        if (order != null && order.id.equals(orderId)) {
            renderMovement();
        }
    });

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Order latest = order == null ? null
                    : ((OrderTrackingService.LocalBinder) service).service().latest(order.id);
            if (latest != null) {
                show(latest);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Nothing to clean up: the order listener keeps the screen correct.
        }
    };

    private final ActivityResultLauncher<Intent> contactPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) {
                    shareWithContact(data.getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTrackingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.trackingBar, R.string.tracking_title);
        session = new Session(this);

        binding.trackingMap.setTileSource(TileSourceFactory.MAPNIK);
        binding.trackingMap.setMultiTouchControls(true);
        binding.trackingMap.setTilesScaledToDpi(true);
        binding.trackingMap.getController().setZoom(15.0);

        binding.trackingCall.setOnClickListener(v -> callRestaurant());
        binding.trackingShare.setOnClickListener(v -> contactPicker.launch(
                new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)));
        binding.trackingChat.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_ORDER_ID, order.id)));
        // A normal phone call to the rider's number; the in-app call is in the chat screen.
        binding.trackingCallRider.setOnClickListener(v -> dialOrderPerson(order.id, "riderPhone"));
        binding.trackingCancel.setOnClickListener(v -> confirmCancel());
        binding.trackingRate.setOnClickListener(v -> showRateDialog());
        loadOrder(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadOrder(intent);
    }

    private void loadOrder(Intent intent) {
        String id = intent.getStringExtra(EXTRA_ORDER_ID);
        drawnRoute = null;
        framed = false;
        AppExecutors.load(() -> OrderRepository.find(this, id), found -> {
            if (found == null) {
                finish();
                return;
            }
            show(found);
            if (getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                startLiveUpdates();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        receiver.register(this);
        if (order != null) {
            startLiveUpdates();
        }
        handler.post(redraw);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.trackingMap.onResume();
    }

    @Override
    protected void onPause() {
        binding.trackingMap.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(receiver);
        handler.removeCallbacks(redraw);
        stopLiveUpdates();
        super.onStop();
    }

    private void startLiveUpdates() {
        stopLiveUpdates();
        if (order.isActive()) {
            OrderTrackingService.startIfNeeded(this);
            bound = bindService(new Intent(this, OrderTrackingService.class), connection, Context.BIND_AUTO_CREATE);
        }
        subscription = OrderStore.watch(this, order.id, this::show);
    }

    private void stopLiveUpdates() {
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        if (subscription != null) {
            subscription.remove();
            subscription = null;
        }
    }

    private void show(Order latest) {
        order = latest;
        if (!order.route.equals(drawnRoute)) {
            drawRoute();
        }
        binding.trackingStatus.setText(NotificationHelper.stageText(this, order));
        binding.trackingStepper.setStage(stepperStage(order.status));

        String pin = session.pin(order.id);
        boolean showPin = pin != null && order.isActive() && order.hasRealRider();
        binding.trackingPin.setVisibility(showPin ? View.VISIBLE : View.GONE);
        binding.trackingPin.setText(getString(R.string.pin_line, pin));
        if (pin == null && order.isActive() && OrderStore.cloud(this)) {
            // Signed in on a new phone: the PIN is kept in a customer-only cloud document.
            OrderStore.pin(order.id, cloudPin -> {
                if (cloudPin != null) {
                    session.setPin(order.id, cloudPin);
                }
            });
        }

        boolean realRider = order.hasRealRider();
        binding.trackingRider.setVisibility(realRider || order.hasSimRider() ? View.VISIBLE : View.GONE);
        binding.trackingRiderName.setText(getString(R.string.your_rider, order.riderName));
        binding.trackingChat.setVisibility(realRider && order.isActive() ? View.VISIBLE : View.GONE);
        binding.trackingCallRider.setVisibility(realRider && order.isActive() ? View.VISIBLE : View.GONE);
        binding.trackingDemoNote.setVisibility(order.hasSimRider() && order.isActive() ? View.VISIBLE : View.GONE);
        binding.trackingCancel.setVisibility(OrderLogic.canCustomerCancel(order) ? View.VISIBLE : View.GONE);
        binding.trackingRate.setVisibility(order.status == Order.DELIVERED && !order.reviewed ? View.VISIBLE : View.GONE);
        boolean hasPhone = !order.kitchens.isEmpty() && !order.kitchens.get(0).phone.isEmpty();
        binding.trackingCall.setVisibility(hasPhone ? View.VISIBLE : View.GONE);
        renderMovement();
    }

    /** Five dots: placed, accepted, cooking, on the way, delivered. */
    private static int stepperStage(int status) {
        switch (status) {
            case Order.CANCELLED:
                return -1;
            case Order.PLACED:
                return 0;
            case Order.ACCEPTED:
                return 1;
            case Order.PREPARING:
            case Order.READY:
                return 2;
            case Order.PICKED_UP:
            case Order.ARRIVING:
                return 3;
            default:
                return 4;
        }
    }

    private void renderMovement() {
        long now = System.currentTimeMillis();
        LatLon rider = OrderLogic.riderPosition(order, now);
        if (riderMarker != null) {
            if (rider != null) {
                riderMarker.setPosition(new GeoPoint(rider.lat, rider.lon));
                riderMarker.setVisible(true);
            } else {
                riderMarker.setVisible(false);
            }
            binding.trackingMap.invalidate();
        }
        if (!order.isActive()) {
            binding.trackingEta.setVisibility(View.GONE);
            return;
        }
        binding.trackingEta.setVisibility(View.VISIBLE);
        int minutes = OrderLogic.minutesLeft(order, now);
        double meters = OrderLogic.metersLeft(order, now);
        if (meters >= 0) {
            binding.trackingEta.setText(getString(R.string.rider_distance, meters / 1000, minutes));
        } else if (rider != null && !order.kitchens.isEmpty()) {
            double toRestaurant = rider.metersTo(order.kitchens.get(0).location()) / 1000;
            binding.trackingEta.setText(getString(R.string.rider_to_restaurant, toRestaurant, minutes));
        } else {
            binding.trackingEta.setText(getString(R.string.arrives_in, minutes));
        }
    }

    private void drawRoute() {
        drawnRoute = order.route;
        binding.trackingMap.getOverlays().clear();
        List<LatLon> points = order.routePoints();
        List<GeoPoint> geo = new ArrayList<>();
        for (LatLon p : points) {
            geo.add(new GeoPoint(p.lat, p.lon));
        }
        if (geo.isEmpty()) {
            geo.add(new GeoPoint(order.lat, order.lon));
        }
        Polyline line = new Polyline(binding.trackingMap);
        line.setPoints(geo);
        line.getOutlinePaint().setColor(ContextCompat.getColor(this, R.color.amber_dark));
        line.getOutlinePaint().setStrokeWidth(10f);
        binding.trackingMap.getOverlays().add(line);

        for (Order.Kitchen k : order.kitchens) {
            addMarker(new GeoPoint(k.lat, k.lon), R.drawable.ic_map_restaurant, k.name);
        }
        addMarker(new GeoPoint(order.lat, order.lon), R.drawable.ic_map_pin, getString(R.string.marker_you));
        riderMarker = addMarker(geo.get(0), R.drawable.ic_map_rider, getString(R.string.marker_rider));
        riderMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        riderMarker.setVisible(false);

        if (!framed) {
            framed = true;
            if (geo.size() > 1) {
                BoundingBox box = BoundingBox.fromGeoPointsSafe(geo);
                binding.trackingMap.post(() -> binding.trackingMap.zoomToBoundingBox(box, false, MAP_PADDING_PX));
            } else {
                binding.trackingMap.getController().setCenter(geo.get(0));
            }
        }
        binding.trackingMap.invalidate();
    }

    private Marker addMarker(GeoPoint point, int icon, String title) {
        Marker marker = new Marker(binding.trackingMap);
        marker.setPosition(point);
        marker.setIcon(ContextCompat.getDrawable(this, icon));
        marker.setTitle(title);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        binding.trackingMap.getOverlays().add(marker);
        return marker;
    }

    private void callRestaurant() {
        if (order != null && !order.kitchens.isEmpty()) {
            dial(order.kitchens.get(0).phone);
        }
    }

    private void confirmCancel() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cancel_order_title)
                .setMessage(R.string.cancel_order_text)
                .setPositiveButton(R.string.cancel_order, (d, w) -> OrderStore.mutate(this, order.id,
                        o -> OrderLogic.cancel(o, getString(R.string.cancelled_by_customer)), changed -> {
                            if (!changed) {
                                showMessage(getString(R.string.cancel_too_late));
                            }
                        }))
                .setNegativeButton(R.string.keep_order, null)
                .show();
    }

    /** After delivery: stars and a tip for the rider, and a review for the restaurant. */
    private void showRateDialog() {
        DialogRateBinding dialog = DialogRateBinding.inflate(getLayoutInflater());
        dialog.rateRiderTitle.setText(getString(R.string.rate_rider, order.riderName));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rate_delivery)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.save, (d, w) -> {
                    int stars = Math.round(dialog.rateRiderStars.getRating());
                    int tip = tipFor(dialog.rateTips.getCheckedChipId());
                    float foodStars = dialog.rateFoodStars.getRating();
                    String text = String.valueOf(dialog.rateFoodText.getText()).trim();
                    String riderComment = String.valueOf(dialog.rateRiderComment.getText()).trim();
                    saveRating(stars, riderComment, tip, foodStars, text);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static int tipFor(int chipId) {
        if (chipId == R.id.tip_20) {
            return 20;
        }
        if (chipId == R.id.tip_50) {
            return 50;
        }
        if (chipId == R.id.tip_100) {
            return 100;
        }
        return 0;
    }

    private void saveRating(int stars, String riderComment, int tip, float foodStars, String text) {
        String riderUid = order.riderUid;
        OrderStore.mutate(this, order.id, o -> {
            o.reviewed = true;
            o.riderRating = stars;
            o.riderComment = riderComment;
            o.tip = tip;
            return true;
        }, changed -> {
            if (changed && stars > 0 && order.hasRealRider()) {
                FirebaseSync.rateRider(this, riderUid, stars);
            }
        });
        if (foodStars > 0 && !text.isEmpty() && !order.kitchens.isEmpty()) {
            Review review = new Review(UUID.randomUUID().toString(), order.kitchens.get(0).restaurantId,
                    session.name(), foodStars, text, System.currentTimeMillis(), "");
            AppExecutors.io(() -> ReviewRepository.save(this, review));
            FirebaseSync.saveReview(this, review);
        }
        showMessage(getString(tip > 0 ? R.string.thanks_tip : R.string.thanks_rating, tip));
    }

    /** Reads the number from the Contacts content provider; the picker grants access to that one row. */
    private void shareWithContact(Uri contact) {
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor c = getContentResolver().query(contact, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String number = c.getString(0);
                int minutes = OrderLogic.minutesLeft(order, System.currentTimeMillis());
                startImplicit(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
                        .putExtra("sms_body", getString(R.string.share_order_text, minutes)));
            } else {
                showMessage(getString(R.string.contact_no_number));
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(redraw);
        super.onDestroy();
    }
}
