package com.foodrush.app.data;

import android.content.Context;

import com.foodrush.app.R;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Turns Firebase sign-in errors into short messages anyone can understand. */
public final class AuthErrors {

    private AuthErrors() {
    }

    public static String message(Context context, Exception e) {
        return context.getString(messageId(e));
    }

    static int messageId(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return R.string.auth_error_network;
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return R.string.auth_error_too_many;
        }
        // Checked before invalid credentials, because a weak password is a kind of invalid credential.
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return R.string.auth_error_weak_password;
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return R.string.auth_error_email_taken;
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) e).getErrorCode();
            return "ERROR_USER_DISABLED".equals(code) ? R.string.auth_error_disabled : R.string.auth_error_no_account;
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            String code = ((FirebaseAuthInvalidCredentialsException) e).getErrorCode();
            return "ERROR_INVALID_EMAIL".equals(code) ? R.string.error_email : R.string.auth_error_wrong_password;
        }
        if (e instanceof FirebaseAuthException) {
            return R.string.auth_error_generic;
        }
        return R.string.auth_error_generic;
    }
}
