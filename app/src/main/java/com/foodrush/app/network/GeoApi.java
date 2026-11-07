package com.foodrush.app.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Free OpenStreetMap-based services read with Retrofit and Gson: Nominatim for turning a map point
 * into an address, and the public OSRM server as a backup router. Neither needs a key.
 */
public final class GeoApi {

    private GeoApi() {
    }

    public interface Nominatim {
        String BASE_URL = "https://nominatim.openstreetmap.org/";

        @GET("reverse?format=jsonv2&zoom=18&addressdetails=1&accept-language=en")
        Call<ReverseResult> reverse(@Query("lat") double lat, @Query("lon") double lon);
    }

    public static class ReverseResult {
        @SerializedName("name")
        public String name;
        @SerializedName("display_name")
        public String displayName;
        @SerializedName("address")
        public Address address;
    }

    public static class Address {
        @SerializedName("house_number")
        public String houseNumber;
        @SerializedName("road")
        public String road;
        @SerializedName("neighbourhood")
        public String neighbourhood;
        @SerializedName("suburb")
        public String suburb;
        @SerializedName("city")
        public String city;
        @SerializedName("town")
        public String town;
        @SerializedName("village")
        public String village;
        @SerializedName("county")
        public String county;
        @SerializedName("state_district")
        public String district;
        @SerializedName("country_code")
        public String countryCode;
    }

    public interface Osrm {
        String BASE_URL = "https://router.project-osrm.org/";

        /** @param coordinates "lon,lat;lon,lat;..." in visiting order */
        @GET("route/v1/driving/{coordinates}?overview=full&geometries=geojson")
        Call<RouteResult> route(@Path(value = "coordinates", encoded = true) String coordinates);
    }

    public static class RouteResult {
        @SerializedName("code")
        public String code;
        @SerializedName("routes")
        public List<Route> routes;
    }

    public static class Route {
        @SerializedName("distance")
        public double meters;
        @SerializedName("duration")
        public double seconds;
        @SerializedName("geometry")
        public Geometry geometry;
    }

    public static class Geometry {
        /** [lon, lat] pairs. */
        @SerializedName("coordinates")
        public List<List<Double>> coordinates;
    }
}
