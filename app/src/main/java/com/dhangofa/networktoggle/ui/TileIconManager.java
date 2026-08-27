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

public final class TileIconManager {
    private static Icon icon4g;
    private static Icon icon5g;
    private static Icon iconP5g;
    private static Icon iconP4g;
    private static Icon iconP3g;
    private static Icon icon2g;
    private static Icon iconUnknown;

    private TileIconManager() {}

    private static Icon createTextOnlyIcon(String text) {
        int size = 256;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(190f);

        float width = paint.measureText(text);
        if (width > 240f) {
            paint.setTextScaleX(240f / width);
        }

        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = (size / 2f) - (metrics.descent + metrics.ascent) / 2f;
        canvas.drawText(text, size / 2f, y, paint);

        return Icon.createWithBitmap(bitmap);
    }

    public static Icon getCachedIcon(String text) {
        switch (text) {
            case "4G":
                if (icon4g == null) icon4g = createTextOnlyIcon("4G");
                return icon4g;
            case "5G":
                if (icon5g == null) icon5g = createTextOnlyIcon("5G");
                return icon5g;
            case "P5G":
                if (iconP5g == null) iconP5g = createTextOnlyIcon("P5G");
                return iconP5g;
            case "P4G":
                if (iconP4g == null) iconP4g = createTextOnlyIcon("P4G");
                return iconP4g;
            case "P3G":
                if (iconP3g == null) iconP3g = createTextOnlyIcon("P3G");
                return iconP3g;
            case "2G":
                if (icon2g == null) icon2g = createTextOnlyIcon("2G");
                return icon2g;
            default:
                if (iconUnknown == null) iconUnknown = createTextOnlyIcon("?");
                return iconUnknown;
        }
    }
}
