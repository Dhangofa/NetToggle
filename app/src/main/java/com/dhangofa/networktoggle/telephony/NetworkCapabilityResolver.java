package com.dhangofa.networktoggle.telephony;

import android.os.Build;

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.config.AppPreferences.NetworkCapabilities;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;

public final class NetworkCapabilityResolver {
    private final AppPreferences appPreferences;
    private final SimResolver simResolver;

    public NetworkCapabilityResolver(AppPreferences appPreferences, SimResolver simResolver) {
        this.appPreferences = appPreferences;
        this.simResolver = simResolver;
    }

    public NetworkCapabilities getCapabilities(ExecutionMode mode) {
        if (mode == ExecutionMode.NONE) return NetworkCapabilities.assumeAll();

        // ONE SINGLE CALL to get slotIndex, subId, and carrierName!
        SimResolver.SimInfo simInfo = simResolver.resolveTargetSimInfo(mode);

        if (simInfo == null || !simResolver.isValidSlotIndex(simInfo.slotIndex) || !simResolver.isValidSubId(simInfo.subId)) {
            return NetworkCapabilities.assumeAll();
        }

        int slotIndex = simInfo.slotIndex;
        int subId = simInfo.subId;
        String carrierName = simInfo.carrierName;

        // Check Cache
        int cachedSubId = appPreferences.getCachedSubIdForSlot(slotIndex);
        NetworkCapabilities cachedCaps = appPreferences.getSlotCapabilities(slotIndex);

        if (cachedSubId == subId && cachedCaps != null) return cachedCaps;

        // Invalidate cache and fetch
        appPreferences.invalidateSlotCache(slotIndex);
        
        // Stage 1 & 2: Global Device/OS Capabilities
        NetworkCapabilities deviceCaps = fetchDeviceCapabilities(mode);
        
        // Stage 3 & 4: Slot-Specific Carrier Capabilities (Pass carrierName directly)
        NetworkCapabilities carrierCaps = fetchCarrierCapabilities(mode, slotIndex, carrierName);

        // Combine
        NetworkCapabilities finalCaps = new NetworkCapabilities(
                deviceCaps.supports2g && carrierCaps.supports2g,
                deviceCaps.supports3g && carrierCaps.supports3g,
                deviceCaps.supports4g && carrierCaps.supports4g,
                deviceCaps.supports5g && carrierCaps.supports5g
        );

        appPreferences.saveSlotCapabilities(slotIndex, subId, finalCaps);
        return finalCaps;
    }

    private NetworkCapabilities fetchDeviceCapabilities(ExecutionMode mode) {
        NetworkCapabilities cachedDevice = appPreferences.getDeviceCapabilities();
        if (cachedDevice != null) return cachedDevice;

        boolean supports5g = false;

        // Stage 1: Android Version Check (Android 11 / API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CommandExecutor executor = CommandExecutorFactory.forMode(mode);
            if (executor != null) {
                // Stage 2: Hardware Ceiling Check
                CommandResult result = executor.execute("getprop ro.telephony.default_network");
                if (result.isSuccess() && !result.getStdout().trim().isEmpty()) {
                    String[] values = result.getStdout().trim().split(",");
                    
                    // Check if ANY value globally supports 5G (>= 23)
                    for (String val : values) {
                        Integer parsed = ShellValueParser.extractFirstInt(val);
                        if (parsed != null && parsed >= 23) {
                            supports5g = true;
                            break;
                        }
                    }
                }
            }
        }

        NetworkCapabilities deviceCaps = new NetworkCapabilities(true, true, true, supports5g);
        appPreferences.saveDeviceCapabilities(deviceCaps);
        return deviceCaps;
    }

    private NetworkCapabilities fetchCarrierCapabilities(ExecutionMode mode, int slotIndex, String carrierName) {
        CommandExecutor executor = CommandExecutorFactory.forMode(mode);
        if (executor == null) return NetworkCapabilities.assumeAll();

        // Stage 3: Carrier Config XML Verification
        String command = "dumpsys carrier_config | grep -E 'Phone Id|hide_enable_2g_bool|carrier_supports_2g_bool|hide_enable_3g_bool|carrier_supports_3g_bool|carrier_nr_availabilities_int_array'";
        CommandResult result = executor.execute(command);
        
        boolean supports2g = true;
        boolean supports3g = true;
        boolean supports5g = true;

        if (result.isSuccess() && !result.getStdout().trim().isEmpty()) {
            String[] lines = result.getStdout().split("\\n");
            boolean inTargetSlot = false;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Phone Id =")) {
                    Integer currentSlot = ShellValueParser.extractFirstInt(trimmed);
                    inTargetSlot = (currentSlot != null && currentSlot == slotIndex);
                } else if (inTargetSlot) {
                    if (trimmed.contains("hide_enable_2g_bool = true") || trimmed.contains("carrier_supports_2g_bool = false")) {
                        supports2g = false;
                    }
                    if (trimmed.contains("hide_enable_3g_bool = true") || trimmed.contains("carrier_supports_3g_bool = false")) {
                        supports3g = false;
                    }
                    if (trimmed.startsWith("carrier_nr_availabilities_int_array = []")) {
                        supports5g = false;
                    }
                }
            }
        }

        // Stage 4: Smart Blocklist Check
        if (LteAndAboveCarrierRegistry.isLteAndAboveOnly(carrierName)) {
            supports2g = false;
            supports3g = false;
        }

        return new NetworkCapabilities(supports2g, supports3g, true, supports5g);
    }
}
