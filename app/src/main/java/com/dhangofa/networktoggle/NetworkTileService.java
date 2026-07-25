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

public class NetworkTileService extends TileService {

    private static final String PREFS_NAME = "NetTogglePrefs";
    private static final String STATE_KEY = "net_state";

    // Hardware Telephony Bitmasks (Translated from legacy IDs 11, 23, 33, 9)
    private static final String BIN_4G_ONLY = "1000000000000";
    private static final String BIN_5G_ONLY = "10000000000000000000";
    private static final String BIN_PREF_5G = "11011101001110000111"; // ID 33 (Includes TDSCDMA)
    private static final String BIN_PREF_4G = "1001101001110000111"; // ID 9

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

    // Dynamically draws the text into an icon so MIUI Control Center displays it correctly
    private Icon createTextIcon(String text) {
        Bitmap bitmap = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        
        paint.setColor(Color.WHITE); 
        paint.setTextSize(55f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setAntiAlias(true);
        
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (144 / 2f) - (fm.descent + fm.ascent) / 2f;
        canvas.drawText(text, 144 / 2f, y, paint);
        
        return Icon.createWithBitmap(bitmap);
    }

    private void updateTileUI(int state) {
        Tile tile = getQsTile();
        if (tile == null) return;
        
        tile.setState(Tile.STATE_ACTIVE);
        
        switch (state) {
            case 1: 
                tile.setLabel("4G Only"); 
                tile.setIcon(createTextIcon("4G"));
                break;
            case 2: 
                tile.setLabel("5G Only"); 
                tile.setIcon(createTextIcon("5G"));
                break;
            case 3: 
                tile.setLabel("Pref 5G"); 
                tile.setIcon(createTextIcon("P5G"));
                break;
            case 4: 
                tile.setLabel("Pref 4G"); 
                tile.setIcon(createTextIcon("P4G"));
                break;
        }
        tile.updateTile();
    }

    private void applyNetworkMode(String binaryString) {
        // Sends the raw binary string straight to the Telephony parser via standard root shell
        String command = "su -c 'cmd phone set-allowed-network-types-for-users -s 0 " + binaryString + "'";
        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
