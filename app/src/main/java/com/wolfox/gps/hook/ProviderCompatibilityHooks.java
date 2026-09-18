package com.wolfox.gps.hook;

import android.location.Location;
import android.location.LocationManager;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFLog;

import java.util.Collections;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Optional provider compatibility derived from the behaviour inventory of the
 * K7 reference module. Every provider is isolated: an absent SDK or a changed
 * vendor implementation must never prevent the host application from starting.
 */
final class ProviderCompatibilityHooks {
    private static final String TAG = "WolFoxProviders";

    private ProviderCompatibilityHooks() {}

    static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        hookFrameworkAvailability();
        hookLocationResultList(lpparam.classLoader,
                "com.google.android.gms.location.LocationResult");
        hookLocationResultList(lpparam.classLoader,
                "com.huawei.hms.location.LocationResult");
        hookFusedTask(lpparam.classLoader,
                "com.huawei.hms.location.FusedLocationProviderClient",
                "com.huawei.hms.tasks.Tasks");
        hookVendorLocation(lpparam.classLoader,
                "com.amap.api.location.AMapLocation");
        hookVendorLocation(lpparam.classLoader,
                "com.baidu.location.BDLocation");
        hookMapbox(lpparam.classLoader);
    }

    private static void hookFrameworkAvailability() {
        hookBoolean(LocationManager.class, "isProviderEnabled", true);
        hookBoolean(LocationManager.class, "isLocationEnabled", true);
    }

    private static void hookLocationResultList(ClassLoader loader, String className) {
        try {
            Class<?> type = XposedHelpers.findClass(className, loader);
            XposedBridge.hookAllMethods(type, "getLocations", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Location location = activeLocation();
                    if (location != null) param.setResult(Collections.singletonList(location));
                }
            });
        } catch (Throwable error) {
            WFLog.d(TAG, className + ".getLocations unavailable: " + error.getMessage());
        }
    }

    private static void hookFusedTask(ClassLoader loader, String clientName, String tasksName) {
        try {
            Class<?> client = XposedHelpers.findClass(clientName, loader);
            XposedBridge.hookAllMethods(client, "getLastLocation", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Location location = activeLocation();
                    if (location == null) return;
                    try {
                        Class<?> tasks = XposedHelpers.findClass(tasksName, loader);
                        Object task = tasks.getMethod("forResult", Object.class)
                                .invoke(null, location);
                        if (task != null) param.setResult(task);
                    } catch (Throwable error) {
                        WFLog.d(TAG, tasksName + ": " + error.getMessage());
                    }
                }
            });
        } catch (Throwable error) {
            WFLog.d(TAG, clientName + " unavailable: " + error.getMessage());
        }
    }

    private static void hookVendorLocation(ClassLoader loader, String className) {
        try {
            Class<?> type = XposedHelpers.findClass(className, loader);
            hookCoordinate(type, "getLatitude", true);
            hookCoordinate(type, "getLongitude", false);
            hookMetric(type, "getAccuracy", "accuracy");
            hookMetric(type, "getSpeed", "speed");
            hookMetric(type, "getAltitude", "altitude");
            hookBoolean(type, "hasAccuracy", true);
            hookBoolean(type, "hasAltitude", true);
            hookBoolean(type, "hasSpeed", true);
            WFLog.i(TAG, "provider compatibility installed: " + className);
        } catch (Throwable error) {
            WFLog.d(TAG, className + " unavailable: " + error.getMessage());
        }
    }

    private static void hookMapbox(ClassLoader loader) {
        try {
            Class<?> result = XposedHelpers.findClass(
                    "com.mapbox.android.core.location.LocationEngineResult", loader);
            XposedBridge.hookAllMethods(result, "getLastLocation", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Location location = activeLocation();
                    if (location != null) param.setResult(location);
                }
            });
            XposedBridge.hookAllMethods(result, "getLocations", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Location location = activeLocation();
                    if (location != null) param.setResult(Collections.singletonList(location));
                }
            });
        } catch (Throwable error) {
            WFLog.d(TAG, "Mapbox unavailable: " + error.getMessage());
        }
    }

    private static void hookCoordinate(Class<?> type, String method, boolean latitude) {
        try {
            XposedBridge.hookAllMethods(type, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    WFLocation location = activeModel();
                    if (location != null) param.setResult(latitude
                            ? location.getLatitude() : location.getLongitude());
                }
            });
        } catch (Throwable error) {
            WFLog.d(TAG, type.getName() + "." + method + ": " + error.getMessage());
        }
    }

    private static void hookMetric(Class<?> type, String method, String metric) {
        try {
            XposedBridge.hookAllMethods(type, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    WFLocation location = activeModel();
                    if (location == null) return;
                    Object original = param.getResult();
                    double value = "altitude".equals(metric) ? location.getAltitude()
                            : "speed".equals(metric) ? location.getSpeed()
                            : location.getAccuracy();
                    if (original instanceof Float) param.setResult((float) value);
                    else if (original instanceof Integer) param.setResult((int) value);
                    else param.setResult(value);
                }
            });
        } catch (Throwable error) {
            WFLog.d(TAG, type.getName() + "." + method + ": " + error.getMessage());
        }
    }

    private static void hookBoolean(Class<?> type, String method, boolean value) {
        try {
            XposedBridge.hookAllMethods(type, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (activeModel() != null) param.setResult(value);
                }
            });
        } catch (Throwable error) {
            WFLog.d(TAG, type.getName() + "." + method + ": " + error.getMessage());
        }
    }

    private static WFLocation activeModel() {
        GPSMockManager manager = GPSMockManager.getInstance();
        if (!manager.isMocking()) return null;
        WFLocation location = manager.getLocation();
        return location != null && location.isValid() ? location : null;
    }

    private static Location activeLocation() {
        WFLocation model = activeModel();
        return model == null ? null : GPSMockManager.getInstance()
                .buildLocation(LocationManager.GPS_PROVIDER, model);
    }
}
