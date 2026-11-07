package com.foodrush.app.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.R;
import com.foodrush.app.databinding.ItemDishCardBinding;
import com.foodrush.app.model.Dish;
import com.foodrush.app.util.Images;
import com.foodrush.app.util.PriceCalculator;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** The design's dish card, in a row on the home screen and in a grid on search and favourites. */
public class DishCardAdapter extends ListAdapter<Dish, DishCardAdapter.Holder> {

    public interface Listener {
        void onDish(Dish dish, int position);

        void onFavourite(Dish dish);
    }

    static final DiffUtil.ItemCallback<Dish> DIFF = new DiffUtil.ItemCallback<Dish>() {
        @Override
        public boolean areItemsTheSame(@NonNull Dish a, @NonNull Dish b) {
            return a.id.equals(b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Dish a, @NonNull Dish b) {
            return a.fromPrice() == b.fromPrice() && a.name.equals(b.name) && a.available == b.available
                    && java.util.Objects.equals(a.image, b.image);
        }
    };

    private final Listener listener;
    private final boolean fillWidth;
    private Set<String> favourites = new HashSet<>();

    public DishCardAdapter(Listener listener, boolean fillWidth) {
        super(DIFF);
        this.listener = listener;
        this.fillWidth = fillWidth;
    }

    public void setFavourites(Set<String> ids) {
        favourites = ids;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDishCardBinding binding = ItemDishCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (fillWidth) {
            ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            binding.getRoot().setLayoutParams(params);
        }
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Dish dish = getItem(position);
        holder.binding.dishName.setText(dish.name);
        holder.binding.dishCategory.setText(dish.category);
        holder.binding.dishRating.setText(String.format(Locale.US, "%.1f", dish.rating));
        holder.binding.dishPrice.setText(PriceCalculator.format(dish.fromPrice()));
        Images.plate(holder.binding.dishImage, dish.image);
        holder.binding.dishFavourite.setImageResource(
                favourites.contains(dish.id) ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
        holder.binding.getRoot().setOnClickListener(v -> listener.onDish(dish, holder.getBindingAdapterPosition()));
        holder.binding.dishFavourite.setOnClickListener(v -> listener.onFavourite(dish));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemDishCardBinding binding;

        Holder(ItemDishCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
