package com.dhangofa.networktoggle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME = "NetTogglePrefs";
    private static final String STATE_KEY = "net_state";
    private static final String AUTO_SIM_ERROR_KEY = "auto_sim_error";

    private static final int STATE_UNKNOWN = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();

        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .putInt(STATE_KEY, STATE_UNKNOWN)
                .putBoolean(AUTO_SIM_ERROR_KEY, false)
                .apply();
    }
}
