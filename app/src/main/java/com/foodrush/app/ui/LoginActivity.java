package com.foodrush.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;

import com.foodrush.app.R;
import com.foodrush.app.data.AuthRepository;
import com.foodrush.app.databinding.ActivityLoginBinding;

public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding binding;
    private AuthRepository auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        auth = new AuthRepository(this);

        // Anonymous listener style, as in the click-handling resource.
        binding.loginSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submit();
            }
        });
        binding.loginForgot.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        binding.loginRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void submit() {
        String email = String.valueOf(binding.loginEmail.getText()).trim();
        String password = String.valueOf(binding.loginPassword.getText());
        boolean emailOk = Patterns.EMAIL_ADDRESS.matcher(email).matches();
        binding.loginEmailLayout.setError(emailOk ? null : getString(R.string.error_email));
        binding.loginPasswordLayout.setError(TextUtils.isEmpty(password) ? getString(R.string.error_password) : null);
        if (!emailOk || TextUtils.isEmpty(password)) {
            return;
        }
        setBusy(true);
        auth.login(email, password, new AuthRepository.Callback() {
            @Override
            public void onSuccess() {
                savePasswordInManager();
                startActivity(RoleRouter.home(LoginActivity.this));
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                showMessage(message);
            }
        });
    }

    private void setBusy(boolean busy) {
        binding.loginSubmit.setEnabled(!busy);
        binding.loginProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }
}
