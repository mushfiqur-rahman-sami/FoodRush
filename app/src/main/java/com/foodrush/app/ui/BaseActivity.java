package com.foodrush.app.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.foodrush.app.BuildConfig;
import com.foodrush.app.R;
import com.foodrush.app.databinding.ViewTopBarBinding;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.receiver.ConnectivityReceiver;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.HashMap;
import java.util.Map;

/** Shared by every screen: lifecycle logging, offline banner, edge-to-edge insets and permissions. */
public abstract class BaseActivity extends AppCompatActivity implements ConnectivityReceiver.Listener {

    private final String tag = getClass().getSimpleName();
    private final ConnectivityReceiver connectivityReceiver = new ConnectivityReceiver(this);
    private final Map<Integer, Runnable> pendingPermissions = new HashMap<>();
    private int nextRequestCode = 100;
    @Nullable
    private Snackbar offlineBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean nightMode = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!nightMode);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(!nightMode);
        Log.d(tag, "onCreate restored=" + (savedInstanceState != null));
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        View target = insetTarget(view);
        int left = target.getPaddingLeft();
        int top = target.getPaddingTop();
        int right = target.getPaddingRight();
        int bottom = target.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** The view padded clear of the system bars. Override when the root ignores padding. */
    protected View insetTarget(View root) {
        return root;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(tag, "onStart");
        connectivityReceiver.register(this);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(tag, "onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(tag, "onResume");
    }

    @Override
    protected void onPause() {
        Log.d(tag, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(tag, "onStop");
        unregisterReceiver(connectivityReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(tag, "onDestroy finishing=" + isFinishing());
        super.onDestroy();
    }

    @Override
    public void onConnectivityChanged(boolean online, boolean airplaneMode) {
        View root = findViewById(android.R.id.content);
        if (online) {
            if (offlineBar != null) {
                offlineBar.dismiss();
                offlineBar = null;
            }
        } else if (offlineBar == null && root != null) {
            offlineBar = Snackbar.make(root, airplaneMode ? R.string.airplane_banner : R.string.offline_banner,
                    Snackbar.LENGTH_INDEFINITE);
            offlineBar.show();
        }
    }

    protected void setUpTopBar(ViewTopBarBinding bar, @StringRes int title) {
        bar.topTitle.setText(title);
        bar.topBack.setOnClickListener(v -> finish());
    }

    /** Starts an implicit intent, which may have no handler on this phone. */
    protected void startImplicit(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app_for_action, Toast.LENGTH_SHORT).show();
        }
    }

    protected void dial(String phone) {
        startImplicit(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
    }

    /**
     * Opens the phone's dialer with someone on an order: {@code field} is "riderPhone" or
     * "customerPhone" from the order's private contact record.
     */
    protected void dialOrderPerson(String orderId, String field) {
        OrderStore.contact(orderId, contact -> {
            Object number = contact.get(field);
            if (number == null || number.toString().isEmpty()) {
                showMessage(getString(R.string.no_phone_number));
            } else {
                dial(number.toString());
            }
        });
    }

    /**
     * Tells the phone's password manager (such as Google Password Manager) that the sign-in form was
     * submitted successfully, so it offers to save the password, or to update an old saved one after
     * a reset. Call it just before leaving a screen with a successful sign-in, sign-up or reset.
     */
    protected void savePasswordInManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofill = getSystemService(AutofillManager.class);
            if (autofill != null) {
                autofill.commit();
            }
        }
    }

    protected void showMessage(CharSequence message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Runs the action once the permission is granted. Explains first when Android says a rationale
     * is due, and points to app settings when the user has denied it permanently.
     */
    protected void withPermission(String permission, @StringRes int rationale, Runnable onGranted) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            onGranted.run();
            return;
        }
        int code = nextRequestCode++;
        pendingPermissions.put(code, onGranted);
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_title)
                    .setMessage(rationale)
                    .setPositiveButton(R.string.ok, (d, w) ->
                            ActivityCompat.requestPermissions(this, new String[]{permission}, code))
                    .setNegativeButton(R.string.cancel, (d, w) -> pendingPermissions.remove(code))
                    .show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, code);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        Runnable action = pendingPermissions.remove(requestCode);
        if (action == null || results.length == 0) {
            return;
        }
        if (results[0] == PackageManager.PERMISSION_GRANTED) {
            action.run();
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
            showOpenSettings();
        }
    }

    private void showOpenSettings() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_denied_settings)
                .setPositiveButton(R.string.open_settings, (d, w) -> startImplicit(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + BuildConfig.APPLICATION_ID))))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
