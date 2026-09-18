package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ViewOutlineProvider;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.HistoryEntry;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.MapSearchResolver;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Full map plus a compact reusable map preview for the main menu. */
public final class MapDialog {
    private static final String TAG = "MapDialog";
    private static final int BG = 0xFF071426;
    private static final int CARD = 0xFF0D203B;
    private static final int BLUE = 0xFF2787F5;
    private static final int WHITE = 0xFFF4F8FF;
    private static Dialog currentDialog;
    private static WebView currentWebView;

    private MapDialog() {}

    public static final class Preview {
        private final FrameLayout root;
        private WebView webView;

        private Preview(FrameLayout root, WebView webView) {
            this.root = root;
            this.webView = webView;
        }

        public View getView() { return root; }

        public void destroy() {
            WebView value = webView;
            webView = null;
            if (value == null) return;
            try {
                value.stopLoading();
                value.loadUrl("about:blank");
                value.removeAllViews();
                value.destroy();
            } catch (Throwable error) {
                WFLog.d(TAG, "preview cleanup: " + error.getMessage());
            }
        }
    }

    /** Creates the rectangular map shown directly after the menu header. */
    public static Preview createPreview(final Activity activity, View.OnClickListener expand) {
        FrameLayout root = new FrameLayout(activity);
        root.setPadding(dp(activity, 1), dp(activity, 1), dp(activity, 1), dp(activity, 1));
        root.setBackground(round(CARD, 15, BLUE, 1));
        if (Build.VERSION.SDK_INT >= 21) {
            root.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            root.setClipToOutline(true);
        }

        final WebView webView = new WebView(activity);
        setupWebView(webView, activity);
        webView.addJavascriptInterface(new MapSearchBridge(activity, webView),
                "WolFoxSearchBridge");
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button expandButton = buildButton(activity, WFTheme.icon("⛶") + "  توسيع", BG);
        expandButton.setTextColor(WHITE);
        expandButton.setOnClickListener(expand);
        FrameLayout.LayoutParams expandLp = new FrameLayout.LayoutParams(
                dp(activity, 92), dp(activity, 38), Gravity.TOP | Gravity.LEFT);
        expandLp.setMargins(dp(activity, 8), dp(activity, 8), 0, 0);
        root.addView(expandButton, expandLp);

        WFLocation current = GPSMockManager.getInstance().getLocation();
        final double latitude = current == null ? 24.7136d : current.getLatitude();
        final double longitude = current == null ? 46.6753d : current.getLongitude();
        final String type = WFStorage.getInstance(activity).getMapType();
        webView.post(() -> loadMap(webView, latitude, longitude, type, activity, true));
        return new Preview(root, webView);
    }

    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> buildAndShow(activity));
    }

    public static void dismiss() {
        try {
            if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();
        } catch (Throwable ignored) {}
        currentDialog = null;
    }

    private static void buildAndShow(final Activity activity) {
        dismiss();
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        final Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.CENTER);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 8), dp(activity, 8));

        TextView title = new TextView(activity);
        title.setText(WFTheme.icon("⌖") + "  WolFox GPS");
        title.setTextColor(BLUE);
        title.setTextSize(16);
        WFTheme.apply(title, true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final String[] mapTypes = {"roadmap", "satellite", "hybrid", "terrain"};
        final Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
                android.R.layout.simple_spinner_item,
                new String[]{"طريق", "قمر صناعي", "هجين", "تضاريس"}) {
            @Override public View getView(int position, View view, ViewGroup parent) {
                View value = super.getView(position, view, parent);
                WFTheme.applyTree(value);
                return value;
            }

            @Override public View getDropDownView(int position, View view, ViewGroup parent) {
                View value = super.getDropDownView(position, view, parent);
                WFTheme.applyTree(value);
                return value;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                dp(activity, 118), dp(activity, 40));
        spinnerLp.setMargins(dp(activity, 4), 0, dp(activity, 4), 0);
        header.addView(spinner, spinnerLp);

        Button close = buildButton(activity, WFTheme.icon("✕"), BG);
        close.setTextColor(WHITE);
        close.setTextSize(18);
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));
        root.addView(header);

        final WebView webView = new WebView(activity);
        setupWebView(webView, activity);
        webView.addJavascriptInterface(new MapSearchBridge(activity, webView),
                "WolFoxSearchBridge");
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6));
        footer.setBackgroundColor(CARD);
        final Button confirm = buildButton(activity,
                WFTheme.icon("✓") + "  تأكيد التزييف", BLUE);
        confirm.setTextColor(BG);
        footer.addView(confirm, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
        Button center = buildButton(activity, WFTheme.icon("⌖") + "  تمركز", CARD);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                0, dp(activity, 44), 1f);
        centerLp.setMargins(dp(activity, 8), 0, 0, 0);
        footer.addView(center, centerLp);
        root.addView(footer);

        WFTheme.applyTree(root);
        dialog.setContentView(root);

        WFLocation current = GPSMockManager.getInstance().getLocation();
        final double latitude = current == null ? 24.7136d : current.getLatitude();
        final double longitude = current == null ? 46.6753d : current.getLongitude();
        final String selectedType = WFStorage.getInstance(activity).getMapType();
        for (int i = 0; i < mapTypes.length; i++) {
            if (mapTypes[i].equals(selectedType)) {
                spinner.setSelection(i, false);
                break;
            }
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}

            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                 int position, long id) {
                WFTheme.applyTree(view);
                String type = mapTypes[position];
                WFStorage.getInstance(activity).setMapType(type);
                webView.evaluateJavascript(
                        "if(typeof setMapType==='function') setMapType('" + type + "');", null);
            }
        });

        confirm.setOnClickListener(v -> webView.evaluateJavascript(
                "getSelectedLocation()", new ValueCallback<String>() {
                    @Override public void onReceiveValue(String value) {
                        confirmSelection(value, confirm, activity, dialog);
                    }
                }));
        center.setOnClickListener(v -> {
            WFLocation location = GPSMockManager.getInstance().getLocation();
            if (location != null) {
                webView.evaluateJavascript("if(typeof centerMap==='function') centerMap("
                        + location.getLatitude() + "," + location.getLongitude() + ");", null);
            }
        });

        dialog.setOnDismissListener(ignored -> {
            if (currentWebView == webView) {
                destroyWebView(webView);
                currentWebView = null;
            }
            if (currentDialog == dialog) currentDialog = null;
        });
        currentDialog = dialog;
        currentWebView = webView;
        dialog.show();
        if (window != null) {
            window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.96f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.90f));
        }
        webView.post(() -> loadMap(webView, latitude, longitude,
                selectedType, activity, false));
    }

    private static void confirmSelection(String value, final Button button,
                                         final Activity activity, final Dialog dialog) {
        if (value == null || "null".equals(value)) return;
        try {
            JSONArray point = new JSONArray(value);
            final double latitude = point.getDouble(0);
            final double longitude = point.getDouble(1);
            final WFLocation location = new WFLocation(latitude, longitude,
                    "اختيار من الخريطة");
            button.setEnabled(false);
            button.setText("جاري تأكيد الحقن…");
            final boolean started = GPSMockManager.getInstance().selectAndStart(location);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!(started && GPSMockManager.getInstance().verifyRuntimeInjection())) {
                    GPSMockManager.getInstance().stopMocking();
                    button.setEnabled(true);
                    button.setText(WFTheme.icon("✓") + "  تأكيد التزييف");
                    Toast.makeText(activity,
                            "تم حفظ النقطة لكن هوك الموقع لم يستجب؛ أعد فتح التطبيق",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                WFStorage.getInstance(activity).addHistory(new HistoryEntry(
                        HistoryEntry.Action.CHANGE_LOCATION, latitude, longitude,
                        "اختيار من الخريطة"));
                WFStorage.getInstance(activity).addHistory(new HistoryEntry(
                        HistoryEntry.Action.START_MOCK, "تأكيد التزييف من الخريطة"));
                Toast.makeText(activity,
                        String.format(Locale.US, "تم تأكيد التزييف: %.5f, %.5f",
                                latitude, longitude), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }, 220L);
        } catch (Throwable error) {
            WFLog.e(TAG, "selected point: " + error.getMessage());
            Toast.makeText(activity, "تعذر قراءة النقطة المحددة", Toast.LENGTH_SHORT).show();
        }
    }

    private static void setupWebView(WebView webView, Activity activity) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(false);
        // The map is injected with an HTTPS base URL and only loads HTTPS tiles.
        // It never needs access to local files, content providers or file:// bridges.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setBlockNetworkLoads(false);
        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDefaultTextEncodingName("utf-8");
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setBackgroundColor(BG);
        WebView.setWebContentsDebuggingEnabled(false);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                WFLog.d(TAG, "map loaded");
            }

            @Override public void onReceivedError(WebView view, int code,
                                                  String description, String failingUrl) {
                WFLog.e(TAG, "WebView " + code + ": " + description);
            }
        });
    }

    private static void loadMap(WebView webView, double latitude, double longitude,
                                String type, Activity activity, boolean preview) {
        String query = "?lat=" + latitude + "&lng=" + longitude + "&type=" + type
                + (preview ? "&preview=1" : "");
        try {
            activity.getAssets().open("wolfox_map.html").close();
            webView.loadUrl("file:///android_asset/wolfox_map.html" + query);
            return;
        } catch (Throwable ignored) {}

        try {
            ClassLoader loader = MapDialog.class.getClassLoader();
            InputStream stream = loader == null ? null
                    : loader.getResourceAsStream("assets/wolfox_map.html");
            if (stream == null) throw new IllegalStateException("module map asset missing");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) output.write(buffer, 0, read);
            stream.close();
            String html = new String(output.toByteArray(), StandardCharsets.UTF_8);
            webView.loadDataWithBaseURL("file:///android_asset/wolfox_map.html" + query,
                    html, "text/html", "utf-8", null);
        } catch (Throwable error) {
            WFLog.e(TAG, "map asset: " + error.getMessage());
            webView.loadDataWithBaseURL("https://app.local/",
                    fallbackMap(latitude, longitude), "text/html", "utf-8", null);
        }
    }

    private static String fallbackMap(double latitude, double longitude) {
        return "<!doctype html><html dir='rtl'><head><meta name='viewport' "
                + "content='width=device-width,initial-scale=1'><style>html,body{height:100%;margin:0;"
                + "background:#071426;color:#fff;font-family:sans-serif;display:grid;place-items:center;"
                + "text-align:center}.card{padding:18px;border:1px solid #2787f5;border-radius:16px;"
                + "background:#0d203b}.coord{direction:ltr;margin-top:8px;color:#9fc7ff}</style></head>"
                + "<body><div class='card'><b>الخريطة غير متاحة</b><div class='coord'>"
                + latitude + ", " + longitude + "</div></div><script>var selectedLat=" + latitude
                + ",selectedLng=" + longitude + ";function getSelectedLocation(){return [selectedLat,selectedLng]}"
                + "function centerMap(a,b){selectedLat=a;selectedLng=b}function setMapType(t){}</script>"
                + "</body></html>";
    }

    private static void destroyWebView(WebView webView) {
        try {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeAllViews();
            webView.destroy();
        } catch (Throwable error) {
            WFLog.d(TAG, "WebView cleanup: " + error.getMessage());
        }
    }

    private static Button buildButton(Context context, String value, int background) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextColor(WHITE);
        button.setTextSize(13);
        button.setBackground(round(background, 11, 0xFF28547E, 1));
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

    private static final class MapSearchBridge {
        private final WeakReference<Activity> activityRef;
        private final WeakReference<WebView> webViewRef;

        private MapSearchBridge(Activity activity, WebView webView) {
            activityRef = new WeakReference<>(activity);
            webViewRef = new WeakReference<>(webView);
        }

        @JavascriptInterface public void resolve(String query) {
            Activity activity = activityRef.get();
            WebView webView = webViewRef.get();
            if (activity == null || webView == null || activity.isFinishing()) return;
            MapSearchResolver.resolve(activity, query, result -> {
                WebView target = webViewRef.get();
                Activity owner = activityRef.get();
                if (target == null || owner == null || owner.isFinishing()) return;
                try {
                    if (!result.isSuccess()) {
                        target.evaluateJavascript("window.onNativeSearchError("
                                + JSONObject.quote(result.getError()) + ");", null);
                        return;
                    }
                    WFLocation location = result.getLocation();
                    target.evaluateJavascript("window.onNativeSearchResult("
                            + location.getLatitude() + "," + location.getLongitude() + ","
                            + JSONObject.quote(location.getLabel() == null ? "" : location.getLabel())
                            + "," + JSONObject.quote(result.getSource()) + ");", null);
                } catch (Throwable error) {
                    WFLog.e(TAG, "map search callback: " + error.getMessage());
                }
            });
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
