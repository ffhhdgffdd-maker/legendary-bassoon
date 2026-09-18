package com.wolfox.gps.util;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Shared Arabic typography and icon-text handling for every WolFox surface. */
public final class WFTheme {
    private static final String MEDIUM_ASSET =
            "wolfox/fonts/DINNextLTArabic-Medium.ttf";
    private static final String REGULAR_ASSET =
            "wolfox/fonts/DINNextLTArabic-Regular.otf";
    private static volatile Typeface medium;
    private static volatile Typeface regular;

    private WFTheme() {}

    public static Typeface regular(Context context) {
        if (regular == null) regular = load(context, REGULAR_ASSET, Typeface.NORMAL);
        return regular;
    }

    public static Typeface medium(Context context) {
        if (medium == null) medium = load(context, MEDIUM_ASSET, Typeface.BOLD);
        return medium;
    }

    public static void apply(TextView view, boolean bold) {
        if (view != null) view.setTypeface(bold ? medium(view.getContext()) : regular(view.getContext()));
    }

    public static void applyTree(View view) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            Typeface current = text.getTypeface();
            apply(text, current != null && current.getStyle() == Typeface.BOLD);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTree(group.getChildAt(i));
        }
    }

    /** Variation-selector keeps symbols rendered as compact monochrome UI icons. */
    public static String icon(String value) {
        return value + "\uFE0E";
    }

    private static Typeface load(Context context, String asset, int fallbackStyle) {
        try {
            return Typeface.createFromAsset(context.getAssets(), asset);
        } catch (Throwable ignored) {
            return Typeface.create("sans-serif", fallbackStyle);
        }
    }
}
