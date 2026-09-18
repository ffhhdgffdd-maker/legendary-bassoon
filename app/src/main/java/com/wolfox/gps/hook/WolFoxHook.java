package com.wolfox.gps.hook;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import com.wolfox.gps.ModuleConfig;
import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFLog;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * WolFoxHook — نقطة الدخول للموديول عبر LSPatch
 *
 * LSPatch يُدمج هذا الموديول مباشرة داخل APK التطبيق المستهدف،
 * ويُشغّل الهوكات بدون root.
 *
 * التزييف يعمل عبر اعتراض استدعاءات الموقع فقط —
 * لا TestProvider، لا MOCK_LOCATION، لا SYSTEM_ALERT_WINDOW.
 */
public class WolFoxHook implements IXposedHookLoadPackage {

    private static final String TAG = "WolFoxHook";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null || lpparam.classLoader == null) return;
        if (!ModuleConfig.isAllowedTestPackage(lpparam.packageName)) {
            return;
        }
        if ("android".equals(lpparam.packageName)
                || lpparam.packageName.startsWith("com.android.systemui")
                || lpparam.packageName.equals("com.wolfox.gps")) return;
        WFLog.i(TAG, "LSPatch loaded in: " + lpparam.packageName);

        HookSafety.run("install.context", this::hookApplicationContext);
        if (ModuleConfig.ENABLE_LOCATION) {
            HookSafety.run("install.locationManager", () -> hookLocationManager(lpparam));
            HookSafety.run("install.locationObject", this::hookLocationObject);
            HookSafety.run("install.gms", () -> hookFusedLocation(lpparam));
            HookSafety.run("install.hms", () -> hookHuaweiLocation(lpparam));
            HookSafety.run("install.providers", () -> ProviderCompatibilityHooks.install(lpparam));
            HookSafety.run("install.webview", () -> hookWebView(lpparam));
        }
        if (ModuleConfig.ENABLE_CAMERA_LAB) {
            HookSafety.run("install.cameraLab", () -> CameraLabHooks.install(lpparam));
        }
        HookSafety.run("install.playRedirect", this::hookPlayStoreRedirects);
        // Always install the lifecycle fallback in the main process.  Merely finding
        // DirectBootstrap in the class loader does not prove that the host calls it:
        // the class is bundled inside the module as well.  ActivityHook checks the
        // runtime host marker and stands down only when direct integration is active.
        if (lpparam.processName == null || lpparam.processName.equals(lpparam.packageName)) {
            HookSafety.run("install.activity", () -> ActivityHook.install(lpparam));
        }

        WFLog.i(TAG, "✅ WolFox hooks active (no-root mode)");
    }

    private void hookLocationObject() {
        hookLocationGetter("getLatitude", "latitude");
        hookLocationGetter("getLongitude", "longitude");
        hookLocationGetter("getAltitude", "altitude");
        hookLocationGetter("getAccuracy", "accuracy");
        hookLocationGetter("getSpeed", "speed");
        hookLocationGetter("getBearing", "bearing");
        try {
            XposedBridge.hookAllMethods(Location.class, "isFromMockProvider",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (GPSMockManager.getInstance().isMocking()) param.setResult(false);
                        }
                    });
        } catch (Throwable e) { WFLog.d(TAG, "isFromMockProvider: " + e.getMessage()); }
        try {
            XposedBridge.hookAllMethods(Location.class, "isMock", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (GPSMockManager.getInstance().isMocking()) param.setResult(false);
                        }
                    });
        } catch (Throwable e) { WFLog.d(TAG, "isMock: " + e.getMessage()); }
    }

    private void hookLocationGetter(String method, String field) {
        try {
            XposedBridge.hookAllMethods(Location.class, method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!GPSMockManager.getInstance().isMocking()) return;
                            WFLocation l = GPSMockManager.getInstance().getLocation();
                            if (l == null) return;
                            if ("latitude".equals(field)) param.setResult(l.getLatitude());
                            else if ("longitude".equals(field)) param.setResult(l.getLongitude());
                            else if ("altitude".equals(field)) param.setResult(l.getAltitude());
                            else if ("accuracy".equals(field)) param.setResult(l.getAccuracy());
                            else if ("speed".equals(field)) param.setResult(l.getSpeed());
                            else if ("bearing".equals(field)) param.setResult(l.getBearing());
                        }
                    });
        } catch (Throwable e) { WFLog.d(TAG, method + ": " + e.getMessage()); }
    }

    // ─── تهيئة السياق ─────────────────────────────────────────────────────────

    private void hookApplicationContext() {
        // Application.attach is the earliest stable context point and also covers hosts
        // whose custom Application overrides onCreate without calling super.
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            Context context = param.args != null && param.args.length > 0
                                    && param.args[0] instanceof Context
                                    ? (Context) param.args[0] : (Context) param.thisObject;
                            contextReady(context, "attach");
                        }
                    });
        } catch (Throwable error) {
            WFLog.e(TAG, "hook Application.attach: " + error.getMessage());
        }

        // Fallback for unusual runtimes where attach has already completed.
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            contextReady((Context) param.thisObject, "onCreate");
                        }
                    });
        } catch (Throwable error) {
            WFLog.e(TAG, "hook Application.onCreate: " + error.getMessage());
        }

        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method current = activityThread.getDeclaredMethod("currentApplication");
            current.setAccessible(true);
            Object application = current.invoke(null);
            if (application instanceof Context) contextReady((Context) application, "current");
        } catch (Throwable ignored) {}
    }

    private static void contextReady(Context context, String source) {
        HookSafety.run("contextReady." + source, () -> {
            if (context == null) return;
            GPSMockManager.getInstance().init(context);
            com.wolfox.gps.util.WFStorage.getInstance(context)
                    .markHookRuntimeActive(ModuleConfig.VERSION_CODE);
            WFLog.i(TAG, "Context ready via " + source);
        });
    }

    private void hookPlayStoreRedirects() {
        XC_MethodHook guard = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                HookSafety.run("playRedirect", () -> {
                    Context context = param.thisObject instanceof Context
                            ? (Context) param.thisObject : null;
                    if (context == null || !com.wolfox.gps.util.WFStorage.getInstance(context)
                            .isBlockPlayRedirectEnabled()) return;
                    Intent intent = null;
                    if (param.args != null) for (Object arg : param.args) {
                        if (arg instanceof Intent) { intent = (Intent) arg; break; }
                    }
                    if (intent == null || !isPlayStoreIntent(intent)) return;
                    param.setResult(null);
                    android.widget.Toast.makeText(context,
                            "تم منع التحويل الإجباري إلى Google Play",
                            android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        };
        XposedBridge.hookAllMethods(Activity.class, "startActivity", guard);
        XposedBridge.hookAllMethods(Activity.class, "startActivityForResult", guard);
    }

    private static boolean isPlayStoreIntent(Intent intent) {
        try {
            String pkg = intent.getPackage();
            if ("com.android.vending".equals(pkg)) return true;
            Uri data = intent.getData();
            if (data == null) return false;
            String scheme = data.getScheme();
            String host = data.getHost();
            return "market".equalsIgnoreCase(scheme)
                    || "play.google.com".equalsIgnoreCase(host)
                    || "market.android.com".equalsIgnoreCase(host);
        } catch (Throwable ignored) { return false; }
    }

    // ─── LocationManager ──────────────────────────────────────────────────────

    private void hookLocationManager(XC_LoadPackage.LoadPackageParam lpparam) {

        // 1) getLastKnownLocation
        try {
            XposedHelpers.findAndHookMethod(
                LocationManager.class,
                "getLastKnownLocation", String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        param.setResult(GPSMockManager.getInstance()
                            .buildLocation((String) param.args[0], wfl));
                        WFLog.hook("getLastKnownLocation", wfl);
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "getLastKnownLocation: " + e.getMessage()); }

        // 2) requestLocationUpdates (String, long, float, LocationListener)
        try {
            XposedHelpers.findAndHookMethod(
                LocationManager.class,
                "requestLocationUpdates",
                String.class, long.class, float.class, LocationListener.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        LocationListener listener = (LocationListener) param.args[3];
                        if (listener == null) return;
                        Location loc = GPSMockManager.getInstance()
                            .buildLocation((String) param.args[0], wfl);
                        try { listener.onLocationChanged(loc); } catch (Exception ignored) {}
                        WFLog.hook("requestLocationUpdates→cb", wfl);
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "requestLocationUpdates: " + e.getMessage()); }

        // 3) requestSingleUpdate
        try {
            XposedHelpers.findAndHookMethod(
                LocationManager.class,
                "requestSingleUpdate",
                String.class, LocationListener.class, android.os.Looper.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        LocationListener listener = (LocationListener) param.args[1];
                        if (listener == null) return;
                        Location loc = GPSMockManager.getInstance()
                            .buildLocation((String) param.args[0], wfl);
                        try { listener.onLocationChanged(loc); } catch (Exception ignored) {}
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "requestSingleUpdate: " + e.getMessage()); }

        // 4) getCurrentLocation (API 30+)
        try {
            XposedHelpers.findAndHookMethod(
                LocationManager.class,
                "getCurrentLocation",
                String.class, android.os.CancellationSignal.class,
                java.util.concurrent.Executor.class,
                lpparam.classLoader.loadClass("java.util.function.Consumer"),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        try {
                            Object consumer = param.args[3];
                            Location loc = GPSMockManager.getInstance()
                                .buildLocation((String) param.args[0], wfl);
                            consumer.getClass().getMethod("accept", Object.class)
                                .invoke(consumer, loc);
                            param.setResult(null);
                        } catch (Exception ex) {
                            WFLog.e(TAG, "getCurrentLocation: " + ex.getMessage());
                        }
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "getCurrentLocation (API30+): " + e.getMessage()); }
    }

    // ─── FusedLocationProviderClient ─────────────────────────────────────────

    private void hookFusedLocation(XC_LoadPackage.LoadPackageParam lpparam) {

        // getLastLocation()
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.FusedLocationProviderClient",
                lpparam.classLoader, "getLastLocation",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        Location loc = GPSMockManager.getInstance()
                            .buildLocation(LocationManager.GPS_PROVIDER, wfl);
                        Object task = buildFakeTask(loc, lpparam.classLoader);
                        if (task != null) param.setResult(task);
                        WFLog.hook("Fused.getLastLocation", wfl);
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "Fused.getLastLocation: " + e.getMessage()); }

        // LocationResult.getLastLocation()
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.LocationResult",
                lpparam.classLoader, "getLastLocation",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        param.setResult(GPSMockManager.getInstance()
                            .buildLocation(LocationManager.GPS_PROVIDER, wfl));
                        WFLog.hook("LocationResult.getLastLocation", wfl);
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "LocationResult: " + e.getMessage()); }

        // LocationAvailability.isLocationAvailable() → true دائماً
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.LocationAvailability",
                lpparam.classLoader, "isLocationAvailable",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (GPSMockManager.getInstance().isMocking())
                            param.setResult(true);
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "LocationAvailability: " + e.getMessage()); }
    }

    private Object buildFakeTask(Location loc, ClassLoader cl) {
        try {
            Class<?> tasks = cl.loadClass("com.google.android.gms.tasks.Tasks");
            return tasks.getMethod("forResult", Object.class).invoke(null, loc);
        } catch (Exception e) {
            WFLog.e(TAG, "buildFakeTask: " + e.getMessage());
            return null;
        }
    }

    private void hookHuaweiLocation(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.huawei.hms.location.LocationResult", lpparam.classLoader,
                    "getLastLocation", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!GPSMockManager.getInstance().isMocking()) return;
                            WFLocation l = GPSMockManager.getInstance().getLocation();
                            if (l != null) param.setResult(GPSMockManager.getInstance()
                                    .buildLocation(LocationManager.GPS_PROVIDER, l));
                        }
                    });
        } catch (Exception e) { WFLog.d(TAG, "HMS LocationResult: " + e.getMessage()); }
    }

    // ─── WebView / navigator.geolocation ─────────────────────────────────────

    private void hookWebView(XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook geoHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                injectGeoJs((android.webkit.WebView) param.thisObject);
            }
        };

        try {
            XposedHelpers.findAndHookMethod(android.webkit.WebView.class,
                "loadUrl", String.class, geoHook);
        } catch (Exception e) { WFLog.d(TAG, "WebView.loadUrl: " + e.getMessage()); }

        try {
            XposedHelpers.findAndHookMethod(android.webkit.WebView.class,
                "loadDataWithBaseURL",
                String.class, String.class, String.class, String.class, String.class, geoHook);
        } catch (Exception e) { WFLog.d(TAG, "WebView.loadData: " + e.getMessage()); }
    }

    private void injectGeoJs(android.webkit.WebView wv) {
        if (wv == null || !GPSMockManager.getInstance().isMocking()) return;
        WFLocation wfl = GPSMockManager.getInstance().getLocation();
        if (wfl == null) return;

        final double lat = wfl.getLatitude();
        final double lng = wfl.getLongitude();
        final long   ts  = System.currentTimeMillis();

        String js = "(function(){"
            + "var pos={coords:{latitude:" + lat + ",longitude:" + lng
            + ",accuracy:1,altitude:0,altitudeAccuracy:null,heading:null,speed:null},"
            + "timestamp:" + ts + "};"
            + "navigator.geolocation={"
            + "getCurrentPosition:function(s){s(pos);},"
            + "watchPosition:function(s){s(pos);return 1;},"
            + "clearWatch:function(){}"
            + "};"
            + "})();";

        try { wv.evaluateJavascript(js, null); }
        catch (Exception e) { WFLog.e(TAG, "injectGeoJs: " + e.getMessage()); }
    }
}
