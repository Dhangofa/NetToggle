package com.dhangofa.networktoggle.automation;

public class AutomationResult {
    public final boolean success;
    public final boolean isPartial;
    public final String errorMessage;

    public AutomationResult(boolean success, boolean isPartial, String errorMessage) {
        this.success = success;
        this.isPartial = isPartial;
        this.errorMessage = errorMessage;
    }
}
