package com.dhangofa.networktoggle;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import java.io.DataOutputStream;

public class NetworkTileService extends TileService {

    private static final String PREFS_NAME = "NetTogglePrefs";
    private static final String STATE_KEY = "net_state";

    // Hardware Telephony Bitmasks (Legacy IDs 11, 23, 33, 9)
    private static final String BIN_4G_ONLY = "1000000000000";
    private static final String BIN_5G_ONLY = "10000000000000000000";
    private static final String BIN_PREF_5G = "11011111101111111111"; // Exact ID 33
    private static final String BIN_PREF_4G = "1001101001110000111";  // ID 9

    @Override
    public void onStartListening() {
        super.onStartListening();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        updateTileUI(prefs.getInt(STATE_KEY, 1));
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int currentState = prefs.getInt(STATE_KEY, 1);

        int nextState = (currentState % 4) + 1;
        String targetBinary = BIN_4G_ONLY;

        switch (nextState) {
            case 1: targetBinary = BIN_4G_ONLY; break; // 11
            case 2: targetBinary = BIN_5G_ONLY; break; // 23
            case 3: targetBinary = BIN_PREF_5G; break; // 33
            case 4: targetBinary = BIN_PREF_4G; break; // 9
        }

        applyNetworkMode(targetBinary);

        prefs.edit().putInt(STATE_KEY, nextState).apply();
        updateTileUI(nextState);
    }

    /**
     * Draws edge-to-edge, uniform height text to bypass MIUI's shrinking.
     */
    private Icon createTextOnlyIcon(String text) {
        int size = 256; 
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);

        // 1. Use a condensed font to naturally fit more text inside a circle
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);

        // 2. Set one MASSIVE baseline height for ALL text so there is zero mismatch
        paint.setTextSize(190f); 

        // 3. Prevent horizontal clipping for 3-letter strings (P5G, P4G)
        // If the text is wider than the 256 canvas (minus an 8px safety margin on each side),
        // we gently squish the letters horizontally to make them fit without losing height.
        float textWidth = paint.measureText(text);
        if (textWidth > 240f) { 
            paint.setTextScaleX(240f / textWidth);
        }

        // Draw it perfectly vertically centered
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (size / 2f) - (fm.descent + fm.ascent) / 2f;
        canvas.drawText(text, size / 2f, y, paint);

        return Icon.createWithBitmap(bitmap);
    }

    private void updateTileUI(int state) {
        Tile tile = getQsTile();
        if (tile == null) return;

        // Force the tile to always stay "Active" so MIUI keeps the background blue
        tile.setState(Tile.STATE_ACTIVE);

        switch (state) {
            case 1: 
                tile.setLabel("4G Only");
                tile.setIcon(createTextOnlyIcon("4G"));
                break;
            case 2: 
                tile.setLabel("5G Only");
                tile.setIcon(createTextOnlyIcon("5G"));
                break;
            case 3: 
                tile.setLabel("Pref 5G");
                tile.setIcon(createTextOnlyIcon("P5G"));
                break;
            case 4: 
                tile.setLabel("Pref 4G");
                tile.setIcon(createTextOnlyIcon("P4G"));
                break;
        }
        tile.updateTile();
    }

    private void applyNetworkMode(String binaryString) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("cmd phone set-allowed-network-types-for-users -s 0 " + binaryString + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
