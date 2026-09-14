package com.dhangofa.networktoggle.model;

/**
 * Enum representing how the app to execute commands (Root, Shizuku, or None/Not setup yet).
 */

public enum ExecutionMode {
    NONE(0),
    ROOT(1),
    SHIZUKU(2);

    private final int value;

    ExecutionMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ExecutionMode fromValue(int value) {
        for (ExecutionMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        return NONE;
    }
}
