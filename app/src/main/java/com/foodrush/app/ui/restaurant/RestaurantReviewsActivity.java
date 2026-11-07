package com.foodrush.app.ui.restaurant;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.R;
import com.foodrush.app.adapter.RestaurantPageAdapter;
import com.foodrush.app.data.FirebaseSync;
import com.foodrush.app.data.ReviewRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityManageListBinding;
import com.foodrush.app.databinding.ItemReviewBinding;
import com.foodrush.app.model.Review;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.States;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** Customers' reviews of this restaurant. Tap one to reply; the reply shows under it for everyone. */
public class RestaurantReviewsActivity extends BaseActivity {

    private ActivityManageListBinding binding;
    private String restaurantId;
    private final List<Review> reviews = new ArrayList<>();
    private final ReviewAdapter adapter = new ReviewAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManageListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.manageBar, R.string.reviews_title);
        binding.manageHint.setText(R.string.reviews_hint);
        restaurantId = new Session(this).restaurantId();
        binding.manageList.setLayoutManager(new LinearLayoutManager(this));
        binding.manageList.setAdapter(adapter);
        States.loading(binding.manageState);
        FirebaseSync.pullReviews(this, restaurantId, this::load);
    }

    private void load() {
        AppExecutors.load(() -> ReviewRepository.forRestaurant(this, restaurantId), result -> {
            reviews.clear();
            reviews.addAll(result);
            adapter.notifyDataSetChanged();
            if (reviews.isEmpty()) {
                States.empty(binding.manageState, R.string.no_reviews_owner);
            } else {
                States.hide(binding.manageState);
            }
        });
    }

    private void reply(Review review) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(review.reply);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.reply_to, review.author))
                .setMessage(review.text)
                .setView(input)
                .setPositiveButton(R.string.send, (d, w) -> {
                    String text = input.getText().toString().trim();
                    FirebaseSync.replyToReview(review, text, () -> {
                        Review updated = new Review(review.id, review.restaurantId, review.author, review.rating,
                                review.text, review.createdAt, text);
                        AppExecutors.load(() -> {
                            ReviewRepository.save(this, updated);
                            return true;
                        }, done -> load());
                    }, () -> showMessage(getString(R.string.save_failed)));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemReviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Review review = reviews.get(position);
            RestaurantPageAdapter.bindReview(holder.binding, review);
            holder.binding.getRoot().setOnClickListener(v -> reply(review));
        }

        @Override
        public int getItemCount() {
            return reviews.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemReviewBinding binding;

            Holder(ItemReviewBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
