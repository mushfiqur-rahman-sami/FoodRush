package com.foodrush.app.ui;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.foodrush.app.R;
import com.foodrush.app.adapter.ChipAdapter;
import com.foodrush.app.adapter.DishCardAdapter;
import com.foodrush.app.data.FavouriteRepository;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.SearchHistoryRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivitySearchBinding;
import com.foodrush.app.model.Dish;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.States;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SearchActivity extends BaseActivity {

    public static final String EXTRA_QUERY = "query";
    private static final String STATE_QUERY = "search_query";
    private static final int UNDER_TAKA = 300;

    private ActivitySearchBinding binding;
    private MenuRepository menu;
    private DishCardAdapter results;
    private ChipAdapter recent;
    private final List<Dish> lastResults = new ArrayList<>();
    private String lastQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        menu = MenuRepository.get(this);
        States.hide(binding.searchState);

        results = new DishCardAdapter(new DishCardAdapter.Listener() {
            @Override
            public void onDish(Dish dish, int position) {
                DishActivity.open(SearchActivity.this, results.getCurrentList(), position);
            }

            @Override
            public void onFavourite(Dish dish) {
                AppExecutors.load(() -> {
                    FavouriteRepository.toggle(SearchActivity.this, dish);
                    return FavouriteRepository.ids(SearchActivity.this);
                }, ids -> results.setFavourites(ids));
            }
        }, true);
        binding.searchResults.setLayoutManager(new GridLayoutManager(this, 2));
        binding.searchResults.setAdapter(results);

        recent = new ChipAdapter(value -> {
            binding.searchInput.setText(value);
            runSearch(value);
        });
        binding.searchRecent.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.searchRecent.setAdapter(recent);

        binding.searchBack.setOnClickListener(v -> finish());
        binding.searchClear.setOnClickListener(v -> AppExecutors.load(() -> {
            SearchHistoryRepository.clear(this);
            return true;
        }, done -> loadRecent()));
        binding.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterKey = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterKey) {
                if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                    runSearch(v.getText().toString());
                }
                return true;
            }
            return false;
        });
        binding.searchInput.requestFocus();
        binding.searchFilters.setOnCheckedStateChangeListener((group, ids) -> showResults());

        String query = savedInstanceState != null ? savedInstanceState.getString(STATE_QUERY, "")
                : getIntent().getStringExtra(EXTRA_QUERY);
        if (query != null && !query.isEmpty()) {
            binding.searchInput.setText(query);
            runSearch(query);
        }
        loadRecent();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, lastQuery);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppExecutors.load(() -> FavouriteRepository.ids(this), ids -> results.setFavourites(ids));
    }

    private void loadRecent() {
        AppExecutors.load(() -> SearchHistoryRepository.recent(this), queries -> {
            recent.submit(queries, null);
            int visibility = queries.isEmpty() ? View.GONE : View.VISIBLE;
            binding.searchRecentHeader.setVisibility(visibility);
            binding.searchRecent.setVisibility(visibility);
        });
    }

    private void runSearch(String rawQuery) {
        String query = rawQuery.trim();
        if (query.isEmpty()) {
            return;
        }
        lastQuery = query;
        hideKeyboard();
        States.loading(binding.searchState);
        results.submitList(new ArrayList<>());
        AppExecutors.load(() -> {
            SearchHistoryRepository.record(this, query);
            return true;
        }, done -> loadRecent());
        searchNearby(query);
    }

    /** Matches dish names, categories, descriptions and restaurant names among restaurants that deliver here. */
    private void searchNearby(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        LatLon customer = new Session(this).addressPoint();
        menu.restaurants(false, new MenuRepository.Listener<List<Restaurant>>() {
            @Override
            public void onResult(List<Restaurant> all) {
                List<Restaurant> nearby = MenuRepository.deliveringTo(all, customer);
                List<String> ids = new ArrayList<>();
                Set<String> nameMatches = new HashSet<>();
                for (Restaurant r : nearby) {
                    ids.add(r.id);
                    if (r.name.toLowerCase(Locale.ROOT).contains(needle) || r.cuisine.toLowerCase(Locale.ROOT).contains(needle)) {
                        nameMatches.add(r.id);
                    }
                }
                menu.dishesOf(ids, false, new MenuRepository.Listener<List<Dish>>() {
                    @Override
                    public void onResult(List<Dish> dishes) {
                        lastResults.clear();
                        for (Dish dish : dishes) {
                            if (nameMatches.contains(dish.restaurantId)
                                    || dish.name.toLowerCase(Locale.ROOT).contains(needle)
                                    || dish.category.toLowerCase(Locale.ROOT).contains(needle)
                                    || dish.description.toLowerCase(Locale.ROOT).contains(needle)) {
                                lastResults.add(dish);
                            }
                        }
                        showResults();
                    }

                    @Override
                    public void onError() {
                        States.error(binding.searchState, () -> runSearch(query));
                    }
                });
            }

            @Override
            public void onError() {
                States.error(binding.searchState, () -> runSearch(query));
            }
        });
    }

    private void showResults() {
        if (lastQuery.isEmpty()) {
            return;
        }
        List<Dish> shown = new ArrayList<>(lastResults);
        int checked = binding.searchFilters.getCheckedChipId();
        if (checked == R.id.filter_rating) {
            Collections.sort(shown, (a, b) -> Float.compare(b.rating, a.rating));
        } else if (checked == R.id.filter_price) {
            Collections.sort(shown, Comparator.comparingInt(Dish::fromPrice));
        } else if (checked == R.id.filter_under) {
            shown.removeIf(d -> d.fromPrice() >= UNDER_TAKA);
        }
        results.submitList(shown);
        if (shown.isEmpty()) {
            States.empty(binding.searchState, getString(R.string.search_empty, lastQuery));
        } else {
            States.hide(binding.searchState);
        }
    }

    private void hideKeyboard() {
        InputMethodManager manager = getSystemService(InputMethodManager.class);
        if (manager != null) {
            manager.hideSoftInputFromWindow(binding.searchInput.getWindowToken(), 0);
        }
    }
}
