package com.dhangofa.networktoggle.telephony;

/**
 * A registry of carriers that require LTE as a minimum.
 * Prevents users from dropping to 3G/2G on networks that no longer support it (like Verizon).
 */

import java.util.Arrays;
import java.util.List;

public final class LteAndAboveCarrierRegistry {

    // REGION: INDIA
    private static final List<String> INDIA_LTE_AND_ABOVE = Arrays.asList(
            "jio", "ind-jio", "jio 4g", "jio true5g"
    );

    // REGION: USA
    // Verizon, AT&T, and T-Mobile have fully shut down 2G/3G networks as of 2026. Dish is 5G only.
    private static final List<String> USA_LTE_AND_ABOVE = Arrays.asList(
            "verizon", "vzw", "at&t", "att", "t-mobile", "tmobile", "dish", "dish wireless"
    );

    // REGION: JAPAN
    // Docomo, KDDI (au), SoftBank, and Rakuten have shut down their 3G networks.
    private static final List<String> JAPAN_LTE_AND_ABOVE = Arrays.asList(
            "docomo", "ntt docomo", "au", "kddi", "softbank", "rakuten", "rakuten mobile"
    );

    // REGION: AUSTRALIA
    // Telstra, Optus, and Vodafone Australia (TPG) have fully shut down 3G.
    private static final List<String> AUSTRALIA_LTE_AND_ABOVE = Arrays.asList(
            "telstra", "optus", "vodafone au", "vodafone australia", "tpg"
    );

    // REGION: TAIWAN
    // Chunghwa Telecom, Taiwan Mobile, and Far EasTone fully shut down 3G in mid-2024.
    private static final List<String> TAIWAN_LTE_AND_ABOVE = Arrays.asList(
            "chunghwa", "chunghwa telecom", "taiwan mobile", "far eastone", "fet"
    );

    // REGION: SINGAPORE
    // Singtel, StarHub, and M1 fully shut down 3G in 2024.
    private static final List<String> SINGAPORE_LTE_AND_ABOVE = Arrays.asList(
            "singtel", "starhub", "m1"
    );

    // REGION: SOUTH KOREA
    // LG U+ never had 3G and shut down 2G. (SK Telecom and KT still operate 3G as of 2026).
    private static final List<String> SOUTHKOREA_LTE_AND_ABOVE = Arrays.asList(
            "lg u+", "lgu+", "lg uplus", "uplus"
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
        return matchesCarrier(normalized, INDIA_LTE_AND_ABOVE) ||
               matchesCarrier(normalized, USA_LTE_AND_ABOVE) ||
               matchesCarrier(normalized, JAPAN_LTE_AND_ABOVE) ||
               matchesCarrier(normalized, AUSTRALIA_LTE_AND_ABOVE) ||
               matchesCarrier(normalized, TAIWAN_LTE_AND_ABOVE) ||
               matchesCarrier(normalized, SINGAPORE_LTE_AND_ABOVE) ||
               matchesCarrier(normalized, SOUTHKOREA_LTE_AND_ABOVE);
    }
    private static boolean matchesCarrier(String carrierName, List<String> carrierAliases) {
        for (String alias : carrierAliases) {
            String normalizedAlias = alias.toLowerCase().trim();
    
            if (carrierName.equals(normalizedAlias)) {
                return true;
            }
            // Short aliases must match exactly.
            if (normalizedAlias.length() <= 3) {
                continue;
            }
            // Longer aliases can match inside an extended carrier label.
            if (containsWholePhrase(carrierName, normalizedAlias)) {
                return true;
            }
        }
    
        return false;
    }
    
    private static boolean containsWholePhrase(String carrierName, String alias) {
        String normalizedCarrier = normalizeSeparators(carrierName);
        String normalizedAlias = normalizeSeparators(alias);
    
        return (" " + normalizedCarrier + " ")
                .contains(" " + normalizedAlias + " ");
    }
    
    private static String normalizeSeparators(String value) {
        return value.replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
