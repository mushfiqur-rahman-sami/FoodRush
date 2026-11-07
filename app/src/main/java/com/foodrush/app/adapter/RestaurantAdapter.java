package com.foodrush.app.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.R;
import com.foodrush.app.databinding.ItemRestaurantBinding;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.util.Images;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Restaurants nearest first. Riding times come from Dijkstra on real roads once they are worked out. */
public class RestaurantAdapter extends RecyclerView.Adapter<RestaurantAdapter.Holder> {

    public interface Listener {
        void onRestaurant(Restaurant restaurant);
    }

    private final List<Restaurant> restaurants = new ArrayList<>();
    private Map<String, Double> minutes = new HashMap<>();
    private final Listener listener;
    private final boolean showEta;

    public RestaurantAdapter(Listener listener) {
        this(listener, true);
    }

    /** @param showEta false shows the city instead of a delivery time, for lists that are not about delivery */
    public RestaurantAdapter(Listener listener, boolean showEta) {
        this.listener = listener;
        this.showEta = showEta;
    }

    public void submit(List<Restaurant> sorted, Map<String, Double> roadMinutes) {
        restaurants.clear();
        restaurants.addAll(sorted);
        minutes = roadMinutes == null ? new HashMap<>() : roadMinutes;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemRestaurantBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Restaurant restaurant = restaurants.get(position);
        holder.binding.restaurantName.setText(restaurant.name);
        holder.binding.restaurantCuisine.setText(holder.itemView.getContext().getString(R.string.restaurant_line,
                restaurant.cuisine, restaurant.areaLabel()));
        holder.binding.restaurantRating.setText(String.format(Locale.US, "%.1f", restaurant.rating));
        Double road = minutes.get(restaurant.id);
        if (!showEta) {
            holder.binding.restaurantEta.setText(restaurant.city);
        } else if (!restaurant.isOpenAt(Calendar.getInstance())) {
            holder.binding.restaurantEta.setText(R.string.closed_now);
        } else if (road == null) {
            holder.binding.restaurantEta.setText(R.string.eta_working);
        } else {
            holder.binding.restaurantEta.setText(etaRange(road + restaurant.busyMinutes));
        }
        Images.cover(holder.binding.restaurantImage, restaurant.image);
        holder.binding.getRoot().setOnClickListener(v -> listener.onRestaurant(restaurant));
    }

    /** "25-30 min": typical kitchen time plus the riding time. */
    public static String etaRange(double roadMinutes) {
        int low = 15 + (int) Math.ceil(roadMinutes);
        return String.format(Locale.US, "%d-%d min", low, low + 5);
    }

    @Override
    public int getItemCount() {
        return restaurants.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemRestaurantBinding binding;

        Holder(ItemRestaurantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
