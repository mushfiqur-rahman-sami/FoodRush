package com.foodrush.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import com.foodrush.app.R;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityPaymentBinding;
import com.foodrush.app.util.Bangladesh;
import com.foodrush.app.util.CardValidator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;

/**
 * Dummy payment picker: cash, bKash, Nagad, Rocket or card. Real wallet APIs need a merchant
 * account, so wallets are imitated with a test OTP and PIN. Nothing is charged or stored.
 */
public class PaymentActivity extends BaseActivity {

    public static final String EXTRA_LABEL = "label";
    /** The OTP every dummy wallet accepts. */
    public static final String TEST_OTP = "123456";

    private ActivityPaymentBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.paymentGroup.setOnCheckedChangeListener((group, checkedId) -> {
            binding.cardForm.setVisibility(checkedId == R.id.pay_card ? View.VISIBLE : View.GONE);
            binding.walletForm.setVisibility(isWallet(checkedId) ? View.VISIBLE : View.GONE);
        });
        String current = new Session(this).payment();
        if (current.startsWith(getString(R.string.pay_bkash))) {
            binding.payBkash.setChecked(true);
        } else if (current.startsWith(getString(R.string.pay_nagad))) {
            binding.payNagad.setChecked(true);
        } else if (current.startsWith(getString(R.string.pay_rocket))) {
            binding.payRocket.setChecked(true);
        } else {
            binding.payCash.setChecked(true);
        }
        binding.walletNumber.setText(new Session(this).phone().replace("+880", "0"));
        binding.paymentConfirm.setOnClickListener(v -> confirm());
    }

    private static boolean isWallet(int id) {
        return id == R.id.pay_bkash || id == R.id.pay_nagad || id == R.id.pay_rocket;
    }

    private String walletName(int id) {
        return getString(id == R.id.pay_bkash ? R.string.pay_bkash : id == R.id.pay_nagad ? R.string.pay_nagad
                : R.string.pay_rocket);
    }

    private void confirm() {
        int checked = binding.paymentGroup.getCheckedRadioButtonId();
        if (checked == R.id.pay_card) {
            if (cardFormIsValid()) {
                finishWith(CardValidator.maskedLabel(String.valueOf(binding.cardNumber.getText())));
            }
        } else if (isWallet(checked)) {
            String number = Bangladesh.normalizePhone(String.valueOf(binding.walletNumber.getText()));
            binding.walletNumberLayout.setError(number == null ? getString(R.string.error_phone) : null);
            if (number != null) {
                askOtp(walletName(checked), number);
            }
        } else {
            finishWith(Session.PAYMENT_CASH);
        }
    }

    /** Imitates a wallet's OTP and PIN steps. */
    private void askOtp(String wallet, String number) {
        EditText otp = new EditText(this);
        otp.setInputType(InputType.TYPE_CLASS_NUMBER);
        otp.setHint(R.string.otp_hint);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.otp_title, wallet))
                .setMessage(getString(R.string.otp_text, number))
                .setView(otp)
                .setPositiveButton(R.string.verify, (d, w) -> {
                    if (!TEST_OTP.equals(otp.getText().toString().trim())) {
                        showMessage(getString(R.string.otp_wrong));
                        return;
                    }
                    askPin(wallet, number);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void askPin(String wallet, String number) {
        EditText pin = new EditText(this);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setHint(R.string.wallet_pin_hint);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.wallet_pin_title, wallet))
                .setView(pin)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    if (!pin.getText().toString().matches("\\d{4,5}")) {
                        showMessage(getString(R.string.wallet_pin_wrong));
                        return;
                    }
                    finishWith(wallet + " ••" + number.substring(number.length() - 3));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void finishWith(String label) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_LABEL, label));
        finish();
    }

    private boolean cardFormIsValid() {
        Calendar now = Calendar.getInstance();
        boolean number = CardValidator.isValidNumber(String.valueOf(binding.cardNumber.getText()));
        boolean expiry = CardValidator.isValidExpiry(String.valueOf(binding.cardExpiry.getText()).trim(),
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1);
        boolean cvv = CardValidator.isValidCvv(String.valueOf(binding.cardCvv.getText()));
        binding.cardNumberLayout.setError(number ? null : getString(R.string.error_card));
        binding.cardExpiryLayout.setError(expiry ? null : getString(R.string.error_expiry));
        binding.cardCvvLayout.setError(cvv ? null : getString(R.string.error_cvv));
        return number && expiry && cvv;
    }
}
