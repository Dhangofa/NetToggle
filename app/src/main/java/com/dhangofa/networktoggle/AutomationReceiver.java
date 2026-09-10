package com.dhangofa.networktoggle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dhangofa.networktoggle.automation.AutomationExecutor;
import com.dhangofa.networktoggle.automation.AutomationRequest;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.util.AppExecutors;

public class AutomationReceiver extends BroadcastReceiver {
    private static final String TAG = "AutomationReceiver";
    private static final String ACTION_SET_MODE = "com.dhangofa.networktoggle.SET_MODE";
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_SIM = "sim";
    private static final String EXTRA_TOKEN = "token";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_SET_MODE.equals(intent.getAction())) {
            return;
        }

        com.dhangofa.networktoggle.config.AppPreferences prefs = new com.dhangofa.networktoggle.config.AppPreferences(context);

        if (!prefs.isExternalAutomationEnabled()) {
            Log.e(TAG, "External automation is disabled in settings.");
            return;
        }

        String expectedToken = prefs.getAutomationToken();
        String receivedToken = intent.getStringExtra(EXTRA_TOKEN);

        if (expectedToken != null && !expectedToken.trim().isEmpty()) {
            if (receivedToken == null || !receivedToken.equals(expectedToken)) {
                Log.e(TAG, "Automation token mismatch or missing. Action blocked.");
                return;
            }
        }

        String modeString = intent.getStringExtra(EXTRA_MODE);
        if (modeString == null || modeString.isEmpty()) {
            Log.e(TAG, "No mode provided in intent extra '" + EXTRA_MODE + "'");
            return;
        }

        NetworkMode targetMode = NetworkMode.fromString(modeString);
        if (targetMode == NetworkMode.UNKNOWN) {
            Log.e(TAG, "Unknown mode requested: " + modeString);
            return;
        }

        int simOverride = intent.getIntExtra(EXTRA_SIM, -1);
        TargetSim targetSim = TargetSim.AUTO; // Default fallback to match current config, usually overridden by preferences inside executor
        if (simOverride == 1) targetSim = TargetSim.SIM_1;
        else if (simOverride == 2) targetSim = TargetSim.SIM_2;
        else if (simOverride == 3) targetSim = TargetSim.BOTH;
        else {
             // Fallback to saved preference if sim is not specified or invalid.
             targetSim = prefs.getTargetSim();
        }

        final PendingResult pendingResult = goAsync();
        final TargetSim finalTargetSim = targetSim;

        try {
            AppExecutors.executeTelephony(() -> {
                try {
                    AutomationRequest request = new AutomationRequest(targetMode, finalTargetSim, true, "BroadcastReceiver");
                    AutomationExecutor.execute(context, request);
                } finally {
                    pendingResult.finish();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to submit execution to executors.", e);
            pendingResult.finish();
        }
    }
}
