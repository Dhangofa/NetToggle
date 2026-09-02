package com.dhangofa.networktoggle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.telephony.NetworkModeController;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.util.AppExecutors;

public class AutomationReceiver extends BroadcastReceiver {

    private static final String TAG = "AutomationReceiver";
    private static final String ACTION_SET_MODE = "com.dhangofa.networktoggle.SET_MODE";
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_SIM = "sim";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_SET_MODE.equals(intent.getAction())) {
            return;
        }

        String modeString = intent.getStringExtra(EXTRA_MODE);
        if (modeString == null || modeString.isEmpty()) {
            Log.e(TAG, "No mode provided in intent extra '" + EXTRA_MODE + "'");
            return;
        }

        NetworkMode targetMode = parseNetworkMode(modeString);
        if (targetMode == NetworkMode.UNKNOWN) {
            Log.e(TAG, "Unknown mode requested: " + modeString);
            return;
        }

        final PendingResult pendingResult = goAsync();

        AppExecutors.executeTelephony(() -> {
            try {
                AppPreferences prefs = new AppPreferences(context);
                ExecutionMode execMode = prefs.getExecutionMode();
                SimResolver simResolver = new SimResolver(context, prefs);
                
                int simOverride = intent.getIntExtra(EXTRA_SIM, -1);
                
                TargetSim targetSim = prefs.getTargetSim();
                if (simOverride == 1) targetSim = TargetSim.SIM_1;
                else if (simOverride == 2) targetSim = TargetSim.SIM_2;
                else if (simOverride == 3) targetSim = TargetSim.BOTH;

                // Explicit Safeguard: Verify the physical SIM slot is valid and active 
                // before doing anything, so we never cross-apply accidentally.
                if (targetSim == TargetSim.SIM_1 || targetSim == TargetSim.SIM_2) {
                    simResolver.setOverrideTargetSim(targetSim);
                    SimResolver.SimInfo info = simResolver.resolveTargetSimInfo(execMode);
                    if (info == null || info.slotIndex != targetSim.getManualSlotIndex()) {
                        Log.e(TAG, "Rejected: Requested SIM " + (targetSim.getManualSlotIndex() + 1) + " is not available or removed.");
                        return; // Abort execution
                    }
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
                switch (targetMode) {
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
                    Log.e(TAG, "Rejected requested mode " + targetMode.getDisplayName() + " as it is not supported by the selected SIM(s).");
                    return; // Abort execution
                }

                NetworkModeController controller = new NetworkModeController(simResolver);
                
                if (targetSim == TargetSim.BOTH) {
                    // Modern APIs execute per-slot, we must loop to execute on BOTH
                    boolean success = true;
                    
                    simResolver.setOverrideTargetSim(TargetSim.SIM_1);
                    if (simResolver.resolveTargetSimInfo(execMode) != null) {
                        CommandResult r1 = controller.apply(targetMode, execMode);
                        if (!r1.isSuccess()) success = false;
                    }
                    
                    simResolver.setOverrideTargetSim(TargetSim.SIM_2);
                    if (simResolver.resolveTargetSimInfo(execMode) != null) {
                        CommandResult r2 = controller.apply(targetMode, execMode);
                        if (!r2.isSuccess()) success = false;
                    }
                    
                    if (success) {
                        Log.i(TAG, "Successfully changed network mode to " + targetMode.getDisplayName() + " on Both SIMs.");
                    } else {
                        Log.e(TAG, "Failed to change network mode on one or both SIMs.");
                    }
                } else {
                    simResolver.setOverrideTargetSim(targetSim);
                    CommandResult result = controller.apply(targetMode, execMode);
                    if (result.isSuccess()) {
                        Log.i(TAG, "Successfully changed network mode to " + targetMode.getDisplayName() + " via automation intent.");
                    } else {
                        Log.e(TAG, "Failed to change network mode via automation: " + result.getStderr());
                    }
                }
            } finally {
                pendingResult.finish();
            }
        });
    }

    private NetworkMode parseNetworkMode(String modeString) {
        modeString = modeString.toUpperCase().trim();
        switch (modeString) {
            case "5G_ONLY":
            case "5G":
                return NetworkMode.FIVE_G_ONLY;
            case "4G_ONLY":
            case "4G":
            case "LTE":
                return NetworkMode.FOUR_G_ONLY;
            case "PREF_5G":
            case "PREFERRED_5G":
                return NetworkMode.PREFERRED_5G;
            case "PREF_4G":
            case "PREFERRED_4G":
                return NetworkMode.PREFERRED_4G;
            case "PREF_3G":
            case "PREFERRED_3G":
                return NetworkMode.PREFERRED_3G;
            case "2G_ONLY":
            case "2G":
                return NetworkMode.TWO_G_ONLY;
            default:
                return NetworkMode.UNKNOWN;
        }
    }
}
