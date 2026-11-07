package com.foodrush.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.foodrush.app.R;
import com.foodrush.app.adapter.RestaurantAdapter;
import com.foodrush.app.adapter.RestaurantPageAdapter;
import com.foodrush.app.data.FirebaseSync;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.ReviewRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityRestaurantBinding;
import com.foodrush.app.databinding.DialogReviewBinding;
import com.foodrush.app.model.Dish;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Promo;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.model.Review;
import com.foodrush.app.route.RoutePlanner;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.Images;
import com.foodrush.app.util.PriceCalculator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class RestaurantActivity extends BaseActivity {

    public static final String EXTRA_RESTAURANT_ID = "restaurant_id";

    private ActivityRestaurantBinding binding;
    private Restaurant restaurant;
    private RestaurantPageAdapter adapter;
    private final List<Dish> dishes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRestaurantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.restaurantBack.setOnClickListener(v -> finish());

        adapter = new RestaurantPageAdapter(new RestaurantPageAdapter.Listener() {
            @Override
            public void onDish(Dish dish, int dishIndex) {
                DishActivity.open(RestaurantActivity.this, dishes, dishIndex);
            }

            @Override
            public void onAddReview() {
                showReviewDialog();
            }

            @Override
            public void onCoupon(Promo coupon) {
                new Session(RestaurantActivity.this).setCoupon(coupon.code);
                showMessage(getString(R.string.coupon_copied, coupon.code));
            }
        });
        binding.restaurantList.setLayoutManager(new LinearLayoutManager(this));
        binding.restaurantList.setAdapter(adapter);
        adapter.setReviews(new ArrayList<>());

        MenuRepository.get(this).restaurant(getIntent().getStringExtra(EXTRA_RESTAURANT_ID),
                new MenuRepository.Listener<Restaurant>() {
                    @Override
                    public void onResult(Restaurant result) {
                        restaurant = result;
                        show();
                    }

                    @Override
                    public void onError() {
                        finish();
                    }
                });
    }

    private void show() {
        binding.restaurantName.setText(restaurant.name);
        binding.restaurantCuisine.setText(String.format(Locale.US, "%s · %.1f ★ (%d)", restaurant.cuisine,
                restaurant.rating, restaurant.ratingCount));
        binding.restaurantAddress.setText(restaurant.address.isEmpty() ? restaurant.areaLabel() : restaurant.address);
        Images.cover(binding.restaurantImage, restaurant.image);
        binding.restaurantDescription.setText(restaurant.description);
        binding.restaurantDescription.setVisibility(TextUtils.isEmpty(restaurant.description) ? View.GONE : View.VISIBLE);

        LatLon customer = new Session(this).addressPoint();
        boolean open = restaurant.isOpenAt(Calendar.getInstance());
        if (!MenuRepository.delivers(restaurant, customer)) {
            binding.restaurantMeta.setText(getString(R.string.too_far, restaurant.name));
        } else if (restaurant.paused) {
            binding.restaurantMeta.setText(R.string.not_taking_orders);
        } else if (!open) {
            binding.restaurantMeta.setText(R.string.closed_now);
        } else {
            binding.restaurantMeta.setText(R.string.eta_working);
            AppExecutors.load(() -> new RoutePlanner(this).minutesToCustomer(customer,
                    Collections.singletonList(restaurant.location()))[0], minutes -> {
                double km = restaurant.location().metersTo(customer) * 1.3 / 1000;
                String meta = getString(R.string.delivery_meta, RestaurantAdapter.etaRange(minutes + restaurant.busyMinutes),
                        PriceCalculator.format(PriceCalculator.deliveryFee(km)));
                binding.restaurantMeta.setText(restaurant.busyMinutes > 0
                        ? getString(R.string.busy_customer_note, meta, restaurant.busyMinutes) : meta);
            });
        }
        binding.restaurantHours.setText(getString(R.string.min_order_line, PriceCalculator.format(restaurant.minOrder)));
        binding.restaurantCall.setVisibility(restaurant.phone.isEmpty() ? View.GONE : View.VISIBLE);
        binding.restaurantCall.setOnClickListener(v -> dial(restaurant.phone));
        binding.restaurantDirections.setOnClickListener(v -> openInMaps());
        binding.restaurantShare.setOnClickListener(v -> startImplicit(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_restaurant_text, restaurant.name,
                        restaurant.areaLabel())), null)));

        MenuRepository menu = MenuRepository.get(this);
        menu.dishesOf(Collections.singletonList(restaurant.id), true, new MenuRepository.Listener<List<Dish>>() {
            @Override
            public void onResult(List<Dish> result) {
                dishes.clear();
                // Available dishes first, each category together.
                for (Dish dish : result) {
                    if (dish.available) {
                        dishes.add(dish);
                    }
                }
                for (Dish dish : result) {
                    if (!dish.available) {
                        dishes.add(dish);
                    }
                }
                Collections.sort(dishes, (a, b) -> a.category.compareTo(b.category));
                adapter.setDishes(dishes);
            }

            @Override
            public void onError() {
                showMessage(getString(R.string.load_error));
            }
        });
        menu.coupons(restaurant.id, new MenuRepository.Listener<List<Promo>>() {
            @Override
            public void onResult(List<Promo> result) {
                List<Promo> usable = new ArrayList<>();
                for (Promo promo : result) {
                    if (promo.usableAt(System.currentTimeMillis())) {
                        usable.add(promo);
                    }
                }
                adapter.setCoupons(usable);
            }

            @Override
            public void onError() {
            }
        });
        loadReviews();
        FirebaseSync.pullReviews(this, restaurant.id, this::loadReviews);
    }

    /** Hands the spot to whatever maps app the phone has, through an implicit geo: intent. */
    private void openInMaps() {
        Uri uri = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)", restaurant.lat, restaurant.lon,
                restaurant.lat, restaurant.lon, Uri.encode(restaurant.name)));
        startImplicit(new Intent(Intent.ACTION_VIEW, uri));
    }

    private void loadReviews() {
        AppExecutors.load(() -> ReviewRepository.forRestaurant(this, restaurant.id), adapter::setReviews);
    }

    private void showReviewDialog() {
        DialogReviewBinding dialog = DialogReviewBinding.inflate(getLayoutInflater());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.review_title)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.save, (d, w) -> {
                    float stars = dialog.reviewInputStars.getRating();
                    String text = String.valueOf(dialog.reviewInputText.getText()).trim();
                    if (stars <= 0 || text.isEmpty()) {
                        showMessage(getString(R.string.review_error));
                        return;
                    }
                    Review review = new Review(UUID.randomUUID().toString(), restaurant.id, new Session(this).name(),
                            stars, text, System.currentTimeMillis(), "");
                    AppExecutors.load(() -> {
                        ReviewRepository.save(this, review);
                        return true;
                    }, done -> loadReviews());
                    FirebaseSync.saveReview(this, review);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
