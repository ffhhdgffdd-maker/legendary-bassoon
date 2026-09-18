package com.wolfox.gps.manager;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.wolfox.gps.ModuleConfig;
import com.wolfox.gps.model.HistoryEntry;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Server-backed license gate. No WolFox feature is exposed before Active. */
public final class LicenseManager {
    private static final String TAG = "LicenseManager";
    private static final LicenseManager INSTANCE = new LicenseManager();
    private static final long REFRESH_INTERVAL_MS = 30000L;
    private static final long MONITOR_INTERVAL_MS = 30000L;
    private static final long FORCE_GUARD_MS = 5000L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean refreshRunning;
    private volatile long lastRefreshAttemptAt;
    private WeakReference<Activity> monitorActivity = new WeakReference<>(null);
    private final Runnable monitorTask = new Runnable() {
        @Override public void run() {
            Activity activity = monitorActivity.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            refresh(activity, true);
            main.postDelayed(this, MONITOR_INTERVAL_MS);
        }
    };

    public interface Callback {
        void onResult(boolean active, String status, String message);
    }

    private LicenseManager() {}

    public static LicenseManager getInstance() { return INSTANCE; }

    public boolean isActive(Context context) {
        return context != null && WFStorage.getInstance(context).isLicenseActive();
    }

    public void startSessionMonitoring(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        monitorActivity = new WeakReference<>(activity);
        main.removeCallbacks(monitorTask);
        refresh(activity, true);
        main.postDelayed(monitorTask, MONITOR_INTERVAL_MS);
    }

    public void stopSessionMonitoring(Activity activity) {
        Activity owner = monitorActivity.get();
        if (owner == null || owner == activity) {
            main.removeCallbacks(monitorTask);
            monitorActivity.clear();
        }
    }

    public void refreshIfDue(Activity activity) { refresh(activity, false); }

    private void refresh(Activity activity, boolean force) {
        if (activity == null || activity.isFinishing() || refreshRunning) return;
        WFStorage storage = WFStorage.getInstance(activity);
        if (!storage.isLicenseActive() || storage.getLicenseCode().isEmpty()) return;
        long now = System.currentTimeMillis();
        long last = Math.max(storage.getLicenseCheckedAt(), lastRefreshAttemptAt);
        if (force) {
            if (now - lastRefreshAttemptAt < FORCE_GUARD_MS) return;
        } else if (now - last < REFRESH_INTERVAL_MS) return;
        refreshRunning = true;
        lastRefreshAttemptAt = now;
        verify(activity, storage.getLicenseCode(), new Callback() {
            @Override public void onResult(boolean active, String status, String message) {
                refreshRunning = false;
                if (!active && !activity.isFinishing()) {
                    enforceInactive(activity);
                }
            }
        });
    }

    public void activate(Activity activity, String rawCode, Callback callback) {
        request(activity, rawCode, "activate", callback);
    }

    public void verify(Activity activity, String rawCode, Callback callback) {
        request(activity, rawCode, "verify", callback);
    }

    private void request(Activity activity, String rawCode, String requestMode, Callback callback) {
        if (activity == null || activity.isFinishing()) return;
        final String code = rawCode == null ? ""
                : rawCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.US);
        if (code.isEmpty()) {
            post(activity, callback, false, "InvalidRequest", "أدخل كود التفعيل");
            return;
        }

        final Context app = activity.getApplicationContext();
        final WFStorage storage = WFStorage.getInstance(app);
        final String installationId = storage.getOrCreateInstallationId();
        final String binding = buildDeviceBinding(app, installationId);
        final String requestId = UUID.randomUUID().toString();

        executor.execute(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("code", code);
                    body.put("license_code", code);
                    body.put("installation_id", installationId);
                    body.put("device_binding", binding);
                    body.put("platform", "android");
                    body.put("project", ModuleConfig.PROJECT);
                    body.put("app_version", ModuleConfig.VERSION_NAME);
                    body.put("module_version_code", ModuleConfig.VERSION_CODE);
                    body.put("package_name", app.getPackageName());
                    body.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);
                    body.put("request_id", requestId);
                    body.put("request_mode", requestMode);

                    URL url = new URL(ModuleConfig.getApiEndpoint());
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(ModuleConfig.CONNECT_TIMEOUT_MS);
                    connection.setReadTimeout(ModuleConfig.READ_TIMEOUT_MS);
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setUseCaches(false);
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setRequestProperty("User-Agent", "WolFox-GPS/" + ModuleConfig.VERSION_NAME);

                    byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(payload.length);
                    OutputStream output = connection.getOutputStream();
                    output.write(payload);
                    output.flush();
                    output.close();

                    int http = connection.getResponseCode();
                    InputStream stream = http >= 200 && http < 400
                            ? connection.getInputStream() : connection.getErrorStream();
                    String responseText = readLimited(stream, 65536);
                    JSONObject response = responseText.isEmpty()
                            ? new JSONObject() : new JSONObject(responseText);
                    String status = response.optString("status", "ServerError");

                    if ("Active".equalsIgnoreCase(status)) {
                        long expiresEpoch = response.optLong("expires_at_epoch", 0L);
                        if (expiresEpoch > 0L && expiresEpoch < 100000000000L) {
                            expiresEpoch *= 1000L;
                        }
                        if (expiresEpoch <= 0L) {
                            expiresEpoch = parseServerDate(response.optString("expires_at", ""));
                        }
                        if (expiresEpoch > 0L && expiresEpoch <= System.currentTimeMillis()) {
                            storage.saveLicense(code, "Expired", expiresEpoch,
                                    response.optString("expires_at", ""));
                            post(activity, callback, false, "Expired", "انتهت صلاحية الكود");
                            return;
                        }
                        storage.saveLicense(code, "Active", expiresEpoch,
                                response.optString("expires_at", ""));
                        storage.addHistory(new HistoryEntry(
                                HistoryEntry.Action.ACTIVATE, "تم التفعيل من لوحة WolFox"));
                        post(activity, callback, true, "Active", "تم التفعيل بنجاح");
                        return;
                    }

                    if ("Reset".equalsIgnoreCase(status)) {
                        storage.clearLicense();
                    } else if ("Expired".equalsIgnoreCase(status)
                            || "Disabled".equalsIgnoreCase(status)
                            || "InvalidCode".equalsIgnoreCase(status)
                            || "TransferRequired".equalsIgnoreCase(status)) {
                        storage.saveLicense(code, status, 0L, "");
                    }
                    post(activity, callback, false, status, messageFor(status, http));
                } catch (Exception error) {
                    WFLog.e(TAG, "verify failed: " + error.getMessage(), error);
                    boolean cached = storage.isLicenseActive();
                    post(activity, callback, cached, cached ? "ActiveOffline" : "NetworkError",
                            cached ? "الترخيص محفوظ ويستمر حتى تاريخ الانتهاء"
                                   : "تعذر الاتصال بخادم التفعيل");
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static void enforceInactive(Activity activity) {
        GPSMockManager.getInstance().stopMocking();
        com.wolfox.gps.ui.WolFoxPanel.dismiss();
        com.wolfox.gps.ui.MapDialog.dismiss();
        FloatingManager.getInstance().show(activity);
        com.wolfox.gps.ui.ActivationDialog.showRequired(activity);
    }

    private static void post(Activity activity, Callback callback, boolean active,
                             String status, String message) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (callback != null) callback.onResult(active, status, message);
            }
        });
    }

    private static String messageFor(String status, int http) {
        if ("InvalidCode".equalsIgnoreCase(status)) return "كود التفعيل غير صحيح";
        if ("Reset".equalsIgnoreCase(status)) return "تمت إعادة ضبط الترخيص؛ أدخل الكود من جديد";
        if ("Expired".equalsIgnoreCase(status)) return "انتهت صلاحية الكود";
        if ("Disabled".equalsIgnoreCase(status)) return "الكود موقوف من لوحة الإدارة";
        if ("TransferRequired".equalsIgnoreCase(status)) return "الكود مرتبط بجهاز آخر؛ نفّذ Reset من اللوحة";
        if ("InvalidRequest".equalsIgnoreCase(status)) return "بيانات التفعيل غير مكتملة";
        if (http == 429) return "محاولات كثيرة؛ انتظر قليلًا ثم أعد المحاولة";
        return "تعذر التفعيل (" + status + ")";
    }

    private static String buildDeviceBinding(Context context, String installationId) {
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String source = String.valueOf(androidId) + "|" + installationId + "|"
                + context.getPackageName() + "|" + Build.BRAND + "|" + Build.DEVICE;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(source.hashCode());
        }
    }

    private static String readLimited(InputStream stream, int limit) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[2048];
        int read;
        while ((read = reader.read(buffer)) >= 0 && out.length() < limit) {
            out.append(buffer, 0, Math.min(read, limit - out.length()));
        }
        reader.close();
        return out.toString();
    }

    private static long parseServerDate(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssXXX"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = format.parse(value.trim());
                if (date != null) return date.getTime();
            } catch (Exception ignored) {}
        }
        return 0L;
    }
}
