package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.ModuleConfig;
import com.wolfox.gps.manager.FloatingManager;
import com.wolfox.gps.manager.LicenseManager;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.lang.ref.WeakReference;

/** Mandatory activation dialog. It is the only WolFox UI visible before Active. */
public final class ActivationDialog {
    private static final int BG = 0xFF071426;
    private static final int CARD = 0xFF0D203B;
    private static final int BLUE = 0xFF2787F5;
    private static final int TEXT = 0xFFF4F8FF;
    private static final int MUTED = 0xFF95A9C4;
    private static final int RED = 0xFFFF5A67;
    private static WeakReference<Activity> owner = new WeakReference<>(null);
    private static Dialog current;

    private ActivationDialog() {}

    public static void showRequired(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (LicenseManager.getInstance().isActive(activity)) {
                    dismiss();
                    return;
                }
                Activity old = owner.get();
                if (current != null && current.isShowing() && old == activity) return;
                dismiss();
                owner = new WeakReference<>(activity);
                current = build(activity);
                current.show();
                Window w = current.getWindow();
                if (w != null) {
                    w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    w.setGravity(Gravity.CENTER);
                }
            }
        });
    }

    public static void dismiss() {
        try {
            if (current != null && current.isShowing()) current.dismiss();
        } catch (Exception ignored) {}
        current = null;
        owner.clear();
    }

    private static Dialog build(Activity activity) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) w.setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(activity, 22), dp(activity, 22), dp(activity, 22), dp(activity, 20));
        root.setBackground(round(BG, 22, 0xFF214D7D, 1));

        TextView mark = text(activity, "WF", 22, BLUE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(CARD, 18, BLUE, 2));
        root.addView(mark, new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 64)));

        TextView title = text(activity, "تفعيل " + ModuleConfig.MODULE_NAME, 20, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = fullWrap();
        titleLp.setMargins(0, dp(activity, 14), 0, dp(activity, 5));
        root.addView(title, titleLp);

        TextView subtitle = text(activity,
                "أدخل كود الترخيص للمتابعة. لن تظهر أي ميزة قبل نجاح التفعيل.",
                13, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, fullWrap());

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("WFXXXXXXXXXXXXX");
        input.setHintTextColor(0xFF607895);
        input.setTextColor(TEXT);
        input.setTextSize(16);
        input.setGravity(Gravity.CENTER);
        input.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        input.setBackground(round(CARD, 12, 0xFF28547E, 1));
        String saved = WFStorage.getInstance(activity).getLicenseCode();
        if (!saved.isEmpty()) input.setText(saved);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52));
        inputLp.setMargins(0, dp(activity, 18), 0, dp(activity, 10));
        root.addView(input, inputLp);

        LinearLayout tools = new LinearLayout(activity);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        Button paste = button(activity, "لصق", CARD, TEXT);
        Button clear = button(activity, "مسح", CARD, TEXT);
        tools.addView(paste, weighted(activity, 42, 6));
        tools.addView(clear, weighted(activity, 42, 0));
        root.addView(tools, fullWrap());

        TextView status = text(activity, "", 13, MUTED, false);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = fullWrap();
        statusLp.setMargins(0, dp(activity, 10), 0, dp(activity, 6));
        root.addView(status, statusLp);

        ProgressBar progress = new ProgressBar(activity);
        progress.setVisibility(ProgressBar.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));

        Button activate = button(activity, "تفعيل الآن", BLUE, Color.WHITE);
        LinearLayout.LayoutParams activateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50));
        activateLp.setMargins(0, dp(activity, 8), 0, 0);
        root.addView(activate, activateLp);

        TextView endpoint = text(activity, "اتصال مشفّر • WolFox GPS • v" + ModuleConfig.VERSION_NAME,
                11, MUTED, false);
        endpoint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams endpointLp = fullWrap();
        endpointLp.setMargins(0, dp(activity, 12), 0, 0);
        root.addView(endpoint, endpointLp);

        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence value = cm.getPrimaryClip().getItemAt(0).coerceToText(activity);
                input.setText(value == null ? "" : value.toString().trim());
                input.setSelection(input.length());
            }
        });
        clear.setOnClickListener(v -> input.setText(""));

        activate.setOnClickListener(v -> {
            String code = input.getText().toString().trim();
            activate.setEnabled(false);
            paste.setEnabled(false);
            clear.setEnabled(false);
            progress.setVisibility(ProgressBar.VISIBLE);
            status.setTextColor(MUTED);
            status.setText("جاري التحقق من الكود...");
            LicenseManager.getInstance().activate(activity, code, (active, state, message) -> {
                if (activity.isFinishing()) return;
                progress.setVisibility(ProgressBar.GONE);
                activate.setEnabled(true);
                paste.setEnabled(true);
                clear.setEnabled(true);
                status.setText(message);
                status.setTextColor(active ? 0xFF55D88B : RED);
                if (active) {
                    Toast.makeText(activity, "تم تفعيل WolFox", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    current = null;
                    WFStorage.getInstance(activity).setBubbleVisible(true);
                    FloatingManager.getInstance().show(activity);
                }
            });
        });

        WFTheme.applyTree(root);
        dialog.setContentView(root);
        return dialog;
    }

    private static TextView text(Context c, String value, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        WFTheme.apply(t, bold);
        return t;
    }

    private static Button button(Context c, String value, int bg, int color) {
        Button b = new Button(c);
        b.setText(value);
        b.setTextColor(color);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(round(bg, 12, 0xFF28547E, 1));
        WFTheme.apply(b, true);
        return b;
    }

    private static GradientDrawable round(int color, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp * 2f);
        if (strokeDp > 0) d.setStroke(strokeDp, stroke);
        return d;
    }

    private static LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weighted(Context c, int heightDp, int endMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(c, heightDp), 1f);
        p.setMargins(0, 0, dp(c, endMarginDp), 0);
        return p;
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
