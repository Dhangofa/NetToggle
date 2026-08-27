package com.dhangofa.networktoggle.telephony;

/**
 * Parses the current network mode through native Settings where supported,
 * then the selected Root or Shizuku privileged shell fallback.
 */
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;

final class PrivilegedModeReader {

    private final Context context;
    private final SimResolver simResolver;

    PrivilegedModeReader(Context context, SimResolver simResolver) {
        this.context = context;
        this.simResolver = simResolver;
    }

    NetworkMode readCurrentMode(ExecutionMode executionMode, TargetSim targetSim) {
        int targetSubId = simResolver.resolveTargetSubId(executionMode);
        
        // NATIVE API FAST-PATH:
        // Only works reliably on Android 9 (Pie) and below. Android 10+ will throw SecurityException.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && simResolver.isValidSubId(targetSubId)) {
            try {
                String nativeValue = Settings.Global.getString(
                        context.getContentResolver(), 
                        "preferred_network_mode" + targetSubId
                );
                
                if (nativeValue != null && !nativeValue.trim().isEmpty() && !nativeValue.equalsIgnoreCase("null")) {
                    return NetworkMode.fromLegacyMode(ShellValueParser.extractFirstInt(nativeValue));
                }
            } catch (Exception ignored) {
                // Ignore SecurityExceptions or null pointers, proceed to shell fallback
            }
        }
        
        // SHELL FALLBACK:
        String command;
        if (simResolver.isValidSubId(targetSubId)) {
            command = "value=$(settings get global preferred_network_mode" + targetSubId + "); " +
                      "if [ -n \"$value\" ] && [ \"$value\" != \"null\" ]; then echo \"$value\"; else exit 1; fi";
        } else if (targetSim == TargetSim.AUTO) {
            // Very slow nested shell fallback if native SubId resolution entirely failed
            command = "data_sim=$(settings get global multi_sim_data_call); " +
                      "[ \"$data_sim\" -gt 0 ] 2>/dev/null || exit 1; " +
                      "settings get global preferred_network_mode${data_sim}";
        } else {
            return NetworkMode.UNKNOWN;
        }
        
        CommandExecutor executor = CommandExecutorFactory.forMode(executionMode);
        
        if (executor == null) {
            return NetworkMode.UNKNOWN;
        }
        
        CommandResult result = executor.execute(command);
        
        return result.isSuccess() 
                ? NetworkMode.fromLegacyMode(ShellValueParser.extractFirstInt(result.getStdout())) 
                : NetworkMode.UNKNOWN;
    }
}
