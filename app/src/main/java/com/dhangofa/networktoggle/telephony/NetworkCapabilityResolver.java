package com.dhangofa.networktoggle.telephony;

/**
 * Resolves what network types (5G, 4G, etc.) the phone actually supports.
 * It parses raw baseband properties to decide which toggle options should be available in the UI.
 */

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

    private static final class CarrierCapabilityResult {
        final NetworkCapabilities capabilities;
        final boolean cacheable;
    
        CarrierCapabilityResult(NetworkCapabilities capabilities, boolean cacheable) {
            this.capabilities = capabilities;
            this.cacheable = cacheable;
        }
    }

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

        if (cachedSubId == subId && cachedCaps != null) {
            if (LteAndAboveCarrierRegistry.isLteAndAboveOnly(carrierName)) {
                return new NetworkCapabilities(false, false, cachedCaps.supports4g, cachedCaps.supports5g);
            }
            return cachedCaps;
        }

        // Invalidate cache and fetch
        appPreferences.invalidateSlotCache(slotIndex);
        
        // Stage 1 & 2: Global Device/OS Capabilities
        NetworkCapabilities deviceCaps = fetchDeviceCapabilities(mode);
        
        // Stage 3 & 4: Slot-Specific Carrier Capabilities (Pass carrierName directly)
        CarrierCapabilityResult carrierResult = fetchCarrierCapabilities(mode, slotIndex, carrierName);
        NetworkCapabilities carrierCaps = carrierResult.capabilities;

        // Combine
        NetworkCapabilities finalCaps = new NetworkCapabilities(
                deviceCaps.supports2g && carrierCaps.supports2g,
                deviceCaps.supports3g && carrierCaps.supports3g,
                deviceCaps.supports4g && carrierCaps.supports4g,
                deviceCaps.supports5g && carrierCaps.supports5g
        );

        if (carrierResult.cacheable) {
            appPreferences.saveSlotCapabilities(slotIndex, subId, finalCaps);
        }
        return finalCaps;
    }

    private NetworkCapabilities fetchDeviceCapabilities(ExecutionMode mode) {
        NetworkCapabilities cachedDevice = appPreferences.getDeviceCapabilities();
    
        if (cachedDevice != null) {
            return cachedDevice;
        }
    
        // 5G capability detection starts from Android 11.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            NetworkCapabilities legacyCapabilities = new NetworkCapabilities(true, true, true, false);
    
            appPreferences.saveDeviceCapabilities(legacyCapabilities);
            return legacyCapabilities;
        }
    
        CommandExecutor executor = CommandExecutorFactory.forMode(mode);
        if (executor == null) {
            return NetworkCapabilities.assumeAll();
        }
        CommandResult result = executor.execute( "getprop ro.telephony.default_network" );
        if (!result.isSuccess()) {
            return NetworkCapabilities.assumeAll();
        }
        String output = result.getStdout();
    
        // Empty output does not prove that 5G is unsupported.
        if (output == null || output.trim().isEmpty()) {
            return NetworkCapabilities.assumeAll();
        }
    
        boolean foundValidProfile = false;
        boolean supports5g = false;
        String[] values = output.trim().split(",");
        for (String value : values) {
            Integer parsed = ShellValueParser.extractFirstInt(value);
            if (parsed == null) {
                continue;
            }
            foundValidProfile = true;
            // Profile 23 and higher means 5G or newer capability.
            if (parsed >= 23) {
                supports5g = true;
                break;
            }
        }
    
        // Do not cache a result when the property had no numeric profile.
        if (!foundValidProfile) {
            return NetworkCapabilities.assumeAll();
        }
            
        NetworkCapabilities deviceCapabilities = new NetworkCapabilities(true, true, true, supports5g);
        appPreferences.saveDeviceCapabilities(deviceCapabilities);
        return deviceCapabilities;
    }

    private CarrierCapabilityResult fetchCarrierCapabilities(
            ExecutionMode mode,
            int slotIndex,
            String carrierName
    ) {
        CommandExecutor executor = CommandExecutorFactory.forMode(mode);
    
        if (executor == null) {
            return new CarrierCapabilityResult(
                    NetworkCapabilities.assumeAll(),
                    false
            );
        }
    
        // Stage 3: Carrier Config XML verification.
        String command =
                "dumpsys carrier_config | grep -E " +
                "'Phone Id|" +
                "hide_enable_2g_bool|" +
                "carrier_supports_2g_bool|" +
                "hide_enable_3g_bool|" +
                "carrier_supports_3g_bool|" +
                "carrier_nr_availabilities_int_array'";
    
        CommandResult result = executor.execute(command);
    
        boolean supports2g = true;
        boolean supports3g = true;
        boolean supports5g = true;
        boolean foundCarrierSetting = false;
    
        if (result.isSuccess() && !result.getStdout().trim().isEmpty()) {
            String[] lines = result.getStdout().split("\\n");
            boolean inTargetSlot = false;
    
            for (String line : lines) {
                String trimmed = line.trim();
    
                if (trimmed.startsWith("Phone Id =")) {
                    Integer currentSlot = ShellValueParser.extractFirstInt(trimmed);
                    inTargetSlot = currentSlot != null && currentSlot == slotIndex;
                    continue;
                }
    
                if (!inTargetSlot) {
                    continue;
                }
    
                if (trimmed.startsWith("hide_enable_2g_bool =")) {
                    foundCarrierSetting = true;
    
                    if (trimmed.endsWith("true")) {
                        supports2g = false;
                    }
                }
    
                if (trimmed.startsWith("carrier_supports_2g_bool =")) {
                    foundCarrierSetting = true;
    
                    if (trimmed.endsWith("false")) {
                        supports2g = false;
                    }
                }
    
                if (trimmed.startsWith("hide_enable_3g_bool =")) {
                    foundCarrierSetting = true;
    
                    if (trimmed.endsWith("true")) {
                        supports3g = false;
                    }
                }
    
                if (trimmed.startsWith("carrier_supports_3g_bool =")) {
                    foundCarrierSetting = true;
    
                    if (trimmed.endsWith("false")) {
                        supports3g = false;
                    }
                }
    
                if (trimmed.startsWith("carrier_nr_availabilities_int_array =")) {
                    foundCarrierSetting = true;
    
                    if (trimmed.endsWith("[]")) {
                        supports5g = false;
                    }
                }
            }
        }
    
        // Stage 4: maintained real-world carrier restrictions.
        boolean registryMatched =
                LteAndAboveCarrierRegistry.isLteAndAboveOnly(carrierName);
    
        if (registryMatched) {
            supports2g = false;
            supports3g = false;
        }
    
        NetworkCapabilities capabilities =
                new NetworkCapabilities(
                        supports2g,
                        supports3g,
                        true,
                        supports5g
                );
    
        boolean cacheable = foundCarrierSetting || registryMatched;
    
        return new CarrierCapabilityResult(capabilities, cacheable);
    }
}
