package com.foodrush.app.data;

import com.foodrush.app.model.Dish;
import com.foodrush.app.model.Option;
import com.foodrush.app.model.Promo;
import com.foodrush.app.model.Restaurant;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the bundled XML files with XmlPullParser. The caller supplies a parser with its input set. */
public final class XmlDataParser {

    private XmlDataParser() {
    }

    public static final class Seed {
        public final List<Restaurant> restaurants = new ArrayList<>();
        public final List<Dish> dishes = new ArrayList<>();
    }

    private static final class Template {
        String name;
        String category;
        String image;
        String description;
        int spicy;
        int prep;
        List<Option> sizePercents;
        List<Option> addons;
    }

    /** restaurants.xml: dish templates, then restaurants whose items point at them. */
    public static Seed parseCatalog(XmlPullParser parser) throws XmlPullParserException, IOException {
        Seed seed = new Seed();
        Map<String, Template> templates = new HashMap<>();
        Restaurant current = null;
        Set<String> categories = new LinkedHashSet<>();
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.START_TAG) {
                switch (parser.getName()) {
                    case "dish":
                        Template t = new Template();
                        t.name = attr(parser, "name");
                        t.category = attr(parser, "category");
                        t.image = "res:" + attr(parser, "image");
                        t.description = optional(parser, "desc", "");
                        t.spicy = Integer.parseInt(optional(parser, "spicy", "0"));
                        t.prep = Integer.parseInt(optional(parser, "prep", "15"));
                        t.sizePercents = Option.parse(optional(parser, "sizes", ""));
                        t.addons = Option.parse(optional(parser, "addons", ""));
                        templates.put(attr(parser, "key"), t);
                        break;
                    case "restaurant":
                        current = readRestaurant(parser);
                        categories.clear();
                        break;
                    case "item":
                        if (current == null) {
                            throw new XmlPullParserException("<item> outside a restaurant");
                        }
                        String key = attr(parser, "ref");
                        Template template = templates.get(key);
                        if (template == null) {
                            throw new XmlPullParserException("Unknown dish " + key);
                        }
                        int price = Integer.parseInt(attr(parser, "price"));
                        int discount = Integer.parseInt(optional(parser, "discount", "0"));
                        String id = current.id + "_" + key;
                        seed.dishes.add(new Dish(id, current.id, template.name, template.description, template.category,
                                price, discount, template.image, false, seededRating(id), template.prep, template.spicy,
                                true, sizesFor(template.sizePercents, price), template.addons));
                        categories.add(template.category);
                        break;
                    default:
                        break;
                }
            } else if (event == XmlPullParser.END_TAG && "restaurant".equals(parser.getName()) && current != null) {
                current.categories = new ArrayList<>(categories);
                seed.restaurants.add(current);
                current = null;
            }
        }
        return seed;
    }

    private static Restaurant readRestaurant(XmlPullParser parser) throws XmlPullParserException {
        Restaurant r = new Restaurant();
        r.id = attr(parser, "id");
        r.name = attr(parser, "name");
        r.cuisine = optional(parser, "cuisine", "");
        r.description = optional(parser, "desc", "");
        r.city = attr(parser, "city");
        r.area = optional(parser, "area", "");
        r.address = optional(parser, "address", "");
        r.lat = Double.parseDouble(attr(parser, "lat"));
        r.lon = Double.parseDouble(attr(parser, "lon"));
        r.phone = optional(parser, "phone", "");
        r.rating = Float.parseFloat(optional(parser, "rating", "4.5"));
        r.ratingCount = 20 + Math.abs(r.id.hashCode() % 180);
        r.image = "res:" + attr(parser, "image");
        String hours = optional(parser, "hours", "10:00-23:00");
        r.hours = new ArrayList<>(Arrays.asList(hours, hours, hours, hours, hours, hours, hours));
        r.minOrder = Integer.parseInt(optional(parser, "minOrder", "150"));
        r.radiusKm = Integer.parseInt(optional(parser, "radius", String.valueOf(Restaurant.DEFAULT_RADIUS_KM)));
        return r;
    }

    /** Size prices from percentages of the base price, rounded to 5 tk. */
    static List<Option> sizesFor(List<Option> percents, int price) {
        List<Option> sizes = new ArrayList<>();
        for (Option percent : percents) {
            sizes.add(new Option(percent.name, Math.round(price * percent.price / 100f / 5f) * 5));
        }
        return sizes;
    }

    /** A stable starting rating between 4.2 and 4.9; real reviews take over from there. */
    static float seededRating(String id) {
        return 4.2f + Math.abs(id.hashCode() % 8) / 10f;
    }

    public static List<Promo> parsePromos(XmlPullParser parser) throws XmlPullParserException, IOException {
        List<Promo> promos = new ArrayList<>();
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.START_TAG && "promo".equals(parser.getName())) {
                promos.add(new Promo(attr(parser, "code"), attr(parser, "title"), attr(parser, "subtitle"),
                        Integer.parseInt(attr(parser, "percent")), Integer.parseInt(optional(parser, "flat", "0")),
                        Boolean.parseBoolean(attr(parser, "freeDelivery")),
                        Integer.parseInt(attr(parser, "minOrder")), null, 0, true));
            }
        }
        return promos;
    }

    private static String attr(XmlPullParser parser, String name) throws XmlPullParserException {
        String value = parser.getAttributeValue(null, name);
        if (value == null) {
            throw new XmlPullParserException("<" + parser.getName() + "> is missing " + name);
        }
        return value;
    }

    private static String optional(XmlPullParser parser, String name, String fallback) {
        String value = parser.getAttributeValue(null, name);
        return value == null ? fallback : value;
    }
}
