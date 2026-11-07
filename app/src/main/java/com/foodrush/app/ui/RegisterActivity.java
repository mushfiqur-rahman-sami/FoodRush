package com.foodrush.app.ui;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import com.foodrush.app.R;
import com.foodrush.app.data.AuthRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityRegisterBinding;
import com.foodrush.app.util.Bangladesh;

/** Sign-up as a customer, a rider or a restaurant. A Bangladeshi mobile number is required for all. */
public class RegisterActivity extends BaseActivity {

    private static final int MIN_PASSWORD = 6;

    private ActivityRegisterBinding binding;
    private AuthRepository auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        auth = new AuthRepository(this);

        binding.registerRoleCustomer.setChecked(true);
        // Riders and restaurants work with other phones, which needs Firebase.
        boolean shared = auth.usesFirebase();
        binding.registerRoleRider.setEnabled(shared);
        binding.registerRoleRestaurant.setEnabled(shared);
        binding.registerRoleNote.setVisibility(shared ? View.GONE : View.VISIBLE);
        binding.registerRoles.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                binding.registerRoleHint.setText(checkedId == R.id.register_role_rider ? R.string.role_rider_hint
                        : checkedId == R.id.register_role_restaurant ? R.string.role_restaurant_hint
                        : R.string.role_customer_hint);
            }
        });
        binding.registerRoleHint.setText(R.string.role_customer_hint);
        binding.registerSubmit.setOnClickListener(v -> submit());
        binding.registerLogin.setOnClickListener(v -> finish());
    }

    private String role() {
        int checked = binding.registerRoles.getCheckedButtonId();
        if (checked == R.id.register_role_rider) {
            return Session.ROLE_RIDER;
        }
        if (checked == R.id.register_role_restaurant) {
            return Session.ROLE_RESTAURANT;
        }
        return Session.ROLE_CUSTOMER;
    }

    private void submit() {
        String name = String.valueOf(binding.registerName.getText()).trim();
        String email = String.valueOf(binding.registerEmail.getText()).trim();
        String phone = Bangladesh.normalizePhone(String.valueOf(binding.registerPhone.getText()));
        String password = String.valueOf(binding.registerPassword.getText());
        boolean nameOk = !name.isEmpty();
        boolean emailOk = Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean phoneOk = phone != null;
        boolean passwordOk = password.length() >= MIN_PASSWORD;
        binding.registerNameLayout.setError(nameOk ? null : getString(R.string.error_name));
        binding.registerEmailLayout.setError(emailOk ? null : getString(R.string.error_email));
        binding.registerPhoneLayout.setError(phoneOk ? null : getString(R.string.error_phone));
        binding.registerPasswordLayout.setError(passwordOk ? null : getString(R.string.error_password));
        if (!nameOk || !emailOk || !phoneOk || !passwordOk) {
            return;
        }
        binding.registerSubmit.setEnabled(false);
        binding.registerProgress.setVisibility(View.VISIBLE);
        auth.register(name, email, phone, password, role(), new AuthRepository.Callback() {
            @Override
            public void onSuccess() {
                savePasswordInManager();
                startActivity(RoleRouter.home(RegisterActivity.this));
            }

            @Override
            public void onError(String message) {
                binding.registerSubmit.setEnabled(true);
                binding.registerProgress.setVisibility(View.GONE);
                showMessage(message);
            }
        });
    }
}
