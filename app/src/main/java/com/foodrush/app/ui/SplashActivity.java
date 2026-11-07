package com.foodrush.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.foodrush.app.data.Catalog;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivitySplashBinding;
import com.foodrush.app.util.AppExecutors;

public class SplashActivity extends BaseActivity {

    private static final long SPLASH_MILLIS = 900;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable next = this::openNextScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(ActivitySplashBinding.inflate(getLayoutInflater()).getRoot());
        // Parse the bundled XML before the first screen needs it.
        AppExecutors.io(() -> Catalog.get(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(next, SPLASH_MILLIS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(next);
        super.onPause();
    }

    private void openNextScreen() {
        Session session = new Session(this);
        if (!session.onboardingDone()) {
            startActivity(new Intent(this, OnboardingActivity.class));
        } else if (session.isSignedIn()) {
            startActivity(RoleRouter.home(this));
        } else {
            startActivity(new Intent(this, LoginActivity.class));
        }
        finish();
    }
}
