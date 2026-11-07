package com.foodrush.app.ui;

import android.os.Bundle;

import androidx.recyclerview.widget.GridLayoutManager;

import com.foodrush.app.R;
import com.foodrush.app.adapter.DishCardAdapter;
import com.foodrush.app.data.FavouriteRepository;
import com.foodrush.app.databinding.ActivityListBinding;
import com.foodrush.app.model.Dish;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.States;

import java.util.HashSet;
import java.util.Set;

public class FavouritesActivity extends BaseActivity {

    private ActivityListBinding binding;
    private DishCardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.listBar, R.string.favourites_title);

        adapter = new DishCardAdapter(new DishCardAdapter.Listener() {
            @Override
            public void onDish(Dish dish, int position) {
                DishActivity.open(FavouritesActivity.this, adapter.getCurrentList(), position);
            }

            @Override
            public void onFavourite(Dish dish) {
                AppExecutors.load(() -> {
                    FavouriteRepository.remove(FavouritesActivity.this, dish.id);
                    return true;
                }, done -> load());
            }
        }, true);
        binding.listItems.setLayoutManager(new GridLayoutManager(this, 2));
        binding.listItems.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        AppExecutors.load(() -> FavouriteRepository.all(this), dishes -> {
            Set<String> ids = new HashSet<>();
            for (Dish dish : dishes) {
                ids.add(dish.id);
            }
            adapter.setFavourites(ids);
            adapter.submitList(dishes);
            if (dishes.isEmpty()) {
                States.empty(binding.listState, R.string.favourites_empty);
            } else {
                States.hide(binding.listState);
            }
        });
    }
}
