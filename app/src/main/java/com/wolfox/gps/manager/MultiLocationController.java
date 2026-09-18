package com.wolfox.gps.manager;

import android.content.Context;
import android.content.SharedPreferences;

import com.wolfox.gps.model.FavoriteLocation;
import com.wolfox.gps.util.WFStorage;

import java.util.List;

/** Cycles saved laboratory locations at a conservative user-controlled interval. */
public final class MultiLocationController {
    private static final long DEFAULT_INTERVAL_MS = 15L * 60L * 1000L;
    private MultiLocationController() {}

    public static void tick(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(
                "wolfox_multi_location_lab", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("enabled", false)) return;
        long interval = Math.max(60_000L,
                prefs.getLong("interval_ms", DEFAULT_INTERVAL_MS));
        long now = System.currentTimeMillis();
        if (now - prefs.getLong("last_switch", 0L) < interval) return;
        List<FavoriteLocation> locations = WFStorage.getInstance(context).getFavorites();
        if (locations.size() < 2) return;
        int next = (prefs.getInt("index", -1) + 1) % locations.size();
        GPSMockManager.getInstance().setLocation(locations.get(next).toWFLocation());
        prefs.edit().putInt("index", next).putLong("last_switch", now).apply();
    }
}
