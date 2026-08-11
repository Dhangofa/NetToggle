package com.dhangofa.networktoggle;

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
