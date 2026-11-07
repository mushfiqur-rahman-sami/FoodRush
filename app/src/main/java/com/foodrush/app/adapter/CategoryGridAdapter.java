package com.foodrush.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.foodrush.app.databinding.ItemCategoryBinding;
import com.foodrush.app.util.Images;

import java.util.ArrayList;
import java.util.List;

/** Classic BaseAdapter for the GridView on the categories screen. Each category shows one of its dishes. */
public class CategoryGridAdapter extends BaseAdapter {

    public static final class Category {
        public final String name;
        public final String image;

        public Category(String name, String image) {
            this.name = name;
            this.image = image;
        }
    }

    private final List<Category> categories = new ArrayList<>();

    public void submit(List<Category> items) {
        categories.clear();
        categories.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return categories.size();
    }

    @Override
    public Category getItem(int position) {
        return categories.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemCategoryBinding binding;
        if (convertView == null) {
            binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            binding.getRoot().setTag(binding);
        } else {
            binding = (ItemCategoryBinding) convertView.getTag();
        }
        Category category = getItem(position);
        binding.categoryName.setText(category.name);
        Images.plate(binding.categoryImage, category.image);
        return binding.getRoot();
    }
}
