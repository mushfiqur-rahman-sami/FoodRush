package com.foodrush.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteConstraintException;
import android.util.Base64;

import com.foodrush.app.R;
import com.foodrush.app.util.AppExecutors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Firebase Auth when configured; otherwise salted, hashed accounts stored on this device. Riders and
 * restaurants need other phones to see their work, so those roles exist only with Firebase.
 */
public final class AuthRepository {

    public interface Callback {
        void onSuccess();

        void onError(String message);
    }

    private static final int PBKDF2_ITERATIONS = 20000;
    private static final int HASH_BITS = 256;

    private final Context context;
    private final Session session;

    public AuthRepository(Context context) {
        this.context = context.getApplicationContext();
        this.session = new Session(context);
    }

    public boolean usesFirebase() {
        return Cloud.isConfigured(context);
    }

    public void register(String name, String email, String phone, String password, String role, Callback callback) {
        if (usesFirebase()) {
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        FirebaseUser user = result.getUser();
                        if (user == null) {
                            callback.onError(context.getString(R.string.auth_error_generic));
                            return;
                        }
                        user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build());
                        session.signIn(user.getUid(), name, email, phone, role, null);
                        FirebaseSync.saveUser(context, user.getUid(), name, email, phone, role, null);
                        callback.onSuccess();
                    })
                    .addOnFailureListener(e -> callback.onError(AuthErrors.message(context, e)));
            return;
        }
        AppExecutors.load(() -> registerLocal(name, email, phone, password), error -> finish(error, callback));
    }

    /** Signs in, then restores the role, phone and restaurant saved in the user's profile. */
    public void login(String email, String password, Callback callback) {
        if (usesFirebase()) {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        FirebaseUser user = result.getUser();
                        if (user == null) {
                            callback.onError(context.getString(R.string.auth_error_generic));
                            return;
                        }
                        String fallbackName = user.getDisplayName() == null ? "" : user.getDisplayName();
                        FirebaseSync.loadUser(user.getUid(), profile -> {
                            if (profile == null) {
                                session.signIn(user.getUid(), fallbackName, email, "", Session.ROLE_CUSTOMER, null);
                            } else {
                                session.signIn(user.getUid(), profile.name == null ? fallbackName : profile.name, email,
                                        profile.phone, profile.role, profile.restaurantId);
                            }
                            callback.onSuccess();
                        });
                    })
                    .addOnFailureListener(e -> callback.onError(AuthErrors.message(context, e)));
            return;
        }
        AppExecutors.load(() -> loginLocal(email, password), error -> finish(error, callback));
    }

    /** Sends a reset email through Firebase. */
    public void sendPasswordReset(String email, Callback callback) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(ignored -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(AuthErrors.message(context, e)));
    }

    /**
     * Device-only accounts have no email channel to prove ownership, so the reset is direct. That
     * is acceptable only because the account lives solely on this phone and holds nothing remote.
     */
    public void resetLocalPassword(String email, String newPassword, Callback callback) {
        AppExecutors.load(() -> {
            byte[] salt = newSalt();
            ContentValues values = new ContentValues();
            values.put("salt", encode(salt));
            values.put("hash", encode(hash(newPassword, salt)));
            int rows = DbHelper.get(context).getWritableDatabase()
                    .update(DbHelper.USERS, values, "email=?", new String[]{normalize(email)});
            return rows > 0 ? null : context.getString(R.string.auth_error_no_account_device);
        }, error -> finish(error, callback));
    }

    public void logout() {
        if (usesFirebase()) {
            FirebaseAuth.getInstance().signOut();
        }
        session.signOut();
    }

    private void finish(String error, Callback callback) {
        if (error == null) {
            callback.onSuccess();
        } else {
            callback.onError(error);
        }
    }

    private String registerLocal(String name, String email, String phone, String password) {
        String normalized = normalize(email);
        byte[] salt = newSalt();
        ContentValues values = new ContentValues();
        String uid = "local-" + UUID.randomUUID();
        values.put("id", uid);
        values.put("name", name);
        values.put("email", normalized);
        values.put("salt", encode(salt));
        values.put("hash", encode(hash(password, salt)));
        try {
            DbHelper.get(context).getWritableDatabase().insertOrThrow(DbHelper.USERS, null, values);
        } catch (SQLiteConstraintException e) {
            return context.getString(R.string.auth_error_email_taken);
        }
        session.signIn(uid, name, normalized, phone, Session.ROLE_CUSTOMER, null);
        return null;
    }

    private String loginLocal(String email, String password) {
        String normalized = normalize(email);
        SQLiteDatabase db = DbHelper.get(context).getReadableDatabase();
        try (Cursor c = db.query(DbHelper.USERS, new String[]{"id", "name", "salt", "hash"}, "email=?",
                new String[]{normalized}, null, null, null)) {
            if (c.moveToFirst()) {
                byte[] expected = Base64.decode(c.getString(3), Base64.NO_WRAP);
                byte[] actual = hash(password, Base64.decode(c.getString(2), Base64.NO_WRAP));
                if (MessageDigest.isEqual(expected, actual)) {
                    session.signIn(c.getString(0), c.getString(1), normalized, session.phone(),
                            Session.ROLE_CUSTOMER, null);
                    return null;
                }
            }
        }
        return context.getString(R.string.auth_error_wrong_password);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.US);
    }

    private static byte[] newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
