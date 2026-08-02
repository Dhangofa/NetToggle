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
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.telephony.SubscriptionManager;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final String BIN_PREF_4G = "1001101001110000111"; // Legacy Id 9, bitmask 316295
	
	private static final int LEGACY_4G_ONLY = 11;
	private static final int LEGACY_5G_ONLY = 23;
	private static final int LEGACY_PREF_5G = 33;
	private static final int LEGACY_PREF_4G = 9;
	
	private static final String TARGET_SIM_KEY = "target_sim";
	private static final String AUTO_SIM_ERROR_KEY = "auto_sim_error";

	private static final int TARGET_SIM_AUTO = 0;
	private static final int TARGET_SIM_1 = 1;
	private static final int TARGET_SIM_2 = 2;

	private static final int INVALID_SLOT_INDEX = -1;
	
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
	private static final AtomicBoolean IS_SWITCHING = new AtomicBoolean(false);
	
	private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
	
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	
	private static Method SHIZUKU_NEW_PROCESS_METHOD;
	
	private static Icon ICON_4G;
	private static Icon ICON_5G;
	private static Icon ICON_P5G;
	private static Icon ICON_P4G;
	private static Icon ICON_UNKNOWN;

	@Override
	public void onStartListening() {
		super.onStartListening();

		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		int cachedState = prefs.getInt(STATE_KEY, STATE_UNKNOWN);

		updateTileUI(cachedState);

		int execMode = prefs.getInt(EXEC_MODE_KEY, MODE_NONE);
		if (execMode == MODE_NONE) {
			return;
		}

		// Lightweight behavior:
		// Only read real system mode if we do not have a cached state yet.
		if (cachedState != STATE_UNKNOWN) {
			return;
		}

		EXECUTOR.execute(() -> {
			int realState = readCurrentNetworkState();

			mainHandler.post(() -> {
				if (realState != STATE_UNKNOWN) {
					prefs.edit().putInt(STATE_KEY, realState).apply();
					updateTileUI(realState);
				} else {
					updateTileUI(cachedState);
				}
			});
		});
	}
	
	@Override
	public void onClick() {
		super.onClick();

		if (!IS_SWITCHING.compareAndSet(false, true)) {
			updateTileSwitchingUI();
			return;
		}

		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

		int execMode = prefs.getInt(EXEC_MODE_KEY, MODE_NONE);
		if (execMode == MODE_NONE) {
			IS_SWITCHING.set(false);
			updateTileUI(STATE_UNKNOWN);
			return;
		}

		int currentState = prefs.getInt(STATE_KEY, STATE_UNKNOWN);

		int nextState = getNextState(currentState);
		String targetBinary = getBinaryForState(nextState);

		updateTileSwitchingUI();

		EXECUTOR.execute(() -> {
			boolean success = applyNetworkMode(targetBinary);

			mainHandler.post(() -> {
				try {
					if (success) {
						prefs.edit()
								.putInt(STATE_KEY, nextState)
								.putBoolean(AUTO_SIM_ERROR_KEY, false)
								.apply();

						updateTileUI(nextState);
					} else {
						updateTileUI(currentState);

						if (prefs.getBoolean(AUTO_SIM_ERROR_KEY, false)) {
							showAutoSimErrorToast();
						}
					}
				} finally {
					IS_SWITCHING.set(false);
				}
			});
		});
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
	
	private static Method getShizukuNewProcessMethod() throws NoSuchMethodException {
		if (SHIZUKU_NEW_PROCESS_METHOD == null) {
			SHIZUKU_NEW_PROCESS_METHOD = Shizuku.class.getDeclaredMethod(
					"newProcess",
					String[].class,
					String[].class,
					String.class
			);
			SHIZUKU_NEW_PROCESS_METHOD.setAccessible(true);
		}

		return SHIZUKU_NEW_PROCESS_METHOD;
	}
	
	private static class CommandResult {
		final int exitCode;
		final String stdout;

		CommandResult(int exitCode, String stdout) {
			this.exitCode = exitCode;
			this.stdout = stdout;
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
	
	private void updateTileSwitchingUI() {
		Tile tile = getQsTile();
		if (tile == null) return;

		tile.setState(Tile.STATE_INACTIVE);
		tile.setLabel("Switching...");
		tile.setIcon(getCachedIcon("?"));
		tile.updateTile();
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
	
	private int readCurrentNetworkState() {
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		int execMode = prefs.getInt(EXEC_MODE_KEY, MODE_NONE);

		if (execMode == MODE_NONE) {
			return STATE_UNKNOWN;
		}

		int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();

		String command;

		if (dataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
			command =
					"value=$(settings get global preferred_network_mode" + dataSubId + "); " +
					"if [ -n \"$value\" ] && [ \"$value\" != \"null\" ]; then " +
					"echo \"$value\"; " +
					"else " +
					"data_sim=$(settings get global multi_sim_data_call); " +
					"[ \"$data_sim\" -gt 0 ] 2>/dev/null || exit 1; " +
					"settings get global preferred_network_mode${data_sim}; " +
					"fi";
		} else {
			command =
					"data_sim=$(settings get global multi_sim_data_call); " +
					"[ \"$data_sim\" -gt 0 ] 2>/dev/null || exit 1; " +
					"settings get global preferred_network_mode${data_sim}";
		}

		CommandResult result;

		if (execMode == MODE_SHIZUKU) {
			result = runCommandForResultWithShizuku(command);
		} else if (execMode == MODE_ROOT) {
			result = runCommandForResultWithRoot(command);
		} else {
			return STATE_UNKNOWN;
		}

		if (result.exitCode != 0) {
			return STATE_UNKNOWN;
		}

		return mapLegacyNetworkModeToState(result.stdout);
	}
	
	private CommandResult runCommandForResultWithRoot(String command) {
		Process process = null;

		try {
			process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});

			int exitCode = process.waitFor();
			String stdout = readStream(process.getInputStream());

			return new CommandResult(exitCode, stdout);
		} catch (Exception e) {
			return new CommandResult(-1, "");
		} finally {
			if (process != null) {
				process.destroy();
			}
		}
	}
	
	private CommandResult runCommandForResultWithShizuku(String command) {
		Process process = null;

		try {
			if (!Shizuku.pingBinder()) {
				return new CommandResult(-1, "");
			}

			if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
				return new CommandResult(-1, "");
			}

			process = (Process) getShizukuNewProcessMethod().invoke(
				null,
				new String[]{"sh", "-c", command},
				null,
				null
			);

			if (process == null) {
				return new CommandResult(-1, "");
			}

			int exitCode = process.waitFor();
			String stdout = readStream(process.getInputStream());

			return new CommandResult(exitCode, stdout);
		} catch (Exception e) {
			return new CommandResult(-1, "");
		} finally {
			if (process != null) {
				process.destroy();
			}
		}
	}
	
	private String readStream(InputStream inputStream) {
		StringBuilder builder = new StringBuilder();

		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			String line;

			while ((line = reader.readLine()) != null) {
				builder.append(line).append('\n');
			}
		} catch (Exception ignored) {
		}

		return builder.toString().trim();
	}
	
	private Integer extractFirstInt(String text) {
		try {
			if (text == null || text.trim().isEmpty() || text.trim().equalsIgnoreCase("null")) {
				return null;
			}

			Matcher matcher = NUMBER_PATTERN.matcher(text);

			if (matcher.find()) {
				return Integer.parseInt(matcher.group());
			}

			return null;
		} catch (Exception e) {
			return null;
		}
	}

	private int mapLegacyNetworkModeToState(String output) {
		Integer legacyMode = extractFirstInt(output);

		if (legacyMode == null) {
			return STATE_UNKNOWN;
		}

		switch (legacyMode) {
			case LEGACY_4G_ONLY:
				return STATE_4G_ONLY;

			case LEGACY_5G_ONLY:
				return STATE_5G_ONLY;

			case LEGACY_PREF_5G:
				return STATE_PREF_5G;

			case LEGACY_PREF_4G:
				return STATE_PREF_4G;

			// Preferred 4G / LTE-preferred variants across OEMs/ROMs
			case 8:  // CDMA + LTE/EvDo (PRL)
			case 10: // LTE/CDMA/EvDo/GSM/WCDMA (PRL)
			case 12:
			case 15:
			case 17:
			case 19:
			case 20:
			case 22:
				return STATE_PREF_4G;

			default:
				// Preferred 5G / NR-capable preferred variants across OEMs/ROMs
				if (legacyMode >= 24 && legacyMode <= 32) {
					return STATE_PREF_5G;
				}

				return STATE_UNKNOWN;
		}
	}
	
	private boolean isValidSlotIndex(int slotIndex) {
		return slotIndex == 0 || slotIndex == 1;
	}

	private void setAutoSimError(boolean hasError) {
		getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
				.edit()
				.putBoolean(AUTO_SIM_ERROR_KEY, hasError)
				.apply();
	}

	private void showAutoSimErrorToast() {
		Toast.makeText(
				this,
				"Unable to detect active data SIM automatically. Please choose SIM from the app.",
				Toast.LENGTH_LONG
		).show();
	}

	private int resolveTargetSlotIndex(int execMode) {
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		int targetSim = prefs.getInt(TARGET_SIM_KEY, TARGET_SIM_AUTO);

		if (targetSim == TARGET_SIM_1) {
			setAutoSimError(false);
			return 0;
		}

		if (targetSim == TARGET_SIM_2) {
			setAutoSimError(false);
			return 1;
		}

		return resolveAutoSlotIndex(execMode);
	}

	private int resolveAutoSlotIndex(int execMode) {
		int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();

		if (dataSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
			setAutoSimError(true);
			return INVALID_SLOT_INDEX;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			try {
				int slotIndex = SubscriptionManager.getSlotIndex(dataSubId);

				if (isValidSlotIndex(slotIndex)) {
					setAutoSimError(false);
					return slotIndex;
				}
			} catch (Exception ignored) {
			}
		}

		int slotIndexFromDumpsys = resolveSlotIndexFromDumpsys(execMode, dataSubId);

		if (isValidSlotIndex(slotIndexFromDumpsys)) {
			setAutoSimError(false);
			return slotIndexFromDumpsys;
		}

		setAutoSimError(true);
		return INVALID_SLOT_INDEX;
	}

	private int resolveSlotIndexFromDumpsys(int execMode, int dataSubId) {
		String command =
				"dumpsys isub | grep -E \"\\{id=" + dataSubId + "([^0-9]| )\" " +
				"| head -n 1 " +
				"| grep -o -E \"simSlotIndex=[0-9]+\" " +
				"| cut -d '=' -f 2";

		CommandResult result;

		if (execMode == MODE_SHIZUKU) {
			result = runCommandForResultWithShizuku(command);
		} else if (execMode == MODE_ROOT) {
			result = runCommandForResultWithRoot(command);
		} else {
			return INVALID_SLOT_INDEX;
		}

		if (result.exitCode != 0) {
			return INVALID_SLOT_INDEX;
		}

		Integer slotIndex = extractFirstInt(result.stdout);

		if (slotIndex == null || !isValidSlotIndex(slotIndex)) {
			return INVALID_SLOT_INDEX;
		}

		return slotIndex;
	}
	
	
	private boolean applyNetworkMode(String binaryString) {
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		int execMode = prefs.getInt(EXEC_MODE_KEY, MODE_NONE);

		if (execMode == MODE_NONE) {
			return false;
		}

		int slotIndex = resolveTargetSlotIndex(execMode);

		if (!isValidSlotIndex(slotIndex)) {
			return false;
		}

		String command =
				"cmd phone set-allowed-network-types-for-users -s " +
				slotIndex +
				" " +
				binaryString;

		if (execMode == MODE_SHIZUKU) {
			return runCommandWithShizuku(command);
		} else if (execMode == MODE_ROOT) {
			return runCommandWithRoot(command);
		}

		return false;
	}
	
	
	
	private boolean runCommandWithRoot(String command) {
		Process process = null;

		try {
			process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
			int exitCode = process.waitFor();
			return exitCode == 0;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			if (process != null) {
				process.destroy();
			}
		}
	}
	
	private boolean runCommandWithShizuku(String command) {
		Process process = null;
		
		try {
			if (!Shizuku.pingBinder()) {
				return false;
			}

			if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
				return false;
			}

			process = (Process) getShizukuNewProcessMethod().invoke(
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
		}finally {
			if (process != null) {
				process.destroy();
			}
		}
	}
}
