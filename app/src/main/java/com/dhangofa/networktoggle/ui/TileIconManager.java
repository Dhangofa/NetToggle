package com.dhangofa.networktoggle.ui;

/**
 * Utility to dynamically draw the Quick Settings tile icons.
 * Instead of having dozens of static image files, this draws the text (like "5G", "LTE")
 * directly onto a blank icon canvas on the fly.
 */

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.graphics.DashPathEffect;
import java.util.HashMap;

public final class TileIconManager {
    private static final HashMap<String, Icon> iconCache = new HashMap<>();

    private TileIconManager() {}

    private static Icon createTextOnlyIcon(String text, String badge, boolean isAuto) {
        int size = 256;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(175f);

        float width = paint.measureText(text);
        if (width > 240f) {
            paint.setTextScaleX(240f / width);
        }

        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = (size / 2f) - (metrics.descent + metrics.ascent) / 2f;
        // Shift text slightly to the left and down to make room for the badge
        canvas.drawText(text, (size / 2f) - 10f, y + 10f, paint);

        if (badge != null && !badge.isEmpty()) {
            float cx = size - 35f;
            float cy = 35f;
            float radius = 28f;

            // Draw circle
            Paint circlePaint = new Paint();
            circlePaint.setAntiAlias(true);
            circlePaint.setColor(Color.WHITE);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(9f);
            if (isAuto) {
                // Circumference is ~176 (2 * pi * 28). To get 4 dashes with smaller gaps, dash+gap should be ~44.
                circlePaint.setPathEffect(new DashPathEffect(new float[]{36f, 8f}, 0f));
            }
            canvas.drawCircle(cx, cy, radius, circlePaint);

            // Draw badge text
            Paint badgeTextPaint = new Paint();
            badgeTextPaint.setAntiAlias(true);
            badgeTextPaint.setColor(Color.WHITE);
            badgeTextPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            badgeTextPaint.setFakeBoldText(true); // Force extra thickness
            badgeTextPaint.setTextAlign(Paint.Align.CENTER);
            badgeTextPaint.setTextSize(45f);
            
            Paint.FontMetrics badgeMetrics = badgeTextPaint.getFontMetrics();
            float badgeY = cy - (badgeMetrics.descent + badgeMetrics.ascent) / 2f;
            canvas.drawText(badge, cx, badgeY, badgeTextPaint);
        }

        return Icon.createWithBitmap(bitmap);
    }

    public static Icon getCachedIcon(String text, String badge, boolean isAuto) {
        String cacheKey = text + "_" + badge + "_" + isAuto;
        if (!iconCache.containsKey(cacheKey)) {
            iconCache.put(cacheKey, createTextOnlyIcon(text, badge, isAuto));
        }
        return iconCache.get(cacheKey);
    }
}
