package com.foodrush.app.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.databinding.ItemLunchBinding;
import com.foodrush.app.model.Dish;
import com.foodrush.app.util.Images;
import com.foodrush.app.util.PriceCalculator;

/** The design's deals row: struck-through old price and a plate overlapping the card. */
public class LunchAdapter extends ListAdapter<Dish, LunchAdapter.Holder> {

    public interface Listener {
        void onLunch(Dish dish, int position);
    }

    private final Listener listener;

    public LunchAdapter(Listener listener) {
        super(DishCardAdapter.DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemLunchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Dish dish = getItem(position);
        holder.binding.lunchName.setText(dish.name);
        holder.binding.lunchCategory.setText(dish.category);
        holder.binding.lunchOldPrice.setText(PriceCalculator.format(dish.price));
        holder.binding.lunchOldPrice.setPaintFlags(
                holder.binding.lunchOldPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        holder.binding.lunchPrice.setText(PriceCalculator.format(dish.currentPrice()));
        Images.plate(holder.binding.lunchImage, dish.image);
        holder.binding.getRoot().setOnClickListener(v -> listener.onLunch(dish, holder.getBindingAdapterPosition()));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemLunchBinding binding;

        Holder(ItemLunchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
