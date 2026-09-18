package com.wolfox.gps.manager;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;

import com.wolfox.gps.ModuleConfig;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;

/**
 * GPSMockManager — مدير التزييف عبر الهوكات (بدون root / بدون TestProvider)
 *
 * طريقة العمل:
 *   - WolFoxHook يعترض استدعاءات LocationManager و FusedLocation
 *   - GPSMockManager يحتفظ بالموقع المزيف
 *   - الهوكات تسأل isMocking() + getLocation() وتُعيد الموقع المزيف
 *
 * لا يوجد أي استخدام لـ:
 *   - setTestProviderLocation  (يحتاج root)
 *   - MOCK_LOCATION permission
 *   - SYSTEM_ALERT_WINDOW
 */
public class GPSMockManager {

    private static final String TAG = "GPSMockManager";

    private static GPSMockManager instance;

    private volatile WFLocation  currentLocation;
    private volatile boolean     mocking = false;
    private Context              appContext;

    private GPSMockManager() {}

    public static synchronized GPSMockManager getInstance() {
        if (instance == null) instance = new GPSMockManager();
        return instance;
    }

    // ─── تهيئة ────────────────────────────────────────────────────────────────

    public synchronized void init(Context ctx) {
        if (ctx != null) {
            Context candidate = ctx.getApplicationContext();
            this.appContext = candidate != null ? candidate : ctx;
            // استعادة الحالة من آخر جلسة
            if (WFStorage.getInstance(appContext).isMocking()) {
                WFLocation last = WFStorage.getInstance(appContext).getLastLocation();
                if (last != null) {
                    this.currentLocation = last;
                    this.mocking = true;
                    WFLog.i(TAG, "استعادة جلسة تزييف سابقة: " + last);
                }
            }
            if (this.currentLocation == null && !this.mocking) {
                WFLocation device = readCurrentDeviceLocation(appContext);
                if (device != null) setLocation(device);
            }
        }
    }

    // ─── الموقع ───────────────────────────────────────────────────────────────

    public void setLocation(WFLocation loc) {
        if (loc == null || !loc.isValid()) {
            WFLog.w(TAG, "موقع غير صالح");
            return;
        }
        this.currentLocation = loc;
        if (appContext != null) {
            WFStorage.getInstance(appContext).saveLastLocation(loc);
        }
        WFLog.i(TAG, "setLocation: " + loc);
        FloatingManager.getInstance().refreshAppearance();
    }

    public WFLocation getLocation() {
        if (appContext != null && WFStorage.getInstance(appContext).isMocking()) {
            WFLocation stored = WFStorage.getInstance(appContext).getLastLocation();
            if (stored != null) currentLocation = stored;
        }
        return currentLocation;
    }

    public boolean hasMockLocation() {
        return currentLocation != null && currentLocation.isValid();
    }

    // ─── بدء / إيقاف ─────────────────────────────────────────────────────────

    public void startMocking() {
        if (appContext == null || !LicenseManager.getInstance().isActive(appContext)) {
            WFLog.w(TAG, "رفض التشغيل: الترخيص غير نشط");
            return;
        }
        if (!hasMockLocation()) {
            WFLog.w(TAG, "لا يوجد موقع محدد");
            return;
        }
        mocking = true;
        if (appContext != null) {
            WFStorage.getInstance(appContext).setMocking(true);
        }
        WFLog.i(TAG, "▶ بدء التزييف: " + currentLocation);
        FloatingManager.getInstance().refreshAppearance();
    }

    /** Selects and starts atomically so UI state and persistent hook state cannot diverge. */
    public boolean selectAndStart(WFLocation location) {
        if (location == null || !location.isValid()) return false;
        setLocation(location);
        startMocking();
        WFLocation active = getLocation();
        return isMocking() && samePoint(location, active);
    }

    /** Confirms that the installed Location getter hook returns the selected point. */
    public boolean verifyRuntimeInjection() {
        WFLocation expected = getLocation();
        if (!isMocking() || expected == null || !expected.isValid()) return false;
        if (appContext == null || !WFStorage.getInstance(appContext)
                .isHookRuntimeActive(ModuleConfig.VERSION_CODE)) {
            WFLog.w(TAG, "runtime hook marker not found for this process");
            return false;
        }
        try {
            double sentinelLat = Math.abs(expected.getLatitude() - 1.234567d) < 0.000001d
                    ? -12.345678d : 1.234567d;
            double sentinelLng = Math.abs(expected.getLongitude() - 2.345678d) < 0.000001d
                    ? -23.456789d : 2.345678d;
            // Use a framework provider so provider-scoped location implementations also run.
            Location probe = new Location(LocationManager.GPS_PROVIDER);
            probe.setLatitude(sentinelLat);
            probe.setLongitude(sentinelLng);
            boolean verified = Math.abs(probe.getLatitude() - expected.getLatitude()) < 0.000001d
                    && Math.abs(probe.getLongitude() - expected.getLongitude()) < 0.000001d;
            WFLog.i(TAG, verified ? "runtime injection verified" : "runtime injection not observed");
            return verified;
        } catch (Throwable error) {
            WFLog.e(TAG, "runtime verification: " + error.getMessage());
            return false;
        }
    }

    public void stopMocking() {
        mocking = false;
        if (appContext != null) {
            WFStorage.getInstance(appContext).setMocking(false);
        }
        WFLog.i(TAG, "⏹ إيقاف التزييف");
        FloatingManager.getInstance().refreshAppearance();
    }

    public boolean isMocking() {
        if (appContext != null) {
            mocking = WFStorage.getInstance(appContext).isMocking();
            if (mocking && currentLocation == null) {
                currentLocation = WFStorage.getInstance(appContext).getLastLocation();
            }
        }
        return mocking;
    }

    private static boolean samePoint(WFLocation first, WFLocation second) {
        return first != null && second != null
                && Math.abs(first.getLatitude() - second.getLatitude()) < 0.000001d
                && Math.abs(first.getLongitude() - second.getLongitude()) < 0.000001d;
    }

    /** Reads the best cached real device location while spoofing is off. */
    public WFLocation useCurrentDeviceLocation(Context context) {
        if (mocking) return null;
        WFLocation location = readCurrentDeviceLocation(context);
        if (location != null) setLocation(location);
        return location;
    }

    private WFLocation readCurrentDeviceLocation(Context context) {
        if (context == null) return null;
        try {
            if (context.checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                    && context.checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
            LocationManager manager = (LocationManager)
                    context.getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) return null;
            Location best = null;
            for (String provider : manager.getProviders(true)) {
                try {
                    Location value = manager.getLastKnownLocation(provider);
                    if (value != null && (best == null
                            || value.getTime() > best.getTime()
                            || value.getAccuracy() < best.getAccuracy())) best = value;
                } catch (SecurityException ignored) {}
            }
            if (best == null) return null;
            WFLocation out = new WFLocation(best.getLatitude(), best.getLongitude(), "موقعي الحالي");
            out.setAltitude(best.hasAltitude() ? best.getAltitude() : 0d);
            out.setAccuracy(best.hasAccuracy() ? best.getAccuracy() : 3f);
            out.setSpeed(best.hasSpeed() ? best.getSpeed() : 0f);
            out.setBearing(best.hasBearing() ? best.getBearing() : 0f);
            return out.isValid() ? out : null;
        } catch (Exception error) {
            WFLog.e(TAG, "device location: " + error.getMessage());
            return null;
        }
    }

    // ─── بناء Location حقيقي (تستخدمه الهوكات) ───────────────────────────────

    public Location buildLocation(String provider, WFLocation wfLoc) {
        Location loc = new Location(provider != null ? provider : LocationManager.GPS_PROVIDER);
        loc.setLatitude(wfLoc.getLatitude());
        loc.setLongitude(wfLoc.getLongitude());
        loc.setAltitude(wfLoc.getAltitude());
        loc.setAccuracy(wfLoc.getAccuracy());
        loc.setSpeed(wfLoc.getSpeed());
        loc.setBearing(wfLoc.getBearing());
        loc.setTime(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            loc.setVerticalAccuracyMeters(1.0f);
            loc.setSpeedAccuracyMetersPerSecond(0.0f);
            loc.setBearingAccuracyDegrees(0.0f);
        }
        return loc;
    }
}
