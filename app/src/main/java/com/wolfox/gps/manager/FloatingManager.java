package com.wolfox.gps.manager;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.ui.ActivationDialog;
import com.wolfox.gps.ui.WolFoxPanel;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.io.InputStream;

/** Draggable circular control attached only to the host Activity DecorView. */
public final class FloatingManager {
    private static final String TAG = "FloatingManager";
    private static final long CLICK_THRESHOLD_MS = 240L;
    private static final long LONG_PRESS_HIDE_MS = 720L;
    private static final int NAVY = 0xFF071426;
    private static final int GREEN = 0xFF25D366;
    private static final int RED = 0xFFFF3B4D;
    private static final String CONTROL_TAG = "WFX_CONTROL_311";
    private static final FloatingManager INSTANCE = new FloatingManager();

    private final Handler main = new Handler(Looper.getMainLooper());
    private Activity currentActivity;
    private View floatingButton;
    private boolean visible;
    private boolean attached;
    private float initialX;
    private float initialY;
    private float initialTouchX;
    private float initialTouchY;
    private long downAt;
    private int attachToken;
    private boolean longPressTriggered;
    private Runnable pendingLongPress;

    private FloatingManager() {}

    public static FloatingManager getInstance() { return INSTANCE; }

    public void onActivityResumed(Activity activity) {
        currentActivity = activity;
        if (visible) scheduleAttach(activity);
    }

    public void onActivityPaused(Activity activity) {}

    public void onActivityDestroyed(Activity activity) {
        if (activity == null || currentActivity != activity) return;
        attachToken++;
        detachFromDecorView();
        currentActivity = null;
    }

    public void show(Activity activity) {
        if (activity == null || activity.isFinishing()
                || ToolControlManager.getInstance().isBlocked(activity)) return;
        currentActivity = activity;
        visible = true;
        WFStorage.getInstance(activity).setBubbleVisible(true);
        scheduleAttach(activity);
    }

    public void hide() { hide(true); }

    public void hide(boolean persist) {
        visible = false;
        attachToken++;
        cancelButtonLongPress();
        Activity activity = currentActivity;
        if (persist && activity != null) {
            WFStorage.getInstance(activity).setBubbleVisible(false);
        }
        main.post(new Runnable() {
            @Override public void run() { detachFromDecorView(); }
        });
    }

    public void requestHideConfirmation(Activity owner) {
        final Activity activity = owner == null ? currentActivity : owner;
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (activity.isFinishing()) return;
                new AlertDialog.Builder(activity)
                        .setTitle("إخفاء أيقونة WolFox")
                        .setMessage("يمكنك إظهارها مجددًا بالضغط المطوّل في منتصف الشاشة.")
                        .setPositiveButton("إخفاء", (dialog, which) -> {
                            WolFoxPanel.dismiss();
                            hide(true);
                            Toast.makeText(activity,
                                    "للإظهار: اضغط مطوّلًا في منتصف الشاشة",
                                    Toast.LENGTH_LONG).show();
                        })
                        .setNegativeButton("إلغاء", null)
                        .show();
            }
        });
    }

    public boolean isVisible() {
        return visible && attached && floatingButton != null
                && floatingButton.getParent() != null
                && floatingButton.getVisibility() == View.VISIBLE;
    }

    public Activity getCurrentActivity() { return currentActivity; }

    /** Refreshes the full outer ring immediately when spoofing changes. */
    public void refreshAppearance() {
        main.post(new Runnable() {
            @Override public void run() {
                if (floatingButton != null && currentActivity != null) {
                    applyStateRing(floatingButton, currentActivity);
                    floatingButton.setAlpha(
                            WFStorage.getInstance(currentActivity).getBubbleOpacity());
                    floatingButton.invalidate();
                }
            }
        });
    }

    private View buildFloatingButton(Context context) {
        WFStorage storage = WFStorage.getInstance(context);
        int size = dp(context, storage.getBubbleSizeDp());
        int ringWidth = Math.max(dp(context, 3), Math.round(size * 0.065f));

        FrameLayout root = new FrameLayout(context);
        root.setPadding(ringWidth, ringWidth, ringWidth, ringWidth);
        root.setElevation(dp(context, 12));
        root.setAlpha(storage.getBubbleOpacity());
        root.setTag(CONTROL_TAG);
        root.setContentDescription("WolFox GPS v3.1.0");
        applyStateRing(root, context);

        Bitmap icon = loadModuleIcon(context);
        if (icon != null) {
            ImageView image = new ImageView(context);
            image.setImageBitmap(icon);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(circle(NAVY, NAVY, 0));
            if (Build.VERSION.SDK_INT >= 21) image.setClipToOutline(true);
            root.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            TextView fallback = new TextView(context);
            fallback.setText("WF");
            fallback.setTextColor(0xFFFFFFFF);
            fallback.setTextSize(Math.max(12, storage.getBubbleSizeDp() / 3f));
            fallback.setGravity(android.view.Gravity.CENTER);
            fallback.setBackground(circle(NAVY, NAVY, 0));
            WFTheme.apply(fallback, true);
            root.addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        root.setOnTouchListener(buildTouchListener());
        return root;
    }

    private static Bitmap loadModuleIcon(Context context) {
        InputStream stream = null;
        try {
            ClassLoader loader = FloatingManager.class.getClassLoader();
            if (loader != null) stream = loader.getResourceAsStream("assets/wolfox_icon.png");
            if (stream == null) stream = context.getAssets().open("wolfox_icon.png");
            return BitmapFactory.decodeStream(stream);
        } catch (Throwable error) {
            WFLog.d(TAG, "icon fallback: " + error.getMessage());
            return null;
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static void applyStateRing(View view, Context context) {
        int edge = GPSMockManager.getInstance().isMocking() ? GREEN : RED;
        int width = Math.max(dp(context, 3),
                Math.round(WFStorage.getInstance(context).getBubbleSizeDp()
                        * context.getResources().getDisplayMetrics().density * 0.065f));
        view.setBackground(circle(NAVY, edge, width));
        if (Build.VERSION.SDK_INT >= 21) view.setClipToOutline(true);
    }

    private void scheduleAttach(final Activity activity) {
        final int token = ++attachToken;
        main.post(new Runnable() {
            @Override public void run() { attachWithRetry(activity, token, 0); }
        });
    }

    private void attachWithRetry(final Activity activity, final int token, final int attempt) {
        if (token != attachToken || currentActivity != activity || !visible
                || activity == null || activity.isFinishing()) return;
        boolean added = attachToActivity(activity);
        if ((!added || floatingButton == null || floatingButton.getParent() == null)
                && attempt < 4) {
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    attachWithRetry(activity, token, attempt + 1);
                }
            }, 250L * (attempt + 1));
        }
    }

    private boolean attachToActivity(Activity activity) {
        if (activity == null || activity.isFinishing() || !visible) return false;
        View content = activity.findViewById(android.R.id.content);
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        final ViewGroup parent = content instanceof ViewGroup
                ? (ViewGroup) content
                : (decor instanceof ViewGroup ? (ViewGroup) decor : null);
        if (parent == null) return false;

        final WFStorage storage = WFStorage.getInstance(activity);
        int size = dp(activity, storage.getBubbleSizeDp());
        View existing = floatingButton;
        if (existing != null && existing.getParent() == parent
                && existing.getLayoutParams() != null
                && existing.getLayoutParams().width == size
                && existing.getLayoutParams().height == size) {
            attached = true;
            existing.setVisibility(View.VISIBLE);
            existing.bringToFront();
            refreshAppearance();
            return true;
        }

        detachFromDecorView();
        floatingButton = buildFloatingButton(activity);
        try {
            parent.addView(floatingButton, new FrameLayout.LayoutParams(size, size));
        } catch (Throwable error) {
            WFLog.e(TAG, "attach failed: " + error.getMessage());
            floatingButton = null;
            attached = false;
            return false;
        }
        attached = true;
        floatingButton.setVisibility(View.VISIBLE);
        floatingButton.bringToFront();
        parent.invalidate();
        floatingButton.post(new Runnable() {
            @Override public void run() {
                if (floatingButton == null || floatingButton.getParent() == null) return;
                int maxX = Math.max(0, parent.getWidth() - floatingButton.getWidth());
                int maxY = Math.max(0, parent.getHeight() - floatingButton.getHeight());
                floatingButton.setX(maxX * storage.getBubbleXFraction());
                floatingButton.setY(maxY * storage.getBubbleYFraction());
                floatingButton.setScaleX(0.72f);
                floatingButton.setScaleY(0.72f);
                floatingButton.animate().scaleX(1f).scaleY(1f)
                        .alpha(storage.getBubbleOpacity()).setDuration(220L).start();
                floatingButton.bringToFront();
            }
        });
        WFLog.i(TAG, "circular control attached");
        return true;
    }

    private void detachFromDecorView() {
        cancelButtonLongPress();
        try {
            if (floatingButton != null && floatingButton.getParent() instanceof ViewGroup) {
                ((ViewGroup) floatingButton.getParent()).removeView(floatingButton);
            }
        } catch (Exception ignored) {}
        floatingButton = null;
        attached = false;
    }

    private View.OnTouchListener buildTouchListener() {
        return new View.OnTouchListener() {
            @Override public boolean onTouch(final View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        cancelButtonLongPress();
                        longPressTriggered = false;
                        initialX = view.getX();
                        initialY = view.getY();
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        downAt = System.currentTimeMillis();
                        view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80L).start();
                        pendingLongPress = new Runnable() {
                            @Override public void run() {
                                if (floatingButton != view || view.getParent() == null
                                        || currentActivity == null || currentActivity.isFinishing()) return;
                                longPressTriggered = true;
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                requestHideConfirmation(currentActivity);
                            }
                        };
                        main.postDelayed(pendingLongPress, LONG_PRESS_HIDE_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!(view.getParent() instanceof ViewGroup)) return true;
                        float moved = Math.abs(event.getRawX() - initialTouchX)
                                + Math.abs(event.getRawY() - initialTouchY);
                        if (moved > dp(view.getContext(), 14)) cancelButtonLongPress();
                        ViewGroup parent = (ViewGroup) view.getParent();
                        float x = initialX + event.getRawX() - initialTouchX;
                        float y = initialY + event.getRawY() - initialTouchY;
                        view.setX(Math.max(0f, Math.min(x, parent.getWidth() - view.getWidth())));
                        view.setY(Math.max(0f, Math.min(y, parent.getHeight() - view.getHeight())));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        cancelButtonLongPress();
                        view.animate().scaleX(1f).scaleY(1f).setDuration(90L).start();
                        if (longPressTriggered) {
                            longPressTriggered = false;
                            return true;
                        }
                        float distance = Math.abs(event.getRawX() - initialTouchX)
                                + Math.abs(event.getRawY() - initialTouchY);
                        if (event.getActionMasked() == MotionEvent.ACTION_UP
                                && System.currentTimeMillis() - downAt <= CLICK_THRESHOLD_MS
                                && distance < dp(view.getContext(), 14)) {
                            openPanel();
                        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            snapAndSave(view);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    private void cancelButtonLongPress() {
        if (pendingLongPress != null) main.removeCallbacks(pendingLongPress);
        pendingLongPress = null;
    }

    private void snapAndSave(View view) {
        if (!(view.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) view.getParent();
        float maxX = Math.max(0f, parent.getWidth() - view.getWidth());
        float maxY = Math.max(0f, parent.getHeight() - view.getHeight());
        float targetX = view.getX() + view.getWidth() / 2f < parent.getWidth() / 2f
                ? 0f : maxX;
        ObjectAnimator.ofFloat(view, "x", targetX).setDuration(180L).start();
        if (currentActivity != null) {
            WFStorage.getInstance(currentActivity).saveBubblePosition(
                    maxX == 0 ? 1f : targetX / maxX,
                    maxY == 0 ? 0.5f : view.getY() / maxY);
        }
    }

    private void openPanel() {
        if (currentActivity == null || currentActivity.isFinishing()) return;
        if (LicenseManager.getInstance().isActive(currentActivity)) {
            WolFoxPanel.show(currentActivity);
        } else {
            ActivationDialog.showRequired(currentActivity);
        }
    }

    private static GradientDrawable circle(int fill, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
