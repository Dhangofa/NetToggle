package com.dhangofa.networktoggle.telephony;

import java.util.Arrays;
import java.util.List;

public final class LteAndAboveCarrierRegistry {
    
    // REGION: INDIA
    // Jio is purely 4G/5G. It has no 2G/3G infrastructure.
    private static final List<String> INDIA_LTE_AND_ABOVE = Arrays.asList(
            "jio", "ind-jio", "jio 4g", "jio true5g"
    );

    // REGION: USA (Examples)
    private static final List<String> USA_LTE_AND_ABOVE = Arrays.asList(
            "verizon", "vzw"
    );

    private LteAndAboveCarrierRegistry() {
        // Private constructor
    }

    /**
     * Stage 4: Checks if the carrier name belongs to a known LTE/5G only network.
     */
    public static boolean isLteAndAboveOnly(String carrierName) {
        if (carrierName == null || carrierName.trim().isEmpty()) {
            return false;
        }
        
        String normalized = carrierName.toLowerCase().trim();

        for (String name : INDIA_LTE_AND_ABOVE) {
            if (normalized.equals(name) || normalized.contains(name)) return true;
        }
        
        for (String name : USA_LTE_AND_ABOVE) {
            if (normalized.equals(name) || normalized.contains(name)) return true;
        }

        return false;
    }
}
