package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.manager.FloatingManager;
import com.wolfox.gps.manager.LicenseManager;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

public class SettingsDialog {

    private static final int COLOR_BG    = 0xFF070B18;
    private static final int COLOR_GOLD  = 0xFF2787F5;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_CARD  = 0xFF0E1428;

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> buildAndShow(activity));
    }

    private static void buildAndShow(Activity activity) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(
                    (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setPadding(dp(activity,16), dp(activity,16), dp(activity,16), dp(activity,16));

        // عنوان
        TextView title = new TextView(activity);
        title.setText("⚙ الإعدادات");
        title.setTextColor(COLOR_GOLD);
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);
        addDivider(root, activity);

        // ─ دقة الموقع ─
        root.addView(buildSectionLabel(activity, "دقة الموقع المزيف"));
        SeekBar accuracyBar = new SeekBar(activity);
        accuracyBar.setMax(100);
        WFLocation current = GPSMockManager.getInstance().getLocation();
        accuracyBar.setProgress(current == null ? 3 : Math.max(1, (int) current.getAccuracy()));
        root.addView(accuracyBar);

        TextView accuracyVal = new TextView(activity);
        accuracyVal.setText("الدقة: " + accuracyBar.getProgress() + " متر");
        accuracyVal.setTextColor(COLOR_WHITE);
        accuracyVal.setTextSize(12);
        root.addView(accuracyVal);

        accuracyBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                int val = Math.max(1, prog);
                accuracyVal.setText("الدقة: " + val + " متر");
                WFLocation loc = GPSMockManager.getInstance().getLocation();
                if (loc != null) loc.setAccuracy(val);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        addSpacing(root, activity, 12);

        // ─ ارتفاع مخصص ─
        root.addView(buildSectionLabel(activity, "الارتفاع (Altitude)"));
        EditText altEt = new EditText(activity);
        altEt.setHint("0.0");
        altEt.setHintTextColor(0xFF666666);
        altEt.setTextColor(COLOR_WHITE);
        altEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        altEt.setBackgroundColor(COLOR_CARD);
        altEt.setPadding(dp(activity,10), dp(activity,8), dp(activity,10), dp(activity,8));
        root.addView(altEt);
        if (current != null) altEt.setText(String.valueOf(current.getAltitude()));

        addSpacing(root, activity, 12);

        // ─ سرعة مخصصة ─
        root.addView(buildSectionLabel(activity, "السرعة (م/ث)"));
        SeekBar speedBar = new SeekBar(activity);
        speedBar.setMax(200);
        speedBar.setProgress(current == null ? 0 : Math.max(0, (int) current.getSpeed()));
        root.addView(speedBar);

        TextView speedVal = new TextView(activity);
        speedVal.setText("السرعة: " + speedBar.getProgress() + " م/ث");
        speedVal.setTextColor(COLOR_WHITE);
        speedVal.setTextSize(12);
        root.addView(speedVal);

        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                speedVal.setText("السرعة: " + prog + " م/ث");
                WFLocation loc = GPSMockManager.getInstance().getLocation();
                if (loc != null) loc.setSpeed(prog);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        addSpacing(root, activity, 12);

        root.addView(buildSectionLabel(activity, "حجم وشفافية الأيقونة"));
        WFStorage storage = WFStorage.getInstance(activity);
        SeekBar sizeBar = new SeekBar(activity);
        sizeBar.setMax(46);
        sizeBar.setProgress(storage.getBubbleSizeDp() - 42);
        root.addView(sizeBar);
        TextView sizeVal = new TextView(activity);
        sizeVal.setText("الحجم: " + storage.getBubbleSizeDp() + "dp");
        sizeVal.setTextColor(COLOR_WHITE);
        root.addView(sizeVal);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                int size = 42 + value;
                storage.setBubbleSizeDp(size);
                sizeVal.setText("الحجم: " + size + "dp");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        SeekBar opacityBar = new SeekBar(activity);
        opacityBar.setMax(65);
        opacityBar.setProgress(Math.max(0, (int) (storage.getBubbleOpacity() * 100f) - 35));
        root.addView(opacityBar);
        TextView opacityVal = new TextView(activity);
        opacityVal.setText("الشفافية: " + (35 + opacityBar.getProgress()) + "%");
        opacityVal.setTextColor(COLOR_WHITE);
        root.addView(opacityVal);
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                int percent = 35 + value;
                storage.setBubbleOpacity(percent / 100f);
                opacityVal.setText("الشفافية: " + percent + "%");
                FloatingManager.getInstance().refreshAppearance();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        addSpacing(root, activity, 12);

        // ─ تصحيح / Debug ─
        root.addView(buildSectionLabel(activity, "وضع التصحيح"));
        Switch debugSwitch = new Switch(activity);
        debugSwitch.setText("تفعيل الـ Log");
        debugSwitch.setTextColor(COLOR_WHITE);
        debugSwitch.setChecked(true);
        debugSwitch.setOnCheckedChangeListener((v, checked) -> WFLog.setDebug(checked));
        root.addView(debugSwitch);

        addSpacing(root, activity, 12);

        // ─ معلومات الترخيص ─
        root.addView(buildSectionLabel(activity, "الترخيص"));
        TextView licTxt = new TextView(activity);
        String status = storage.getLicenseStatus();
        String expiry = storage.getLicenseExpiryText();
        licTxt.setText("الحالة: " + (status.isEmpty() ? "غير مفعل" : status)
                + (expiry.isEmpty() ? "" : "\nالانتهاء: " + expiry));
        licTxt.setTextColor("Active".equalsIgnoreCase(status) ? 0xFF4CAF50 : 0xFFFF5252);
        licTxt.setTextSize(13);
        licTxt.setBackgroundColor(COLOR_CARD);
        licTxt.setPadding(dp(activity,10), dp(activity,8), dp(activity,10), dp(activity,8));
        root.addView(licTxt);

        Button verifyBtn = new Button(activity);
        verifyBtn.setText("التحقق من الترخيص الآن");
        verifyBtn.setTextColor(COLOR_WHITE);
        verifyBtn.setBackgroundColor(COLOR_CARD);
        verifyBtn.setOnClickListener(v -> {
            String code = storage.getLicenseCode();
            if (code.isEmpty()) {
                Toast.makeText(activity, "لا يوجد كود محفوظ", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyBtn.setEnabled(false);
            LicenseManager.getInstance().verify(activity, code, (active, state, message) -> {
                verifyBtn.setEnabled(true);
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                if (!active) {
                    dialog.dismiss();
                    FloatingManager.getInstance().hide(false);
                    ActivationDialog.showRequired(activity);
                }
            });
        });
        root.addView(verifyBtn);

        addSpacing(root, activity, 16);

        // ─ أزرار ─
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button saveBtn = new Button(activity);
        saveBtn.setText("حفظ");
        saveBtn.setTextColor(COLOR_BG);
        saveBtn.setBackgroundColor(COLOR_GOLD);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(activity,44), 1f);
        saveLp.setMargins(0,0,dp(activity,8),0);
        saveBtn.setLayoutParams(saveLp);
        saveBtn.setOnClickListener(v -> {
            try {
                double alt = Double.parseDouble(altEt.getText().toString().trim());
                WFLocation loc = GPSMockManager.getInstance().getLocation();
                if (loc != null) {
                    loc.setAltitude(alt);
                    GPSMockManager.getInstance().setLocation(loc);
                }
            } catch (Exception ignored) {}
            FloatingManager.getInstance().refreshAppearance();
            Toast.makeText(activity, "✅ تم الحفظ", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        btnRow.addView(saveBtn);

        Button closeBtn = new Button(activity);
        closeBtn.setText("إغلاق");
        closeBtn.setTextColor(COLOR_WHITE);
        closeBtn.setBackgroundColor(COLOR_CARD);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(activity,44), 1f));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(closeBtn);

        root.addView(btnRow);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);
        WFTheme.applyTree(root);
        dialog.setContentView(scroll);
        dialog.show();
        if (w != null) {
            w.setLayout((int)(activity.getResources().getDisplayMetrics().widthPixels * 0.92),
                    (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.86));
        }
    }

    private static TextView buildSectionLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(COLOR_GOLD);
        tv.setTextSize(13);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(ctx, 4));
        tv.setLayoutParams(lp);
        return tv;
    }

    private static void addDivider(LinearLayout parent, Context ctx) {
        View d = new View(ctx);
        d.setBackgroundColor(COLOR_GOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1));
        lp.setMargins(0, dp(ctx,8), 0, dp(ctx,12));
        d.setLayoutParams(lp);
        parent.addView(d);
    }

    private static void addSpacing(LinearLayout parent, Context ctx, int dpVal) {
        View s = new View(ctx);
        s.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, dpVal)));
        parent.addView(s);
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
