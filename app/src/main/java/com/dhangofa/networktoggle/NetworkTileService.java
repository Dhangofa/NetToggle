package com.dhangofa.networktoggle;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.telephony.SubscriptionManager;
import android.widget.Toast;

import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class NetworkTileService extends TileService {
    private static final int INVALID_SLOT_INDEX = -1;
    private static final int INVALID_SUB_ID = -1;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean IS_SWITCHING = new AtomicBoolean(false);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\d+");
    private static Method shizukuNewProcessMethod;

    private static Icon icon4g;
    private static Icon icon5g;
    private static Icon iconP5g;
    private static Icon iconP4g;
    private static Icon iconUnknown;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AppPreferences appPreferences;

    @Override
    public void onCreate() {
        super.onCreate();
        appPreferences = new AppPreferences(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        NetworkMode cachedMode = appPreferences.getCachedNetworkMode();
        updateTileUI(cachedMode);

        if (appPreferences.getExecutionMode() == ExecutionMode.NONE
                || cachedMode != NetworkMode.UNKNOWN) return;

        EXECUTOR.execute(() -> {
            NetworkMode realMode = readCurrentNetworkMode();
            mainHandler.post(() -> {
                if (realMode != NetworkMode.UNKNOWN) {
                    appPreferences.setCachedNetworkMode(realMode);
                    updateTileUI(realMode);
                } else {
                    updateTileUI(cachedMode);
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

        ExecutionMode executionMode = appPreferences.getExecutionMode();
        if (executionMode == ExecutionMode.NONE) {
            IS_SWITCHING.set(false);
            updateTileUI(NetworkMode.UNKNOWN);
            return;
        }

        NetworkMode currentMode = appPreferences.getCachedNetworkMode();
        NetworkMode nextMode = NetworkMode.nextInDefaultCycle(currentMode);
        updateTileSwitchingUI();

        EXECUTOR.execute(() -> {
            boolean success = applyNetworkMode(nextMode, executionMode);
            mainHandler.post(() -> {
                try {
                    if (success) {
                        appPreferences.setCachedNetworkMode(nextMode);
                        appPreferences.setAutoSimError(false);
                        updateTileUI(nextMode);
                    } else {
                        updateTileUI(currentMode);
                        if (appPreferences.hasAutoSimError()) showAutoSimErrorToast();
                    }
                } finally {
                    IS_SWITCHING.set(false);
                }
            });
        });
    }

    private static Method getShizukuNewProcessMethod() throws NoSuchMethodException {
        if (shizukuNewProcessMethod == null) {
            shizukuNewProcessMethod = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            shizukuNewProcessMethod.setAccessible(true);
        }
        return shizukuNewProcessMethod;
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
        float width = paint.measureText(text);
        if (width > 240f) paint.setTextScaleX(240f / width);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (size / 2f) - (fm.descent + fm.ascent) / 2f;
        canvas.drawText(text, size / 2f, y, paint);
        return Icon.createWithBitmap(bitmap);
    }

    private Icon getCachedIcon(String text) {
        switch (text) {
            case "4G": if (icon4g == null) icon4g = createTextOnlyIcon("4G"); return icon4g;
            case "5G": if (icon5g == null) icon5g = createTextOnlyIcon("5G"); return icon5g;
            case "P5G": if (iconP5g == null) iconP5g = createTextOnlyIcon("P5G"); return iconP5g;
            case "P4G": if (iconP4g == null) iconP4g = createTextOnlyIcon("P4G"); return iconP4g;
            default: if (iconUnknown == null) iconUnknown = createTextOnlyIcon("?"); return iconUnknown;
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

    private void updateTileUI(NetworkMode mode) {
        Tile tile = getQsTile();
        if (tile == null) return;
        if (mode == NetworkMode.UNKNOWN) {
            if (appPreferences.getExecutionMode() == ExecutionMode.NONE) {
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.setLabel("Setup Required");
            } else {
                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel(mode.getTileLabel());
            }
            tile.setIcon(getCachedIcon("?"));
        } else {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(mode.getTileLabel());
            tile.setIcon(getCachedIcon(mode.getIconText()));
        }
        tile.updateTile();
    }

    private NetworkMode readCurrentNetworkMode() {
        ExecutionMode executionMode = appPreferences.getExecutionMode();
        if (executionMode == ExecutionMode.NONE) return NetworkMode.UNKNOWN;

        TargetSim targetSim = appPreferences.getTargetSim();
        int targetSubId = INVALID_SUB_ID;
        if (targetSim == TargetSim.AUTO) {
            targetSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        } else {
            targetSubId = resolveSubIdFromDumpsys(executionMode, targetSim.getManualSlotIndex());
        }

        String command;
        if (targetSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && targetSubId != INVALID_SUB_ID) {
            command = "value=$(settings get global preferred_network_mode" + targetSubId + "); "
                    + "if [ -n \"$value\" ] && [ \"$value\" != \"null\" ]; then "
                    + "echo \"$value\"; else exit 1; fi";
        } else if (targetSim == TargetSim.AUTO) {
            command = "data_sim=$(settings get global multi_sim_data_call); "
                    + "[ \"$data_sim\" -gt 0 ] 2>/dev/null || exit 1; "
                    + "settings get global preferred_network_mode${data_sim}";
        } else {
            return NetworkMode.UNKNOWN;
        }

        CommandResult result = runCommandForResult(executionMode, command);
        if (result.exitCode != 0) return NetworkMode.UNKNOWN;
        return NetworkMode.fromLegacyMode(extractFirstInt(result.stdout));
    }

    private CommandResult runCommandForResult(ExecutionMode mode, String command) {
        if (mode == ExecutionMode.SHIZUKU) return runCommandForResultWithShizuku(command);
        if (mode == ExecutionMode.ROOT) return runCommandForResultWithRoot(command);
        return new CommandResult(-1, "");
    }

    private CommandResult runCommandForResultWithRoot(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, readStream(process.getInputStream()));
        } catch (Exception e) {
            return new CommandResult(-1, "");
        } finally {
            if (process != null) process.destroy();
        }
    }

    private CommandResult runCommandForResultWithShizuku(String command) {
        Process process = null;
        try {
            if (!Shizuku.pingBinder()
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return new CommandResult(-1, "");
            }
            process = (Process) getShizukuNewProcessMethod().invoke(
                    null, new String[]{"sh", "-c", command}, null, null);
            if (process == null) return new CommandResult(-1, "");
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, readStream(process.getInputStream()));
        } catch (Exception e) {
            return new CommandResult(-1, "");
        } finally {
            if (process != null) process.destroy();
        }
    }

    private String readStream(InputStream stream) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        } catch (Exception ignored) {
        }
        return builder.toString().trim();
    }

    private Integer extractFirstInt(String text) {
        try {
            if (text == null || text.trim().isEmpty() || text.trim().equalsIgnoreCase("null")) return null;
            Matcher matcher = NUMBER_PATTERN.matcher(text);
            return matcher.find() ? Integer.parseInt(matcher.group()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidSlotIndex(int slotIndex) {
        return slotIndex == 0 || slotIndex == 1;
    }

    private void setAutoSimError(boolean value) {
        appPreferences.setAutoSimError(value);
    }

    private void showAutoSimErrorToast() {
        Toast.makeText(this,
                "Unable to detect active data SIM automatically. Please choose SIM from the app.",
                Toast.LENGTH_LONG).show();
    }

    private int resolveTargetSlotIndex(ExecutionMode executionMode) {
        TargetSim targetSim = appPreferences.getTargetSim();
        if (!targetSim.isAuto()) {
            setAutoSimError(false);
            return targetSim.getManualSlotIndex();
        }
        return resolveAutoSlotIndex(executionMode);
    }

    private int resolveAutoSlotIndex(ExecutionMode executionMode) {
        int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (dataSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            setAutoSimError(true);
            return INVALID_SLOT_INDEX;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                int slot = SubscriptionManager.getSlotIndex(dataSubId);
                if (isValidSlotIndex(slot)) {
                    setAutoSimError(false);
                    return slot;
                }
            } catch (Exception ignored) {
            }
        }
        int slot = resolveSlotIndexFromDumpsys(executionMode, dataSubId);
        if (isValidSlotIndex(slot)) {
            setAutoSimError(false);
            return slot;
        }
        setAutoSimError(true);
        return INVALID_SLOT_INDEX;
    }

    private int resolveSlotIndexFromDumpsys(ExecutionMode executionMode, int dataSubId) {
        String command = "dumpsys isub | grep -E \"\\{id=" + dataSubId
                + "([^0-9]| )\" | head -n 1 | grep -o -E \"simSlotIndex=[0-9]+\" "
                + "| cut -d '=' -f 2";
        CommandResult result = runCommandForResult(executionMode, command);
        Integer slot = result.exitCode == 0 ? extractFirstInt(result.stdout) : null;
        return slot != null && isValidSlotIndex(slot) ? slot : INVALID_SLOT_INDEX;
    }

    private int resolveSubIdFromDumpsys(ExecutionMode executionMode, int slotIndex) {
        String command = "dumpsys isub | grep -E \"simSlotIndex=" + slotIndex
                + "([^0-9]| )\" | head -n 1 | grep -o -E \"\\{id=[0-9]+\" "
                + "| cut -d '=' -f 2";
        CommandResult result = runCommandForResult(executionMode, command);
        Integer subId = result.exitCode == 0 ? extractFirstInt(result.stdout) : null;
        return subId != null && subId > 0 ? subId : INVALID_SUB_ID;
    }

    private boolean applyNetworkMode(NetworkMode mode, ExecutionMode executionMode) {
        int slotIndex = resolveTargetSlotIndex(executionMode);
        if (!isValidSlotIndex(slotIndex) || mode.getBinaryMask() == null) return false;
        String command = "cmd phone set-allowed-network-types-for-users -s "
                + slotIndex + " " + mode.getBinaryMask();
        if (executionMode == ExecutionMode.SHIZUKU) return runCommandWithShizuku(command);
        if (executionMode == ExecutionMode.ROOT) return runCommandWithRoot(command);
        return false;
    }

    private boolean runCommandWithRoot(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private boolean runCommandWithShizuku(String command) {
        Process process = null;
        try {
            if (!Shizuku.pingBinder()
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return false;
            process = (Process) getShizukuNewProcessMethod().invoke(
                    null, new String[]{"sh", "-c", command}, null, null);
            return process != null && process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
