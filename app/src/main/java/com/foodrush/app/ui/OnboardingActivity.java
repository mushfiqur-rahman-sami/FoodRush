package com.foodrush.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.foodrush.app.R;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityOnboardingBinding;
import com.foodrush.app.databinding.ItemOnboardingBinding;

public class OnboardingActivity extends BaseActivity {

    private static final int[][] PAGES = {
            {R.drawable.ic_restaurant, R.string.onboarding_title_1, R.string.onboarding_text_1},
            {R.drawable.ic_heart, R.string.onboarding_title_2, R.string.onboarding_text_2},
            {R.drawable.ic_rider, R.string.onboarding_title_3, R.string.onboarding_text_3},
    };

    private ActivityOnboardingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.onboardingPager.setAdapter(new PageAdapter());
        binding.onboardingPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.onboardingNext.setText(position == PAGES.length - 1 ? R.string.get_started : R.string.next);
            }
        });
        binding.onboardingNext.setOnClickListener(v -> {
            int current = binding.onboardingPager.getCurrentItem();
            if (current < PAGES.length - 1) {
                binding.onboardingPager.setCurrentItem(current + 1);
            } else {
                finishOnboarding();
            }
        });
        binding.onboardingSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        new Session(this).setOnboardingDone();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private static class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PageHolder(ItemOnboardingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.binding.onboardingIcon.setImageResource(PAGES[position][0]);
            holder.binding.onboardingTitle.setText(PAGES[position][1]);
            holder.binding.onboardingText.setText(PAGES[position][2]);
        }

        @Override
        public int getItemCount() {
            return PAGES.length;
        }
    }

    private static class PageHolder extends RecyclerView.ViewHolder {
        final ItemOnboardingBinding binding;

        PageHolder(ItemOnboardingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
