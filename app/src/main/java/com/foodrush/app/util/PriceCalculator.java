package com.foodrush.app.util;

import java.util.Locale;

/** Order totals in whole taka. */
public final class PriceCalculator {

    public static final int VAT_PERCENT = 5;
    public static final int BASE_FEE = 30;
    public static final int FEE_PER_KM = 10;
    public static final int MAX_FEE = 150;

    private PriceCalculator() {
    }

    public static final class Quote {
        public final int subtotal;
        public final int discount;
        public final int vat;
        public final int fee;
        public final int total;

        Quote(int subtotal, int discount, int vat, int fee) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.vat = vat;
            this.fee = fee;
            this.total = subtotal - discount + vat + fee;
        }
    }

    /**
     * @param eligible     the part of the subtotal the coupon covers (a restaurant coupon covers only
     *                     that restaurant's items); equal to subtotal for platform coupons
     * @param percentOff   coupon percentage, 0 for none
     * @param flatOff      coupon taka off, 0 for none
     * @param freeDelivery whether the coupon removes the fee
     * @param minOrder     coupon minimum on the eligible part; below it the coupon does nothing
     */
    public static Quote quote(int subtotal, int eligible, int fee, int percentOff, int flatOff, boolean freeDelivery,
                              int minOrder) {
        boolean applies = eligible > 0 && eligible >= minOrder;
        int discount = applies ? Math.min(eligible, Math.round(eligible * percentOff / 100f) + flatOff) : 0;
        int finalFee = applies && freeDelivery ? 0 : fee;
        int vat = Math.round((subtotal - discount) * VAT_PERCENT / 100f);
        return new Quote(subtotal, discount, vat, finalFee);
    }

    public static Quote plain(int subtotal, int fee) {
        return quote(subtotal, subtotal, fee, 0, 0, false, 0);
    }

    /** 30 tk plus 10 tk per km of road, rounded to 5 tk, at most 150 tk. */
    public static int deliveryFee(double roadKm) {
        int raw = BASE_FEE + (int) Math.ceil(roadKm * FEE_PER_KM);
        return Math.min(MAX_FEE, (raw + 4) / 5 * 5);
    }

    /** "1,250 tk". */
    public static String format(int taka) {
        return String.format(Locale.US, "%,d tk", taka);
    }
}
