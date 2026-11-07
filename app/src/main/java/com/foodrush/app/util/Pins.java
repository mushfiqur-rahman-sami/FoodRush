package com.foodrush.app.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/** The 4-digit delivery PIN. Riders only ever see its hash, so they can check it but not read it. */
public final class Pins {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Pins() {
    }

    public static String newPin() {
        return String.format(Locale.US, "%04d", RANDOM.nextInt(10000));
    }

    public static String hash(String orderId, String pin) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((orderId + ":" + pin.trim()).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format(Locale.US, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String orderId, String pin, String expectedHash) {
        return pin != null && pin.trim().matches("\\d{4}") && hash(orderId, pin).equals(expectedHash);
    }
}
