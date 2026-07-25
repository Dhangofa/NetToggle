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
import java.lang.reflect.Method;
import rikka.shizuku.Shizuku;

public class NetworkTileService extends TileService {

    private static final String PREFS_NAME = "NetTogglePrefs";
    private static final String STATE_KEY = "net_state";
    private static final String EXEC_MODE_KEY = "exec_mode";

    private static final String BIN_4G_ONLY = "1000000000000";
    private static final String BIN_5G_ONLY = "10000000000000000000";
    private static final String BIN_PREF_5G = "11011111101111111111"; 
    private static final String BIN_PREF_4G = "1001101001110000111";  

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
            case 1: targetBinary = BIN_4G_ONLY; break; 
            case 2: targetBinary = BIN_5G_ONLY; break; 
            case 3: targetBinary = BIN_PREF_5G; break; 
            case 4: targetBinary = BIN_PREF_4G; break; 
        }

        applyNetworkMode(targetBinary);

        prefs.edit().putInt(STATE_KEY, nextState).apply();
        updateTileUI(nextState);
    }

    private Icon createTextOnlyIcon(String text) {
        int size = 256; 
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);

        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(190f); 

        float textWidth = paint.measureText(text);
        if (textWidth > 240f) { 
            paint.setTextScaleX(240f / textWidth);
        }

        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (size / 2f) - (fm.descent + fm.ascent) / 2f;
        canvas.drawText(text, size / 2f, y, paint);

        return Icon.createWithBitmap(bitmap);
    }

    private void updateTileUI(int state) {
        Tile tile = getQsTile();
        if (tile == null) return;
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
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int execMode = prefs.getInt(EXEC_MODE_KEY, 1); // 1 = Root, 2 = Shizuku
        String command = "cmd phone set-allowed-network-types-for-users -s 0 " + binaryString;

        if (execMode == 2) {
            // Shizuku Execution Method via Reflection
            try {
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    
                    // Uses Reflection to bypass the private access restriction on Shizuku.newProcess
                    Method newProcessMethod = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                    newProcessMethod.setAccessible(true);
                    
                    Process process = (Process) newProcessMethod.invoke(null, new String[]{"sh", "-c", command}, null, null);
                    if (process != null) {
                        process.waitFor();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Standard Root Execution Method
            try {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                os.writeBytes(command + "\n");
                os.writeBytes("exit\n");
                os.flush();
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
