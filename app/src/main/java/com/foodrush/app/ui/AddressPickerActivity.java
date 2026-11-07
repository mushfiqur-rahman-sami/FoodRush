package com.foodrush.app.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;

import androidx.core.content.ContextCompat;

import com.foodrush.app.R;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityAddressBinding;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.network.ApiClient;
import com.foodrush.app.network.GeoApi;
import com.foodrush.app.network.SimpleHttp;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.Bangladesh;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pick a spot anywhere in Bangladesh on a free OpenStreetMap map: search an address, use GPS or tap
 * the map. The point is turned into a street address by Android's Geocoder or, failing that, Nominatim.
 */
public class AddressPickerActivity extends BaseActivity {

    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";
    public static final String EXTRA_RETURN_RESULT = "return_result";
    /** Start here instead of the saved delivery address (used for a restaurant's location). */
    public static final String EXTRA_START_LAT = "start_lat";
    public static final String EXTRA_START_LON = "start_lon";
    public static final String EXTRA_TITLE = "title";
    private static final String STATE_LABEL = "state_label";
    private static final String STATE_LAT = "state_lat";
    private static final String STATE_LON = "state_lon";
    private static final double DEFAULT_ZOOM = 16.0;
    private static final long SEARCH_DELAY_MILLIS = 600;

    private ActivityAddressBinding binding;
    private Marker marker;
    private String selectedLabel;
    private LatLon selected;
    private int lookupGeneration;
    private int searchGeneration;
    private final List<SimpleHttp.Place> results = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchLater = () -> search(false);
    private boolean settingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddressBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        int title = getIntent().getIntExtra(EXTRA_TITLE, R.string.address_title);
        setUpTopBar(binding.addressBar, title);

        Session session = new Session(this);
        if (savedInstanceState != null) {
            selected = new LatLon(savedInstanceState.getDouble(STATE_LAT), savedInstanceState.getDouble(STATE_LON));
            selectedLabel = savedInstanceState.getString(STATE_LABEL);
        } else if (getIntent().hasExtra(EXTRA_START_LAT)) {
            selected = new LatLon(getIntent().getDoubleExtra(EXTRA_START_LAT, 0), getIntent().getDoubleExtra(EXTRA_START_LON, 0));
            selectedLabel = getIntent().getStringExtra(EXTRA_LABEL);
        } else {
            selected = session.addressPoint();
            selectedLabel = session.addressLabel();
        }

        GeoPoint start = new GeoPoint(selected.lat, selected.lon);
        binding.addressMap.setTileSource(TileSourceFactory.MAPNIK);
        binding.addressMap.setMultiTouchControls(true);
        binding.addressMap.setTilesScaledToDpi(true);
        // The whole of Bangladesh, and nothing beyond it.
        binding.addressMap.setScrollableAreaLimitDouble(new BoundingBox(Bangladesh.NORTH, Bangladesh.EAST,
                Bangladesh.SOUTH, Bangladesh.WEST));
        binding.addressMap.setMinZoomLevel(7.0);
        binding.addressMap.getController().setZoom(DEFAULT_ZOOM);
        binding.addressMap.getController().setCenter(start);

        marker = new Marker(binding.addressMap);
        marker.setTitle(getString(R.string.marker_you));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setPosition(start);
        marker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_map_pin));
        binding.addressMap.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                hideResults();
                choose(new LatLon(point.getLatitude(), point.getLongitude()), null, R.string.outside_bangladesh);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        }));
        binding.addressMap.getOverlays().add(marker);
        binding.addressSelected.setText(selectedLabel);

        setUpSearch();
        binding.addressLocate.setOnClickListener(v -> withPermission(Manifest.permission.ACCESS_FINE_LOCATION,
                R.string.permission_location, this::useMyLocation));
        binding.addressConfirm.setOnClickListener(v -> confirm());
    }

    private void setUpSearch() {
        binding.addressResults.setOnItemClickListener((parent, view, position, id) -> {
            SimpleHttp.Place place = results.get(position);
            hideResults();
            hideKeyboard();
            settingText = true;
            binding.addressSearch.setText(place.title);
            settingText = false;
            choose(place.point, place.label(), R.string.outside_bangladesh);
        });
        binding.addressSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(searchLater);
                if (!settingText && s.toString().trim().length() >= 3) {
                    handler.postDelayed(searchLater, SEARCH_DELAY_MILLIS);
                } else if (s.length() == 0) {
                    hideResults();
                }
            }
        });
        binding.addressSearch.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                handler.removeCallbacks(searchLater);
                search(true);
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void search(boolean userAsked) {
        String query = String.valueOf(binding.addressSearch.getText()).trim();
        if (query.isEmpty()) {
            return;
        }
        int generation = ++searchGeneration;
        LatLon bias = selected;
        AppExecutors.load(() -> {
            try {
                return SimpleHttp.searchPlaces(query, bias);
            } catch (Exception e) {
                return null;
            }
        }, (List<SimpleHttp.Place> found) -> {
            if (generation != searchGeneration || isFinishing()) {
                return;
            }
            if (found == null) {
                if (userAsked) {
                    showMessage(getString(R.string.load_error));
                }
                return;
            }
            results.clear();
            results.addAll(found);
            if (results.isEmpty()) {
                hideResults();
                if (userAsked) {
                    showMessage(getString(R.string.address_not_found));
                }
                return;
            }
            List<String> labels = new ArrayList<>();
            for (SimpleHttp.Place place : results) {
                labels.add(place.label());
            }
            binding.addressResults.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
            binding.addressResults.setVisibility(View.VISIBLE);
        });
    }

    private void hideResults() {
        binding.addressResults.setVisibility(View.GONE);
    }

    private void hideKeyboard() {
        InputMethodManager manager = getSystemService(InputMethodManager.class);
        if (manager != null) {
            manager.hideSoftInputFromWindow(binding.addressSearch.getWindowToken(), 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.addressMap.onResume();
    }

    @Override
    protected void onPause() {
        binding.addressMap.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(searchLater);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_LABEL, selectedLabel);
        outState.putDouble(STATE_LAT, selected.lat);
        outState.putDouble(STATE_LON, selected.lon);
    }

    /** Called only after withPermission has confirmed the location permission. */
    @SuppressLint("MissingPermission")
    private void useMyLocation() {
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        showMessage(getString(R.string.location_failed));
                    } else {
                        choose(new LatLon(location.getLatitude(), location.getLongitude()), null,
                                R.string.outside_bangladesh_gps);
                    }
                })
                .addOnFailureListener(e -> showMessage(getString(R.string.location_failed)));
    }

    private void choose(LatLon point, String knownLabel, int outsideMessage) {
        if (!Bangladesh.contains(point)) {
            showMessage(getString(outsideMessage));
            return;
        }
        selected = point;
        GeoPoint geo = new GeoPoint(point.lat, point.lon);
        marker.setPosition(geo);
        binding.addressMap.getController().animateTo(geo);
        binding.addressMap.invalidate();
        if (knownLabel != null) {
            selectedLabel = knownLabel;
            binding.addressSelected.setText(knownLabel);
            return;
        }
        selectedLabel = String.format(Locale.US, "%.5f, %.5f", point.lat, point.lon);
        binding.addressSelected.setText(R.string.address_looking_up);
        int generation = ++lookupGeneration;
        Context app = getApplicationContext();
        AppExecutors.load(() -> reverseGeocode(app, point), label -> {
            if (generation == lookupGeneration) {
                if (label != null) {
                    selectedLabel = label;
                }
                binding.addressSelected.setText(selectedLabel);
            }
        });
    }

    /** Android's Geocoder first (free, built in); Nominatim when the phone has no geocoder. */
    @SuppressWarnings("deprecation")
    static String reverseGeocode(Context context, LatLon point) {
        if (Geocoder.isPresent()) {
            try {
                List<Address> found = new Geocoder(context, Locale.ENGLISH).getFromLocation(point.lat, point.lon, 1);
                if (found != null && !found.isEmpty()) {
                    String label = label(found.get(0));
                    if (label != null) {
                        return label;
                    }
                }
            } catch (Exception e) {
                // Fall through to Nominatim.
            }
        }
        try {
            retrofit2.Response<GeoApi.ReverseResult> response = ApiClient.nominatim(context)
                    .reverse(point.lat, point.lon).execute();
            GeoApi.ReverseResult body = response.body();
            if (response.isSuccessful() && body != null && body.address != null) {
                return label(body);
            }
        } catch (Exception e) {
            // The coordinates already on screen are a fine fallback.
        }
        return null;
    }

    private static String label(Address address) {
        Set<String> parts = new LinkedHashSet<>();
        String street = address.getThoroughfare();
        if (street != null) {
            String number = address.getSubThoroughfare();
            parts.add(number == null ? street : number + " " + street);
        }
        add(parts, address.getSubLocality());
        add(parts, address.getLocality());
        if (parts.isEmpty()) {
            add(parts, address.getSubAdminArea());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String label(GeoApi.ReverseResult result) {
        GeoApi.Address a = result.address;
        Set<String> parts = new LinkedHashSet<>();
        if (result.name != null && !result.name.isEmpty() && !result.name.equals(a.road)) {
            parts.add(result.name);
        }
        if (a.road != null) {
            parts.add(a.houseNumber == null ? a.road : a.houseNumber + " " + a.road);
        }
        add(parts, a.neighbourhood != null ? a.neighbourhood : a.suburb);
        add(parts, a.city != null ? a.city : a.town != null ? a.town : a.village != null ? a.village : a.county);
        return parts.isEmpty() ? result.displayName : String.join(", ", parts);
    }

    private static void add(Set<String> parts, String value) {
        if (value != null && !value.isEmpty()) {
            parts.add(value);
        }
    }

    private void confirm() {
        String details = String.valueOf(binding.addressDetails.getText()).trim();
        String label = details.isEmpty() ? selectedLabel : details + ", " + selectedLabel;
        if (getIntent().getBooleanExtra(EXTRA_RETURN_RESULT, false)) {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_LABEL, label)
                    .putExtra(EXTRA_LAT, selected.lat).putExtra(EXTRA_LON, selected.lon));
        } else {
            new Session(this).setAddress(label, selected);
        }
        finish();
    }
}
