package com.dhangofa.networktoggle.telephony;

/**
 * Main entry point for changing network modes.
 * It figures out whether to use Root or Shizuku, and whether to use the Modern or Legacy root methods
 * based on the device API level.
 */

import android.os.Build;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

public final class NetworkModeController {
    private final ShizukuBinderModeController shizukuBinderController;
    private final LegacyRootModeController legacyController;
    private final ModernRootModeController modernRootController;

    public NetworkModeController(SimResolver simResolver) {
        this.shizukuBinderController = new ShizukuBinderModeController(simResolver);
        this.legacyController = new LegacyRootModeController(
                simResolver.getContext(), simResolver);
        this.modernRootController = new ModernRootModeController(simResolver);
    }

    public CommandResult apply(NetworkMode networkMode, ExecutionMode executionMode) {
        // 1. Shizuku Fast-Path (Binder IPC)
        if (executionMode == ExecutionMode.SHIZUKU) {
            return shizukuBinderController.apply(networkMode, executionMode);
        }

        // 2. Root Legacy Fallback (Android 11 and below)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return legacyController.apply(networkMode, executionMode);
        }

        // 3. Root Modern Fallback (Android 12+)
        return modernRootController.apply(networkMode, executionMode);
    }
}
