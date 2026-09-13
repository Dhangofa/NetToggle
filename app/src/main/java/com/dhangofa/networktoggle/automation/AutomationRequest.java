package com.dhangofa.networktoggle.automation;

import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;

public class AutomationRequest {
    public final NetworkMode mode;
    public final TargetSim target;
    public final boolean external;
    public final String source;
    public final boolean updatePreferredMode;

    public AutomationRequest(NetworkMode mode, TargetSim target, boolean external, String source) {
        this(mode, target, external, source, true);
    }

    public AutomationRequest(NetworkMode mode, TargetSim target, boolean external, String source, boolean updatePreferredMode) {
        this.mode = mode;
        this.target = target;
        this.external = external;
        this.source = source;
        this.updatePreferredMode = updatePreferredMode;
    }
}
