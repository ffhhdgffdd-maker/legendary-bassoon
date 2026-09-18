package com.wolfox.gps;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.wolfox.gps.manager.FloatingManager;
import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.manager.LicenseManager;
import com.wolfox.gps.manager.ToolControlManager;
import com.wolfox.gps.manager.MultiLocationController;
import com.wolfox.gps.ui.ActivationDialog;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;

import java.lang.ref.WeakReference;

/** Direct lifecycle entry used by the patched host in addition to Xposed hooks. */
public final class DirectBootstrap {
    private static final String TAG = "DirectBootstrap";
    private static final long REVEAL_HOLD_MS = 850L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static boolean lifecycleInstalled;
    private static volatile boolean hostAttached;
    private static boolean gestureArmed;
    private static float gestureDownX;
    private static float gestureDownY;
    private static Runnable pendingReveal;
    private static WeakReference<Activity> gestureOwner = new WeakReference<>(null);

    private DirectBootstrap() {}

    public static void attach(Activity activity) {
        if (!usable(activity)) return;
        hostAttached = true;
        installLifecycleCallbacks(activity.getApplication());
        render(activity, 0L);
        render(activity, 650L);
        render(activity, 1800L);
    }

    private static void render(final Activity activity, long delay) {
        MAIN.postDelayed(() -> {
            if (!usable(activity)) return;
            try {
                GPSMockManager.getInstance().init(activity.getApplicationContext());
                if (ModuleConfig.ENABLE_MULTI_LOCATION) {
                    MultiLocationController.tick(activity.getApplicationContext());
                }
                ToolControlManager policy = ToolControlManager.getInstance();
                policy.refresh(activity);
                if (policy.isBlocked(activity)) {
                    policy.enforceCached(activity);
                    return;
                }
                FloatingManager floating = FloatingManager.getInstance();
                floating.onActivityResumed(activity);
                if (WFStorage.getInstance(activity).isBubbleVisible()) floating.show(activity);
                else floating.hide(false);
                if (LicenseManager.getInstance().isActive(activity)) {
                    ActivationDialog.dismiss();
                    LicenseManager.getInstance().refreshIfDue(activity);
                } else {
                    GPSMockManager.getInstance().stopMocking();
                }
                WFLog.i(TAG, "floating control requested");
            } catch (Throwable error) {
                WFLog.e(TAG, "attach failed: " + error.getMessage(), error);
            }
        }, delay);
    }

    /** Observes the center hold gesture without consuming the host application's touch. */
    public static void observeTouch(Activity activity, MotionEvent event) {
        if (!usable(activity) || event == null) return;
        hostAttached = true;
        if (ToolControlManager.getInstance().isBlocked(activity)) {
            cancelReveal();
            ToolControlManager.getInstance().enforceCached(activity);
            return;
        }
        if (WFStorage.getInstance(activity).isBubbleVisible()) {
            cancelReveal();
            return;
        }
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) return;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelReveal();
                float centerX = decor.getWidth() / 2f;
                float centerY = decor.getHeight() / 2f;
                float radius = Math.max(dp(activity, 64),
                        Math.min(decor.getWidth(), decor.getHeight()) * 0.13f);
                if (Math.abs(event.getX() - centerX) > radius
                        || Math.abs(event.getY() - centerY) > radius) return;
                gestureOwner = new WeakReference<>(activity);
                gestureDownX = event.getX();
                gestureDownY = event.getY();
                gestureArmed = true;
                pendingReveal = () -> {
                    Activity owner = gestureOwner.get();
                    if (!gestureArmed || !usable(owner)) return;
                    gestureArmed = false;
                    WFStorage.getInstance(owner).setBubbleVisible(true);
                    FloatingManager.getInstance().show(owner);
                    View ownerDecor = owner.getWindow() == null
                            ? null : owner.getWindow().getDecorView();
                    if (ownerDecor != null) ownerDecor.performHapticFeedback(0);
                    Toast.makeText(owner, "تم إظهار أيقونة WolFox",
                            Toast.LENGTH_SHORT).show();
                };
                MAIN.postDelayed(pendingReveal, REVEAL_HOLD_MS);
                break;
            case MotionEvent.ACTION_MOVE:
                if (gestureArmed && (Math.abs(event.getX() - gestureDownX) > dp(activity, 22)
                        || Math.abs(event.getY() - gestureDownY) > dp(activity, 22))) {
                    cancelReveal();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelReveal();
                break;
            default:
                break;
        }
    }

    private static void cancelReveal() {
        gestureArmed = false;
        if (pendingReveal != null) MAIN.removeCallbacks(pendingReveal);
        pendingReveal = null;
    }

    /** True only after the host explicitly invokes the direct integration bridge. */
    public static boolean isHostAttached() {
        return hostAttached;
    }

    private static void installLifecycleCallbacks(Application application) {
        if (application == null || lifecycleInstalled) return;
        synchronized (LOCK) {
            if (lifecycleInstalled) return;
            application.registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override public void onActivityCreated(Activity activity, Bundle state) {}
                        @Override public void onActivityStarted(Activity activity) {}
                        @Override public void onActivityStopped(Activity activity) {}
                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                        @Override public void onActivityResumed(Activity activity) {
                            LicenseManager.getInstance().startSessionMonitoring(activity);
                            ToolControlManager.getInstance().startSessionMonitoring(activity);
                            render(activity, 0L);
                            render(activity, 450L);
                        }

                        @Override public void onActivityPaused(Activity activity) {
                            LicenseManager.getInstance().stopSessionMonitoring(activity);
                            ToolControlManager.getInstance().stopSessionMonitoring(activity);
                            FloatingManager.getInstance().onActivityPaused(activity);
                        }

                        @Override public void onActivityDestroyed(Activity activity) {
                            FloatingManager.getInstance().onActivityDestroyed(activity);
                        }
                    });
            lifecycleInstalled = true;
        }
    }

    private static boolean usable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
