package com.dhangofa.networktoggle.automation;

import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;

public class AutomationResult {
    public final boolean success;
    public final boolean isPartial;
    public final String errorMessage;
    public final NetworkMode requestedMode;
    public final TargetSim requestedTarget;
    public final boolean sim1Success;
    public final boolean sim2Success;

    public AutomationResult(boolean success, boolean isPartial, String errorMessage) {
        this(success, isPartial, errorMessage, null, null, success, success);
    }

    public AutomationResult(boolean success, boolean isPartial, String errorMessage,
                            NetworkMode requestedMode, TargetSim requestedTarget,
                            boolean sim1Success, boolean sim2Success) {
        this.success = success;
        this.isPartial = isPartial;
        this.errorMessage = errorMessage;
        this.requestedMode = requestedMode;
        this.requestedTarget = requestedTarget;
        this.sim1Success = sim1Success;
        this.sim2Success = sim2Success;
    }
}
