package com.dhangofa.networktoggle.telephony;

import android.content.Context;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

final class LegacyNetworkModeController {

    private final Context context;
    private final SimResolver simResolver;

    LegacyNetworkModeController(Context context, SimResolver simResolver) {
        this.context = context.getApplicationContext();
        this.simResolver = simResolver;
    }

    CommandResult apply(NetworkMode networkMode, ExecutionMode executionMode) {
        if (networkMode == null || networkMode == NetworkMode.UNKNOWN
                || networkMode.getLegacyMode() < 0) {
            return CommandResult.failed("", "Invalid legacy network mode selected.");
        }

        if (isAirplaneModeEnabled()) {
            return CommandResult.failed("",
                    "Disable Airplane mode before changing the network mode.");
        }

        SimResolver.SimInfo simInfo = simResolver.resolveTargetSimInfo(executionMode);
        if (simInfo == null || !simResolver.isValidSlotIndex(simInfo.slotIndex)
                || !simResolver.isValidSubId(simInfo.subId)) {
            return CommandResult.failed("", "Unable to resolve the target SIM.");
        }

        Integer[] currentModes = readCombinedModes();
        int requiredEntries = getRequiredCombinedEntries(simInfo.slotIndex);
        if (currentModes.length < requiredEntries) {
            return CommandResult.failed("",
                    "Unable to preserve the current mode of every SIM slot.");
        }

        for (int i = 0; i < requiredEntries; i++) {
            if (currentModes[i] == null) {
                return CommandResult.failed("",
                        "Unable to read the current mode for SIM slot " + (i + 1) + ".");
            }
        }

        currentModes[simInfo.slotIndex] = networkMode.getLegacyMode();
        String combinedModes = buildCombinedModes(currentModes, requiredEntries);
        String originalRadios = readAirplaneModeRadios();
        String restoreRadios = originalRadios == null
                ? "settings delete global airplane_mode_radios"
                : "settings put global airplane_mode_radios " + shellEscape(originalRadios);

        String command = buildTransactionCommand(
                simInfo.subId,
                networkMode.getLegacyMode(),
                combinedModes,
                restoreRadios
        );

        CommandExecutor executor = CommandExecutorFactory.forMode(executionMode);
        if (executor == null) {
            return CommandResult.failed(command, "No execution mode selected.");
        }

        CommandResult result = executor.execute(command);
        if (!result.isSuccess()) return result;

        return verifyResult(command, simInfo, networkMode.getLegacyMode(),
                originalRadios, requiredEntries);
    }

    private Integer[] readCombinedModes() {
        String value = Settings.Global.getString(
                context.getContentResolver(), "preferred_network_mode");
        if (value == null || value.trim().isEmpty()
                || value.trim().equalsIgnoreCase("null")) {
            return new Integer[0];
        }

        String[] parts = value.trim().split(",", -1);
        Integer[] modes = new Integer[parts.length];
        for (int i = 0; i < parts.length; i++) {
            modes[i] = ShellValueParser.extractFirstInt(parts[i]);
        }
        return modes;
    }

    private int getRequiredCombinedEntries(int targetSlot) {
        TelephonyManager manager = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        int phoneCount = manager == null ? 1 : manager.getPhoneCount();
        return Math.max(targetSlot + 1, Math.min(phoneCount, 2));
    }

    private String buildCombinedModes(Integer[] modes, int count) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) value.append(',');
            value.append(modes[i]);
        }
        return value.toString();
    }

    private boolean isAirplaneModeEnabled() {
        return Settings.Global.getInt(
                context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    private String readAirplaneModeRadios() {
        return Settings.Global.getString(
                context.getContentResolver(), Settings.Global.AIRPLANE_MODE_RADIOS);
    }

    private String buildTransactionCommand(int subId, int requestedMode,
                                           String combinedModes, String restoreRadios) {
        return "cleanup() { "
                + "cmd connectivity airplane-mode disable >/dev/null 2>&1; "
                + restoreRadios + " >/dev/null 2>&1; "
                + "}; "
                + "trap cleanup EXIT INT TERM; "
                + "settings put global airplane_mode_radios cell || exit 20; "
                + "settings put global preferred_network_mode" + subId + " "
                + requestedMode + " || exit 21; "
                + "settings put global preferred_network_mode " + combinedModes
                + " || exit 22; "
                + "cmd connectivity airplane-mode enable || exit 23; "
                + "sleep 1; "
                + "cmd connectivity airplane-mode disable || exit 24; "
                + restoreRadios + " || exit 25; "
                + "trap - EXIT INT TERM; "
                + "exit 0";
    }

    private CommandResult verifyResult(String command, SimResolver.SimInfo simInfo,
                                       int requestedMode, String originalRadios,
                                       int requiredEntries) {
        String subValue = Settings.Global.getString(
                context.getContentResolver(),
                "preferred_network_mode" + simInfo.subId);
        Integer finalSubMode = ShellValueParser.extractFirstInt(subValue);
        Integer[] finalCombinedModes = readCombinedModes();
        String finalRadios = readAirplaneModeRadios();

        if (isAirplaneModeEnabled()) {
            return CommandResult.failed(command,
                    "Legacy mode was written, but Airplane mode is still enabled.");
        }
        if (finalSubMode == null || finalSubMode != requestedMode) {
            return CommandResult.failed(command,
                    "Legacy per-subscription mode verification failed.");
        }
        if (finalCombinedModes.length < requiredEntries
                || finalCombinedModes[simInfo.slotIndex] == null
                || finalCombinedModes[simInfo.slotIndex] != requestedMode) {
            return CommandResult.failed(command,
                    "Legacy combined mode verification failed.");
        }
        if (originalRadios == null ? finalRadios != null
                : !originalRadios.equals(finalRadios)) {
            return CommandResult.failed(command,
                    "Original Airplane mode radio configuration was not restored.");
        }

        return CommandResult.completed(command, 0,
                "Legacy network mode applied and verified.", "");
    }

    private String shellEscape(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
