package com.dhangofa.networktoggle.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;

import java.util.ArrayList;
import java.util.List;

public final class AppPreferences {
    private static final String PREFS_NAME = "NetTogglePrefs";
    private static final String KEY_EXEC_MODE = "exec_mode";
    private static final String KEY_TARGET_SIM = "target_sim";
    private static final String KEY_NETWORK_STATE = "net_state";
    private static final String KEY_AUTO_SIM_ERROR = "auto_sim_error";
    private static final String KEY_TILE_CYCLE_MODES = "tile_cycle_modes";
    
    private static final String KEY_LAST_ERROR_CMD = "last_error_cmd";
    private static final String KEY_LAST_ERROR_STDERR = "last_error_stderr";
    private static final String KEY_LAST_ERROR_TIMESTAMP = "last_error_time";
    
    // Keys for capabilities caching
    private static final String KEY_DEVICE_CAPS_PREFIX = "device_cap_";
    private static final String KEY_SLOT_SUBID_PREFIX = "slot_subid_";
    private static final String KEY_SLOT_CAPS_PREFIX = "slot_cap_";
    
    public static final int TILE_ERROR_NONE = 0;
    public static final int TILE_ERROR_SHIZUKU = 1;
    public static final int TILE_ERROR_ROOT = 2;
    public static final int TILE_ERROR_CMD = 3;
    private static final String KEY_TILE_ERROR = "tile_error_state";

    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getTileErrorState() {
        return preferences.getInt(KEY_TILE_ERROR, TILE_ERROR_NONE);
    }

    public void setTileErrorState(int state) {
        preferences.edit().putInt(KEY_TILE_ERROR, state).apply();
    }

    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public ExecutionMode getExecutionMode() {
        return ExecutionMode.fromValue(
                preferences.getInt(KEY_EXEC_MODE, ExecutionMode.NONE.getValue()));
    }

    public TargetSim getTargetSim() {
        return TargetSim.fromValue(
                preferences.getInt(KEY_TARGET_SIM, TargetSim.AUTO.getValue()));
    }

    public NetworkMode getCachedNetworkMode() {
        return NetworkMode.fromStateValue(
                preferences.getInt(KEY_NETWORK_STATE, NetworkMode.UNKNOWN.getStateValue()));
    }

    public void setCachedNetworkMode(NetworkMode mode) {
        preferences.edit().putInt(KEY_NETWORK_STATE, mode.getStateValue()).apply();
    }

    public void clearCachedNetworkMode() {
        setCachedNetworkMode(NetworkMode.UNKNOWN);
    }

    public boolean hasAutoSimError() {
        return preferences.getBoolean(KEY_AUTO_SIM_ERROR, false);
    }

    public void setAutoSimError(boolean hasError) {
        preferences.edit().putBoolean(KEY_AUTO_SIM_ERROR, hasError).apply();
    }

    public void setLastError(String command, String stderr) {
        preferences.edit()
                .putString(KEY_LAST_ERROR_CMD, command)
                .putString(KEY_LAST_ERROR_STDERR, stderr)
                .putLong(KEY_LAST_ERROR_TIMESTAMP, System.currentTimeMillis())
                .apply();
    }

    public com.dhangofa.networktoggle.model.DiagnosticError getLastError() {
        String cmd = preferences.getString(KEY_LAST_ERROR_CMD, null);
        String stderr = preferences.getString(KEY_LAST_ERROR_STDERR, null);
        long time = preferences.getLong(KEY_LAST_ERROR_TIMESTAMP, 0);
        
        if (cmd == null && stderr == null) return null;
        return new com.dhangofa.networktoggle.model.DiagnosticError(cmd, stderr, time);
    }

    public void clearLastError() {
        if (getTileErrorState() == TILE_ERROR_CMD) {
            setTileErrorState(TILE_ERROR_NONE);
        }
        preferences.edit()
                .remove(KEY_LAST_ERROR_CMD)
                .remove(KEY_LAST_ERROR_STDERR)
                .remove(KEY_LAST_ERROR_TIMESTAMP)
                .apply();
    }

    public void onExecutionModeChanged(ExecutionMode mode) {
        setTileErrorState(TILE_ERROR_NONE);
        preferences.edit()
                .putInt(KEY_EXEC_MODE, mode.getValue())
                .putInt(KEY_NETWORK_STATE, NetworkMode.UNKNOWN.getStateValue())
                .putBoolean(KEY_AUTO_SIM_ERROR, false)
                .apply();
    }

    public void onTargetSimChanged(TargetSim targetSim) {
        preferences.edit()
                .putInt(KEY_TARGET_SIM, targetSim.getValue())
                .putInt(KEY_NETWORK_STATE, NetworkMode.UNKNOWN.getStateValue())
                .putBoolean(KEY_AUTO_SIM_ERROR, false)
                .apply();
    }

    public List<NetworkMode> getTileCycleModes() {
        String saved = preferences.getString(KEY_TILE_CYCLE_MODES, "");
        List<NetworkMode> modes = new ArrayList<>();
        if (saved == null || saved.trim().isEmpty()) return modes;

        String[] ids = saved.split(",");
        for (String id : ids) {
            try {
                NetworkMode mode = NetworkMode.valueOf(id.trim());
                if (mode != NetworkMode.UNKNOWN && !modes.contains(mode)) modes.add(mode);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return modes;
    }

    public void setTileCycleModes(List<NetworkMode> modes) {
        StringBuilder value = new StringBuilder();
        for (NetworkMode mode : modes) {
            if (value.length() > 0) value.append(',');
            value.append(mode.name());
        }
        preferences.edit().putString(KEY_TILE_CYCLE_MODES, value.toString()).apply();
    }

    public void clearTransientState() {
        preferences.edit()
                .putInt(KEY_NETWORK_STATE, NetworkMode.UNKNOWN.getStateValue())
                .putBoolean(KEY_AUTO_SIM_ERROR, false)
                .apply();
    }

    // --- CAPABILITY CACHING LOGIC ---

    public static class NetworkCapabilities {
        public final boolean supports2g;
        public final boolean supports3g;
        public final boolean supports4g;
        public final boolean supports5g;

        public NetworkCapabilities(boolean supports2g, boolean supports3g, boolean supports4g, boolean supports5g) {
            this.supports2g = supports2g;
            this.supports3g = supports3g;
            this.supports4g = supports4g;
            this.supports5g = supports5g;
        }

        // Failsafe fallback: Assume everything is supported if we can't fetch it
        public static NetworkCapabilities assumeAll() {
            return new NetworkCapabilities(true, true, true, true);
        }
    }

    public void saveDeviceCapabilities(NetworkCapabilities caps) {
        preferences.edit()
                .putBoolean(KEY_DEVICE_CAPS_PREFIX + "2g", caps.supports2g)
                .putBoolean(KEY_DEVICE_CAPS_PREFIX + "3g", caps.supports3g)
                .putBoolean(KEY_DEVICE_CAPS_PREFIX + "4g", caps.supports4g)
                .putBoolean(KEY_DEVICE_CAPS_PREFIX + "5g", caps.supports5g)
                .apply();
    }

    public NetworkCapabilities getDeviceCapabilities() {
        if (!preferences.contains(KEY_DEVICE_CAPS_PREFIX + "5g")) {
            return null; // Return null so the resolver knows it needs to fetch them
        }
        return new NetworkCapabilities(
                preferences.getBoolean(KEY_DEVICE_CAPS_PREFIX + "2g", true),
                preferences.getBoolean(KEY_DEVICE_CAPS_PREFIX + "3g", true),
                preferences.getBoolean(KEY_DEVICE_CAPS_PREFIX + "4g", true),
                preferences.getBoolean(KEY_DEVICE_CAPS_PREFIX + "5g", true)
        );
    }

    public void saveSlotCapabilities(int slotIndex, int subId, NetworkCapabilities caps) {
        preferences.edit()
                .putInt(KEY_SLOT_SUBID_PREFIX + slotIndex, subId)
                .putBoolean(KEY_SLOT_CAPS_PREFIX + slotIndex + "_2g", caps.supports2g)
                .putBoolean(KEY_SLOT_CAPS_PREFIX + slotIndex + "_3g", caps.supports3g)
                .putBoolean(KEY_SLOT_CAPS_PREFIX + slotIndex + "_4g", caps.supports4g)
                .putBoolean(KEY_SLOT_CAPS_PREFIX + slotIndex + "_5g", caps.supports5g)
                .apply();
    }

    public int getCachedSubIdForSlot(int slotIndex) {
        return preferences.getInt(KEY_SLOT_SUBID_PREFIX + slotIndex, -1);
    }

    public NetworkCapabilities getSlotCapabilities(int slotIndex) {
        if (!preferences.contains(KEY_SLOT_CAPS_PREFIX + slotIndex + "_5g")) {
            return null;
        }
        return new NetworkCapabilities(
                preferences.getBoolean(KEY_SLOT_CAPS_PREFIX + slotIndex + "_2g", true),
                preferences.getBoolean(KEY_SLOT_CAPS_PREFIX + slotIndex + "_3g", true),
                preferences.getBoolean(KEY_SLOT_CAPS_PREFIX + slotIndex + "_4g", true),
                preferences.getBoolean(KEY_SLOT_CAPS_PREFIX + slotIndex + "_5g", true)
        );
    }
    
    public void clearDeviceCapabilities() {
        preferences.edit()
                .remove(KEY_DEVICE_CAPS_PREFIX + "2g")
                .remove(KEY_DEVICE_CAPS_PREFIX + "3g")
                .remove(KEY_DEVICE_CAPS_PREFIX + "4g")
                .remove(KEY_DEVICE_CAPS_PREFIX + "5g")
                .apply();
    }

    public void invalidateSlotCache(int slotIndex) {
        preferences.edit()
                .remove(KEY_SLOT_SUBID_PREFIX + slotIndex)
                .remove(KEY_SLOT_CAPS_PREFIX + slotIndex + "_2g")
                .remove(KEY_SLOT_CAPS_PREFIX + slotIndex + "_3g")
                .remove(KEY_SLOT_CAPS_PREFIX + slotIndex + "_4g")
                .remove(KEY_SLOT_CAPS_PREFIX + slotIndex + "_5g")
                .apply();
    }
}
