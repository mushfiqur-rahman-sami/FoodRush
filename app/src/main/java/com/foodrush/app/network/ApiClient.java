package com.foodrush.app.network;

import android.content.Context;

import com.foodrush.app.BuildConfig;

import java.io.File;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Shared HTTP setup. OpenStreetMap's services ask every app to send an identifying User-Agent and to
 * go easy on them, so all calls share one client with a disk cache.
 */
public final class ApiClient {

    public static final String USER_AGENT = "FoodRush-Android/" + BuildConfig.VERSION_NAME + " (student project)";
    private static final long CACHE_BYTES = 10L * 1024 * 1024;

    private static OkHttpClient client;
    private static OkHttpClient slowClient;
    private static GeoApi.Nominatim nominatim;
    private static GeoApi.Osrm osrm;

    private ApiClient() {
    }

    public static synchronized OkHttpClient http(Context context) {
        if (client == null) {
            Context app = context.getApplicationContext();
            client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(25, TimeUnit.SECONDS)
                    .cache(new Cache(new File(app.getCacheDir(), "http"), CACHE_BYTES))
                    .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .build()))
                    .build();
        }
        return client;
    }

    /** Overpass answers a big road download slowly when it is busy, so it gets a longer wait. */
    public static synchronized OkHttpClient slowHttp(Context context) {
        if (slowClient == null) {
            slowClient = http(context).newBuilder()
                    .readTimeout(45, TimeUnit.SECONDS)
                    .callTimeout(50, TimeUnit.SECONDS)
                    .build();
        }
        return slowClient;
    }

    public static synchronized GeoApi.Nominatim nominatim(Context context) {
        if (nominatim == null) {
            nominatim = retrofit(context, GeoApi.Nominatim.BASE_URL).create(GeoApi.Nominatim.class);
        }
        return nominatim;
    }

    public static synchronized GeoApi.Osrm osrm(Context context) {
        if (osrm == null) {
            osrm = retrofit(context, GeoApi.Osrm.BASE_URL).create(GeoApi.Osrm.class);
        }
        return osrm;
    }

    private static Retrofit retrofit(Context context, String baseUrl) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(http(context))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
