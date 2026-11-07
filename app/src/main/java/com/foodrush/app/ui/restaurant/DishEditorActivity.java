package com.foodrush.app.ui.restaurant;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import com.foodrush.app.R;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityDishEditorBinding;
import com.foodrush.app.databinding.ItemOptionEditBinding;
import com.foodrush.app.model.Dish;
import com.foodrush.app.model.Option;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.ui.PhotoPicker;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.Images;
import com.foodrush.app.util.PhotoEncoder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Add or edit one dish: photo from the camera or gallery, name, description, category, price and
 * discount price, sizes, add-ons, preparation time, spicy level and whether it's available today.
 */
public class DishEditorActivity extends BaseActivity {

    public static final String EXTRA_DISH_ID = "dish_id";
    private static final String[] CATEGORIES = {"Biryani", "Bangla", "Kebab & Grill", "Fish", "Chinese",
            "Fast Food", "Street Food", "Sweets", "Bakery", "Drinks"};

    private ActivityDishEditorBinding binding;
    private MenuRepository menu;
    private String restaurantId;
    private Dish editing;
    private String thumb;
    private String fullPhoto;
    private final List<ItemOptionEditBinding> sizeRows = new ArrayList<>();
    private final List<ItemOptionEditBinding> addonRows = new ArrayList<>();

    private final PhotoPicker photoPicker = new PhotoPicker(this, this::usePhoto);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDishEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        menu = MenuRepository.get(this);
        restaurantId = new Session(this).restaurantId();

        String[] spicy = getResources().getStringArray(R.array.spicy_levels);
        binding.spicy0.setText(spicy[0]);
        binding.spicy1.setText(spicy[1]);
        binding.spicy2.setText(spicy[2]);
        binding.spicy3.setText(spicy[3]);
        binding.spicy0.setChecked(true);
        binding.editorCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, CATEGORIES));

        binding.editorChangePhoto.setOnClickListener(v -> photoPicker.choose());
        binding.editorPhoto.setOnClickListener(v -> photoPicker.choose());
        binding.editorAddSize.setOnClickListener(v -> addRow(binding.editorSizes, sizeRows, null));
        binding.editorAddAddon.setOnClickListener(v -> addRow(binding.editorAddons, addonRows, null));
        binding.editorSave.setOnClickListener(v -> save());
        binding.editorDelete.setOnClickListener(v -> confirmDelete());

        String dishId = getIntent().getStringExtra(EXTRA_DISH_ID);
        if (dishId == null) {
            setUpTopBar(binding.editorBar, R.string.add_dish);
            Images.plate(binding.editorPhoto, null);
        } else {
            setUpTopBar(binding.editorBar, R.string.edit_dish);
            binding.editorDelete.setVisibility(View.VISIBLE);
            menu.dish(dishId, new MenuRepository.Listener<Dish>() {
                @Override
                public void onResult(Dish result) {
                    fill(result);
                }

                @Override
                public void onError() {
                    showMessage(getString(R.string.load_error));
                }
            });
        }
    }

    private void fill(Dish dish) {
        editing = dish;
        binding.editorName.setText(dish.name);
        binding.editorDescription.setText(dish.description);
        binding.editorCategory.setText(dish.category, false);
        binding.editorPrice.setText(String.valueOf(dish.price));
        binding.editorDiscount.setText(dish.discountPrice > 0 ? String.valueOf(dish.discountPrice) : "");
        binding.editorPrep.setText(String.valueOf(dish.prepMinutes));
        int[] spicyIds = {R.id.spicy_0, R.id.spicy_1, R.id.spicy_2, R.id.spicy_3};
        binding.editorSpicy.check(spicyIds[Math.max(0, Math.min(3, dish.spicy))]);
        binding.editorAvailable.setChecked(dish.available);
        for (Option size : dish.sizes) {
            addRow(binding.editorSizes, sizeRows, size);
        }
        for (Option addon : dish.addons) {
            addRow(binding.editorAddons, addonRows, addon);
        }
        Images.plate(binding.editorPhoto, dish.image);
    }

    private void addRow(LinearLayout container, List<ItemOptionEditBinding> rows, Option option) {
        ItemOptionEditBinding row = ItemOptionEditBinding.inflate(getLayoutInflater(), container, true);
        if (option != null) {
            row.optionName.setText(option.name);
            row.optionPrice.setText(String.valueOf(option.price));
        }
        row.optionRemove.setOnClickListener(v -> {
            container.removeView(row.getRoot());
            rows.remove(row);
        });
        rows.add(row);
    }

    /** Shrinks the photo off the main thread, then shows it. */
    private void usePhoto(Uri uri) {
        binding.editorProgress.setVisibility(View.VISIBLE);
        AppExecutors.load(() -> {
            try {
                return PhotoEncoder.encode(this, uri);
            } catch (Exception e) {
                return null;
            }
        }, encoded -> {
            binding.editorProgress.setVisibility(View.GONE);
            if (encoded == null) {
                showMessage(getString(R.string.photo_failed));
                return;
            }
            thumb = encoded.thumb;
            fullPhoto = encoded.full;
            Images.plate(binding.editorPhoto, fullPhoto);
        });
    }

    private static int number(CharSequence text) {
        try {
            return Integer.parseInt(String.valueOf(text).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private List<Option> options(List<ItemOptionEditBinding> rows) {
        List<Option> options = new ArrayList<>();
        for (ItemOptionEditBinding row : rows) {
            String name = String.valueOf(row.optionName.getText()).trim();
            int price = number(row.optionPrice.getText());
            if (!name.isEmpty() && price >= 0) {
                options.add(new Option(name, price));
            }
        }
        return options;
    }

    private int spicy() {
        int checked = binding.editorSpicy.getCheckedChipId();
        return checked == R.id.spicy_1 ? 1 : checked == R.id.spicy_2 ? 2 : checked == R.id.spicy_3 ? 3 : 0;
    }

    private void save() {
        String name = String.valueOf(binding.editorName.getText()).trim();
        String category = binding.editorCategory.getText().toString().trim();
        int price = number(binding.editorPrice.getText());
        String discountText = String.valueOf(binding.editorDiscount.getText()).trim();
        int discount = discountText.isEmpty() ? 0 : number(discountText);
        int prep = number(binding.editorPrep.getText());
        boolean nameOk = !name.isEmpty();
        boolean priceOk = price > 0;
        boolean discountOk = discount == 0 || discount > 0 && discount < price;
        binding.editorNameLayout.setError(nameOk ? null : getString(R.string.error_dish_name));
        binding.editorCategoryLayout.setError(category.isEmpty() ? getString(R.string.error_category) : null);
        binding.editorPriceLayout.setError(priceOk ? null : getString(R.string.error_price));
        binding.editorDiscountLayout.setError(discountOk ? null : getString(R.string.error_discount));
        if (!nameOk || category.isEmpty() || !priceOk || !discountOk) {
            return;
        }
        String id = editing != null ? editing.id : restaurantId + "_" + UUID.randomUUID().toString().substring(0, 8);
        String image = thumb != null ? thumb : editing != null ? editing.image : null;
        boolean hasPhoto = fullPhoto != null || editing != null && editing.hasPhoto;
        Dish dish = new Dish(id, restaurantId, name, String.valueOf(binding.editorDescription.getText()).trim(),
                category, price, discount, image, hasPhoto, editing != null ? editing.rating : 4.5f,
                prep > 0 ? prep : 15, spicy(), binding.editorAvailable.isChecked(), options(sizeRows),
                options(addonRows));
        binding.editorSave.setEnabled(false);
        binding.editorProgress.setVisibility(View.VISIBLE);
        menu.saveDish(dish, fullPhoto, new MenuRepository.Listener<Void>() {
            @Override
            public void onResult(Void result) {
                showMessage(getString(R.string.dish_saved));
                finish();
            }

            @Override
            public void onError() {
                binding.editorSave.setEnabled(true);
                binding.editorProgress.setVisibility(View.GONE);
                showMessage(getString(R.string.save_failed));
            }
        });
        addCategory(category);
    }

    /** Keeps the restaurant's category list (shown to customers) in step with its dishes. */
    private void addCategory(String category) {
        menu.restaurant(restaurantId, new MenuRepository.Listener<com.foodrush.app.model.Restaurant>() {
            @Override
            public void onResult(com.foodrush.app.model.Restaurant restaurant) {
                if (!restaurant.categories.contains(category)) {
                    restaurant.categories = new ArrayList<>(restaurant.categories);
                    restaurant.categories.add(category);
                    menu.saveRestaurant(restaurant, new MenuRepository.Listener<Void>() {
                        @Override
                        public void onResult(Void result) {
                        }

                        @Override
                        public void onError() {
                        }
                    });
                }
            }

            @Override
            public void onError() {
            }
        });
    }

    private void confirmDelete() {
        if (editing == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_dish_title, editing.name))
                .setPositiveButton(R.string.delete, (d, w) -> menu.deleteDish(editing, new MenuRepository.Listener<Void>() {
                    @Override
                    public void onResult(Void result) {
                        finish();
                    }

                    @Override
                    public void onError() {
                        showMessage(getString(R.string.save_failed));
                    }
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    static List<String> categories() {
        return Arrays.asList(CATEGORIES);
    }
}
