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
    private static final String BIN_PREF_5G = "11011111101111111111"; // ID 33
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

    /**
     * Draws a colored circular background with enlarged text.
     */
    private Icon createCustomIcon(String text, int bgColor, int textColor) {
        int size = 192; // High-res canvas for crisp rendering
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // 1. Draw Background Circle
        paint.setColor(bgColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2f, paint);

        // 2. Draw Enlarged Text
        paint.setColor(textColor);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);

        // Scales font size up (88pt for "4G/5G", 68pt for "P5G/P4G")
        float textSize = (text.length() <= 2) ? 88f : 68f; 
        paint.setTextSize(textSize);

        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (size / 2f) - (fm.descent + fm.ascent) / 2f;
        canvas.drawText(text, size / 2f, y, paint);

        return Icon.createWithBitmap(bitmap);
    }

    private void updateTileUI(int state) {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setState(Tile.STATE_ACTIVE);

        // Hex Colors
        int white  = Color.parseColor("#FFFFFF");
        int blue   = Color.parseColor("#0066FF");
        int green  = Color.parseColor("#1B873F");
        int yellow = Color.parseColor("#FFB300");

        switch (state) {
            case 1: // 4G -> Blue background, White text
                tile.setLabel("4G Only");
                tile.setIcon(createCustomIcon("4G", blue, white));
                break;

            case 2: // 5G -> White background, Blue text
                tile.setLabel("5G Only");
                tile.setIcon(createCustomIcon("5G", white, blue));
                break;

            case 3: // P5G -> White background, Green text
                tile.setLabel("Pref 5G");
                tile.setIcon(createCustomIcon("P5G", white, green));
                break;

            case 4: // P4G -> Yellow background, White text
                tile.setLabel("Pref 4G");
                tile.setIcon(createCustomIcon("P4G", yellow, white));
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
        }
    }
}
