package com.foodrush.app.ui.restaurant;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.R;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityMenuEditorBinding;
import com.foodrush.app.databinding.ItemMenuDishBinding;
import com.foodrush.app.model.Dish;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.util.Images;
import com.foodrush.app.util.PriceCalculator;
import com.foodrush.app.util.States;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The restaurant's whole menu, sold-out dishes included. Tap a dish to edit it. */
public class MenuEditorActivity extends BaseActivity {

    private ActivityMenuEditorBinding binding;
    private String restaurantId;
    private final List<Dish> dishes = new ArrayList<>();
    private final DishAdapter adapter = new DishAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMenuEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.editorBar, R.string.menu_editor_title);
        restaurantId = new Session(this).restaurantId();
        binding.editorList.setLayoutManager(new LinearLayoutManager(this));
        binding.editorList.setAdapter(adapter);
        binding.editorAdd.setOnClickListener(v -> startActivity(new Intent(this, DishEditorActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        States.loading(binding.editorState);
        MenuRepository.get(this).dishesOf(Collections.singletonList(restaurantId), true,
                new MenuRepository.Listener<List<Dish>>() {
                    @Override
                    public void onResult(List<Dish> result) {
                        dishes.clear();
                        dishes.addAll(result);
                        Collections.sort(dishes, (a, b) -> (a.category + a.name).compareTo(b.category + b.name));
                        adapter.notifyDataSetChanged();
                        if (dishes.isEmpty()) {
                            States.empty(binding.editorState, R.string.menu_editor_empty);
                        } else {
                            States.hide(binding.editorState);
                        }
                    }

                    @Override
                    public void onError() {
                        States.error(binding.editorState, MenuEditorActivity.this::load);
                    }
                });
    }

    private class DishAdapter extends RecyclerView.Adapter<DishAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemMenuDishBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Dish dish = dishes.get(position);
            ItemMenuDishBinding b = holder.binding;
            b.menuName.setText(dish.name);
            b.menuCategory.setText(dish.available ? dish.category : getString(R.string.sold_out_category, dish.category));
            b.menuPrice.setText(PriceCalculator.format(dish.currentPrice()));
            b.menuOldPrice.setVisibility(dish.discounted() ? View.VISIBLE : View.GONE);
            b.menuOldPrice.setText(PriceCalculator.format(dish.price));
            b.menuOldPrice.setPaintFlags(b.menuOldPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            b.menuRating.setText(String.valueOf(dish.rating));
            b.getRoot().setAlpha(dish.available ? 1f : 0.55f);
            Images.plate(b.menuImage, dish.image);
            b.getRoot().setOnClickListener(v -> startActivity(new Intent(MenuEditorActivity.this,
                    DishEditorActivity.class).putExtra(DishEditorActivity.EXTRA_DISH_ID, dish.id)));
        }

        @Override
        public int getItemCount() {
            return dishes.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemMenuDishBinding binding;

            Holder(ItemMenuDishBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
