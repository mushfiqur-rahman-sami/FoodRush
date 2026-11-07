package com.foodrush.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.foodrush.app.model.Dish;
import com.foodrush.app.model.Option;
import com.foodrush.app.model.Promo;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.util.Bangladesh;

import org.junit.Test;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checks the real bundled data files, so a bad edit fails here and not on a phone. */
public class XmlDataTest {

    private static File file(String path) {
        File file = new File("src/main/" + path);
        return file.exists() ? file : new File("app/src/main/" + path);
    }

    private static XmlPullParser parser(String name) throws Exception {
        KXmlParser parser = new KXmlParser();
        parser.setInput(new FileInputStream(file("res/raw/" + name)), "UTF-8");
        return parser;
    }

    @Test
    public void restaurantsAreInBangladeshWithDishes() throws Exception {
        XmlDataParser.Seed seed = XmlDataParser.parseCatalog(parser("restaurants.xml"));
        assertTrue(seed.restaurants.size() >= 30);
        Set<String> ids = new HashSet<>();
        for (Restaurant r : seed.restaurants) {
            assertTrue(r.id, ids.add(r.id));
            assertTrue(r.name, Bangladesh.contains(r.location()));
            assertFalse(r.name, r.categories.isEmpty());
            assertEquals(Restaurant.DAYS, r.hours.size());
        }
        for (Dish d : seed.dishes) {
            assertTrue(d.id, ids.contains(d.restaurantId));
            assertTrue(d.id, d.price > 0);
            assertTrue(d.id, d.discountPrice == 0 || d.discounted());
        }
    }

    @Test
    public void mostRestaurantsAreInKhulna() throws Exception {
        int khulna = 0;
        for (Restaurant r : XmlDataParser.parseCatalog(parser("restaurants.xml")).restaurants) {
            if ("Khulna".equals(r.city)) {
                khulna++;
            }
        }
        assertTrue(khulna >= 20);
    }

    @Test
    public void everyBundledPhotoExists() throws Exception {
        XmlDataParser.Seed seed = XmlDataParser.parseCatalog(parser("restaurants.xml"));
        for (Dish d : seed.dishes) {
            assertTrue(d.image, file("res/drawable-nodpi/" + d.image.substring(4) + ".jpg").exists());
        }
        for (Restaurant r : seed.restaurants) {
            assertTrue(r.image, file("res/drawable-nodpi/" + r.image.substring(4) + ".jpg").exists());
        }
    }

    @Test
    public void sizePricesComeFromPercentages() throws Exception {
        for (Dish d : XmlDataParser.parseCatalog(parser("restaurants.xml")).dishes) {
            if (d.id.equals("khl_kacchighor_kacchi")) {
                assertEquals(350, d.price);
                assertEquals(new Option("Half", 210), d.sizes.get(0));
                assertEquals(new Option("Full", 350), d.sizes.get(1));
                return;
            }
        }
        throw new AssertionError("Kacchi Ghor's kacchi is missing");
    }

    @Test
    public void promosParse() throws Exception {
        List<Promo> promos = XmlDataParser.parsePromos(parser("promotions.xml"));
        assertEquals(4, promos.size());
        assertEquals("RUSH20", promos.get(0).code);
        assertEquals(20, promos.get(0).percent);
        assertEquals(50, promos.get(2).flat);
        assertTrue(promos.get(3).freeDelivery);
    }
}
