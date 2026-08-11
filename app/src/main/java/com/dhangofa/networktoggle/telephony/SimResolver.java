package com.dhangofa.networktoggle.telephony;

import android.os.Build;
import android.telephony.SubscriptionManager;

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.TargetSim;

public final class SimResolver {
    public static final int INVALID_SLOT_INDEX = -1;
    public static final int INVALID_SUB_ID = -1;

    private final AppPreferences appPreferences;

    public SimResolver(AppPreferences appPreferences) {
        this.appPreferences = appPreferences;
    }

    public int resolveTargetSlotIndex(ExecutionMode executionMode) {
        TargetSim targetSim = appPreferences.getTargetSim();

        if (!targetSim.isAuto()) {
            appPreferences.setAutoSimError(false);
            return targetSim.getManualSlotIndex();
        }

        return resolveAutoSlotIndex(executionMode);
    }

    public int resolveTargetSubId(ExecutionMode executionMode) {
        TargetSim targetSim = appPreferences.getTargetSim();

        if (targetSim == TargetSim.AUTO) {
            int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            return isValidSubId(subId) ? subId : INVALID_SUB_ID;
        }

        return resolveSubIdFromDumpsys(
                executionMode,
                targetSim.getManualSlotIndex()
        );
    }

    public boolean isValidSlotIndex(int slotIndex) {
        return slotIndex == 0 || slotIndex == 1;
    }

    public boolean isValidSubId(int subId) {
        return subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && subId != INVALID_SUB_ID;
    }

    private int resolveAutoSlotIndex(ExecutionMode executionMode) {
        int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();

        if (!isValidSubId(dataSubId)) {
            appPreferences.setAutoSimError(true);
            return INVALID_SLOT_INDEX;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                int slotIndex = SubscriptionManager.getSlotIndex(dataSubId);
                if (isValidSlotIndex(slotIndex)) {
                    appPreferences.setAutoSimError(false);
                    return slotIndex;
                }
            } catch (Exception ignored) {
            }
        }

        int slotIndex = resolveSlotIndexFromDumpsys(executionMode, dataSubId);
        if (isValidSlotIndex(slotIndex)) {
            appPreferences.setAutoSimError(false);
            return slotIndex;
        }

        appPreferences.setAutoSimError(true);
        return INVALID_SLOT_INDEX;
    }

    private int resolveSlotIndexFromDumpsys(
            ExecutionMode executionMode,
            int dataSubId
    ) {
        String command = "dumpsys isub | grep -E \"\\{id=" + dataSubId
                + "([^0-9]| )\" | head -n 1 "
                + "| grep -o -E \"simSlotIndex=[0-9]+\" "
                + "| cut -d '=' -f 2";

        CommandResult result = execute(executionMode, command);
        Integer slotIndex = result.isSuccess()
                ? ShellValueParser.extractFirstInt(result.getStdout())
                : null;

        return slotIndex != null && isValidSlotIndex(slotIndex)
                ? slotIndex
                : INVALID_SLOT_INDEX;
    }

    private int resolveSubIdFromDumpsys(
            ExecutionMode executionMode,
            int slotIndex
    ) {
        String command = "dumpsys isub | grep -E \"simSlotIndex=" + slotIndex
                + "([^0-9]| )\" | head -n 1 "
                + "| grep -o -E \"\\{id=[0-9]+\" "
                + "| cut -d '=' -f 2";

        CommandResult result = execute(executionMode, command);
        Integer subId = result.isSuccess()
                ? ShellValueParser.extractFirstInt(result.getStdout())
                : null;

        return subId != null && subId > 0
                ? subId
                : INVALID_SUB_ID;
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
