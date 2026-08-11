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

    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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

    public void onExecutionModeChanged(ExecutionMode mode) {
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
}
