package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.ModuleConfig;
import com.wolfox.gps.manager.FloatingManager;
import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.manager.LicenseManager;
import com.wolfox.gps.model.HistoryEntry;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.util.Locale;

/** Compact menu: header, expandable rectangular map, status and essential controls. */
public final class WolFoxPanel {
    private static final int BG = 0xFF071426;
    private static final int CARD = 0xFF0D203B;
    private static final int CARD_2 = 0xFF123052;
    private static final int BLUE = 0xFF2787F5;
    private static final int WHITE = 0xFFF4F8FF;
    private static final int MUTED = 0xFF97ABC5;
    private static final int GREEN = 0xFF36D783;
    private static final int RED = 0xFFFF5563;
    private static Dialog currentDialog;

    private WolFoxPanel() {}

    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (!LicenseManager.getInstance().isActive(activity)) {
            ActivationDialog.showRequired(activity);
            return;
        }
        activity.runOnUiThread(() -> {
            dismiss();
            currentDialog = buildDialog(activity);
            currentDialog.show();
            Window window = currentDialog.getWindow();
            if (window != null) {
                window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
            }
        });
    }

    public static void dismiss() {
        try {
            if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();
        } catch (Throwable ignored) {}
        currentDialog = null;
    }

    private static Dialog buildDialog(final Activity activity) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 13));
        root.setBackground(round(BG, 22, 0xFF214D7D, 1));

        root.addView(header(activity, dialog));

        final MapDialog.Preview preview = MapDialog.createPreview(activity, v -> {
            dialog.dismiss();
            MapDialog.show(activity);
        });
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 155));
        mapLp.setMargins(0, dp(activity, 10), 0, dp(activity, 8));
        root.addView(preview.getView(), mapLp);

        View status = statusCard(activity);
        status.setOnClickListener(v -> copyCoordinates(activity));
        root.addView(status);

        final boolean running = GPSMockManager.getInstance().isMocking();
        Button toggle = button(activity,
                running ? WFTheme.icon("■") + "  إيقاف تزييف الموقع"
                        : WFTheme.icon("▶") + "  تشغيل تزييف الموقع",
                running ? RED : GREEN, running ? Color.WHITE : BG);
        LinearLayout.LayoutParams toggleLp = full(dp(activity, 48));
        toggleLp.setMargins(0, dp(activity, 8), 0, dp(activity, 8));
        root.addView(toggle, toggleLp);

        toggle.setOnClickListener(v -> {
            if (running) {
                GPSMockManager.getInstance().stopMocking();
                WFStorage.getInstance(activity).addHistory(new HistoryEntry(
                        HistoryEntry.Action.STOP_MOCK, "إيقاف من اللوحة"));
                Toast.makeText(activity, "تم إيقاف التزييف", Toast.LENGTH_SHORT).show();
            } else if (!GPSMockManager.getInstance().hasMockLocation()) {
                Toast.makeText(activity, "حدد موقعًا أولًا من الخريطة أو البحث",
                        Toast.LENGTH_SHORT).show();
                return;
            } else {
                GPSMockManager.getInstance().startMocking();
                if (!GPSMockManager.getInstance().verifyRuntimeInjection()) {
                    GPSMockManager.getInstance().stopMocking();
                    Toast.makeText(activity,
                            "هوك الموقع لم يستجب؛ أعد فتح التطبيق ثم حاول مجددًا",
                            Toast.LENGTH_LONG).show();
                } else {
                    WFStorage.getInstance(activity).addHistory(new HistoryEntry(
                            HistoryEntry.Action.START_MOCK, "تشغيل مؤكد من اللوحة"));
                    Toast.makeText(activity, "تزييف الموقع نشط ومؤكد",
                            Toast.LENGTH_SHORT).show();
                }
            }
            dialog.dismiss();
            show(activity);
        });

        root.addView(row(activity,
                action(activity, WFTheme.icon("⌕") + "  بحث", v -> {
                    dialog.dismiss();
                    SearchDialog.show(activity);
                }),
                action(activity, WFTheme.icon("⌖") + "  موقعي", v -> useDeviceLocation(activity, dialog))));

        root.addView(row(activity,
                action(activity, WFTheme.icon("★") + "  المفضلة", v -> {
                    dialog.dismiss();
                    FavoritesDialog.show(activity);
                }),
                action(activity, WFTheme.icon("◷") + "  السجل", v -> {
                    dialog.dismiss();
                    HistoryDialog.show(activity);
                })));

        root.addView(row(activity,
                action(activity, WFTheme.icon("⚙") + "  الإعدادات", v -> {
                    dialog.dismiss();
                    SettingsDialog.show(activity);
                }),
                action(activity, WFTheme.icon("○") + "  إخفاء الأيقونة", v ->
                        FloatingManager.getInstance().requestHideConfirmation(activity))));

        TextView footer = text(activity,
                "اضغط على بطاقة الإحداثيات لنسخها  •  v" + ModuleConfig.VERSION_NAME,
                10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = fullWrap();
        footerLp.setMargins(0, dp(activity, 5), 0, 0);
        root.addView(footer, footerLp);

        WFTheme.applyTree(root);
        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> {
            preview.destroy();
            if (currentDialog == dialog) currentDialog = null;
        });
        return dialog;
    }

    private static View header(Context context, Dialog dialog) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text(context, "WF", 13, WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(BLUE, 22, 0xFF79B8FF, 1));
        row.addView(badge, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(context, "WolFox GPS", 18, WHITE, true));
        titles.addView(text(context, "لوحة الموقع الذكية", 11, MUTED, false));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMargins(dp(context, 10), 0, 0, 0);
        row.addView(titles, titleLp);

        Button close = button(context, WFTheme.icon("✕"), CARD, WHITE);
        close.setTextSize(18);
        close.setContentDescription("إغلاق");
        close.setOnClickListener(v -> dialog.dismiss());
        row.addView(close, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));
        return row;
    }

    private static View statusCard(Context context) {
        boolean running = GPSMockManager.getInstance().isMocking();
        WFLocation location = GPSMockManager.getInstance().getLocation();
        WFStorage storage = WFStorage.getInstance(context);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        card.setBackground(round(CARD, 14, running ? GREEN : RED, 1));
        card.setClickable(true);

        card.addView(text(context, running ? "●  التزييف يعمل" : "●  التزييف متوقف",
                14, running ? GREEN : RED, true));
        card.addView(text(context,
                location == null ? "لم يتم اختيار موقع"
                        : String.format(Locale.US, "%.6f, %.6f",
                        location.getLatitude(), location.getLongitude()),
                13, WHITE, false));
        String expiry = storage.getLicenseExpiryText();
        card.addView(text(context,
                expiry.isEmpty() ? "الترخيص نشط • اضغط لنسخ الإحداثيات"
                        : "الترخيص حتى: " + expiry + " • اضغط للنسخ",
                10, MUTED, false));
        return card;
    }

    private static void useDeviceLocation(Activity activity, Dialog dialog) {
        GPSMockManager.getInstance().stopMocking();
        WFLocation found = GPSMockManager.getInstance().useCurrentDeviceLocation(activity);
        if (found == null) {
            Toast.makeText(activity, "تعذر قراءة الموقع الحالي؛ تحقق من إذن الموقع",
                    Toast.LENGTH_LONG).show();
            return;
        }
        WFStorage.getInstance(activity).addHistory(new HistoryEntry(
                HistoryEntry.Action.CHANGE_LOCATION, found.getLatitude(), found.getLongitude(),
                "الموقع الحقيقي للجهاز"));
        Toast.makeText(activity, "تم تحديد موقع الجهاز", Toast.LENGTH_SHORT).show();
        dialog.dismiss();
        show(activity);
    }

    private static LinearLayout row(Context context, Button left, Button right) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, weighted(context, 44, 6));
        row.addView(right, weighted(context, 44, 0));
        LinearLayout.LayoutParams params = fullWrap();
        params.setMargins(0, 0, 0, dp(context, 6));
        row.setLayoutParams(params);
        return row;
    }

    private static Button action(Context context, String label, View.OnClickListener listener) {
        Button button = button(context, label, CARD_2, WHITE);
        button.setOnClickListener(listener);
        return button;
    }

    private static void copyCoordinates(Activity activity) {
        WFLocation location = GPSMockManager.getInstance().getLocation();
        if (location == null) {
            Toast.makeText(activity, "لا توجد إحداثيات", Toast.LENGTH_SHORT).show();
            return;
        }
        String value = String.format(Locale.US, "%.6f, %.6f",
                location.getLatitude(), location.getLongitude());
        ClipboardManager manager = (ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(
                ClipData.newPlainText("WolFox GPS", value));
        Toast.makeText(activity, "تم نسخ الإحداثيات", Toast.LENGTH_SHORT).show();
    }

    private static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        WFTheme.apply(text, bold);
        return text;
    }

    private static Button button(Context context, String value, int background, int color) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextColor(color);
        button.setTextSize(12);
        button.setBackground(round(background, 12, 0xFF28547E, 1));
        WFTheme.apply(button, true);
        return button;
    }

    private static GradientDrawable round(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radiusDp * 2f);
        if (strokeDp > 0) drawable.setStroke(strokeDp, stroke);
        return drawable;
    }

    private static LinearLayout.LayoutParams full(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private static LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weighted(Context context, int heightDp, int endMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(context, heightDp), 1f);
        params.setMargins(0, 0, dp(context, endMarginDp), 0);
        return params;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
