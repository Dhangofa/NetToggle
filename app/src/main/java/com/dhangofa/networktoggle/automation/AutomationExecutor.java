package com.dhangofa.networktoggle.automation;

import android.content.Context;
import android.util.Log;

import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.telephony.NetworkModeController;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.NetworkTileService;
import android.service.quicksettings.TileService;
import android.content.ComponentName;

public class AutomationExecutor {
    private static final String TAG = "AutomationExecutor";

    public static AutomationResult execute(Context context, AutomationRequest request) {
        AppPreferences prefs = new AppPreferences(context);
        ExecutionMode execMode = prefs.getExecutionMode();

        if (execMode == ExecutionMode.NONE) {
            String err = "Rejected: Execution mode is NONE.";
            Log.e(TAG, err);
            return new AutomationResult(false, false, err);
        }

        if (request.external && !prefs.isExternalAutomationEnabled()) {
            String err = "Rejected: External automation is disabled.";
            Log.e(TAG, err);
            return new AutomationResult(false, false, err);
        }

        SimResolver simResolver = new SimResolver(context, prefs);
        TargetSim targetSim = request.target;
        if (targetSim == TargetSim.AUTO) {
            // resolve auto directly
            simResolver.setOverrideTargetSim(TargetSim.AUTO);
            SimResolver.SimInfo info = simResolver.resolveTargetSimInfo(execMode);
            if (info == null || info.slotIndex < 0) {
                String err = "Rejected: Auto SIM resolution failed.";
                Log.e(TAG, err);
                simResolver.setOverrideTargetSim(null);
                return new AutomationResult(false, false, err);
            }
            targetSim = info.slotIndex == 0 ? TargetSim.SIM_1 : TargetSim.SIM_2;
            simResolver.setOverrideTargetSim(null);
        }

        AppPreferences.NetworkCapabilities caps;
        if (targetSim == TargetSim.BOTH) {
            AppPreferences.NetworkCapabilities sim1Caps = prefs.getSlotCapabilities(0);
            AppPreferences.NetworkCapabilities sim2Caps = prefs.getSlotCapabilities(1);
            if (sim1Caps == null) sim1Caps = prefs.getDeviceCapabilities();
            if (sim2Caps == null) sim2Caps = prefs.getDeviceCapabilities();

            if (sim1Caps == null || sim2Caps == null) {
                caps = AppPreferences.NetworkCapabilities.assumeAll();
            } else {
                caps = new AppPreferences.NetworkCapabilities(
                    sim1Caps.supports2g && sim2Caps.supports2g,
                    sim1Caps.supports3g && sim2Caps.supports3g,
                    sim1Caps.supports4g && sim2Caps.supports4g,
                    sim1Caps.supports5g && sim2Caps.supports5g
                );
            }
        } else {
            int slotIndex = targetSim.getManualSlotIndex();
            caps = prefs.getSlotCapabilities(slotIndex);
            if (caps == null) caps = prefs.getDeviceCapabilities();
            if (caps == null) caps = AppPreferences.NetworkCapabilities.assumeAll();
        }

        boolean supported = false;
        switch (request.mode) {
            case FIVE_G_ONLY:
            case PREFERRED_5G:
                supported = caps.supports5g;
                break;
            case FOUR_G_ONLY:
            case PREFERRED_4G:
                supported = caps.supports4g;
                break;
            case PREFERRED_3G:
                supported = caps.supports3g;
                break;
            case TWO_G_ONLY:
                supported = caps.supports2g;
                break;
            default:
                supported = true;
        }

        if (!supported) {
            String err = "Rejected requested mode " + request.mode.getDisplayName() + " as it is not supported by the selected SIM(s).";
            Log.e(TAG, err);
            return new AutomationResult(false, false, err);
        }

        NetworkModeController controller = new NetworkModeController(simResolver);
        boolean success = false;
        boolean isPartial = false;
        String errorMessage = "";

        try {
            if (targetSim == TargetSim.BOTH) {
                simResolver.setOverrideTargetSim(TargetSim.SIM_1);
                SimResolver.SimInfo info1 = simResolver.resolveTargetSimInfo(execMode);
                simResolver.setOverrideTargetSim(TargetSim.SIM_2);
                SimResolver.SimInfo info2 = simResolver.resolveTargetSimInfo(execMode);

                if (info1 == null || info2 == null) {
                    errorMessage = "Rejected: One or both SIMs are not available.";
                    Log.e(TAG, errorMessage);
                    return new AutomationResult(false, false, errorMessage);
                }

                simResolver.setOverrideTargetSim(TargetSim.SIM_1);
                CommandResult r1 = controller.apply(request.mode, execMode);
                simResolver.setOverrideTargetSim(TargetSim.SIM_2);
                CommandResult r2 = controller.apply(request.mode, execMode);

                if (r1.isSuccess() && r2.isSuccess()) {
                    success = true;
                } else if (r1.isSuccess() || r2.isSuccess()) {
                    isPartial = true;
                    errorMessage = "Partial success. SIM 1: " + r1.isSuccess() + ", SIM 2: " + r2.isSuccess() +
                                   " | SIM 1 err: " + r1.getStderr() + " | SIM 2 err: " + r2.getStderr();
                    Log.e(TAG, errorMessage);
                    CommandResult failedResult = r1.isSuccess() ? r2 : r1;
                    prefs.setLastError(failedResult.getCommand(), failedResult.getExitCode(), failedResult.getStdout(), failedResult.getStderr(), errorMessage);
                } else {
                    errorMessage = "Failed on both SIMs. SIM 1 err: " + r1.getStderr() + " | SIM 2 err: " + r2.getStderr();
                    Log.e(TAG, errorMessage);
                    prefs.setLastError(r1.getCommand(), r1.getExitCode(), r1.getStdout(), r1.getStderr(), errorMessage);
                }
            } else {
                simResolver.setOverrideTargetSim(targetSim);
                SimResolver.SimInfo info = simResolver.resolveTargetSimInfo(execMode);
                if (info == null || info.slotIndex != targetSim.getManualSlotIndex()) {
                    errorMessage = "Rejected: Requested SIM " + (targetSim.getManualSlotIndex() + 1) + " is not available or removed.";
                    Log.e(TAG, errorMessage);
                    return new AutomationResult(false, false, errorMessage);
                }

                CommandResult result = controller.apply(request.mode, execMode);
                if (result.isSuccess()) {
                    success = true;
                } else {
                    errorMessage = "Failed to change network mode via automation: " + result.getStderr();
                    Log.e(TAG, errorMessage);
                    prefs.setLastError(result.getCommand(), result.getExitCode(), result.getStdout(), result.getStderr(), errorMessage);
                }
            }

            if (success) {
                prefs.setCachedNetworkMode(request.mode);
                prefs.setLastNetworkCheckTimestamp(System.currentTimeMillis());
                prefs.setAutoSimError(false);
                prefs.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
            } else if (isPartial) {
                prefs.setCachedNetworkMode(com.dhangofa.networktoggle.model.NetworkMode.UNKNOWN);
                prefs.setLastNetworkCheckTimestamp(System.currentTimeMillis());
                prefs.setTileErrorState(AppPreferences.TILE_ERROR_CMD);
            }
        } finally {
            simResolver.setOverrideTargetSim(null);
            TileService.requestListeningState(context, new ComponentName(context, NetworkTileService.class));
        }

        return new AutomationResult(success, isPartial, errorMessage);
    }
}
