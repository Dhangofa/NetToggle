package com.dhangofa.networktoggle.ui;

/** Synchronizes the active modem mode when the Quick Tile Cycle changes. */
import android.content.Context;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.telephony.NetworkModeController;
import com.dhangofa.networktoggle.telephony.NetworkModeReader;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.util.AppExecutors;
import java.util.List;

public final class TileCycleSyncController implements TileCycleUiController.OnCycleChangedListener {
    
    private final Context context;
    private final AppPreferences prefs;
    private final SimResolver simResolver;
    private final NetworkModeController controller;
    
    public TileCycleSyncController(Context context, AppPreferences prefs, SimResolver simResolver, NetworkModeController controller) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.simResolver = simResolver;
        this.controller = controller;
    }
    
    @Override 
    public void onCycleChanged(List<NetworkMode> newCycle) {
        AppExecutors.executeTelephony(() -> sync(newCycle));
    }
    
    private void sync(List<NetworkMode> newCycle) {
        NetworkMode currentMode = prefs.getCachedNetworkMode();
        
        // If cache is wiped (e.g. from SIM switch), but execution is allowed, read it directly once
        if (currentMode == NetworkMode.UNKNOWN && prefs.getExecutionMode() != ExecutionMode.NONE) {
            currentMode = new NetworkModeReader(context, prefs, simResolver).readCurrentMode();
            if (currentMode != NetworkMode.UNKNOWN) {
                prefs.setLastNetworkCheckTimestamp(System.currentTimeMillis());
                prefs.setCachedNetworkMode(currentMode);
            }
        }
        
        if (currentMode != NetworkMode.UNKNOWN && !newCycle.contains(currentMode)) {
            NetworkMode fallbackMode = newCycle.get(0);
            
            // Attempt to sync the network state silently
            CommandResult result = controller.apply(fallbackMode, prefs.getExecutionMode());
            if (result.isSuccess()) {
                prefs.setCachedNetworkMode(fallbackMode);
                prefs.setLastNetworkCheckTimestamp(System.currentTimeMillis());
            }
        } else if (currentMode != NetworkMode.UNKNOWN) {
            // It's in the cycle, ensure it is cached so UI shows the actual active state
            prefs.setCachedNetworkMode(currentMode);
        }
    }
}
