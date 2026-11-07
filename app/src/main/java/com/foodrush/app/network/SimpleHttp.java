package com.foodrush.app.network;

import com.foodrush.app.model.LatLon;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The low-level path: HttpURLConnection plus org.json, used for address search with Photon, a free
 * OpenStreetMap search service. Results are limited to Bangladesh.
 */
public final class SimpleHttp {

    private static final String PHOTON = "https://photon.komoot.io/api/";
    /** Bangladesh's bounding box: west, south, east, north. */
    private static final String BANGLADESH_BOX = "88.0,20.6,92.7,26.7";

    public static final class Place {
        public final String title;
        public final String detail;
        public final LatLon point;

        Place(String title, String detail, LatLon point) {
            this.title = title;
            this.detail = detail;
            this.point = point;
        }

        public String label() {
            return detail.isEmpty() ? title : title + ", " + detail;
        }
    }

    private SimpleHttp() {
    }

    /** Blocking; call off the main thread. Results near {@code bias} come first when it is given. */
    public static List<Place> searchPlaces(String query, LatLon bias) throws IOException, JSONException {
        String url = PHOTON + "?limit=8&lang=en&bbox=" + BANGLADESH_BOX + "&q=" + URLEncoder.encode(query, "UTF-8")
                + (bias == null ? "" : String.format(Locale.US, "&lat=%.5f&lon=%.5f", bias.lat, bias.lon));
        JSONArray features = new JSONObject(get(url)).getJSONArray("features");
        List<Place> places = new ArrayList<>();
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONObject props = feature.getJSONObject("properties");
            if (!"BD".equalsIgnoreCase(props.optString("countrycode", "BD"))) {
                continue;
            }
            JSONArray coords = feature.getJSONObject("geometry").getJSONArray("coordinates");
            String street = props.optString("street", "");
            String number = props.optString("housenumber", "");
            String name = props.optString("name", "");
            String title = !name.isEmpty() ? name : (number + " " + street).trim();
            if (title.isEmpty()) {
                continue;
            }
            Set<String> parts = new LinkedHashSet<>();
            for (String key : new String[]{"street", "district", "county", "city", "state"}) {
                String value = props.optString(key, "");
                if (!value.isEmpty() && !value.equals(title)) {
                    parts.add(value);
                }
            }
            places.add(new Place(title, String.join(", ", parts),
                    new LatLon(coords.getDouble(1), coords.getDouble(0))));
        }
        return places;
    }

    private static String get(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", ApiClient.USER_AGENT);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            return body.toString();
        } finally {
            connection.disconnect();
        }
    }
}
