package com.dhangofa.networktoggle.telephony;

/**
 * Changes the network mode on modern Android versions using root.
 * It utilizes the newer `cmd phone` utility which is much cleaner than the old service calls.
 */

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

final class ModernRootModeController {
    private final SimResolver simResolver;

    ModernRootModeController(SimResolver simResolver) {
        this.simResolver = simResolver;
    }

    CommandResult apply(NetworkMode networkMode, ExecutionMode executionMode) {
        if (networkMode == null || networkMode == NetworkMode.UNKNOWN
                || networkMode.getBinaryMask() == null) {
            return CommandResult.failed("", "Invalid network mode selected.");
        }

        int slotIndex = simResolver.resolveTargetSlotIndex(executionMode);
        if (!simResolver.isValidSlotIndex(slotIndex)) {
            return CommandResult.failed("",
                    "Unable to resolve the target physical SIM slot.");
        }

        String command = "cmd phone set-allowed-network-types-for-users -s "
                + slotIndex + " " + networkMode.getBinaryMask();

        CommandExecutor executor = CommandExecutorFactory.forMode(executionMode);
        if (executor == null) {
            return CommandResult.failed(command, "No execution mode selected.");
        }

        return executor.execute(command);
    }
}
