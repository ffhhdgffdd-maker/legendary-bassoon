package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.HistoryEntry;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.MapSearchResolver;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.util.Locale;

/** Address/coordinate/share-link search with deterministic progress and result states. */
public final class SearchDialog {
    private static final int BG = 0xFF071426;
    private static final int BLUE = 0xFF2787F5;
    private static final int CARD = 0xFF0D203B;
    private static final int WHITE = 0xFFF4F8FF;
    private static final int MUTED = 0xFF97ABC5;
    private static final int GREEN = 0xFF36D783;
    private static final int RED = 0xFFFF5563;

    private SearchDialog() {}

    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> buildAndShow(activity));
    }

    private static void buildAndShow(final Activity activity) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.CENTER);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16));
        root.setBackground(round(BG, 20, 0xFF214D7D, 1));

        TextView title = text(activity, WFTheme.icon("⌕") + "  بحث عن موقع", 18, BLUE, true);
        root.addView(title);
        TextView hint = text(activity,
                "عنوان، إحداثيات، أو رابط مشاركة من Google Maps أو Apple Maps أو خرائط Android.",
                12, MUTED, false);
        LinearLayout.LayoutParams hintLp = fullWrap();
        hintLp.setMargins(0, dp(activity, 5), 0, dp(activity, 10));
        root.addView(hint, hintLp);

        final EditText query = new EditText(activity);
        query.setSingleLine(true);
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setHint("الرياض، 24.7136, 46.6753، أو رابط خريطة");
        query.setHintTextColor(0xFF687F9B);
        query.setTextColor(WHITE);
        query.setTextSize(14);
        query.setBackground(round(CARD, 12, 0xFF28547E, 1));
        query.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        LinearLayout.LayoutParams queryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50));
        queryLp.setMargins(0, 0, 0, dp(activity, 8));
        root.addView(query, queryLp);

        LinearLayout helpers = new LinearLayout(activity);
        helpers.setOrientation(LinearLayout.HORIZONTAL);
        Button paste = button(activity, WFTheme.icon("⇧") + " لصق الرابط", CARD, WHITE);
        Button clear = button(activity, WFTheme.icon("×") + " مسح", CARD, WHITE);
        helpers.addView(paste, weighted(activity, 40, 6));
        helpers.addView(clear, weighted(activity, 40, 0));
        root.addView(helpers, fullWrap());

        final TextView resultText = text(activity, "", 13, WHITE, false);
        resultText.setGravity(Gravity.CENTER);
        resultText.setVisibility(View.GONE);
        resultText.setPadding(dp(activity, 8), dp(activity, 10),
                dp(activity, 8), dp(activity, 10));
        root.addView(resultText, fullWrap());

        final ProgressBar progress = new ProgressBar(activity);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                dp(activity, 32), dp(activity, 32));
        progressLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(progress, progressLp);

        final Button apply = button(activity,
                WFTheme.icon("✓") + " تأكيد وتشغيل التزييف", GREEN, BG);
        apply.setVisibility(View.GONE);
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48));
        applyLp.setMargins(0, dp(activity, 8), 0, dp(activity, 8));
        root.addView(apply, applyLp);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        final Button search = button(activity, WFTheme.icon("⌕") + " بحث", BLUE, WHITE);
        Button close = button(activity, "إغلاق", CARD, WHITE);
        actions.addView(search, weighted(activity, 44, 6));
        actions.addView(close, weighted(activity, 44, 0));
        root.addView(actions, fullWrap());

        WFTheme.applyTree(root);
        dialog.setContentView(root);

        final WFLocation[] selected = new WFLocation[1];
        final String[] source = {""};
        final String[] searchedText = {""};

        paste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                Toast.makeText(activity, "الحافظة فارغة", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence value = clip.getItemAt(0).coerceToText(activity);
            query.setText(value == null ? "" : value.toString().trim());
            query.setSelection(query.length());
            search.performClick();
        });

        clear.setOnClickListener(v -> {
            query.setText("");
            selected[0] = null;
            resultText.setVisibility(View.GONE);
            apply.setVisibility(View.GONE);
            query.requestFocus();
        });
        close.setOnClickListener(v -> dialog.dismiss());

        search.setOnClickListener(v -> {
            final String input = query.getText().toString().trim();
            if (input.isEmpty()) {
                query.requestFocus();
                resultText.setText("أدخل موقعًا للبحث");
                resultText.setTextColor(RED);
                resultText.setVisibility(View.VISIBLE);
                return;
            }
            searchedText[0] = input;
            selected[0] = null;
            source[0] = "";
            resultText.setVisibility(View.GONE);
            apply.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            search.setEnabled(false);
            search.setText("جاري البحث…");

            MapSearchResolver.resolve(activity, input, resolved -> {
                if (!dialog.isShowing()) return;
                progress.setVisibility(View.GONE);
                search.setEnabled(true);
                search.setText(WFTheme.icon("⌕") + " بحث");
                resultText.setVisibility(View.VISIBLE);
                if (!resolved.isSuccess()) {
                    resultText.setText(WFTheme.icon("×") + " " + resolved.getError());
                    resultText.setTextColor(RED);
                    return;
                }
                WFLocation location = resolved.getLocation();
                selected[0] = location;
                source[0] = resolved.getSource();
                String label = location.getLabel() == null ? "" : location.getLabel();
                resultText.setText(WFTheme.icon("✓") + " " + resolved.getSource()
                        + (label.isEmpty() ? "" : "\n" + label)
                        + "\n" + String.format(Locale.US, "%.6f, %.6f",
                        location.getLatitude(), location.getLongitude()));
                resultText.setTextColor(WHITE);
                apply.setVisibility(View.VISIBLE);
            });
        });

        apply.setOnClickListener(v -> {
            final WFLocation location = selected[0];
            if (location == null) return;
            apply.setEnabled(false);
            resultText.setText("جاري تأكيد حقن الموقع داخل التطبيق…");
            resultText.setTextColor(MUTED);
            final boolean started = GPSMockManager.getInstance().selectAndStart(location);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!dialog.isShowing()) return;
                if (!(started && GPSMockManager.getInstance().verifyRuntimeInjection())) {
                    GPSMockManager.getInstance().stopMocking();
                    apply.setEnabled(true);
                    resultText.setText(WFTheme.icon("×")
                            + " تم حفظ الموقع، لكن هوك التزييف لم يستجب. أعد فتح التطبيق ثم حاول مجددًا.");
                    resultText.setTextColor(RED);
                    return;
                }
                WFStorage.getInstance(activity).addHistory(new HistoryEntry(
                        HistoryEntry.Action.SEARCH, location.getLatitude(), location.getLongitude(),
                        searchedText[0] + " • " + source[0]));
                WFStorage.getInstance(activity).addHistory(new HistoryEntry(
                        HistoryEntry.Action.START_MOCK, "تأكيد التزييف من البحث"));
                Toast.makeText(activity, "تم تأكيد التزييف بنجاح", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }, 220L);
        });

        query.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                search.performClick();
                return true;
            }
            return false;
        });

        dialog.show();
        if (window != null) {
            window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        WFTheme.apply(view, bold);
        return view;
    }

    private static Button button(Context context, String value, int background, int color) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextColor(color);
        button.setTextSize(13);
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

    private static LinearLayout.LayoutParams weighted(Context context, int heightDp, int endMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(context, heightDp), 1f);
        params.setMargins(0, 0, dp(context, endMarginDp), 0);
        return params;
    }

    private static LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
