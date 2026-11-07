package com.foodrush.app.util;

/** Format checks for the dummy payment form. Nothing here charges a card. */
public final class CardValidator {

    private CardValidator() {
    }

    public static String digitsOnly(String input) {
        return input == null ? "" : input.replaceAll("[^0-9]", "");
    }

    public static boolean isValidNumber(String input) {
        String digits = digitsOnly(input);
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** @param expiry MM/YY */
    public static boolean isValidExpiry(String expiry, int currentYear, int currentMonth) {
        if (expiry == null || !expiry.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            return false;
        }
        int month = Integer.parseInt(expiry.substring(0, 2));
        int year = 2000 + Integer.parseInt(expiry.substring(3));
        return year > currentYear || (year == currentYear && month >= currentMonth);
    }

    public static boolean isValidCvv(String cvv) {
        return cvv != null && cvv.matches("\\d{3,4}");
    }

    /** The only card detail the app keeps, for showing on the order. */
    public static String maskedLabel(String input) {
        String digits = digitsOnly(input);
        String brand = digits.startsWith("4") ? "Visa" : digits.startsWith("5") ? "Mastercard" : "Card";
        return brand + " •••• " + digits.substring(Math.max(0, digits.length() - 4));
    }
}
