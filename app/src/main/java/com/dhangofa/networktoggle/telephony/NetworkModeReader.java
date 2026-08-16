package com.dhangofa.networktoggle.telephony;

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import android.content.Context;
import android.provider.Settings;

public final class NetworkModeReader {
    private final Context context;
    private final AppPreferences appPreferences;
    private final SimResolver simResolver;

    public NetworkModeReader(
            Context context,
            AppPreferences appPreferences,
            SimResolver simResolver
    ) {
        this.context = context;
        this.appPreferences = appPreferences;
        this.simResolver = simResolver;
    }

    public NetworkMode readCurrentMode() {
        ExecutionMode executionMode = appPreferences.getExecutionMode();
        if (executionMode == ExecutionMode.NONE) {
            return NetworkMode.UNKNOWN;
        }

        TargetSim targetSim = appPreferences.getTargetSim();
        int targetSubId = simResolver.resolveTargetSubId(executionMode);

        // NATIVE API FAST-PATH:
        // Try reading natively without spawning shell if we have a valid SubId
        if (simResolver.isValidSubId(targetSubId)) {
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
            command = "value=$(settings get global preferred_network_mode"
                    + targetSubId
                    + "); if [ -n \"$value\" ] "
                    + "&& [ \"$value\" != \"null\" ]; then "
                    + "echo \"$value\"; else exit 1; fi";
        } else if (targetSim == TargetSim.AUTO) {
            // Very slow nested shell fallback if native SubId resolution entirely failed
            command = "data_sim=$(settings get global multi_sim_data_call); "
                    + "[ \"$data_sim\" -gt 0 ] 2>/dev/null || exit 1; "
                    + "settings get global preferred_network_mode${data_sim}";
        } else {
            return NetworkMode.UNKNOWN;
        }

        CommandResult result = execute(executionMode, command);
        if (!result.isSuccess()) {
            return NetworkMode.UNKNOWN;
        }

        return NetworkMode.fromLegacyMode(
                ShellValueParser.extractFirstInt(result.getStdout())
        );
    }

    private CommandResult execute(
            ExecutionMode executionMode,
            String command
    ) {
        CommandExecutor executor = CommandExecutorFactory.forMode(executionMode);
        if (executor == null) {
            return CommandResult.failed(command, "No execution mode selected.");
        }
        return executor.execute(command);
    }
}
