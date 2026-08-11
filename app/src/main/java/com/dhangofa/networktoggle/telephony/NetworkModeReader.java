package com.dhangofa.networktoggle.telephony;

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;

public final class NetworkModeReader {
    private final AppPreferences appPreferences;
    private final SimResolver simResolver;

    public NetworkModeReader(
            AppPreferences appPreferences,
            SimResolver simResolver
    ) {
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

        String command;
        if (simResolver.isValidSubId(targetSubId)) {
            command = "value=$(settings get global preferred_network_mode"
                    + targetSubId
                    + "); if [ -n \"$value\" ] "
                    + "&& [ \"$value\" != \"null\" ]; then "
                    + "echo \"$value\"; else exit 1; fi";
        } else if (targetSim == TargetSim.AUTO) {
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
