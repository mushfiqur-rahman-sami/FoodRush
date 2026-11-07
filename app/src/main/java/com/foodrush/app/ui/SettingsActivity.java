package com.foodrush.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import com.foodrush.app.BuildConfig;
import com.foodrush.app.FoodRushApp;
import com.foodrush.app.R;
import com.foodrush.app.data.Cloud;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivitySettingsBinding;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySettingsBinding binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.settingsBar, R.string.settings_title);
        Session session = new Session(this);

        binding.settingDark.setChecked(session.darkMode());
        binding.settingDark.setOnCheckedChangeListener((button, checked) -> {
            session.setDarkMode(checked);
            FoodRushApp.applyTheme(checked);
        });
        binding.settingPromos.setChecked(session.promoNotifications());
        binding.settingPromos.setOnCheckedChangeListener((button, checked) -> session.setPromoNotifications(checked));

        binding.settingSystem.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startImplicit(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } else {
                startImplicit(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        binding.settingTerms.setOnClickListener(v -> WebPageActivity.open(this, R.string.terms, "terms.html"));
        binding.settingPrivacy.setOnClickListener(v -> WebPageActivity.open(this, R.string.privacy, "privacy.html"));
        binding.settingCredits.setOnClickListener(v -> WebPageActivity.open(this, R.string.photo_credits, "photo_credits.html"));
        binding.settingCloud.setText(Cloud.isConfigured(this) ? R.string.cloud_on : R.string.cloud_off);
        binding.settingVersion.setText(getString(R.string.version_label, BuildConfig.VERSION_NAME));
    }
}
