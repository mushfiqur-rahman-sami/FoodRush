package com.foodrush.app.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.databinding.ItemPromoBinding;
import com.foodrush.app.model.Promo;

import java.util.List;

public class PromoAdapter extends RecyclerView.Adapter<PromoAdapter.Holder> {

    public interface Listener {
        void onPromo(Promo promo);
    }

    private final List<Promo> promos;
    private final Listener listener;

    public PromoAdapter(List<Promo> promos, Listener listener) {
        this.promos = promos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPromoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Promo promo = promos.get(position);
        holder.binding.promoTitle.setText(promo.title);
        holder.binding.promoSubtitle.setText(promo.subtitle);
        holder.binding.promoCode.setText(promo.code);
        holder.binding.getRoot().setOnClickListener(v -> listener.onPromo(promo));
    }

    @Override
    public int getItemCount() {
        return promos.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemPromoBinding binding;

        Holder(ItemPromoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
