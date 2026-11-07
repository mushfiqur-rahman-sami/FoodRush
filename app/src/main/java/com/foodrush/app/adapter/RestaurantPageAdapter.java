package com.foodrush.app.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.R;
import com.foodrush.app.databinding.ItemCouponRowBinding;
import com.foodrush.app.databinding.ItemMenuDishBinding;
import com.foodrush.app.databinding.ItemReviewBinding;
import com.foodrush.app.databinding.ItemSectionHeaderBinding;
import com.foodrush.app.model.Dish;
import com.foodrush.app.model.Promo;
import com.foodrush.app.model.Review;
import com.foodrush.app.util.Images;
import com.foodrush.app.util.PriceCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One list with several view types: section headers, the restaurant's coupons, menu dishes grouped by
 * category, reviews (with the restaurant's replies) and a "none yet" row.
 */
public class RestaurantPageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onDish(Dish dish, int dishIndex);

        void onAddReview();

        void onCoupon(Promo coupon);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_DISH = 1;
    private static final int TYPE_REVIEW = 2;
    private static final int TYPE_NO_REVIEWS = 3;
    private static final int TYPE_COUPON = 4;

    private static final class Row {
        final int type;
        final Object value;
        final int dishIndex;

        Row(int type, Object value, int dishIndex) {
            this.type = type;
            this.value = value;
            this.dishIndex = dishIndex;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;
    private List<Dish> dishes = new ArrayList<>();
    private List<Review> reviews = new ArrayList<>();
    private List<Promo> coupons = new ArrayList<>();

    public RestaurantPageAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setDishes(List<Dish> newDishes) {
        dishes = newDishes;
        rebuild();
    }

    public void setReviews(List<Review> newReviews) {
        reviews = newReviews;
        rebuild();
    }

    public void setCoupons(List<Promo> newCoupons) {
        coupons = newCoupons;
        rebuild();
    }

    private void rebuild() {
        rows.clear();
        if (!coupons.isEmpty()) {
            rows.add(new Row(TYPE_HEADER, R.string.offers_section, 0));
            for (Promo coupon : coupons) {
                rows.add(new Row(TYPE_COUPON, coupon, 0));
            }
        }
        Map<String, List<Integer>> byCategory = new LinkedHashMap<>();
        for (int i = 0; i < dishes.size(); i++) {
            byCategory.computeIfAbsent(dishes.get(i).category, k -> new ArrayList<>()).add(i);
        }
        if (byCategory.isEmpty()) {
            rows.add(new Row(TYPE_HEADER, R.string.menu_section, 0));
        }
        for (Map.Entry<String, List<Integer>> entry : byCategory.entrySet()) {
            rows.add(new Row(TYPE_HEADER, entry.getKey(), 0));
            for (int index : entry.getValue()) {
                rows.add(new Row(TYPE_DISH, dishes.get(index), index));
            }
        }
        rows.add(new Row(TYPE_HEADER, R.string.reviews_section, 0));
        if (reviews.isEmpty()) {
            rows.add(new Row(TYPE_NO_REVIEWS, null, 0));
        }
        for (Review review : reviews) {
            rows.add(new Row(TYPE_REVIEW, review, 0));
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_DISH:
                return new DishHolder(ItemMenuDishBinding.inflate(inflater, parent, false));
            case TYPE_REVIEW:
                return new ReviewHolder(ItemReviewBinding.inflate(inflater, parent, false));
            case TYPE_COUPON:
                return new CouponHolder(ItemCouponRowBinding.inflate(inflater, parent, false));
            case TYPE_NO_REVIEWS:
                return new PlainHolder(inflater.inflate(R.layout.item_no_reviews, parent, false));
            default:
                return new HeaderHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ItemSectionHeaderBinding binding = ((HeaderHolder) holder).binding;
            boolean isReviews = Integer.valueOf(R.string.reviews_section).equals(row.value);
            if (row.value instanceof Integer) {
                binding.sectionTitle.setText((Integer) row.value);
            } else {
                binding.sectionTitle.setText(String.valueOf(row.value));
            }
            binding.sectionAction.setVisibility(isReviews ? View.VISIBLE : View.GONE);
            binding.sectionAction.setOnClickListener(v -> listener.onAddReview());
        } else if (holder instanceof DishHolder) {
            bindDish(((DishHolder) holder).binding, (Dish) row.value, row.dishIndex);
        } else if (holder instanceof ReviewHolder) {
            bindReview(((ReviewHolder) holder).binding, (Review) row.value);
        } else if (holder instanceof CouponHolder) {
            Promo coupon = (Promo) row.value;
            ItemCouponRowBinding binding = ((CouponHolder) holder).binding;
            binding.couponCode.setText(coupon.code);
            binding.couponText.setText(binding.getRoot().getContext().getString(R.string.coupon_line, coupon.title,
                    coupon.subtitle));
            binding.getRoot().setOnClickListener(v -> listener.onCoupon(coupon));
        }
    }

    private void bindDish(ItemMenuDishBinding binding, Dish dish, int index) {
        binding.menuName.setText(dish.name);
        binding.menuCategory.setText(dish.available ? dish.description : binding.getRoot().getContext()
                .getString(R.string.sold_out));
        binding.menuPrice.setText(PriceCalculator.format(dish.fromPrice()));
        binding.menuOldPrice.setVisibility(dish.discounted() ? View.VISIBLE : View.GONE);
        binding.menuOldPrice.setText(PriceCalculator.format(dish.price));
        binding.menuOldPrice.setPaintFlags(binding.menuOldPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        binding.menuRating.setText(String.format(Locale.US, "%.1f", dish.rating));
        binding.getRoot().setAlpha(dish.available ? 1f : 0.5f);
        Images.plate(binding.menuImage, dish.image);
        binding.getRoot().setOnClickListener(v -> listener.onDish(dish, index));
    }

    public static void bindReview(ItemReviewBinding binding, Review review) {
        binding.reviewAuthor.setText(review.author);
        binding.reviewText.setText(review.text);
        binding.reviewStars.setRating(review.rating);
        binding.reviewReply.setVisibility(review.reply.isEmpty() ? View.GONE : View.VISIBLE);
        binding.reviewReply.setText(binding.getRoot().getContext().getString(R.string.review_reply, review.reply));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemSectionHeaderBinding binding;

        HeaderHolder(ItemSectionHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class DishHolder extends RecyclerView.ViewHolder {
        final ItemMenuDishBinding binding;

        DishHolder(ItemMenuDishBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class ReviewHolder extends RecyclerView.ViewHolder {
        final ItemReviewBinding binding;

        ReviewHolder(ItemReviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class CouponHolder extends RecyclerView.ViewHolder {
        final ItemCouponRowBinding binding;

        CouponHolder(ItemCouponRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class PlainHolder extends RecyclerView.ViewHolder {
        PlainHolder(View view) {
            super(view);
        }
    }
}
