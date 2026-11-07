package com.foodrush.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.foodrush.app.model.Dish;
import com.foodrush.app.model.LatLon;
import com.foodrush.app.model.Option;
import com.foodrush.app.model.Restaurant;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;

public class LogicTest {

    @Test
    public void vatIsFivePercentOfFoodAfterDiscount() {
        PriceCalculator.Quote quote = PriceCalculator.plain(1000, 60);
        assertEquals(50, quote.vat);
        assertEquals(1110, quote.total);
        PriceCalculator.Quote coupon = PriceCalculator.quote(1000, 1000, 60, 20, 0, false, 500);
        assertEquals(200, coupon.discount);
        assertEquals(40, coupon.vat);
        assertEquals(900, coupon.total);
    }

    @Test
    public void couponBelowMinimumDoesNothing() {
        PriceCalculator.Quote quote = PriceCalculator.quote(400, 400, 60, 20, 0, false, 500);
        assertEquals(0, quote.discount);
        assertEquals(60, quote.fee);
    }

    @Test
    public void restaurantCouponOnlyCoversItsPart() {
        // 600 tk of food, 200 tk of it from the coupon's restaurant: 50 tk off 200, not 600.
        PriceCalculator.Quote quote = PriceCalculator.quote(600, 200, 40, 0, 50, false, 150);
        assertEquals(50, quote.discount);
        assertEquals(Math.round(550 * 0.05f), quote.vat);
        PriceCalculator.Quote tooSmall = PriceCalculator.quote(600, 100, 40, 0, 50, false, 150);
        assertEquals(0, tooSmall.discount);
    }

    @Test
    public void freeDeliveryAndFlatNeverGoNegative() {
        assertEquals(0, PriceCalculator.quote(800, 800, 70, 0, 0, true, 800).fee);
        assertEquals(100, PriceCalculator.quote(100, 100, 30, 0, 500, false, 0).discount);
    }

    @Test
    public void deliveryFeeFromRoadKilometres() {
        assertEquals(30, PriceCalculator.deliveryFee(0));
        assertEquals(55, PriceCalculator.deliveryFee(2.3));
        assertEquals(150, PriceCalculator.deliveryFee(40));
    }

    @Test
    public void takaFormat() {
        assertEquals("1,250 tk", PriceCalculator.format(1250));
        assertEquals("80 tk", PriceCalculator.format(80));
    }

    @Test
    public void bangladeshiPhones() {
        assertEquals("+8801712345678", Bangladesh.normalizePhone("01712345678"));
        assertEquals("+8801712345678", Bangladesh.normalizePhone("+880 1712-345678"));
        assertEquals("+8801912345678", Bangladesh.normalizePhone("8801912345678"));
        assertNull(Bangladesh.normalizePhone("01212345678"));
        assertNull(Bangladesh.normalizePhone("0171234567"));
        assertNull(Bangladesh.normalizePhone("+12125550100"));
    }

    @Test
    public void bangladeshBorder() {
        assertTrue(Bangladesh.contains(new LatLon(22.8222, 89.5529)));   // Khulna
        assertTrue(Bangladesh.contains(new LatLon(23.7806, 90.4166)));   // Dhaka
        assertTrue(Bangladesh.contains(new LatLon(22.3260, 91.8110)));   // Chattogram
        assertTrue(Bangladesh.contains(new LatLon(20.6270, 92.3230)));   // St. Martin's Island
        assertFalse(Bangladesh.contains(new LatLon(22.5726, 88.3639)));  // Kolkata
        assertFalse(Bangladesh.contains(new LatLon(40.7484, -73.9846))); // New York
    }

    @Test
    public void distances() {
        double meters = new LatLon(22.8222, 89.5529).metersTo(new LatLon(22.7981, 89.5289));
        assertEquals(3600, meters, 150);
    }

    @Test
    public void pinsCheckWithoutRevealing() {
        String hash = Pins.hash("order-1", "0427");
        assertTrue(Pins.matches("order-1", "0427", hash));
        assertFalse(Pins.matches("order-1", "0428", hash));
        assertFalse(Pins.matches("order-2", "0427", hash));
        assertFalse(Pins.matches("order-1", "427", hash));
        assertTrue(Pins.newPin().matches("\\d{4}"));
    }

    @Test
    public void discountScalesSizes() {
        Dish dish = new Dish("d", "r", "Kacchi", "", "Biryani", 350, 300, null, false, 4.5f, 15, 1, true,
                Arrays.asList(new Option("Half", 210), new Option("Full", 350)), Collections.emptyList());
        assertEquals(300, dish.currentPrice());
        assertEquals(180, dish.priceFor(dish.sizes.get(0)));
        assertEquals(300, dish.priceFor(dish.sizes.get(1)));
        assertEquals(180, dish.fromPrice());
    }

    @Test
    public void openingHoursIncludingPastMidnight() {
        Restaurant r = new Restaurant();
        r.hours = Arrays.asList("10:00-23:00", "18:00-02:00", "", "10:00-23:00", "10:00-23:00", "10:00-23:00",
                "10:00-23:00");
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 30);
        assertTrue(r.isOpenAt(c));
        c.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
        c.set(Calendar.HOUR_OF_DAY, 1);
        assertTrue(r.isOpenAt(c));
        c.set(Calendar.HOUR_OF_DAY, 12);
        assertFalse(r.isOpenAt(c));
        r.open = false;
        c.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        assertFalse(r.isOpenAt(c));
    }

    @Test
    public void luhn() {
        assertTrue(CardValidator.isValidNumber("4242 4242 4242 4242"));
        assertFalse(CardValidator.isValidNumber("4242 4242 4242 4241"));
        assertFalse(CardValidator.isValidNumber("42"));
    }

    @Test
    public void expiry() {
        assertTrue(CardValidator.isValidExpiry("12/26", 2026, 12));
        assertFalse(CardValidator.isValidExpiry("11/26", 2026, 12));
        assertFalse(CardValidator.isValidExpiry("13/30", 2026, 1));
    }

    @Test
    public void cardLabelKeepsOnlyLastFour() {
        assertEquals("Visa •••• 4242", CardValidator.maskedLabel("4242424242424242"));
    }
}
