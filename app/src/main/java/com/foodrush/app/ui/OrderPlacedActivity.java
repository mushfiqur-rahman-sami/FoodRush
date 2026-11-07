package com.foodrush.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.foodrush.app.R;
import com.foodrush.app.data.OrderRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityOrderPlacedBinding;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.PriceCalculator;


public class OrderPlacedActivity extends BaseActivity {

    public static final String EXTRA_ORDER_ID = "order_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityOrderPlacedBinding binding = ActivityOrderPlacedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        AppExecutors.load(() -> OrderRepository.find(this, orderId), order -> {
            if (order == null) {
                finish();
                return;
            }
            binding.placedMessage.setText(getString(R.string.order_placed_text, order.etaMinutes));
            binding.placedNumber.setText(getString(R.string.order_number, order.shortId()));
            String pin = new Session(this).pin(order.id);
            binding.placedPin.setVisibility(pin == null ? View.GONE : View.VISIBLE);
            binding.placedPin.setText(getString(R.string.pin_line, pin));
            binding.placedItems.setText(order.items);
            binding.placedAddress.setText(order.address);
            binding.placedPayment.setText(order.payment);
            binding.placedSubtotal.setText(PriceCalculator.format(order.subtotal));
            binding.placedDiscount.setText("-" + PriceCalculator.format(order.discount));
            binding.placedVat.setText(PriceCalculator.format(order.vat));
            binding.placedFee.setText(PriceCalculator.format(order.fee));
            binding.placedTotal.setText(PriceCalculator.format(order.total));
        });

        binding.placedTrack.setOnClickListener(v -> {
            startActivity(new Intent(this, TrackingActivity.class).putExtra(TrackingActivity.EXTRA_ORDER_ID, orderId));
            finish();
        });
        binding.placedHome.setOnClickListener(v -> finish());
    }
}
