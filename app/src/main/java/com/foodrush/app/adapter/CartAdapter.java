package com.foodrush.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.databinding.ItemCartBinding;
import com.foodrush.app.model.CartItem;
import com.foodrush.app.util.Images;
import com.foodrush.app.util.PriceCalculator;

import java.util.ArrayList;
import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.Holder> {

    public interface Listener {
        void onQuantity(CartItem item, int quantity);

        void onRemove(CartItem item);
    }

    private final List<CartItem> items = new ArrayList<>();
    private final Listener listener;

    public CartAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<CartItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCartBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CartItem item = items.get(position);
        holder.binding.cartName.setText(item.name);
        holder.binding.cartSize.setText(item.size);
        holder.binding.cartSize.setVisibility(item.size.isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.cartExtras.setText(item.extras);
        holder.binding.cartExtras.setVisibility(item.extras.isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.cartPrice.setText(PriceCalculator.format(item.lineTotal()));
        Images.plate(holder.binding.cartImage, item.image);
        holder.binding.cartQuantity.setOnChangeListener(null);
        holder.binding.cartQuantity.setMinValue(0);
        holder.binding.cartQuantity.setValue(item.quantity);
        holder.binding.cartQuantity.setOnChangeListener(value -> listener.onQuantity(item, value));
        holder.binding.getRoot().setOnLongClickListener(v -> {
            listener.onRemove(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemCartBinding binding;

        Holder(ItemCartBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
