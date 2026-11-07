package com.foodrush.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.foodrush.app.R;
import com.foodrush.app.data.CartRepository;
import com.foodrush.app.data.FavouriteRepository;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityDishBinding;
import com.foodrush.app.databinding.ItemExtraBinding;
import com.foodrush.app.model.CartItem;
import com.foodrush.app.model.Dish;
import com.foodrush.app.model.Option;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.Images;
import com.foodrush.app.util.PriceCalculator;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** One dish: pick a size and add-ons, then add to cart. Next shows the other dishes of the list it came from. */
public class DishActivity extends BaseActivity {

    private static final String EXTRA_IDS = "dish_ids";
    private static final String EXTRA_INDEX = "index";
    private static final String STATE_INDEX = "state_index";
    private static final String STATE_SIZE = "state_size";

    private ActivityDishBinding binding;
    private MenuRepository menu;
    private final List<Dish> dishes = new ArrayList<>();
    private String[] ids;
    private int index;
    private int sizeIndex;
    private final List<ItemExtraBinding> addonRows = new ArrayList<>();

    /** Only ids travel in the intent; the dishes themselves (photos included) stay in the repository. */
    public static void open(Context context, List<Dish> list, int index) {
        if (index < 0 || index >= list.size()) {
            return;
        }
        String[] ids = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            ids[i] = list.get(i).id;
        }
        context.startActivity(new Intent(context, DishActivity.class)
                .putExtra(EXTRA_IDS, ids)
                .putExtra(EXTRA_INDEX, index));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDishBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        menu = MenuRepository.get(this);

        ids = getIntent().getStringArrayExtra(EXTRA_IDS);
        if (ids == null || ids.length == 0) {
            finish();
            return;
        }
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (savedInstanceState != null) {
            index = savedInstanceState.getInt(STATE_INDEX, index);
            sizeIndex = savedInstanceState.getInt(STATE_SIZE, 0);
        }

        binding.dishClose.setOnClickListener(v -> finish());
        binding.dishCart.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));
        binding.dishQuantity.setOnChangeListener(value -> renderPrice());
        binding.dishAdd.setOnClickListener(v -> addToCart());
        binding.dishFavourite.setOnClickListener(v -> toggleFavourite());
        binding.dishShare.setOnClickListener(v -> share());
        binding.dishNext.setVisibility(ids.length > 1 ? View.VISIBLE : View.GONE);
        binding.dishNext.setOnClickListener(v -> {
            index = (index + 1) % ids.length;
            sizeIndex = 0;
            binding.dishQuantity.setValue(1);
            showCurrent();
        });
        showCurrent();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_INDEX, index);
        outState.putInt(STATE_SIZE, sizeIndex);
    }

    private Dish dish() {
        return dishes.isEmpty() ? null : dishes.get(0);
    }

    private void showCurrent() {
        String id = ids[Math.min(index, ids.length - 1)];
        menu.dish(id, new MenuRepository.Listener<Dish>() {
            @Override
            public void onResult(Dish result) {
                if (!id.equals(ids[Math.min(index, ids.length - 1)])) {
                    return;
                }
                dishes.clear();
                dishes.add(result);
                renderDish(result);
            }

            @Override
            public void onError() {
                showMessage(getString(R.string.load_error));
            }
        });
    }

    private void renderDish(Dish dish) {
        binding.dishName.setText(dish.name);
        binding.dishRating.setText(String.format(Locale.US, "%.1f", dish.rating));
        binding.dishDescription.setText(dish.description);
        binding.dishMeta.setText(getString(R.string.dish_meta, dish.prepMinutes, spicyLabel(dish.spicy)));
        Images.plate(binding.dishImage, dish.image);
        if (dish.hasPhoto) {
            // The thumbnail shows at once; the restaurant's full photo replaces it when it arrives.
            menu.photo(dish.id, new MenuRepository.Listener<String>() {
                @Override
                public void onResult(String data) {
                    if (dish() != null && dish().id.equals(dish.id)) {
                        Images.plate(binding.dishImage, data);
                    }
                }

                @Override
                public void onError() {
                }
            });
        }

        Restaurant restaurant = menu.restaurant(dish.restaurantId);
        if (restaurant != null) {
            binding.dishRestaurant.setText(getString(R.string.from_restaurant, restaurant.name));
            binding.dishRestaurant.setOnClickListener(v -> startActivity(new Intent(this, RestaurantActivity.class)
                    .putExtra(RestaurantActivity.EXTRA_RESTAURANT_ID, restaurant.id)));
        }

        binding.dishSizes.removeAllViews();
        binding.dishSizesTitle.setVisibility(dish.sizes.isEmpty() ? View.GONE : View.VISIBLE);
        binding.dishSizes.setVisibility(dish.sizes.isEmpty() ? View.GONE : View.VISIBLE);
        sizeIndex = Math.min(sizeIndex, Math.max(0, dish.sizes.size() - 1));
        for (int i = 0; i < dish.sizes.size(); i++) {
            Option size = dish.sizes.get(i);
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setCheckable(true);
            chip.setText(getString(R.string.size_chip, size.name, PriceCalculator.format(dish.priceFor(size))));
            chip.setChecked(i == sizeIndex);
            int position = i;
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    sizeIndex = position;
                    renderPrice();
                }
            });
            binding.dishSizes.addView(chip);
        }

        binding.dishExtras.removeAllViews();
        addonRows.clear();
        binding.dishExtrasTitle.setVisibility(dish.addons.isEmpty() ? View.GONE : View.VISIBLE);
        for (Option addon : dish.addons) {
            ItemExtraBinding row = ItemExtraBinding.inflate(getLayoutInflater(), binding.dishExtras, true);
            row.extraName.setText(addon.name);
            row.extraPrice.setText("+" + PriceCalculator.format(addon.price));
            row.extraStepper.setOnChangeListener(value -> renderPrice());
            addonRows.add(row);
        }

        boolean orderable = dish.available && (restaurant == null || restaurant.isOpenAt(Calendar.getInstance()));
        binding.dishAdd.setEnabled(orderable);
        AppExecutors.load(() -> FavouriteRepository.ids(this).contains(dish.id), this::renderFavourite);
        renderPrice();
        if (!dish.available) {
            binding.dishAdd.setText(R.string.sold_out);
        } else if (!orderable) {
            binding.dishAdd.setText(R.string.closed_now);
        }
    }

    private String spicyLabel(int spicy) {
        String[] labels = getResources().getStringArray(R.array.spicy_levels);
        return labels[Math.max(0, Math.min(labels.length - 1, spicy))];
    }

    private void renderFavourite(boolean favourite) {
        binding.dishFavourite.setImageResource(favourite ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
    }

    private Option size() {
        Dish dish = dish();
        return dish == null || dish.sizes.isEmpty() ? null : dish.sizes.get(Math.min(sizeIndex, dish.sizes.size() - 1));
    }

    private int unitPrice() {
        Dish dish = dish();
        if (dish == null) {
            return 0;
        }
        int price = dish.priceFor(size());
        for (int i = 0; i < addonRows.size() && i < dish.addons.size(); i++) {
            price += addonRows.get(i).extraStepper.getValue() * dish.addons.get(i).price;
        }
        return price;
    }

    private void renderPrice() {
        Dish dish = dish();
        if (dish == null) {
            return;
        }
        binding.dishPrice.setText(PriceCalculator.format(unitPrice()));
        if (binding.dishAdd.isEnabled()) {
            binding.dishAdd.setText(getString(R.string.add_to_cart_price,
                    PriceCalculator.format(unitPrice() * binding.dishQuantity.getValue())));
        }
    }

    private String extrasText() {
        Dish dish = dish();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < addonRows.size() && dish != null && i < dish.addons.size(); i++) {
            int count = addonRows.get(i).extraStepper.getValue();
            if (count > 0) {
                parts.add((count > 1 ? count + "× " : "") + dish.addons.get(i).name);
            }
        }
        return String.join(", ", parts);
    }

    private void addToCart() {
        Dish dish = dish();
        if (dish == null) {
            return;
        }
        Restaurant restaurant = menu.restaurant(dish.restaurantId);
        if (restaurant != null && !MenuRepository.delivers(restaurant, new Session(this).addressPoint())) {
            showMessage(getString(R.string.too_far, restaurant.name));
            return;
        }
        Option size = size();
        CartItem item = new CartItem(0, dish.id, dish.name, dish.image, dish.restaurantId,
                size == null ? "" : size.name, extrasText(), unitPrice(), binding.dishQuantity.getValue(), dish.prepMinutes);
        AppExecutors.load(() -> {
            CartRepository.add(this, item);
            return true;
        }, done -> Snackbar.make(binding.getRoot(), R.string.added_to_cart, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.dishBottom)
                .setAction(R.string.view_cart, v -> startActivity(new Intent(this, CartActivity.class)))
                .show());
    }

    private void toggleFavourite() {
        Dish dish = dish();
        if (dish != null) {
            AppExecutors.load(() -> FavouriteRepository.toggle(this, dish), this::renderFavourite);
        }
    }

    private void share() {
        Dish dish = dish();
        if (dish == null) {
            return;
        }
        Restaurant restaurant = menu.restaurant(dish.restaurantId);
        String where = restaurant == null ? "" : restaurant.name + ", " + restaurant.areaLabel();
        startImplicit(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_dish_text, dish.name, where,
                        PriceCalculator.format(dish.currentPrice()))), null));
    }
}
