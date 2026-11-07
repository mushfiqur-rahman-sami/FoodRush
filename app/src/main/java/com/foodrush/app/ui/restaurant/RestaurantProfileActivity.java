package com.foodrush.app.ui.restaurant;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.foodrush.app.R;
import com.foodrush.app.data.FirebaseSync;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityRestaurantProfileBinding;
import com.foodrush.app.databinding.ItemHoursRowBinding;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.ui.AddressPickerActivity;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.ui.PhotoPicker;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.Bangladesh;
import com.foodrush.app.util.Images;
import com.foodrush.app.util.PhotoEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The restaurant's public page, edited by its owner: cover and logo photos, name, cuisine,
 * description, phone, map pin, opening hours for each day, minimum order and delivery radius.
 * With {@link #EXTRA_CREATE}, it adds a brand new restaurant for this account.
 */
public class RestaurantProfileActivity extends BaseActivity {

    public static final String EXTRA_CREATE = "create";
    /** Shown Saturday first, as the Bangladeshi week runs; stored Sunday first. */
    private static final int[] DISPLAY_ORDER = {6, 0, 1, 2, 3, 4, 5};

    private ActivityRestaurantProfileBinding binding;
    private Session session;
    private Restaurant restaurant;
    private boolean creating;
    private boolean pickingLogo;
    private final List<ItemHoursRowBinding> hourRows = new ArrayList<>();
    private final List<String> hours = new ArrayList<>();

    private final PhotoPicker photoPicker = new PhotoPicker(this, this::usePhoto);

    private final ActivityResultLauncher<Intent> locationPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null && restaurant != null) {
                    restaurant.address = data.getStringExtra(AddressPickerActivity.EXTRA_LABEL);
                    restaurant.lat = data.getDoubleExtra(AddressPickerActivity.EXTRA_LAT, restaurant.lat);
                    restaurant.lon = data.getDoubleExtra(AddressPickerActivity.EXTRA_LON, restaurant.lon);
                    binding.rpLocation.infoText.setText(restaurant.address);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRestaurantProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        session = new Session(this);
        creating = getIntent().getBooleanExtra(EXTRA_CREATE, false);
        setUpTopBar(binding.profileBar, creating ? R.string.claim_new : R.string.restaurant_profile_title);

        binding.rpLocation.infoIcon.setImageResource(R.drawable.ic_pin);
        binding.rpLocation.getRoot().setOnClickListener(v -> pickLocation());
        binding.rpChangeCover.setOnClickListener(v -> {
            pickingLogo = false;
            photoPicker.choose();
        });
        binding.rpChangeLogo.setOnClickListener(v -> {
            pickingLogo = true;
            photoPicker.choose();
        });
        binding.rpSave.setOnClickListener(v -> save());

        if (creating) {
            Restaurant fresh = new Restaurant();
            fresh.id = "own_" + UUID.randomUUID().toString().substring(0, 12);
            fresh.lat = session.addressPoint().lat;
            fresh.lon = session.addressPoint().lon;
            fresh.address = "";
            fresh.city = "Khulna";
            fresh.phone = session.phone();
            fresh.ownerUid = session.uid();
            fill(fresh);
        } else {
            MenuRepository.get(this).restaurant(session.restaurantId(), new MenuRepository.Listener<Restaurant>() {
                @Override
                public void onResult(Restaurant result) {
                    fill(result);
                }

                @Override
                public void onError() {
                    showMessage(getString(R.string.load_error));
                }
            });
        }
    }

    private void fill(Restaurant r) {
        restaurant = r;
        binding.rpName.setText(r.name);
        binding.rpCuisine.setText(r.cuisine);
        binding.rpDescription.setText(r.description);
        binding.rpPhone.setText(r.phone == null ? "" : r.phone.replace("+880", "0"));
        binding.rpCity.setText(r.city);
        binding.rpArea.setText(r.area);
        binding.rpMin.setText(String.valueOf(r.minOrder));
        binding.rpRadius.setText(String.valueOf(r.radiusKm));
        binding.rpLocation.infoText.setText(r.address == null || r.address.isEmpty()
                ? getString(R.string.pick_on_map) : r.address);
        Images.cover(binding.rpCover, r.image);
        Images.plate(binding.rpLogo, r.logo != null ? r.logo : r.image);

        hours.clear();
        hours.addAll(r.hours);
        binding.rpHours.removeAllViews();
        hourRows.clear();
        String[] days = getResources().getStringArray(R.array.days_sunday_first);
        for (int day : DISPLAY_ORDER) {
            ItemHoursRowBinding row = ItemHoursRowBinding.inflate(getLayoutInflater(), binding.rpHours, true);
            row.hoursOpen.setText(days[day]);
            hourRows.add(row);
            renderDay(row, day);
            row.hoursRange.setOnClickListener(v -> pickHours(row, day));
        }
    }

    private void renderDay(ItemHoursRowBinding row, int day) {
        String range = hours.get(day);
        boolean open = Restaurant.parseRange(range) != null;
        row.hoursOpen.setOnCheckedChangeListener(null);
        row.hoursOpen.setChecked(open);
        row.hoursOpen.setOnCheckedChangeListener((button, checked) -> {
            hours.set(day, checked ? "10:00-23:00" : "");
            renderDay(row, day);
        });
        row.hoursRange.setEnabled(open);
        row.hoursRange.setText(open ? range.replace("-", " – ") : getString(R.string.closed_day));
    }

    /** Opening time, then closing time. A closing time before the opening one runs past midnight. */
    private void pickHours(ItemHoursRowBinding row, int day) {
        int[] current = Restaurant.parseRange(hours.get(day));
        int open = current == null ? 600 : current[0];
        int close = current == null ? 1380 : current[1];
        TimePickerDialog opening = new TimePickerDialog(this, (picker, openHour, openMinute) -> {
            int openAt = openHour * 60 + openMinute;
            TimePickerDialog closing = new TimePickerDialog(this, (p, closeHour, closeMinute) -> {
                hours.set(day, Restaurant.formatRange(openAt, closeHour * 60 + closeMinute));
                renderDay(row, day);
            }, close / 60, close % 60, true);
            closing.setTitle(R.string.closing_time);
            closing.show();
        }, open / 60, open % 60, true);
        opening.setTitle(R.string.opening_time);
        opening.show();
    }

    private void pickLocation() {
        if (restaurant == null) {
            return;
        }
        locationPicker.launch(new Intent(this, AddressPickerActivity.class)
                .putExtra(AddressPickerActivity.EXTRA_RETURN_RESULT, true)
                .putExtra(AddressPickerActivity.EXTRA_TITLE, R.string.restaurant_location)
                .putExtra(AddressPickerActivity.EXTRA_START_LAT, restaurant.lat)
                .putExtra(AddressPickerActivity.EXTRA_START_LON, restaurant.lon)
                .putExtra(AddressPickerActivity.EXTRA_LABEL, restaurant.address));
    }

    private void usePhoto(Uri uri) {
        boolean logo = pickingLogo;
        binding.rpProgress.setVisibility(View.VISIBLE);
        AppExecutors.load(() -> {
            try {
                return PhotoEncoder.encode(this, uri);
            } catch (Exception e) {
                return null;
            }
        }, encoded -> {
            binding.rpProgress.setVisibility(View.GONE);
            if (encoded == null || restaurant == null) {
                showMessage(getString(R.string.photo_failed));
                return;
            }
            // Every customer loads covers, so they stay medium sized; logos only need the thumbnail.
            if (logo) {
                restaurant.logo = encoded.thumb;
                Images.plate(binding.rpLogo, restaurant.logo);
            } else {
                restaurant.image = encoded.medium;
                Images.cover(binding.rpCover, restaurant.image);
            }
        });
    }

    private static int number(CharSequence text, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(text).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save() {
        if (restaurant == null) {
            return;
        }
        String name = String.valueOf(binding.rpName.getText()).trim();
        String phoneText = String.valueOf(binding.rpPhone.getText()).trim();
        String phone = phoneText.isEmpty() ? "" : Bangladesh.normalizePhone(phoneText);
        String city = String.valueOf(binding.rpCity.getText()).trim();
        int minOrder = number(binding.rpMin.getText(), -1);
        int radius = number(binding.rpRadius.getText(), -1);
        boolean nameOk = !name.isEmpty();
        boolean phoneOk = phone != null;
        boolean minOk = minOrder >= 0;
        boolean radiusOk = radius >= 1 && radius <= 25;
        binding.rpNameLayout.setError(nameOk ? null : getString(R.string.error_name));
        binding.rpPhoneLayout.setError(phoneOk ? null : getString(R.string.error_phone));
        binding.rpCityLayout.setError(city.isEmpty() ? getString(R.string.error_city) : null);
        binding.rpMinLayout.setError(minOk ? null : getString(R.string.error_price));
        binding.rpRadiusLayout.setError(radiusOk ? null : getString(R.string.error_radius));
        if (!nameOk || !phoneOk || city.isEmpty() || !minOk || !radiusOk) {
            return;
        }
        if (restaurant.address == null || restaurant.address.isEmpty()) {
            showMessage(getString(R.string.error_location));
            return;
        }
        restaurant.name = name;
        restaurant.cuisine = String.valueOf(binding.rpCuisine.getText()).trim();
        restaurant.description = String.valueOf(binding.rpDescription.getText()).trim();
        restaurant.phone = phone;
        restaurant.city = city;
        restaurant.area = String.valueOf(binding.rpArea.getText()).trim();
        restaurant.minOrder = minOrder;
        restaurant.radiusKm = radius;
        restaurant.hours = new ArrayList<>(hours);
        restaurant.ownerUid = session.uid();
        binding.rpSave.setEnabled(false);
        binding.rpProgress.setVisibility(View.VISIBLE);
        MenuRepository.get(this).saveRestaurant(restaurant, new MenuRepository.Listener<Void>() {
            @Override
            public void onResult(Void result) {
                if (creating) {
                    session.setRestaurantId(restaurant.id);
                    FirebaseSync.saveUser(RestaurantProfileActivity.this, session.uid(), session.name(),
                            session.email(), session.phone(), session.role(), restaurant.id);
                }
                showMessage(getString(R.string.profile_saved));
                finish();
            }

            @Override
            public void onError() {
                binding.rpSave.setEnabled(true);
                binding.rpProgress.setVisibility(View.GONE);
                showMessage(getString(R.string.save_failed));
            }
        });
    }
}
