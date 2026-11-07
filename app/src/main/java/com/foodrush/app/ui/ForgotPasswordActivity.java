package com.foodrush.app.ui;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import com.foodrush.app.R;
import com.foodrush.app.data.AuthRepository;
import com.foodrush.app.databinding.ActivityForgotPasswordBinding;

public class ForgotPasswordActivity extends BaseActivity {

    private ActivityForgotPasswordBinding binding;
    private AuthRepository auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        auth = new AuthRepository(this);

        boolean firebase = auth.usesFirebase();
        binding.forgotHint.setText(firebase ? R.string.reset_hint_firebase : R.string.reset_hint_local);
        binding.forgotPasswordLayout.setVisibility(firebase ? View.GONE : View.VISIBLE);
        binding.forgotSubmit.setText(firebase ? R.string.reset_send : R.string.reset_save);
        binding.forgotSubmit.setOnClickListener(v -> submit(firebase));
    }

    private void submit(boolean firebase) {
        String email = String.valueOf(binding.forgotEmail.getText()).trim();
        String password = String.valueOf(binding.forgotPassword.getText());
        boolean emailOk = Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean passwordOk = firebase || password.length() >= 6;
        binding.forgotEmailLayout.setError(emailOk ? null : getString(R.string.error_email));
        binding.forgotPasswordLayout.setError(passwordOk ? null : getString(R.string.error_password));
        if (!emailOk || !passwordOk) {
            return;
        }
        AuthRepository.Callback callback = new AuthRepository.Callback() {
            @Override
            public void onSuccess() {
                if (!firebase) {
                    // The new password was typed here, so offer to replace the saved one.
                    savePasswordInManager();
                }
                Toast.makeText(ForgotPasswordActivity.this,
                        firebase ? R.string.reset_sent : R.string.reset_done, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                showMessage(message);
            }
        };
        if (firebase) {
            auth.sendPasswordReset(email, callback);
        } else {
            auth.resetLocalPassword(email, password, callback);
        }
    }
}
