package com.dhangofa.networktoggle.model;

/**
 * This enum maps the different network types (like 5G, 4G, 3G) to their internal Android telephony IDs.
 * Translates requested modes into the raw values the `cmd phone` commands expect.
 */

public enum NetworkMode {
    UNKNOWN(0, "Unknown", "Tap to Set 4G", "?", null, -1),
    FOUR_G_ONLY(1, "4G Only", "4G Only", "4G", "1000000000000", 11),
    FIVE_G_ONLY(2, "5G Only", "5G Only", "5G", "10000000000000000000", 23),
    PREFERRED_5G(3, "Preferred 5G", "Pref 5G", "P5G", "11011111101111111111", 33),
    PREFERRED_4G(4, "Preferred 4G", "Pref 4G", "P4G", "1011111101111111111", 9),
    PREFERRED_3G(5, "Preferred 3G", "Pref 3G", "P3G", "11110101111111111", 0),
    TWO_G_ONLY(6, "2G Only", "2G Only", "2G", "1000000000000011", 1);

    private final int stateValue;
    private final String displayName;
    private final String tileLabel;
    private final String iconText;
    private final String binaryMask;
    private final int legacyMode;

    NetworkMode(int stateValue, String displayName, String tileLabel,
                String iconText, String binaryMask, int legacyMode) {
        this.stateValue = stateValue;
        this.displayName = displayName;
        this.tileLabel = tileLabel;
        this.iconText = iconText;
        this.binaryMask = binaryMask;
        this.legacyMode = legacyMode;
    }

    public int getStateValue() { return stateValue; }
    public String getDisplayName() { return displayName; }
    public String getTileLabel() { return tileLabel; }
    public String getIconText() { return iconText; }
    public String getBinaryMask() { return binaryMask; }
    public int getLegacyMode() { return legacyMode; }

    public static NetworkMode fromStateValue(int value) {
        for (NetworkMode mode : values()) {
            if (mode.stateValue == value) return mode;
        }
        return UNKNOWN;
    }

    public static NetworkMode nextInDefaultCycle(NetworkMode current) {
        switch (current) {
            case FOUR_G_ONLY: return FIVE_G_ONLY;
            case FIVE_G_ONLY: return PREFERRED_5G;
            case PREFERRED_5G: return PREFERRED_4G;
            case PREFERRED_4G:
            case PREFERRED_3G:
            case TWO_G_ONLY:
            case UNKNOWN:
            default: return FOUR_G_ONLY;
        }
    }

    public static NetworkMode fromLegacyMode(Integer legacyMode) {
        if (legacyMode == null) return UNKNOWN;
        switch (legacyMode) {
            case 1:
            case 16:
                return TWO_G_ONLY;
            case 0:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 13:
            case 14:
            case 18:
            case 21: // Global 3G (CDMA+EVDO+GSM+WCDMA)
                return PREFERRED_3G;
            case 11: return FOUR_G_ONLY;
            case 23: return FIVE_G_ONLY;
            case 33: return PREFERRED_5G;
            case 8:
            case 9:
            case 10:
            case 12:
            case 15:
            case 17:
            case 19:
            case 20:
            case 22: // Global 4G (LTE+CDMA+EVDO+GSM+WCDMA)
                return PREFERRED_4G;
            default:
                if (legacyMode >= 24 && legacyMode <= 32) return PREFERRED_5G;
                return UNKNOWN;
        }
    }
}
