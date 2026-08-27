package com.dhangofa.networktoggle.telephony;

/**
 * Main entry point for reading the current active network mode.
 * It routes the request to either the Root or Shizuku readers depending on user settings.
 */

import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import android.content.Context;

public final class NetworkModeReader {
    private final AppPreferences appPreferences;
    private final ShizukuBinderModeReader shizukuBinderReader;
    private final RootModeReader rootModeReader;

    public NetworkModeReader(
            Context context,
            AppPreferences appPreferences,
            SimResolver simResolver
    ) {
        this.appPreferences = appPreferences;
        this.shizukuBinderReader = new ShizukuBinderModeReader(simResolver);
        this.rootModeReader = new RootModeReader(context, simResolver);
    }

    public NetworkMode readCurrentMode() {
        ExecutionMode executionMode = appPreferences.getExecutionMode();
        if (executionMode == ExecutionMode.NONE) {
            return NetworkMode.UNKNOWN;
        }

        // 1. Shizuku Fast-Path (Binder IPC)
        if (executionMode == ExecutionMode.SHIZUKU) {
            NetworkMode shizukuMode = shizukuBinderReader.readCurrentMode(executionMode);
            if (shizukuMode != NetworkMode.UNKNOWN) {
                return shizukuMode;
            }
        }

        // 2. Root/Native Fallback Path
        return rootModeReader.readCurrentMode(executionMode, appPreferences.getTargetSim());
    }
}
