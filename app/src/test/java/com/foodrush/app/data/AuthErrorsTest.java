package com.foodrush.app.data;

import static org.junit.Assert.assertEquals;

import com.foodrush.app.R;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

import org.junit.Test;

public class AuthErrorsTest {

    @Test
    public void firebaseErrorsBecomePlainMessages() {
        assertEquals(R.string.auth_error_wrong_password, AuthErrors.messageId(
                new FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "The supplied auth credential is incorrect")));
        assertEquals(R.string.error_email, AuthErrors.messageId(
                new FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "badly formatted")));
        assertEquals(R.string.auth_error_no_account, AuthErrors.messageId(
                new FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no user")));
        assertEquals(R.string.auth_error_disabled, AuthErrors.messageId(
                new FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled")));
        assertEquals(R.string.auth_error_email_taken, AuthErrors.messageId(
                new FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "in use")));
        assertEquals(R.string.auth_error_weak_password, AuthErrors.messageId(
                new FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak", "too short")));
        assertEquals(R.string.auth_error_network, AuthErrors.messageId(new FirebaseNetworkException("offline")));
        assertEquals(R.string.auth_error_too_many, AuthErrors.messageId(new FirebaseTooManyRequestsException("slow down")));
        assertEquals(R.string.auth_error_generic, AuthErrors.messageId(new IllegalStateException("?")));
    }
}
