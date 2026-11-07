package com.foodrush.app.ui;

import android.Manifest;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.foodrush.app.R;
import com.foodrush.app.adapter.ChipAdapter;
import com.foodrush.app.adapter.DishCardAdapter;
import com.foodrush.app.adapter.LunchAdapter;
import com.foodrush.app.adapter.PromoAdapter;
import com.foodrush.app.adapter.RestaurantAdapter;
import com.foodrush.app.data.AuthRepository;
import com.foodrush.app.data.CartRepository;
import com.foodrush.app.data.Catalog;
import com.foodrush.app.data.FavouriteRepository;
import com.foodrush.app.data.FirebaseSync;
import com.foodrush.app.data.FoodRushContract;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.RiderPresence;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityMainBinding;
import com.foodrush.app.databinding.NavHeaderBinding;
import com.foodrush.app.model.Dish;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Order;
import com.foodrush.app.order.OrderLogic;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.util.NotificationHelper;
import com.foodrush.app.model.Promo;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.route.RoutePlanner;
import com.foodrush.app.service.OrderTrackingService;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.Images;
import com.foodrush.app.util.States;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Customer home: dishes, deals and restaurants that deliver to the chosen address. */
public class MainActivity extends BaseActivity {

    private static final String STATE_CATEGORY = "category";
    /** Road times are worked out for this many nearest restaurants. */
    private static final int ROUTED_RESTAURANTS = 12;

    private ActivityMainBinding binding;
    private Session session;
    private MenuRepository menu;
    private ChipAdapter chips;
    private DishCardAdapter dishes;
    private LunchAdapter deals;
    private RestaurantAdapter restaurantAdapter;
    private String allFood;
    private String selectedCategory;
    private String loadedAddress;
    private final List<Dish> nearbyDishes = new ArrayList<>();
    private List<Restaurant> shownRestaurants = new ArrayList<>();
    private int loadGeneration;
    private OrderStore.Subscription liveOrder;
    private OrderStore.Subscription ridersOnline;

    private final ContentObserver cartObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            refreshBadge();
        }
    };

    private final ActivityResultLauncher<Intent> categoryPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    selectCategory(data.getStringExtra(CategoriesActivity.EXTRA_CATEGORY));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new Session(this);
        if (!session.isCustomer()) {
            startActivity(RoleRouter.home(this));
            finish();
            return;
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        menu = MenuRepository.get(this);
        allFood = getString(R.string.all_food);
        selectedCategory = savedInstanceState == null ? allFood : savedInstanceState.getString(STATE_CATEGORY, allFood);

        setUpLists();
        setUpDrawer();
        binding.homeSearch.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        binding.homeAddress.setOnClickListener(v -> startActivity(new Intent(this, AddressPickerActivity.class)));
        binding.homeCart.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));
        binding.homeSeeAll.setOnClickListener(v -> categoryPicker.launch(
                CategoriesActivity.intent(this, categoriesWithImages())));
        binding.homeRefresh.setColorSchemeResources(R.color.amber_dark);
        binding.homeRefresh.setOnRefreshListener(() -> loadAll(true));

        FirebaseSync.subscribeToPromos(this);
        askForNotificationsOnce();
    }

    /** DrawerLayout ignores its own padding, so the screen content takes the insets instead. */
    @Override
    protected View insetTarget(View root) {
        return binding.drawer.getChildAt(0);
    }

    private void setUpLists() {
        chips = new ChipAdapter(this::selectCategory);
        binding.homeChips.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.homeChips.setAdapter(chips);

        dishes = new DishCardAdapter(new DishCardAdapter.Listener() {
            @Override
            public void onDish(Dish dish, int position) {
                DishActivity.open(MainActivity.this, dishes.getCurrentList(), position);
            }

            @Override
            public void onFavourite(Dish dish) {
                toggleFavourite(dish);
            }
        }, false);
        binding.homeDishes.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.homeDishes.setAdapter(dishes);

        binding.homePromos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.homePromos.setAdapter(new PromoAdapter(Catalog.get(this).promos, this::applyPromo));

        deals = new LunchAdapter((dish, position) -> DishActivity.open(this, deals.getCurrentList(), position));
        binding.homeLunch.setLayoutManager(new LinearLayoutManager(this));
        binding.homeLunch.setAdapter(deals);

        restaurantAdapter = new RestaurantAdapter(this::openRestaurant);
        binding.homeRestaurants.setLayoutManager(new LinearLayoutManager(this));
        binding.homeRestaurants.setAdapter(restaurantAdapter);
    }

    private void setUpDrawer() {
        binding.homeMenu.setOnClickListener(v -> binding.drawer.openDrawer(GravityCompat.START));
        binding.homeNav.setNavigationItemSelectedListener(item -> {
            binding.drawer.closeDrawers();
            int id = item.getItemId();
            if (id == R.id.nav_orders) {
                startActivity(new Intent(this, OrdersActivity.class));
            } else if (id == R.id.nav_favourites) {
                startActivity(new Intent(this, FavouritesActivity.class));
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.nav_help) {
                startActivity(new Intent(this, HelpActivity.class));
            } else if (id == R.id.nav_logout) {
                signOut();
            }
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawer.isDrawerOpen(GravityCompat.START)) {
                    binding.drawer.closeDrawers();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    private void signOut() {
        new AuthRepository(this).logout();
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    private void askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !session.notificationPromptShown()) {
            session.setNotificationPromptShown();
            withPermission(Manifest.permission.POST_NOTIFICATIONS, R.string.permission_notifications, () -> {
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        getContentResolver().registerContentObserver(FoodRushContract.Cart.URI, true, cartObserver);
        liveOrder = OrderStore.watchCustomer(this, session.uid(), this::showLiveOrder);
        ridersOnline = RiderPresence.watchCount(this, this::showRidersOnline);
    }

    /** "5 riders online now", live from Realtime Database; hidden when it can't be read. */
    private void showRidersOnline(int riders) {
        binding.homeRidersOnline.setVisibility(riders < 0 ? View.GONE : View.VISIBLE);
        if (riders >= 0) {
            binding.homeRidersOnline.setText(getResources().getQuantityString(R.plurals.riders_online, riders, riders));
        }
    }

    /** A banner for the order on its way; tap to track it. */
    private void showLiveOrder(List<Order> orders) {
        Order active = null;
        for (Order o : orders) {
            if (o.isActive()) {
                active = o;
                break;
            }
        }
        binding.homeLiveOrder.setVisibility(active == null ? View.GONE : View.VISIBLE);
        if (active == null) {
            return;
        }
        Order shown = active;
        binding.homeLiveTitle.setText(NotificationHelper.stageText(this, shown));
        binding.homeLiveText.setText(getString(R.string.live_order_text, shown.shortId(),
                OrderLogic.minutesLeft(shown, System.currentTimeMillis())));
        binding.homeLiveOrder.setOnClickListener(v -> startActivity(new Intent(this, TrackingActivity.class)
                .putExtra(TrackingActivity.EXTRA_ORDER_ID, shown.id)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBadge();
        refreshDrawerHeader();
        binding.homeAddressText.setText(session.addressLabel());
        OrderTrackingService.startIfNeeded(this);
        String address = session.addressPoint().encode();
        if (!address.equals(loadedAddress)) {
            loadedAddress = address;
            loadAll(false);
        } else {
            refreshFavourites();
        }
    }

    @Override
    protected void onStop() {
        getContentResolver().unregisterContentObserver(cartObserver);
        if (liveOrder != null) {
            liveOrder.remove();
            liveOrder = null;
        }
        if (ridersOnline != null) {
            ridersOnline.remove();
            ridersOnline = null;
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CATEGORY, selectedCategory);
    }

    private void loadAll(boolean refresh) {
        int generation = ++loadGeneration;
        States.loading(binding.homeState);
        binding.homeDishes.setVisibility(View.GONE);
        LatLon customer = session.addressPoint();
        menu.restaurants(refresh, new MenuRepository.Listener<List<Restaurant>>() {
            @Override
            public void onResult(List<Restaurant> all) {
                if (generation != loadGeneration) {
                    return;
                }
                List<Restaurant> delivering = MenuRepository.deliveringTo(all, customer);
                binding.homeNoDelivery.setVisibility(delivering.isEmpty() ? View.VISIBLE : View.GONE);
                shownRestaurants = delivering.isEmpty() ? nearest(all, customer, 6) : delivering;
                restaurantAdapter.submit(shownRestaurants, null);
                loadRoadTimes(customer, generation);
                loadDishes(generation);
            }

            @Override
            public void onError() {
                binding.homeRefresh.setRefreshing(false);
                States.error(binding.homeState, () -> loadAll(true));
            }
        });
    }

    private static List<Restaurant> nearest(List<Restaurant> all, LatLon customer, int count) {
        List<Restaurant> sorted = new ArrayList<>(all);
        Collections.sort(sorted, (a, b) -> Double.compare(a.location().metersTo(customer), b.location().metersTo(customer)));
        return sorted.subList(0, Math.min(count, sorted.size()));
    }

    /** Dijkstra on real roads, in the background; the list first shows "working out time". */
    private void loadRoadTimes(LatLon customer, int generation) {
        List<Restaurant> routed = shownRestaurants.subList(0, Math.min(ROUTED_RESTAURANTS, shownRestaurants.size()));
        List<LatLon> points = new ArrayList<>();
        for (Restaurant r : routed) {
            points.add(r.location());
        }
        List<Restaurant> snapshot = new ArrayList<>(routed);
        AppExecutors.load(() -> new RoutePlanner(this).minutesToCustomer(customer, points), minutes -> {
            if (generation != loadGeneration) {
                return;
            }
            Map<String, Double> byId = new HashMap<>();
            for (int i = 0; i < snapshot.size(); i++) {
                byId.put(snapshot.get(i).id, minutes[i]);
            }
            // Sort by riding time, closed restaurants last.
            Calendar now = Calendar.getInstance();
            List<Restaurant> sorted = new ArrayList<>(shownRestaurants);
            Collections.sort(sorted, (a, b) -> {
                boolean openA = a.isOpenAt(now);
                boolean openB = b.isOpenAt(now);
                if (openA != openB) {
                    return openA ? -1 : 1;
                }
                Double ma = byId.get(a.id);
                Double mb = byId.get(b.id);
                return Double.compare(ma == null ? Double.MAX_VALUE : ma, mb == null ? Double.MAX_VALUE : mb);
            });
            shownRestaurants = sorted;
            restaurantAdapter.submit(sorted, byId);
        });
    }

    private void loadDishes(int generation) {
        List<String> ids = new ArrayList<>();
        for (Restaurant r : shownRestaurants) {
            ids.add(r.id);
        }
        menu.dishesOf(ids, false, new MenuRepository.Listener<List<Dish>>() {
            @Override
            public void onResult(List<Dish> result) {
                if (generation != loadGeneration) {
                    return;
                }
                binding.homeRefresh.setRefreshing(false);
                nearbyDishes.clear();
                nearbyDishes.addAll(result);
                Collections.sort(nearbyDishes, (a, b) -> Float.compare(b.rating, a.rating));
                showCategories();
                showDishes();
                showDeals();
                showDishOfTheDay();
            }

            @Override
            public void onError() {
                binding.homeRefresh.setRefreshing(false);
                States.error(binding.homeState, () -> loadAll(true));
            }
        });
    }

    private void showCategories() {
        Set<String> names = new LinkedHashSet<>();
        names.add(allFood);
        for (Dish dish : nearbyDishes) {
            names.add(dish.category);
        }
        if (!names.contains(selectedCategory)) {
            selectedCategory = allFood;
        }
        chips.submit(new ArrayList<>(names), selectedCategory);
    }

    private ArrayList<String> categoriesWithImages() {
        ArrayList<String> pairs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Dish dish : nearbyDishes) {
            if (seen.add(dish.category)) {
                pairs.add(dish.category);
                pairs.add(dish.image);
            }
        }
        return pairs;
    }

    private void selectCategory(String category) {
        selectedCategory = category;
        chips.select(category);
        showDishes();
    }

    /** The user's saved dishes lead the "All food" row, followed by the best rated nearby. */
    private void showDishes() {
        boolean all = selectedCategory.equals(allFood);
        AppExecutors.load(() -> FavouriteRepository.all(this), saved -> {
            Set<String> nearbyIds = new HashSet<>();
            for (Dish dish : nearbyDishes) {
                nearbyIds.add(dish.id);
            }
            List<Dish> merged = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            if (all) {
                for (Dish dish : saved) {
                    if (nearbyIds.contains(dish.id) && ids.add(dish.id)) {
                        merged.add(menu.dish(dish.id) != null ? menu.dish(dish.id) : dish);
                    }
                }
            }
            for (Dish dish : nearbyDishes) {
                if ((all || dish.category.equals(selectedCategory)) && ids.add(dish.id)) {
                    merged.add(dish);
                }
            }
            Set<String> favouriteIds = new HashSet<>();
            for (Dish dish : saved) {
                favouriteIds.add(dish.id);
            }
            dishes.setFavourites(favouriteIds);
            dishes.submitList(merged.subList(0, Math.min(30, merged.size())));
            if (merged.isEmpty()) {
                States.empty(binding.homeState, R.string.no_dishes);
            } else {
                States.hide(binding.homeState);
                binding.homeDishes.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showDeals() {
        List<Dish> discounted = new ArrayList<>();
        for (Dish dish : nearbyDishes) {
            if (dish.discounted()) {
                discounted.add(dish);
            }
        }
        binding.homeDealsTitle.setVisibility(discounted.isEmpty() ? View.GONE : View.VISIBLE);
        deals.submitList(discounted.subList(0, Math.min(6, discounted.size())));
    }

    /** A different top-rated dish each day. */
    private void showDishOfTheDay() {
        if (nearbyDishes.isEmpty()) {
            binding.homeDishOfDay.setVisibility(View.GONE);
            return;
        }
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        Dish dish = nearbyDishes.get(day % Math.min(10, nearbyDishes.size()));
        binding.homeDishOfDay.setVisibility(View.VISIBLE);
        binding.dishOfDayName.setText(dish.name);
        Images.plate(binding.dishOfDayImage, dish.image);
        binding.homeDishOfDay.setOnClickListener(v -> DishActivity.open(this, Collections.singletonList(dish), 0));
    }

    private void refreshFavourites() {
        AppExecutors.load(() -> FavouriteRepository.ids(this), ids -> dishes.setFavourites(ids));
    }

    private void toggleFavourite(Dish dish) {
        AppExecutors.load(() -> {
            FavouriteRepository.toggle(this, dish);
            return FavouriteRepository.ids(this);
        }, ids -> dishes.setFavourites(ids));
    }

    private void applyPromo(Promo promo) {
        session.setCoupon(promo.code);
        showMessage(getString(R.string.coupon_copied, promo.code));
    }

    private void openRestaurant(Restaurant restaurant) {
        startActivity(new Intent(this, RestaurantActivity.class)
                .putExtra(RestaurantActivity.EXTRA_RESTAURANT_ID, restaurant.id));
    }

    private void refreshBadge() {
        AppExecutors.load(() -> CartRepository.count(this), count -> {
            binding.homeCartBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            binding.homeCartBadge.setText(String.valueOf(count));
        });
    }

    private void refreshDrawerHeader() {
        NavHeaderBinding header = NavHeaderBinding.bind(binding.homeNav.getHeaderView(0));
        header.navName.setText(session.name());
        header.navEmail.setText(session.email());
        String photo = session.photoPath();
        if (photo != null) {
            Glide.with(this).load(new File(photo)).signature(new com.bumptech.glide.signature.ObjectKey(photo))
                    .circleCrop().into(header.navPhoto);
        }
    }
}
