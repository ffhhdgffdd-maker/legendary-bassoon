package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.FavoriteLocation;
import com.wolfox.gps.model.HistoryEntry;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.util.List;

public class FavoritesDialog {

    private static final int COLOR_BG    = 0xFF070B18;
    private static final int COLOR_GOLD  = 0xFF2787F5;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_CARD  = 0xFF0E1428;
    private static final int COLOR_RED   = 0xFFD32F2F;

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
            w.setLayout((int)(activity.getResources().getDisplayMetrics().widthPixels * 0.94),
                    (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.75));
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
        title.setText("⭐ المفضلة");
        title.setTextColor(COLOR_GOLD);
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button addBtn = buildBtn(activity, "+ إضافة", COLOR_GOLD);
        addBtn.setTextColor(COLOR_BG);
        header.addView(addBtn);

        Button exportBtn = buildBtn(activity, "📤", COLOR_CARD);
        header.addView(exportBtn);

        Button importBtn = buildBtn(activity, "📥", COLOR_CARD);
        header.addView(importBtn);

        root.addView(header);
        addDivider(root, activity);

        EditText filterEt = buildEditText(activity, "بحث في المفضلة...");
        filterEt.setSingleLine(true);
        filterEt.setTextColor(COLOR_WHITE);
        filterEt.setBackgroundColor(COLOR_CARD);
        root.addView(filterEt);

        // ─ قائمة ─
        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollLp);
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

        // ─ تحميل البيانات ─
        Runnable reload = () -> {
            list.removeAllViews();
            List<FavoriteLocation> favs = WFStorage.getInstance(activity).getFavorites();
            String filter = filterEt.getText().toString().trim().toLowerCase();
            int shown = 0;
            if (favs.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText("لا توجد مفضلة بعد");
                empty.setTextColor(0xFF888888);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(activity,24), 0, dp(activity,24));
                list.addView(empty);
            } else {
                for (FavoriteLocation fav : favs) {
                    String searchable = (String.valueOf(fav.getName()) + " "
                            + String.valueOf(fav.getAddress())).toLowerCase();
                    if (!filter.isEmpty() && !searchable.contains(filter)) continue;
                    list.addView(buildFavItem(activity, fav, dialog, list));
                    shown++;
                }
                if (shown == 0) {
                    TextView empty = new TextView(activity);
                    empty.setText("لا توجد نتائج");
                    empty.setTextColor(0xFF888888);
                    empty.setGravity(Gravity.CENTER);
                    empty.setPadding(0, dp(activity,24), 0, dp(activity,24));
                    list.addView(empty);
                }
            }
            WFTheme.applyTree(list);
        };

        reload.run();

        filterEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { reload.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // ─ إضافة موقع جديد ─
        addBtn.setOnClickListener(v -> showAddDialog(activity, dialog, reload));

        // ─ تصدير ─
        exportBtn.setOnClickListener(v -> {
            String json = WFStorage.getInstance(activity).exportFavoritesJson();
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("favorites", json));
                Toast.makeText(activity, "✅ تم نسخ المفضلة", Toast.LENGTH_SHORT).show();
            }
        });

        // ─ استيراد ─
        importBtn.setOnClickListener(v -> showImportDialog(activity, dialog, reload));

        dialog.show();
        if (w != null) {
            w.setLayout((int)(activity.getResources().getDisplayMetrics().widthPixels * 0.94),
                    (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.78));
        }
    }

    private static View buildFavItem(Activity activity, FavoriteLocation fav,
                                      Dialog parent, LinearLayout list) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(COLOR_CARD);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 52));
        rowLp.setMargins(0, 0, 0, dp(activity, 4));
        row.setLayoutParams(rowLp);
        row.setPadding(dp(activity,10), 0, dp(activity,6), 0);

        // أيقونة + اسم
        LinearLayout info = new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(activity);
        name.setText("⭐ " + fav.getName());
        name.setTextColor(COLOR_WHITE);
        name.setTextSize(13);
        info.addView(name);

        TextView coord = new TextView(activity);
        coord.setText(String.format("%.5f, %.5f", fav.getLatitude(), fav.getLongitude()));
        coord.setTextColor(0xFF888888);
        coord.setTextSize(11);
        info.addView(coord);

        row.addView(info);

        // زر تطبيق
        Button applyBtn = buildSmallBtn(activity, "📍", COLOR_GOLD);
        applyBtn.setTextColor(COLOR_BG);
        applyBtn.setOnClickListener(v -> {
            GPSMockManager.getInstance().setLocation(fav.toWFLocation());
            WFStorage.getInstance(activity).addHistory(
                    new HistoryEntry(HistoryEntry.Action.CHANGE_LOCATION,
                            fav.getLatitude(), fav.getLongitude(), fav.getName()));
            parent.dismiss();
            Toast.makeText(activity, "📍 " + fav.getName(), Toast.LENGTH_SHORT).show();
        });
        row.addView(applyBtn);

        // زر تعديل
        Button editBtn = buildSmallBtn(activity, "✏", COLOR_CARD);
        editBtn.setOnClickListener(v -> showEditDialog(activity, fav, parent, () -> {
            list.removeAllViews();
            for (FavoriteLocation f : WFStorage.getInstance(activity).getFavorites())
                list.addView(buildFavItem(activity, f, parent, list));
        }));
        row.addView(editBtn);

        // زر حذف
        Button delBtn = buildSmallBtn(activity, "🗑", COLOR_RED);
        delBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(activity)
                    .setTitle("حذف")
                    .setMessage("حذف \"" + fav.getName() + "\"؟")
                    .setPositiveButton("حذف", (d, w2) -> {
                        WFStorage.getInstance(activity).deleteFavorite(fav.getId());
                        list.removeAllViews();
                        for (FavoriteLocation f : WFStorage.getInstance(activity).getFavorites())
                            list.addView(buildFavItem(activity, f, parent, list));
                    })
                    .setNegativeButton("إلغاء", null)
                    .show();
        });
        row.addView(delBtn);

        return row;
    }

    private static void showAddDialog(Activity activity, Dialog parent, Runnable reload) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity,16), dp(activity,8), dp(activity,16), dp(activity,8));

        EditText nameEt  = buildEditText(activity, "الاسم");
        EditText latEt   = buildEditText(activity, "خط العرض (Latitude)");
        EditText lngEt   = buildEditText(activity, "خط الطول (Longitude)");
        EditText addrEt  = buildEditText(activity, "العنوان (اختياري)");

        // ملء من الموقع الحالي
        WFLocation cur = GPSMockManager.getInstance().getLocation();
        if (cur != null) {
            latEt.setText(String.valueOf(cur.getLatitude()));
            lngEt.setText(String.valueOf(cur.getLongitude()));
        }

        form.addView(nameEt); form.addView(latEt);
        form.addView(lngEt);  form.addView(addrEt);

        new AlertDialog.Builder(activity)
                .setTitle("إضافة مفضلة")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    try {
                        String name = nameEt.getText().toString().trim();
                        double lat  = Double.parseDouble(latEt.getText().toString().trim());
                        double lng  = Double.parseDouble(lngEt.getText().toString().trim());
                        WFLocation candidate = new WFLocation(lat, lng);
                        if (!candidate.isValid()) throw new IllegalArgumentException("coordinates");
                        if (name.isEmpty()) name = String.format(java.util.Locale.US,
                                "%.5f, %.5f", lat, lng);
                        FavoriteLocation fav = new FavoriteLocation(name, lat, lng);
                        fav.setAddress(addrEt.getText().toString().trim());
                        WFStorage.getInstance(activity).addFavorite(fav);
                        WFStorage.getInstance(activity).addHistory(
                                new HistoryEntry(HistoryEntry.Action.ADD_FAVORITE, name));
                        reload.run();
                        Toast.makeText(activity, "✅ تم الحفظ", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(activity, "❌ بيانات غير صحيحة", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private static void showEditDialog(Activity activity, FavoriteLocation fav,
                                        Dialog parent, Runnable reload) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity,16), dp(activity,8), dp(activity,16), dp(activity,8));

        EditText nameEt = buildEditText(activity, "الاسم");
        EditText latEt  = buildEditText(activity, "خط العرض");
        EditText lngEt  = buildEditText(activity, "خط الطول");

        nameEt.setText(fav.getName());
        latEt.setText(String.valueOf(fav.getLatitude()));
        lngEt.setText(String.valueOf(fav.getLongitude()));

        form.addView(nameEt); form.addView(latEt); form.addView(lngEt);

        new AlertDialog.Builder(activity)
                .setTitle("تعديل مفضلة")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    try {
                        fav.setName(nameEt.getText().toString().trim());
                        fav.setLatitude(Double.parseDouble(latEt.getText().toString().trim()));
                        fav.setLongitude(Double.parseDouble(lngEt.getText().toString().trim()));
                        if (!fav.toWFLocation().isValid()) throw new IllegalArgumentException("coordinates");
                        WFStorage.getInstance(activity).updateFavorite(fav);
                        reload.run();
                        Toast.makeText(activity, "✅ تم التحديث", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(activity, "❌ بيانات غير صحيحة", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private static void showImportDialog(Activity activity, Dialog parent, Runnable reload) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity,16), dp(activity,8), dp(activity,16), dp(activity,8));

        EditText jsonEt = new EditText(activity);
        jsonEt.setHint("الصق JSON المفضلة هنا");
        jsonEt.setMinLines(4);
        jsonEt.setGravity(Gravity.TOP);
        form.addView(jsonEt);

        new AlertDialog.Builder(activity)
                .setTitle("استيراد مفضلة")
                .setView(form)
                .setPositiveButton("استيراد", (d, w) -> {
                    String json = jsonEt.getText().toString().trim();
                    if (WFStorage.getInstance(activity).importFavoritesJson(json)) {
                        WFStorage.getInstance(activity).addHistory(
                                new HistoryEntry(HistoryEntry.Action.IMPORT, "استيراد مفضلة"));
                        reload.run();
                        Toast.makeText(activity, "✅ تم الاستيراد", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "❌ JSON غير صالح", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static Button buildBtn(Activity ctx, String label, int bg) {
        Button b = new Button(ctx);
        b.setText(label); b.setTextColor(COLOR_WHITE);
        b.setBackgroundColor(bg); b.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx,36));
        lp.setMargins(dp(ctx,4), 0, 0, 0);
        b.setLayoutParams(lp);
        b.setPadding(dp(ctx,8),0,dp(ctx,8),0);
        return b;
    }

    private static Button buildSmallBtn(Activity ctx, String label, int bg) {
        Button b = new Button(ctx);
        b.setText(label); b.setTextColor(COLOR_WHITE);
        b.setBackgroundColor(bg); b.setTextSize(13);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx,40), dp(ctx,40)));
        b.setPadding(0,0,0,0);
        return b;
    }

    private static EditText buildEditText(Activity ctx, String hint) {
        EditText et = new EditText(ctx);
        et.setHint(hint); et.setHintTextColor(0xFF666666);
        et.setTextColor(0xFF000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(ctx,4), 0, dp(ctx,4));
        et.setLayoutParams(lp);
        return et;
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
