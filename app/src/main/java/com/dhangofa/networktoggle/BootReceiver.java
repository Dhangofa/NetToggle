package com.dhangofa.networktoggle;

/**
 * Broadcast receiver for system boot and app update events.
 * Clears out any stale transient state (like temporary errors or cached 
 * network modes) so the app can start completely fresh after a device reboot or app update.
 */
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dhangofa.networktoggle.config.AppPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        new AppPreferences(context).clearTransientState();
    }
}
