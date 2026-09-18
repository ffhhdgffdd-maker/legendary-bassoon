package com.wolfox.gps.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;

import com.wolfox.gps.model.FavoriteLocation;
import com.wolfox.gps.model.HistoryEntry;
import com.wolfox.gps.model.WFLocation;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WFStorage — طبقة التخزين الموحدة للموديول
 * يستخدم SharedPreferences بصيغة JSON (بدون SQLite لأننا داخل موديول)
 */
public class WFStorage {

    private static final String PREF_NAME          = "wolfox_gps_prefs";
    private static final String KEY_FAVORITES      = "favorites";
    private static final String KEY_HISTORY        = "history";
    private static final String KEY_LAST_LAT       = "last_lat";
    private static final String KEY_LAST_LNG       = "last_lng";
    private static final String KEY_LAST_ALT       = "last_alt";
    private static final String KEY_LAST_ACCURACY  = "last_accuracy";
    private static final String KEY_LAST_SPEED     = "last_speed";
    private static final String KEY_LAST_BEARING   = "last_bearing";
    private static final String KEY_IS_MOCKING     = "is_mocking";
    private static final String KEY_MAP_TYPE       = "map_type";
    private static final String KEY_LICENSE_STATUS = "license_status";
    private static final String KEY_LICENSE_CODE   = "license_code";
    private static final String KEY_LICENSE_EXPIRY = "license_expiry_epoch";
    private static final String KEY_LICENSE_EXPIRY_TEXT = "license_expiry_text";
    private static final String KEY_LICENSE_CHECKED_AT = "license_checked_at";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEY_BUBBLE_VISIBLE = "bubble_visible";
    private static final String KEY_BUBBLE_SIZE = "bubble_size_dp";
    private static final String KEY_BUBBLE_OPACITY = "bubble_opacity";
    private static final String KEY_BUBBLE_X = "bubble_x_fraction";
    private static final String KEY_BUBBLE_Y = "bubble_y_fraction";
    private static final String KEY_HOOK_PID = "hook_runtime_pid";
    private static final String KEY_HOOK_VERSION = "hook_runtime_version";
    private static final String KEY_BLOCK_PLAY_REDIRECT = "block_play_redirect";
    private static final int    MAX_HISTORY        = 200;

    private static WFStorage instance;
    private final SharedPreferences prefs;

    private WFStorage(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized WFStorage getInstance(Context context) {
        if (instance == null) {
            instance = new WFStorage(context);
        }
        return instance;
    }

    // ─── الموقع الأخير ────────────────────────────────────────────────────────

    public void saveLastLocation(WFLocation loc) {
        if (loc == null) return;
        prefs.edit()
             .putFloat(KEY_LAST_LAT, (float) loc.getLatitude())
             .putFloat(KEY_LAST_LNG, (float) loc.getLongitude())
             .putLong(KEY_LAST_ALT, Double.doubleToRawLongBits(loc.getAltitude()))
             .putFloat(KEY_LAST_ACCURACY, loc.getAccuracy())
             .putFloat(KEY_LAST_SPEED, loc.getSpeed())
             .putFloat(KEY_LAST_BEARING, loc.getBearing())
             .commit();
    }

    public WFLocation getLastLocation() {
        float lat = prefs.getFloat(KEY_LAST_LAT, 0f);
        float lng = prefs.getFloat(KEY_LAST_LNG, 0f);
        if (lat == 0f && lng == 0f) return null;
        WFLocation loc = new WFLocation(lat, lng);
        loc.setAltitude(Double.longBitsToDouble(prefs.getLong(
                KEY_LAST_ALT, Double.doubleToRawLongBits(0d))));
        loc.setAccuracy(prefs.getFloat(KEY_LAST_ACCURACY, 3f));
        loc.setSpeed(prefs.getFloat(KEY_LAST_SPEED, 0f));
        loc.setBearing(prefs.getFloat(KEY_LAST_BEARING, 0f));
        return loc;
    }

    // ─── حالة التزييف ─────────────────────────────────────────────────────────

    public void setMocking(boolean active) {
        // The UI and the embedded hook can be loaded by different class loaders.
        // Commit critical state synchronously so the hook sees it immediately.
        prefs.edit().putBoolean(KEY_IS_MOCKING, active).commit();
    }

    public boolean isMocking() {
        return prefs.getBoolean(KEY_IS_MOCKING, false);
    }

    /** Records that the embedded hook completed startup in this exact process. */
    public void markHookRuntimeActive(int versionCode) {
        prefs.edit()
                .putInt(KEY_HOOK_PID, Process.myPid())
                .putInt(KEY_HOOK_VERSION, versionCode)
                .commit();
    }

    /** Prevents a stale marker from a previous process or module version being accepted. */
    public boolean isHookRuntimeActive(int versionCode) {
        return prefs.getInt(KEY_HOOK_PID, -1) == Process.myPid()
                && prefs.getInt(KEY_HOOK_VERSION, -1) == versionCode;
    }

    public void setBlockPlayRedirect(boolean enabled) {
        prefs.edit().putBoolean(KEY_BLOCK_PLAY_REDIRECT, enabled).apply();
    }

    public boolean isBlockPlayRedirectEnabled() {
        // Compatibility-safe default: never interfere with the host application's
        // update or store flow unless the user explicitly enables this option.
        return prefs.getBoolean(KEY_BLOCK_PLAY_REDIRECT, false);
    }

    // ─── نوع الخريطة ──────────────────────────────────────────────────────────

    public void setMapType(String type) {
        prefs.edit().putString(KEY_MAP_TYPE, type).apply();
    }

    public String getMapType() {
        return prefs.getString(KEY_MAP_TYPE, "roadmap");
    }

    // ─── حالة الترخيص ─────────────────────────────────────────────────────────

    public void setLicenseStatus(String status) {
        prefs.edit().putString(KEY_LICENSE_STATUS, status).apply();
    }

    public String getLicenseStatus() {
        return prefs.getString(KEY_LICENSE_STATUS, "");
    }

    public boolean isLicenseActive() {
        if (!"Active".equalsIgnoreCase(getLicenseStatus())) return false;
        long expiry = getLicenseExpiryEpoch();
        if (expiry > 0L && expiry <= System.currentTimeMillis()) {
            prefs.edit().putString(KEY_LICENSE_STATUS, "Expired").apply();
            return false;
        }
        return true;
    }

    public void saveLicense(String code, String status, long expiryEpoch, String expiryText) {
        prefs.edit()
                .putString(KEY_LICENSE_CODE, code == null ? "" : code)
                .putString(KEY_LICENSE_STATUS, status == null ? "" : status)
                .putLong(KEY_LICENSE_EXPIRY, Math.max(0L, expiryEpoch))
                .putString(KEY_LICENSE_EXPIRY_TEXT, expiryText == null ? "" : expiryText)
                .putLong(KEY_LICENSE_CHECKED_AT, System.currentTimeMillis())
                .apply();
    }

    public String getLicenseCode() {
        return prefs.getString(KEY_LICENSE_CODE, "");
    }

    public long getLicenseExpiryEpoch() {
        return prefs.getLong(KEY_LICENSE_EXPIRY, 0L);
    }

    public String getLicenseExpiryText() {
        return prefs.getString(KEY_LICENSE_EXPIRY_TEXT, "");
    }

    public long getLicenseCheckedAt() {
        return prefs.getLong(KEY_LICENSE_CHECKED_AT, 0L);
    }

    public void clearLicense() {
        prefs.edit()
                .remove(KEY_LICENSE_CODE)
                .remove(KEY_LICENSE_STATUS)
                .remove(KEY_LICENSE_EXPIRY)
                .remove(KEY_LICENSE_EXPIRY_TEXT)
                .remove(KEY_LICENSE_CHECKED_AT)
                .putBoolean(KEY_IS_MOCKING, false)
                .apply();
    }

    public String getOrCreateInstallationId() {
        String id = prefs.getString(KEY_INSTALLATION_ID, "");
        if (id != null && !id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_INSTALLATION_ID, id).commit();
        return id;
    }

    public void setBubbleVisible(boolean visible) {
        prefs.edit().putBoolean(KEY_BUBBLE_VISIBLE, visible).apply();
    }

    public boolean isBubbleVisible() {
        return prefs.getBoolean(KEY_BUBBLE_VISIBLE, true);
    }

    public void setBubbleSizeDp(int value) {
        prefs.edit().putInt(KEY_BUBBLE_SIZE, Math.max(42, Math.min(88, value))).apply();
    }

    public int getBubbleSizeDp() {
        return prefs.getInt(KEY_BUBBLE_SIZE, 56);
    }

    public void setBubbleOpacity(float value) {
        prefs.edit().putFloat(KEY_BUBBLE_OPACITY, Math.max(0.35f, Math.min(1f, value))).apply();
    }

    public float getBubbleOpacity() {
        return prefs.getFloat(KEY_BUBBLE_OPACITY, 0.94f);
    }

    public void saveBubblePosition(float xFraction, float yFraction) {
        prefs.edit()
                .putFloat(KEY_BUBBLE_X, Math.max(0f, Math.min(1f, xFraction)))
                .putFloat(KEY_BUBBLE_Y, Math.max(0f, Math.min(1f, yFraction)))
                .apply();
    }

    public float getBubbleXFraction() { return prefs.getFloat(KEY_BUBBLE_X, 0.96f); }
    public float getBubbleYFraction() { return prefs.getFloat(KEY_BUBBLE_Y, 0.50f); }

    // ─── المفضلة ──────────────────────────────────────────────────────────────

    public List<FavoriteLocation> getFavorites() {
        List<FavoriteLocation> list = new ArrayList<>();
        String json = prefs.getString(KEY_FAVORITES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(FavoriteLocation.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            WFLog.e("WFStorage", "getFavorites error: " + e.getMessage());
        }
        return list;
    }

    public void saveFavorites(List<FavoriteLocation> list) {
        JSONArray arr = new JSONArray();
        for (FavoriteLocation f : list) {
            try { arr.put(f.toJson()); } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply();
    }

    public void addFavorite(FavoriteLocation fav) {
        List<FavoriteLocation> list = getFavorites();
        fav.setId(System.currentTimeMillis());
        list.add(fav);
        saveFavorites(list);
    }

    public void updateFavorite(FavoriteLocation fav) {
        List<FavoriteLocation> list = getFavorites();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == fav.getId()) {
                fav.setUpdatedAt(System.currentTimeMillis());
                list.set(i, fav);
                break;
            }
        }
        saveFavorites(list);
    }

    public void deleteFavorite(long id) {
        List<FavoriteLocation> list = getFavorites();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).getId() == id) {
                list.remove(i);
                break;
            }
        }
        saveFavorites(list);
    }

    // ─── السجل ────────────────────────────────────────────────────────────────

    public List<HistoryEntry> getHistory() {
        List<HistoryEntry> list = new ArrayList<>();
        String json = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(HistoryEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            WFLog.e("WFStorage", "getHistory error: " + e.getMessage());
        }
        return list;
    }

    public void addHistory(HistoryEntry entry) {
        List<HistoryEntry> list = getHistory();
        entry.setId(System.currentTimeMillis());
        list.add(0, entry); // الأحدث في الأعلى
        if (list.size() > MAX_HISTORY) {
            list = list.subList(0, MAX_HISTORY);
        }
        JSONArray arr = new JSONArray();
        for (HistoryEntry h : list) {
            try { arr.put(h.toJson()); } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    public void clearHistory() {
        prefs.edit().putString(KEY_HISTORY, "[]").apply();
    }

    // ─── تصدير / استيراد المفضلة ──────────────────────────────────────────────

    public String exportFavoritesJson() {
        return prefs.getString(KEY_FAVORITES, "[]");
    }

    public boolean importFavoritesJson(String json) {
        try {
            JSONArray arr = new JSONArray(json); // للتحقق من الصلاحية
            prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply();
            return true;
        } catch (JSONException e) {
            WFLog.e("WFStorage", "importFavorites error: " + e.getMessage());
            return false;
        }
    }
}
