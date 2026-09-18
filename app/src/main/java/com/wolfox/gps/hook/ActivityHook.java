package com.wolfox.gps.hook;

import android.app.Activity;
import android.content.Context;
import android.view.MotionEvent;

import com.wolfox.gps.DirectBootstrap;
import com.wolfox.gps.ModuleConfig;
import com.wolfox.gps.manager.FloatingManager;
import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.manager.LicenseManager;
import com.wolfox.gps.manager.ToolControlManager;
import com.wolfox.gps.ui.ActivationDialog;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * ActivityHook — يعترض دورة حياة Activity:
 * - onCreate  → تهيئة
 * - onResume  → إظهار الأيقونة العائمة إذا كان الترخيص نشطاً
 * - onPause   → إبلاغ FloatingManager
 * - onDestroy → تنظيف
 *
 * يُستدعى من WolFoxHook.handleLoadPackage
 */
public class ActivityHook {

    private static final String TAG = "ActivityHook";
    private static volatile boolean installed;

    public static synchronized void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (installed) return;
        installed = true;

        // ─ onResume ──────────────────────────────────────────────────────────
        try {
            XposedHelpers.findAndHookMethod(
                    Activity.class,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            HookSafety.run("Activity.onResume", () -> {
                            if (DirectBootstrap.isHostAttached()) return;
                            Activity act = (Activity) param.thisObject;
                            Context ctx  = act.getApplicationContext();

                            GPSMockManager.getInstance().init(ctx);
                            // ActivityHook itself only runs from the injected runtime, so this
                            // is a safe fallback marker if an OEM skipped Application callbacks.
                            WFStorage.getInstance(ctx)
                                    .markHookRuntimeActive(ModuleConfig.VERSION_CODE);
                            ToolControlManager policy = ToolControlManager.getInstance();
                            policy.startSessionMonitoring(act);
                            if (policy.isBlocked(act)) {
                                policy.enforceCached(act);
                                return;
                            }
                            LicenseManager.getInstance().startSessionMonitoring(act);
                            FloatingManager.getInstance().onActivityResumed(act);

                            if (LicenseManager.getInstance().isActive(ctx)) {
                                ActivationDialog.dismiss();
                                LicenseManager.getInstance().refreshIfDue(act);
                                if (WFStorage.getInstance(ctx).isBubbleVisible()
                                        && !FloatingManager.getInstance().isVisible()) {
                                    FloatingManager.getInstance().show(act);
                                } else if (!WFStorage.getInstance(ctx).isBubbleVisible()) {
                                    FloatingManager.getInstance().hide(false);
                                }
                            } else {
                                GPSMockManager.getInstance().stopMocking();
                                FloatingManager.getInstance().show(act);
                                ActivationDialog.showRequired(act);
                            }
                            WFLog.d(TAG, "onResume: " + act.getClass().getSimpleName());
                            });
                        }
                    }
            );
        } catch (Exception e) {
            WFLog.e(TAG, "hook onResume: " + e.getMessage());
        }

        // ─ onPause ───────────────────────────────────────────────────────────
        try {
            XposedHelpers.findAndHookMethod(
                    Activity.class,
                    "onPause",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            HookSafety.run("Activity.onPause", () -> {
                            if (DirectBootstrap.isHostAttached()) return;
                            LicenseManager.getInstance().stopSessionMonitoring(
                                    (Activity) param.thisObject);
                            ToolControlManager.getInstance().stopSessionMonitoring(
                                    (Activity) param.thisObject);
                            FloatingManager.getInstance().onActivityPaused(
                                    (Activity) param.thisObject);
                            });
                        }
                    }
            );
        } catch (Exception e) {
            WFLog.e(TAG, "hook onPause: " + e.getMessage());
        }

        // ─ onDestroy ─────────────────────────────────────────────────────────
        try {
            XposedHelpers.findAndHookMethod(
                    Activity.class,
                    "onDestroy",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            HookSafety.run("Activity.onDestroy", () -> {
                            if (DirectBootstrap.isHostAttached()) return;
                            FloatingManager.getInstance().onActivityDestroyed(
                                    (Activity) param.thisObject);
                            });
                        }
                    }
            );
        } catch (Exception e) {
            WFLog.e(TAG, "hook onDestroy: " + e.getMessage());
        }

        // مراقبة ضغط مطوّل في منتصف الشاشة بدون استهلاك لمس التطبيق الأصلي.
        try {
            XposedHelpers.findAndHookMethod(
                    Activity.class,
                    "dispatchTouchEvent", MotionEvent.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            HookSafety.run("Activity.dispatchTouchEvent", () -> {
                            if (DirectBootstrap.isHostAttached()) return;
                            CenterLongPressHook.observe((Activity) param.thisObject,
                                    (MotionEvent) param.args[0]);
                            });
                        }
                    }
            );
        } catch (Exception e) {
            WFLog.e(TAG, "hook dispatchTouchEvent: " + e.getMessage());
        }
    }
}
