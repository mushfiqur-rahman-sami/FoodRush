package com.foodrush.app.adapter;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.R;
import com.foodrush.app.databinding.ItemOrderBinding;
import com.foodrush.app.model.Order;
import com.foodrush.app.util.PriceCalculator;

import java.util.ArrayList;
import java.util.List;

/** Past and current orders. Riders see their earning instead of the order total. */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.Holder> {

    public interface Listener {
        void onOrder(Order order);
    }

    public interface ReorderListener {
        void onReorder(Order order);
    }

    private final List<Order> orders = new ArrayList<>();
    private final Listener listener;
    private final boolean showEarning;
    private ReorderListener reorderListener;

    public OrderAdapter(Listener listener) {
        this(listener, false);
    }

    public OrderAdapter(Listener listener, boolean showEarning) {
        this.listener = listener;
        this.showEarning = showEarning;
    }

    /** Shows "Order again" on finished orders that list their dishes. */
    public void setReorderListener(ReorderListener listener) {
        reorderListener = listener;
    }

    public void submit(List<Order> newOrders) {
        orders.clear();
        orders.addAll(newOrders);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemOrderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Order order = orders.get(position);
        holder.binding.orderItems.setText(order.items);
        holder.binding.orderDate.setText(DateUtils.getRelativeTimeSpanString(order.placedAt));
        holder.binding.orderTotal.setText(PriceCalculator.format(showEarning ? order.fee + order.tip : order.total));
        holder.binding.orderStatus.setText(stageLabel(order.status));
        holder.binding.getRoot().setOnClickListener(v -> listener.onOrder(order));
        boolean canReorder = reorderListener != null && !order.isActive() && !order.lines.isEmpty();
        holder.binding.orderAgain.setVisibility(canReorder ? android.view.View.VISIBLE : android.view.View.GONE);
        holder.binding.orderAgain.setOnClickListener(v -> reorderListener.onReorder(order));
    }

    public static int stageLabel(int status) {
        switch (status) {
            case Order.CANCELLED:
                return R.string.stage_cancelled;
            case Order.ACCEPTED:
                return R.string.stage_accepted;
            case Order.PREPARING:
                return R.string.stage_preparing;
            case Order.READY:
                return R.string.stage_ready;
            case Order.PICKED_UP:
                return R.string.stage_on_way;
            case Order.ARRIVING:
                return R.string.stage_arriving;
            case Order.DELIVERED:
                return R.string.stage_delivered;
            default:
                return R.string.stage_placed;
        }
    }

    public static String kitchenLabel(Context context, int kitchenStatus) {
        switch (kitchenStatus) {
            case Order.Kitchen.REJECTED:
                return context.getString(R.string.kitchen_rejected);
            case Order.Kitchen.ACCEPTED:
                return context.getString(R.string.stage_accepted);
            case Order.Kitchen.PREPARING:
                return context.getString(R.string.stage_preparing);
            case Order.Kitchen.READY:
                return context.getString(R.string.stage_ready);
            default:
                return context.getString(R.string.kitchen_new);
        }
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemOrderBinding binding;

        Holder(ItemOrderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
