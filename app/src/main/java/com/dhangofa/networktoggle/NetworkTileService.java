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
import android.telephony.SubscriptionManager;
import java.lang.reflect.Method;
import rikka.shizuku.Shizuku;

public class NetworkTileService extends TileService {

    private static final String PREFS_NAME = "NetTogglePrefs";
    private static final String STATE_KEY = "net_state";
    private static final String EXEC_MODE_KEY = "exec_mode";
	
	private static final int MODE_NONE = 0;
	private static final int MODE_ROOT = 1;
	private static final int MODE_SHIZUKU = 2;
	
	private static final int STATE_UNKNOWN = 0;
	private static final int STATE_4G_ONLY = 1;
	private static final int STATE_5G_ONLY = 2;
	private static final int STATE_PREF_5G = 3;
	private static final int STATE_PREF_4G = 4;

    private static final String BIN_4G_ONLY = "1000000000000"; // Legacy Id 11, bitmask 4096
    private static final String BIN_5G_ONLY = "10000000000000000000"; // Legacy Id 23, bitmask 524288
    private static final String BIN_PREF_5G = "11011111101111111111"; // Legacy Id 33, bitmask 916479
    private static final String BIN_PREF_4G = "1001101001110000111"; // Legacy Id 9 , bitmask 316295
	
	private static Icon ICON_4G;
	private static Icon ICON_5G;
	private static Icon ICON_P5G;
	private static Icon ICON_P4G;
	private static Icon ICON_UNKNOWN;

    @Override
    public void onStartListening() {
        super.onStartListening();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        updateTileUI(prefs.getInt(STATE_KEY, STATE_UNKNOWN));
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		
		int execMode = prefs.getInt(EXEC_MODE_KEY, MODE_NONE);
		if (execMode == MODE_NONE) {
			updateTileUI(STATE_UNKNOWN);
			return;
		}

        int currentState = prefs.getInt(STATE_KEY, STATE_UNKNOWN);

        int nextState = getNextState(currentState);
		String targetBinary = getBinaryForState(nextState);

        boolean success = applyNetworkMode(targetBinary);

        if (success) {
			prefs.edit().putInt(STATE_KEY, nextState).apply();
			updateTileUI(nextState);
		} else {
			updateTileUI(currentState);
		}
    }
	
	private int getNextState(int currentState) {
		switch (currentState) {
			case STATE_4G_ONLY:
				return STATE_5G_ONLY;

			case STATE_5G_ONLY:
				return STATE_PREF_5G;

			case STATE_PREF_5G:
				return STATE_PREF_4G;

			case STATE_PREF_4G:
			case STATE_UNKNOWN:
			default:
				return STATE_4G_ONLY;
		}
	}
	
	private String getBinaryForState(int state) {
		switch (state) {
			case STATE_5G_ONLY:
				return BIN_5G_ONLY;

			case STATE_PREF_5G:
				return BIN_PREF_5G;

			case STATE_PREF_4G:
				return BIN_PREF_4G;

			case STATE_4G_ONLY:
			default:
				return BIN_4G_ONLY;
		}
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
	
	private Icon getCachedIcon(String text) {
		switch (text) {
			case "4G":
				if (ICON_4G == null) {
					ICON_4G = createTextOnlyIcon("4G");
				}
				return ICON_4G;

			case "5G":
				if (ICON_5G == null) {
					ICON_5G = createTextOnlyIcon("5G");
				}
				return ICON_5G;

			case "P5G":
				if (ICON_P5G == null) {
					ICON_P5G = createTextOnlyIcon("P5G");
				}
				return ICON_P5G;

			case "P4G":
				if (ICON_P4G == null) {
					ICON_P4G = createTextOnlyIcon("P4G");
				}
				return ICON_P4G;

			default:
				if (ICON_UNKNOWN == null) {
					ICON_UNKNOWN = createTextOnlyIcon("?");
				}
				return ICON_UNKNOWN;
		}
	}

    private void updateTileUI(int state) {
		Tile tile = getQsTile();
		if (tile == null) return;

		switch (state) {
			case STATE_4G_ONLY:
				tile.setState(Tile.STATE_ACTIVE);
				tile.setLabel("4G Only");
				tile.setIcon(getCachedIcon("4G"));
				break;

			case STATE_5G_ONLY:
				tile.setState(Tile.STATE_ACTIVE);
				tile.setLabel("5G Only");
				tile.setIcon(getCachedIcon("5G"));
				break;

			case STATE_PREF_5G:
				tile.setState(Tile.STATE_ACTIVE);
				tile.setLabel("Pref 5G");
				tile.setIcon(getCachedIcon("P5G"));
				break;

			case STATE_PREF_4G:
				tile.setState(Tile.STATE_ACTIVE);
				tile.setLabel("Pref 4G");
				tile.setIcon(getCachedIcon("P4G"));
				break;

			case STATE_UNKNOWN:
			default:
				SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
				int execMode = prefs.getInt(EXEC_MODE_KEY, MODE_NONE);

				if (execMode == MODE_NONE) {
					tile.setState(Tile.STATE_UNAVAILABLE);
					tile.setLabel("Setup Required");
				} else {
					tile.setState(Tile.STATE_INACTIVE);
					tile.setLabel("Tap to Set 4G");
				}

				tile.setIcon(getCachedIcon("?"));
				break;
		}

		tile.updateTile();
	}
	
	private int getDefaultDataSubId() {
		return SubscriptionManager.getDefaultDataSubscriptionId();
	}
	
	private boolean applyNetworkMode(String binaryString) {
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		int execMode = prefs.getInt(EXEC_MODE_KEY, MODE_NONE);

		if (execMode == MODE_NONE) {
			return false;
		}

		int subId = getDefaultDataSubId();

		if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
			return false;
		}

		String command = "cmd phone set-allowed-network-types-for-users -s " + subId + " " + binaryString;

		if (execMode == MODE_SHIZUKU) {
			return runCommandWithShizuku(command);
		} else if (execMode == MODE_ROOT) {
			return runCommandWithRoot(command);
		}

		return false;
	}
	
	private boolean runCommandWithRoot(String command) {
		try {
			Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
			int exitCode = process.waitFor();
			return exitCode == 0;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private boolean runCommandWithShizuku(String command) {
		try {
			if (!Shizuku.pingBinder()) {
				return false;
			}

			if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
				return false;
			}

			Method newProcessMethod = Shizuku.class.getDeclaredMethod(
					"newProcess",
					String[].class,
					String[].class,
					String.class
			);
			newProcessMethod.setAccessible(true);

			Process process = (Process) newProcessMethod.invoke(
					null,
					new String[]{"sh", "-c", command},
					null,
					null
			);

			if (process == null) {
				return false;
			}

			int exitCode = process.waitFor();
			return exitCode == 0;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
}
