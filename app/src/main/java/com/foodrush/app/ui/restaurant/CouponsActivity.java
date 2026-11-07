package com.foodrush.app.ui.restaurant;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.R;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityManageListBinding;
import com.foodrush.app.databinding.DialogCouponBinding;
import com.foodrush.app.databinding.ItemCouponRowBinding;
import com.foodrush.app.model.Promo;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.util.PriceCalculator;
import com.foodrush.app.util.States;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The restaurant's own coupon codes: a percentage or a flat taka discount on its part of an order,
 * with a minimum order and an expiry date. Customers see them on the restaurant page.
 */
public class CouponsActivity extends BaseActivity {

    private ActivityManageListBinding binding;
    private String restaurantId;
    private final List<Promo> coupons = new ArrayList<>();
    private final CouponAdapter adapter = new CouponAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManageListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.manageBar, R.string.coupons_title);
        restaurantId = new Session(this).restaurantId();
        binding.manageHint.setText(R.string.coupons_hint);
        binding.manageAdd.setVisibility(View.VISIBLE);
        binding.manageAdd.setText(R.string.add_coupon);
        binding.manageAdd.setOnClickListener(v -> edit(null));
        binding.manageList.setLayoutManager(new LinearLayoutManager(this));
        binding.manageList.setAdapter(adapter);
        load();
    }

    private void load() {
        States.loading(binding.manageState);
        MenuRepository.get(this).coupons(restaurantId, new MenuRepository.Listener<List<Promo>>() {
            @Override
            public void onResult(List<Promo> result) {
                coupons.clear();
                coupons.addAll(result);
                adapter.notifyDataSetChanged();
                if (coupons.isEmpty()) {
                    States.empty(binding.manageState, R.string.coupons_empty);
                } else {
                    States.hide(binding.manageState);
                }
            }

            @Override
            public void onError() {
                States.error(binding.manageState, CouponsActivity.this::load);
            }
        });
    }

    private void edit(Promo existing) {
        DialogCouponBinding form = DialogCouponBinding.inflate(getLayoutInflater());
        long[] expiry = {existing == null ? 0 : existing.expiresAt};
        if (existing != null) {
            form.couponCodeInput.setText(existing.code);
            form.couponCodeInput.setEnabled(false);
            form.couponType.check(existing.flat > 0 ? R.id.coupon_type_flat : R.id.coupon_type_percent);
            form.couponValue.setText(String.valueOf(existing.flat > 0 ? existing.flat : existing.percent));
            form.couponMin.setText(String.valueOf(existing.minOrder));
            form.couponActive.setChecked(existing.active);
        }
        renderExpiry(form, expiry[0]);
        form.couponExpiry.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            if (expiry[0] > 0) {
                c.setTimeInMillis(expiry[0]);
            }
            new DatePickerDialog(this, (picker, year, month, day) -> {
                Calendar end = Calendar.getInstance();
                end.set(year, month, day, 23, 59, 59);
                expiry[0] = end.getTimeInMillis();
                renderExpiry(form, expiry[0]);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add_coupon : R.string.edit_coupon)
                .setView(form.getRoot())
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.delete, (d, w) -> delete(existing));
        }
        AlertDialog dialog = builder.create();
        // Validate before closing, so a typo doesn't throw the form away.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Promo promo = read(form, expiry[0]);
            if (promo != null) {
                save(promo);
                dialog.dismiss();
            }
        }));
        dialog.show();
    }

    private void renderExpiry(DialogCouponBinding form, long expiry) {
        form.couponExpiry.setText(expiry == 0 ? getString(R.string.coupon_no_expiry)
                : getString(R.string.coupon_expires, DateFormat.getMediumDateFormat(this).format(new Date(expiry))));
    }

    private Promo read(DialogCouponBinding form, long expiry) {
        String code = String.valueOf(form.couponCodeInput.getText()).trim().toUpperCase(Locale.US);
        boolean flat = form.couponType.getCheckedButtonId() == R.id.coupon_type_flat;
        int value = number(form.couponValue.getText());
        int min = Math.max(0, number(form.couponMin.getText()));
        boolean codeOk = code.matches("[A-Z0-9]{4,16}") && MenuRepository.get(this).platformPromo(code) == null;
        boolean valueOk = flat ? value > 0 : value > 0 && value <= 80;
        form.couponCodeLayout.setError(codeOk ? null : getString(R.string.error_coupon_code));
        form.couponValueLayout.setError(valueOk ? null : getString(flat ? R.string.error_price : R.string.error_percent));
        if (!codeOk || !valueOk) {
            return null;
        }
        String title = flat ? getString(R.string.coupon_title_flat, PriceCalculator.format(value))
                : getString(R.string.coupon_title_percent, value);
        String subtitle = min > 0 ? getString(R.string.coupon_subtitle_min, PriceCalculator.format(min))
                : getString(R.string.coupon_subtitle_any);
        return new Promo(code, title, subtitle, flat ? 0 : value, flat ? value : 0, false, min, restaurantId, expiry,
                form.couponActive.isChecked());
    }

    private static int number(CharSequence text) {
        try {
            return Integer.parseInt(String.valueOf(text).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void save(Promo promo) {
        MenuRepository.get(this).saveCoupon(promo, new MenuRepository.Listener<Void>() {
            @Override
            public void onResult(Void result) {
                load();
            }

            @Override
            public void onError() {
                showMessage(getString(R.string.coupon_save_failed));
            }
        });
    }

    private void delete(Promo promo) {
        MenuRepository.get(this).deleteCoupon(promo.code, new MenuRepository.Listener<Void>() {
            @Override
            public void onResult(Void result) {
                load();
            }

            @Override
            public void onError() {
                showMessage(getString(R.string.save_failed));
            }
        });
    }

    private class CouponAdapter extends RecyclerView.Adapter<CouponAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemCouponRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Promo promo = coupons.get(position);
            holder.binding.couponCode.setText(promo.code);
            String state = promo.usableAt(System.currentTimeMillis()) ? "" : " · " + getString(R.string.coupon_inactive);
            holder.binding.couponText.setText(getString(R.string.coupon_line, promo.title, promo.subtitle) + state);
            holder.binding.getRoot().setOnClickListener(v -> edit(promo));
        }

        @Override
        public int getItemCount() {
            return coupons.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemCouponRowBinding binding;

            Holder(ItemCouponRowBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
