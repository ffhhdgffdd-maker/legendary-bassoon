package com.wolfox.gps.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.wolfox.gps.model.WFLocation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Resilient resolver with a guaranteed callback, local parsing and provider fallbacks. */
public final class MapSearchResolver {
    private static final String TAG = "MapSearchResolver";
    private static final long RESPONSE_DEADLINE_MS = 18000L;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final ExecutorService GEOCODER_EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback { void onComplete(Result result); }

    public static final class Result {
        private final WFLocation location;
        private final String source;
        private final String error;

        private Result(WFLocation location, String source, String error) {
            this.location = location;
            this.source = source == null ? "" : source;
            this.error = error == null ? "" : error;
        }

        public boolean isSuccess() { return location != null && location.isValid(); }
        public WFLocation getLocation() { return location; }
        public String getSource() { return source; }
        public String getError() { return error; }
    }

    private MapSearchResolver() {}

    public static void resolve(Context context, String input, final Callback callback) {
        final Context app = context == null ? null : context.getApplicationContext();
        final String value = input == null ? "" : input.trim();
        final AtomicBoolean delivered = new AtomicBoolean(false);
        final Runnable timeout = new Runnable() {
            @Override public void run() {
                if (delivered.compareAndSet(false, true) && callback != null) {
                    callback.onComplete(failure("انتهت مهلة البحث؛ تحقق من الإنترنت وحاول مجددًا"));
                }
            }
        };
        MAIN.postDelayed(timeout, RESPONSE_DEADLINE_MS);

        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                Result result;
                try {
                    result = resolveBlocking(app, value);
                } catch (Throwable error) {
                    WFLog.e(TAG, "resolver: " + error.getMessage());
                    result = failure("تعذر إكمال البحث؛ حاول مرة أخرى");
                }
                final Result completed = result;
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        MAIN.removeCallbacks(timeout);
                        if (delivered.compareAndSet(false, true) && callback != null) {
                            callback.onComplete(completed);
                        }
                    }
                });
            }
        });
    }

    static Result resolveBlocking(Context context, String input) {
        if (input == null || input.trim().isEmpty()) {
            return failure("أدخل عنوانًا أو إحداثيات أو رابط خريطة");
        }

        String expanded = input.trim();
        try {
            if (MapLinkParser.needsExpansion(expanded)) {
                expanded = MapLinkParser.expandKnownShortLink(expanded);
            }
        } catch (Throwable error) {
            WFLog.d(TAG, "short link: " + error.getMessage());
        }

        MapLinkParser.Parsed parsed = MapLinkParser.parse(expanded);
        if (parsed != null && parsed.hasCoordinates()) {
            String label = parsed.getLabel().isEmpty() ? parsed.getProvider() : parsed.getLabel();
            WFLocation location = new WFLocation(
                    parsed.getLatitude(), parsed.getLongitude(), label);
            return location.isValid()
                    ? success(location, parsed.getProvider())
                    : failure("الإحداثيات خارج النطاق");
        }

        String query = parsed == null ? input.trim() : parsed.getQuery();
        if (query == null || query.trim().isEmpty()) {
            return failure(parsed != null && parsed.isMapLink()
                    ? "تعذر استخراج موقع من رابط المشاركة"
                    : "لم يتم العثور على الموقع");
        }

        WFLocation location = geocodeAndroidWithTimeout(context, query, 3500L);
        String provider = "بحث الجهاز";
        if (location == null) {
            location = geocodeNominatim(query);
            provider = "OpenStreetMap";
        }
        if (location == null) {
            location = geocodePhoton(query);
            provider = "Photon";
        }
        if (location == null || !location.isValid()) {
            return failure("لم يتم العثور على الموقع؛ جرّب اسمًا أدق أو الصق رابط المشاركة كاملًا");
        }
        String source = parsed != null && parsed.isMapLink() && !parsed.getProvider().isEmpty()
                ? parsed.getProvider() : provider;
        return success(location, source);
    }

    private static WFLocation geocodeAndroidWithTimeout(
            final Context context, final String query, long timeoutMs) {
        if (context == null || !Geocoder.isPresent()) return null;
        Future<WFLocation> future = GEOCODER_EXECUTOR.submit(() -> geocodeAndroid(context, query));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Throwable error) {
            future.cancel(true);
            WFLog.d(TAG, "Android geocoder timeout/failure: " + error.getMessage());
            return null;
        }
    }

    private static WFLocation geocodeAndroid(Context context, String query) {
        try {
            Geocoder geocoder = new Geocoder(context, new Locale("ar"));
            List<Address> rows;
            if (Build.VERSION.SDK_INT >= 33) {
                final CountDownLatch latch = new CountDownLatch(1);
                final AtomicReference<List<Address>> result = new AtomicReference<>();
                geocoder.getFromLocationName(query, 1, new Geocoder.GeocodeListener() {
                    @Override public void onGeocode(List<Address> addresses) {
                        result.set(addresses);
                        latch.countDown();
                    }

                    @Override public void onError(String errorMessage) {
                        latch.countDown();
                    }
                });
                if (!latch.await(2800L, TimeUnit.MILLISECONDS)) return null;
                rows = result.get();
            } else {
                rows = geocodeLegacy(geocoder, query);
            }
            if (rows == null || rows.isEmpty()) return null;
            Address row = rows.get(0);
            String label = row.getAddressLine(0);
            WFLocation location = new WFLocation(row.getLatitude(), row.getLongitude(),
                    label == null || label.trim().isEmpty() ? query : label);
            return location.isValid() ? location : null;
        } catch (Throwable error) {
            WFLog.d(TAG, "Android geocoder: " + error.getMessage());
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static List<Address> geocodeLegacy(Geocoder geocoder, String query)
            throws java.io.IOException {
        return geocoder.getFromLocationName(query, 1);
    }

    private static WFLocation geocodeNominatim(String query) {
        HttpURLConnection connection = null;
        try {
            String endpoint = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1"
                    + "&accept-language=ar&q=" + URLEncoder.encode(query, "UTF-8");
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            configure(connection);
            if (connection.getResponseCode() != 200) return null;
            JSONArray rows = new JSONArray(readLimited(connection.getInputStream(), 131072));
            if (rows.length() == 0) return null;
            JSONObject row = rows.getJSONObject(0);
            WFLocation location = new WFLocation(
                    Double.parseDouble(row.getString("lat")),
                    Double.parseDouble(row.getString("lon")),
                    row.optString("display_name", query));
            return location.isValid() ? location : null;
        } catch (Throwable error) {
            WFLog.d(TAG, "Nominatim: " + error.getMessage());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static WFLocation geocodePhoton(String query) {
        HttpURLConnection connection = null;
        try {
            String endpoint = "https://photon.komoot.io/api/?limit=1&lang=ar&q="
                    + URLEncoder.encode(query, "UTF-8");
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            configure(connection);
            if (connection.getResponseCode() != 200) return null;
            JSONObject root = new JSONObject(readLimited(connection.getInputStream(), 131072));
            JSONArray features = root.optJSONArray("features");
            if (features == null || features.length() == 0) return null;
            JSONObject feature = features.getJSONObject(0);
            JSONArray coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates");
            JSONObject properties = feature.optJSONObject("properties");
            String label = buildPhotonLabel(properties, query);
            WFLocation location = new WFLocation(
                    coordinates.getDouble(1), coordinates.getDouble(0), label);
            return location.isValid() ? location : null;
        } catch (Throwable error) {
            WFLog.d(TAG, "Photon: " + error.getMessage());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String buildPhotonLabel(JSONObject properties, String fallback) {
        if (properties == null) return fallback;
        String[] keys = {"name", "street", "city", "state", "country"};
        StringBuilder label = new StringBuilder();
        for (String key : keys) {
            String value = properties.optString(key, "").trim();
            if (value.isEmpty() || label.indexOf(value) >= 0) continue;
            if (label.length() > 0) label.append("، ");
            label.append(value);
        }
        return label.length() == 0 ? fallback : label.toString();
    }

    private static void configure(HttpURLConnection connection) {
        connection.setConnectTimeout(5500);
        connection.setReadTimeout(5500);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "ar,en;q=0.7");
        connection.setRequestProperty("User-Agent", "WolFox-GPS/3.1.0 Android");
    }

    private static String readLimited(InputStream stream, int limit) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[2048];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            if (out.length() + read > limit) throw new IllegalStateException("response too large");
            out.append(buffer, 0, read);
        }
        reader.close();
        return out.toString();
    }

    private static Result success(WFLocation location, String source) {
        return new Result(location, source, "");
    }

    private static Result failure(String error) {
        return new Result(null, "", error);
    }
}
