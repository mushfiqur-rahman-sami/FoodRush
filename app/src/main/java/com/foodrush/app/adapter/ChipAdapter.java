package com.foodrush.app.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.R;
import com.foodrush.app.databinding.ItemChipBinding;

import java.util.ArrayList;
import java.util.List;

public class ChipAdapter extends RecyclerView.Adapter<ChipAdapter.Holder> {

    public interface Listener {
        void onChip(String value);
    }

    private final List<String> values = new ArrayList<>();
    private final Listener listener;
    private String selected;

    public ChipAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<String> items, String selectedValue) {
        values.clear();
        values.addAll(items);
        selected = selectedValue;
        notifyDataSetChanged();
    }

    public void select(String value) {
        selected = value;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemChipBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        String value = values.get(position);
        holder.binding.chipText.setText(value);
        boolean isSelected = value.equals(selected);
        holder.binding.chipText.setBackgroundResource(isSelected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        holder.binding.chipText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                isSelected ? R.color.on_amber : R.color.text_primary));
        holder.binding.chipText.setOnClickListener(v -> listener.onChip(value));
    }

    @Override
    public int getItemCount() {
        return values.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemChipBinding binding;

        Holder(ItemChipBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
