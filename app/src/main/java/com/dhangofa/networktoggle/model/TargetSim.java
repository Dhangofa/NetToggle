package com.dhangofa.networktoggle.model;

/**
 * Enum representing which SIM slot the app should target (SIM 1, SIM 2, or Auto).
 */

public enum TargetSim {
    AUTO(0, -1),
    SIM_1(1, 0),
    SIM_2(2, 1),
    BOTH(3, -1);

    private final int value;
    private final int manualSlotIndex;

    TargetSim(int value, int manualSlotIndex) {
        this.value = value;
        this.manualSlotIndex = manualSlotIndex;
    }

    public int getValue() {
        return value;
    }

    public boolean isAuto() {
        return this == AUTO;
    }

    public int getManualSlotIndex() {
        return manualSlotIndex;
    }

    public static TargetSim fromValue(int value) {
        for (TargetSim target : values()) {
            if (target.value == value) {
                return target;
            }
        }
        return AUTO;
    }
}
