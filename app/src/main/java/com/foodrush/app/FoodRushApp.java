package com.foodrush.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.foodrush.app.data.Session;
import com.foodrush.app.service.PromoRefreshWorker;
import com.foodrush.app.util.NotificationHelper;

import org.osmdroid.config.Configuration;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class FoodRushApp extends Application {

    private static int startedActivities;

    @Override
    public void onCreate() {
        super.onCreate();
        applyTheme(new Session(this).darkMode());
        NotificationHelper.createChannels(this);

        // OpenStreetMap's tile policy asks every app to identify itself.
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        // Keep map tiles in private storage; the library's default location needs storage access.
        File mapDir = new File(getCacheDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(mapDir);
        Configuration.getInstance().setOsmdroidTileCache(new File(mapDir, "tiles"));

        WorkManager.getInstance(this).enqueueUniquePeriodicWork("promo-refresh", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(PromoRefreshWorker.class, 1, TimeUnit.DAYS).build());

        // Counts visible screens, so an incoming call opens its screen directly when the app is in use.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                startedActivities++;
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                startedActivities--;
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }

    public static boolean isInForeground() {
        return startedActivities > 0;
    }

    public static void applyTheme(boolean dark) {
        AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
