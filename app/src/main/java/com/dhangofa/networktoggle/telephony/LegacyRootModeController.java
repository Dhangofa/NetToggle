package com.dhangofa.networktoggle.telephony;

/**
 * Changes the network mode on older Android versions using root.
 * It essentially builds the legacy payload and executes the root command.
 */

import android.content.Context;
import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

final class LegacyRootModeController {
    private final Context context;
    private final SimResolver simResolver;

    LegacyRootModeController(Context context, SimResolver simResolver) {
        this.context = context.getApplicationContext();
        this.simResolver = simResolver;
    }

    CommandResult apply(NetworkMode networkMode, ExecutionMode executionMode) {
        if (networkMode == null || networkMode == NetworkMode.UNKNOWN
                || networkMode.getLegacyMode() < 0) {
            return CommandResult.failed("", "Invalid legacy network mode selected.");
        }

        SimResolver.SimInfo simInfo = simResolver.resolveTargetSimInfo(executionMode);
        if (simInfo == null || !simResolver.isValidSlotIndex(simInfo.slotIndex)
                || !simResolver.isValidSubId(simInfo.subId)) {
            return CommandResult.failed("", "Unable to resolve the target SIM.");
        }
        
        String apkPath = context.getApplicationInfo().sourceDir;
        String className = "com.dhangofa.networktoggle.telephony.LegacyRootPayload";
        
        String command = "CLASSPATH=\"" + apkPath + "\" app_process /system/bin " + className + " " + simInfo.subId + " " + networkMode.getLegacyMode();
        
        CommandExecutor executor = CommandExecutorFactory.forMode(executionMode);
        if (executor == null) {
            return CommandResult.failed(command, "No execution mode selected.");
        }
        
        CommandResult result = executor.execute(command);
        if (!result.isSuccess()) {
            return result;
        }
        
        return CommandResult.completed(command, 0,
                "Legacy network mode applied successfully via Java payload.", "");
    }
}
