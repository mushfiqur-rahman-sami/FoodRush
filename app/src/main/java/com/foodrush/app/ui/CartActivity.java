package com.foodrush.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.foodrush.app.R;
import com.foodrush.app.adapter.CartAdapter;
import com.foodrush.app.data.CartRepository;
import com.foodrush.app.data.FirebaseSync;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityCartBinding;
import com.foodrush.app.model.CartItem;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Promo;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.route.Polylines;
import com.foodrush.app.route.RoutePlanner;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.Bangladesh;
import com.foodrush.app.util.PriceCalculator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cart: items, address, time, payment and coupon, with an estimated bill. The real road route
 * starts working out in the background here, so checkout usually has it ready.
 */
public class CartActivity extends BaseActivity {

    private static final String STATE_DELAY = "delay_minutes";

    private ActivityCartBinding binding;
    private Session session;
    private MenuRepository menu;
    private CartAdapter adapter;
    private List<CartItem> items = new ArrayList<>();
    private final Map<String, Restaurant> restaurants = new LinkedHashMap<>();
    private PriceCalculator.Quote quote;
    private Promo promo;
    /** 0 for as soon as possible; otherwise the user chose a later time. */
    private int delayMinutes;
    private boolean restaurantsLoaded;

    private final ActivityResultLauncher<Intent> addressPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    session.setAddress(data.getStringExtra(AddressPickerActivity.EXTRA_LABEL),
                            new LatLon(data.getDoubleExtra(AddressPickerActivity.EXTRA_LAT, 0),
                                    data.getDoubleExtra(AddressPickerActivity.EXTRA_LON, 0)));
                    load();
                }
            });

    private final ActivityResultLauncher<Intent> paymentPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    session.setPayment(data.getStringExtra(PaymentActivity.EXTRA_LABEL));
                    render();
                }
            });

    private final ActivityResultLauncher<Intent> checkout = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCartBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        session = new Session(this);
        menu = MenuRepository.get(this);
        if (savedInstanceState != null) {
            delayMinutes = savedInstanceState.getInt(STATE_DELAY);
        }

        adapter = new CartAdapter(new CartAdapter.Listener() {
            @Override
            public void onQuantity(CartItem item, int quantity) {
                AppExecutors.load(() -> {
                    CartRepository.setQuantity(CartActivity.this, item.rowId, quantity);
                    return true;
                }, done -> load());
            }

            @Override
            public void onRemove(CartItem item) {
                confirmRemove(item);
            }
        });
        binding.cartItems.setLayoutManager(new LinearLayoutManager(this));
        binding.cartItems.setAdapter(adapter);

        binding.cartAddress.infoIcon.setImageResource(R.drawable.ic_pin);
        binding.cartTime.infoIcon.setImageResource(R.drawable.ic_clock);
        binding.cartPayment.infoIcon.setImageResource(R.drawable.ic_wallet);

        binding.cartClose.setOnClickListener(v -> finish());
        binding.cartBrowse.setOnClickListener(v -> finish());
        binding.cartAddress.getRoot().setOnClickListener(v ->
                addressPicker.launch(new Intent(this, AddressPickerActivity.class)
                        .putExtra(AddressPickerActivity.EXTRA_RETURN_RESULT, true)));
        binding.cartPayment.getRoot().setOnClickListener(v ->
                paymentPicker.launch(new Intent(this, PaymentActivity.class)));
        binding.cartTime.getRoot().setOnClickListener(v -> chooseTime());
        binding.cartCouponApply.setOnClickListener(v -> applyCoupon(binding.cartCoupon.getText().toString(), true));
        binding.cartPlace.setText(R.string.proceed_to_checkout);
        binding.cartPlace.setOnClickListener(v -> proceed());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_DELAY, delayMinutes);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        AppExecutors.load(() -> CartRepository.all(this), loaded -> {
            items = loaded;
            adapter.submit(items);
            render();
            if (items.isEmpty()) {
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
                    restaurantsLoaded = true;
                    // Get a head start on the road route; checkout picks up the same work.
                    if (!restaurants.isEmpty()) {
                        new RoutePlanner(CartActivity.this).planAsync(CartCheck.pickups(restaurants),
                                session.addressPoint());
                    }
                    if (promo == null && session.coupon() != null) {
                        applyCoupon(session.coupon(), false);
                    }
                    render();
                }

                @Override
                public void onError() {
                    showMessage(getString(R.string.load_error));
                }
            });
        });
    }

    /** Road km for the fee: the real route if it's ready, else straight lines between the stops plus 40%. */
    private double estimatedKm() {
        List<LatLon> pickups = CartCheck.pickups(restaurants);
        LatLon customer = session.addressPoint();
        RoutePlanner.Result known = RoutePlanner.cached(pickups, customer);
        if (known != null) {
            return known.meters / 1000;
        }
        List<LatLon> stops = new ArrayList<>(pickups);
        stops.add(customer);
        return Polylines.length(stops) * 1.4 / 1000;
    }

    private void render() {
        boolean empty = items.isEmpty();
        binding.cartEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.cartContent.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.cartPlace.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            return;
        }
        binding.cartAddress.infoText.setText(session.addressLabel());
        binding.cartPayment.infoText.setText(session.payment());
        if (delayMinutes == 30) {
            binding.cartTime.infoText.setText(R.string.eta_scheduled_30);
        } else if (delayMinutes == 60) {
            binding.cartTime.infoText.setText(R.string.eta_scheduled_60);
        } else {
            binding.cartTime.infoText.setText(R.string.time_asap);
        }

        int subtotal = CartCheck.subtotal(items);
        int fee = restaurants.isEmpty() ? 0 : PriceCalculator.deliveryFee(estimatedKm());
        if (promo != null) {
            int eligible = promo.restaurantId == null ? subtotal : CartCheck.subtotalFor(items, promo.restaurantId);
            quote = PriceCalculator.quote(subtotal, eligible, fee, promo.percent, promo.flat, promo.freeDelivery,
                    promo.minOrder);
        } else {
            quote = PriceCalculator.plain(subtotal, fee);
        }
        binding.cartSubtotal.setText(PriceCalculator.format(quote.subtotal));
        binding.cartDiscountRow.setVisibility(quote.discount > 0 ? View.VISIBLE : View.GONE);
        binding.cartDiscount.setText(getString(R.string.minus_amount, PriceCalculator.format(quote.discount)));
        binding.cartVat.setText(PriceCalculator.format(quote.vat));
        binding.cartFee.setText(getString(R.string.about_amount, PriceCalculator.format(quote.fee)));
        binding.cartTotal.setText(getString(R.string.about_amount, PriceCalculator.format(quote.total)));
        binding.cartRouteStatus.setVisibility(View.VISIBLE);
        binding.cartRouteStatus.setText(R.string.fee_final_at_checkout);
        renderCouponStatus();

        String problem = restaurantsLoaded ? CartCheck.problem(this, items, restaurants, session.addressPoint()) : null;
        binding.cartProblem.setVisibility(problem == null ? View.GONE : View.VISIBLE);
        binding.cartProblem.setText(problem);
        binding.cartPlace.setEnabled(restaurantsLoaded && problem == null);
    }

    private void renderCouponStatus() {
        if (promo == null) {
            return;
        }
        binding.cartCouponStatus.setVisibility(View.VISIBLE);
        boolean forCart = promo.restaurantId == null || restaurants.containsKey(promo.restaurantId);
        boolean applies = forCart && (quote.discount > 0 || promo.freeDelivery && quote.fee == 0);
        String message;
        if (!forCart) {
            Restaurant owner = menu.restaurant(promo.restaurantId);
            message = getString(R.string.coupon_other_restaurant, promo.code, owner == null ? "" : owner.name);
        } else if (applies) {
            message = getString(R.string.coupon_applied, promo.code);
        } else {
            message = getString(R.string.coupon_below_min, promo.code, promo.subtitle);
        }
        binding.cartCouponStatus.setText(message);
        binding.cartCouponStatus.setTextColor(getColor(applies ? R.color.amber_dark : R.color.error));
        if (binding.cartCoupon.getText().length() == 0) {
            binding.cartCoupon.setText(promo.code);
        }
    }

    private void applyCoupon(String code, boolean userAction) {
        menu.findCoupon(code, new MenuRepository.Listener<Promo>() {
            @Override
            public void onResult(Promo found) {
                if (found == null || !found.usableAt(System.currentTimeMillis())) {
                    promo = null;
                    session.setCoupon(null);
                    render();
                    if (userAction) {
                        binding.cartCouponStatus.setVisibility(View.VISIBLE);
                        binding.cartCouponStatus.setText(R.string.coupon_invalid);
                        binding.cartCouponStatus.setTextColor(getColor(R.color.error));
                    }
                    return;
                }
                promo = found;
                session.setCoupon(found.code);
                render();
            }

            @Override
            public void onError() {
                showMessage(getString(R.string.load_error));
            }
        });
    }

    private void chooseTime() {
        String[] options = {getString(R.string.time_asap), getString(R.string.time_30), getString(R.string.time_60)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delivery_time_title)
                .setItems(options, (d, which) -> {
                    delayMinutes = which * 30;
                    render();
                })
                .show();
    }

    private void confirmRemove(CartItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.remove_item_title, item.name))
                .setPositiveButton(R.string.remove, (d, w) -> AppExecutors.load(() -> {
                    CartRepository.remove(this, item.rowId);
                    return true;
                }, done -> load()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void proceed() {
        if (items.isEmpty() || !restaurantsLoaded) {
            return;
        }
        String problem = CartCheck.problem(this, items, restaurants, session.addressPoint());
        if (problem != null) {
            showMessage(problem);
            return;
        }
        if (Bangladesh.normalizePhone(session.phone()) == null) {
            askPhone();
            return;
        }
        checkout.launch(new Intent(this, CheckoutActivity.class)
                .putExtra(CheckoutActivity.EXTRA_DELAY_MINUTES, delayMinutes));
    }

    /** Riders and restaurants need a number to reach the customer. */
    private void askPhone() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setHint("01XXXXXXXXX");
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.phone_needed_title)
                .setMessage(R.string.phone_needed_text)
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String phone = Bangladesh.normalizePhone(input.getText().toString());
                    if (phone == null) {
                        showMessage(getString(R.string.error_phone));
                        return;
                    }
                    session.updateProfile(session.name(), phone);
                    FirebaseSync.saveUser(this, session.uid(), session.name(), session.email(), phone, session.role(),
                            session.restaurantId());
                    proceed();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
