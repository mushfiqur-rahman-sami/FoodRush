package com.foodrush.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.databinding.ItemKitchenOrderBinding;
import com.foodrush.app.model.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * Order cards with up to two action buttons, used by the kitchen screen and the rider's job list.
 * The screen decides the wording and the actions through {@link Binder}.
 */
public class JobAdapter extends RecyclerView.Adapter<JobAdapter.Holder> {

    public interface Binder {
        void bind(ItemKitchenOrderBinding card, Order order);
    }

    private final List<Order> orders = new ArrayList<>();
    private final Binder binder;

    public JobAdapter(Binder binder) {
        this.binder = binder;
    }

    public void submit(List<Order> newOrders) {
        orders.clear();
        orders.addAll(newOrders);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemKitchenOrderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ItemKitchenOrderBinding card = holder.binding;
        card.jobPrimary.setVisibility(View.GONE);
        card.jobSecondary.setVisibility(View.GONE);
        card.jobPrimary.setOnClickListener(null);
        card.jobSecondary.setOnClickListener(null);
        card.getRoot().setOnClickListener(null);
        binder.bind(card, orders.get(position));
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemKitchenOrderBinding binding;

        Holder(ItemKitchenOrderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
