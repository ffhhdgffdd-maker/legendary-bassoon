package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;

import com.wolfox.gps.model.HistoryEntry;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryDialog {

    private static final int COLOR_BG    = 0xFF070B18;
    private static final int COLOR_GOLD  = 0xFF2787F5;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_CARD  = 0xFF0E1428;
    private static final int COLOR_RED   = 0xFFD32F2F;

    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault());

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
                    (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.94),
                    (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.80));
            w.setGravity(Gravity.CENTER);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setPadding(dp(activity,14), dp(activity,14), dp(activity,14), dp(activity,14));

        // ─ رأس ─
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText("🕐 سجل العمليات");
        title.setTextColor(COLOR_GOLD);
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button clearBtn = new Button(activity);
        clearBtn.setText("مسح الكل");
        clearBtn.setTextColor(COLOR_WHITE);
        clearBtn.setBackgroundColor(COLOR_RED);
        clearBtn.setTextSize(11);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(activity, 34));
        clearBtn.setLayoutParams(clp);
        clearBtn.setPadding(dp(activity,8), 0, dp(activity,8), 0);
        header.addView(clearBtn);

        root.addView(header);
        addDivider(root, activity);

        // ─ قائمة ─
        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(scroll);

        // ─ إغلاق ─
        Button closeBtn = new Button(activity);
        closeBtn.setText("إغلاق");
        closeBtn.setTextColor(COLOR_WHITE);
        closeBtn.setBackgroundColor(COLOR_CARD);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity,44));
        closeLp.setMargins(0, dp(activity,8), 0, 0);
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        root.addView(closeBtn);

        WFTheme.applyTree(root);
        dialog.setContentView(root);

        // ─ تحميل ─
        Runnable reload = () -> {
            list.removeAllViews();
            List<HistoryEntry> entries = WFStorage.getInstance(activity).getHistory();
            if (entries.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText("لا يوجد سجل بعد");
                empty.setTextColor(0xFF888888);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(activity,24), 0, 0);
                list.addView(empty);
            } else {
                for (HistoryEntry e : entries) {
                    list.addView(buildHistoryItem(activity, e));
                }
            }
            WFTheme.applyTree(list);
        };

        reload.run();

        clearBtn.setOnClickListener(v ->
                new AlertDialog.Builder(activity)
                        .setTitle("مسح السجل")
                        .setMessage("هل تريد مسح كل السجل؟")
                        .setPositiveButton("مسح", (d2, w2) -> {
                            WFStorage.getInstance(activity).clearHistory();
                            reload.run();
                        })
                        .setNegativeButton("إلغاء", null)
                        .show()
        );

        dialog.show();
        if (w != null) {
            w.setLayout((int)(activity.getResources().getDisplayMetrics().widthPixels * 0.94),
                    (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.80));
        }
    }

    private static View buildHistoryItem(Activity activity, HistoryEntry entry) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(COLOR_CARD);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(activity, 3));
        row.setLayoutParams(rowLp);
        row.setPadding(dp(activity,10), dp(activity,8), dp(activity,10), dp(activity,8));

        LinearLayout info = new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView action = new TextView(activity);
        action.setText(entry.getActionLabel());
        action.setTextColor(COLOR_GOLD);
        action.setTextSize(13);
        info.addView(action);

        if (entry.getDetail() != null && !entry.getDetail().isEmpty()) {
            TextView detail = new TextView(activity);
            detail.setText(entry.getDetail());
            detail.setTextColor(COLOR_WHITE);
            detail.setTextSize(11);
            info.addView(detail);
        }

        if (entry.getLatitude() != 0 || entry.getLongitude() != 0) {
            TextView coord = new TextView(activity);
            coord.setText(String.format("📍 %.5f, %.5f",
                    entry.getLatitude(), entry.getLongitude()));
            coord.setTextColor(0xFF888888);
            coord.setTextSize(11);
            info.addView(coord);
        }

        row.addView(info);

        TextView time = new TextView(activity);
        time.setText(SDF.format(new Date(entry.getTimestamp())));
        time.setTextColor(0xFF666666);
        time.setTextSize(10);
        time.setGravity(Gravity.END);
        row.addView(time);

        return row;
    }

    private static void addDivider(LinearLayout parent, Context ctx) {
        View d = new View(ctx);
        d.setBackgroundColor(COLOR_GOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1));
        lp.setMargins(0, dp(ctx,8), 0, dp(ctx,8));
        d.setLayoutParams(lp);
        parent.addView(d);
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
