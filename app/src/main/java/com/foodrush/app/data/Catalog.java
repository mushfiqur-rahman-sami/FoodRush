package com.foodrush.app.data;

import android.content.Context;
import android.content.res.Resources;
import android.util.Xml;

import com.foodrush.app.R;
import com.foodrush.app.model.Promo;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** The bundled starting menu and platform promos, parsed once from res/raw. */
public final class Catalog {

    private static Catalog instance;

    public final XmlDataParser.Seed seed;
    public final List<Promo> promos;

    public static synchronized Catalog get(Context context) {
        if (instance == null) {
            instance = new Catalog(context.getApplicationContext().getResources());
        }
        return instance;
    }

    private Catalog(Resources res) {
        try {
            seed = XmlDataParser.parseCatalog(parser(res, R.raw.restaurants));
            promos = XmlDataParser.parsePromos(parser(res, R.raw.promotions));
        } catch (XmlPullParserException | IOException e) {
            throw new IllegalStateException("Bundled data is broken", e);
        }
    }

    private static XmlPullParser parser(Resources res, int rawId) throws XmlPullParserException {
        InputStream input = res.openRawResource(rawId);
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(input, "UTF-8");
        return parser;
    }

    public Promo promo(String code) {
        if (code == null) {
            return null;
        }
        for (Promo promo : promos) {
            if (promo.code.equalsIgnoreCase(code.trim())) {
                return promo;
            }
        }
        return null;
    }
}
