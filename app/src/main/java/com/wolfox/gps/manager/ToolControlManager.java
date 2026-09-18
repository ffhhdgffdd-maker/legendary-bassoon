package com.wolfox.gps.manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.wolfox.gps.ModuleConfig;
import com.wolfox.gps.ui.MapDialog;
import com.wolfox.gps.ui.WolFoxPanel;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cached server policy for maintenance, disablement and mandatory updates. */
public final class ToolControlManager {
    private static final String TAG = "ToolControl";
    private static final String PREFS = "wolfox_remote_control";
    private static final long ATTEMPT_GUARD_MS = 5000L;
    private static final long CHECK_INTERVAL_MS = 45000L;
    private static final long MONITOR_INTERVAL_MS = 60000L;
    private static final ToolControlManager INSTANCE = new ToolControlManager();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile long lastAttempt;
    private volatile boolean requestRunning;
    private AlertDialog dialog;
    private WeakReference<Activity> dialogOwner = new WeakReference<>(null);
    private WeakReference<Activity> monitorOwner = new WeakReference<>(null);
    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            Activity activity = monitorOwner.get();
            if (!usable(activity)) return;
            refresh(activity);
            main.postDelayed(this, MONITOR_INTERVAL_MS);
        }
    };

    private ToolControlManager() {}

    public static ToolControlManager getInstance() { return INSTANCE; }

    public boolean isBlocked(Context context) {
        return context != null && preferences(context).getBoolean("blocked", false);
    }

    public void startSessionMonitoring(Activity activity) {
        if (!usable(activity)) return;
        monitorOwner = new WeakReference<>(activity);
        main.removeCallbacks(monitor);
        refresh(activity);
        main.postDelayed(monitor, MONITOR_INTERVAL_MS);
    }

    public void stopSessionMonitoring(Activity activity) {
        Activity owner = monitorOwner.get();
        if (owner == null || owner == activity) {
            main.removeCallbacks(monitor);
            monitorOwner.clear();
        }
    }

    public void refresh(final Activity activity) {
        if (!usable(activity) || requestRunning) return;
        long now = System.currentTimeMillis();
        SharedPreferences prefs = preferences(activity);
        if (now - prefs.getLong("checked_at", 0L) < CHECK_INTERVAL_MS
                || now - lastAttempt < ATTEMPT_GUARD_MS) {
            if (prefs.getBoolean("blocked", false)) enforceCached(activity);
            return;
        }
        requestRunning = true;
        lastAttempt = now;
        final Context app = activity.getApplicationContext();
        executor.execute(() -> fetchPolicy(app, activity));
    }

    private void fetchPolicy(final Context context, final Activity activity) {
        HttpURLConnection connection = null;
        try {
            String endpoint = ModuleConfig.getControlEndpoint()
                    + "?package_name=" + URLEncoder.encode("com.wolfox.gps", "UTF-8")
                    + "&version_code=" + ModuleConfig.VERSION_CODE;
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(ModuleConfig.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(ModuleConfig.READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent",
                    "WolFox-GPS/" + ModuleConfig.VERSION_NAME + " Android");
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            final JSONObject response = new JSONObject(readLimited(stream, 65536));
            if (code < 200 || code >= 400 || !response.optBoolean("ok", false)) {
                throw new IllegalStateException("HTTP " + code);
            }
            persist(context, response);
            main.post(() -> {
                requestRunning = false;
                if (response.optBoolean("blocked", false)) enforce(activity, response);
                else release(activity);
            });
        } catch (Throwable error) {
            WFLog.d(TAG, "policy check: " + error.getMessage());
            main.post(() -> {
                requestRunning = false;
                if (isBlocked(context)) enforceCached(activity);
            });
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public void enforceCached(Activity activity) {
        if (!usable(activity)) return;
        SharedPreferences prefs = preferences(activity);
        if (!prefs.getBoolean("blocked", false)) return;
        try {
            JSONObject cached = new JSONObject();
            cached.put("blocked", true);
            cached.put("update_required", prefs.getBoolean("update_required", false));
            cached.put("message_title", prefs.getString("message_title", "الخدمة غير متاحة"));
            cached.put("message_text", prefs.getString("message_text", "حاول مرة أخرى لاحقًا."));
            cached.put("store_deep_link", prefs.getString("store_deep_link", ""));
            cached.put("update_page", prefs.getString("update_page", ""));
            enforce(activity, cached);
        } catch (Throwable ignored) {}
    }

    private void persist(Context context, JSONObject response) {
        preferences(context).edit()
                .putBoolean("blocked", response.optBoolean("blocked", false))
                .putBoolean("update_required", response.optBoolean("update_required", false))
                .putString("message_title", response.optString("message_title", ""))
                .putString("message_text", response.optString("message_text", ""))
                .putString("store_deep_link", response.optString("store_deep_link", ""))
                .putString("update_page", response.optString("update_page", ""))
                .putLong("checked_at", System.currentTimeMillis())
                .apply();
    }

    private void enforce(final Activity activity, JSONObject response) {
        if (!usable(activity)) return;
        GPSMockManager.getInstance().stopMocking();
        WolFoxPanel.dismiss();
        MapDialog.dismiss();
        FloatingManager.getInstance().hide(false);

        Activity owner = dialogOwner.get();
        if (dialog != null && dialog.isShowing() && owner == activity) return;
        dismissDialog();

        final boolean update = response.optBoolean("update_required", false);
        String title = response.optString("message_title",
                update ? "تحديث إجباري" : "الخدمة غير متاحة");
        String message = response.optString("message_text",
                update ? "يجب تحديث الأداة للمتابعة." : "حاول مرة أخرى لاحقًا.");
        final String deepLink = response.optString("store_deep_link", "");
        final String updatePage = response.optString("update_page", "");

        dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton("إغلاق", (value, which) -> activity.finish())
                .setPositiveButton(update ? "فتح WolFox Store" : "إعادة المحاولة", null)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialogOwner = new WeakReference<>(activity);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (update) openUpdate(activity, deepLink, updatePage);
                    else {
                        preferences(activity).edit().putLong("checked_at", 0L).apply();
                        refresh(activity);
                    }
                }));
        dialog.show();
    }

    private void release(Activity activity) {
        dismissDialog();
        if (usable(activity) && WFStorage.getInstance(activity).isBubbleVisible()) {
            FloatingManager.getInstance().show(activity);
        }
    }

    private void openUpdate(Activity activity, String deepLink, String page) {
        try {
            Uri uri = Uri.parse(deepLink);
            if ("wolfoxstore".equalsIgnoreCase(uri.getScheme())
                    && "app".equalsIgnoreCase(uri.getHost())) {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                if (intent.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivity(intent);
                    return;
                }
            }
        } catch (Throwable ignored) {}
        try {
            Uri uri = Uri.parse(page);
            Uri trusted = Uri.parse(ModuleConfig.getControlEndpoint());
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && trusted.getHost() != null
                    && trusted.getHost().equalsIgnoreCase(uri.getHost())) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        } catch (Throwable error) {
            WFLog.d(TAG, "open update: " + error.getMessage());
        }
    }

    private void dismissDialog() {
        try {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        } catch (Throwable ignored) {}
        dialog = null;
        dialogOwner.clear();
    }

    private SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String readLimited(InputStream stream, int limit) throws Exception {
        if (stream == null) return "{}";
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

    private static boolean usable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
}
