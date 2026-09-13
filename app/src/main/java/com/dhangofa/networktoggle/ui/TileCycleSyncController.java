package com.dhangofa.networktoggle.ui;

/** Synchronizes the active modem mode when the Quick Tile Cycle changes. */
import android.content.ComponentName;
import android.content.Context;
import android.service.quicksettings.TileService;
import com.dhangofa.networktoggle.NetworkTileService;
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

            if (prefs.getTargetSim() == com.dhangofa.networktoggle.model.TargetSim.BOTH) {
                SimResolver.SimInfo info1 = simResolver.resolveTargetSimInfo(prefs.getExecutionMode(), com.dhangofa.networktoggle.model.TargetSim.SIM_1);
                SimResolver.SimInfo info2 = simResolver.resolveTargetSimInfo(prefs.getExecutionMode(), com.dhangofa.networktoggle.model.TargetSim.SIM_2);
                if (info1 == null || info2 == null) {
                    prefs.setLastError("", -1, "", "One or both SIMs are not available for sync.", "TileCycle Sync Both failed: SIM unavailable");
                    prefs.setTileErrorState(AppPreferences.TILE_ERROR_CMD);
                    TileService.requestListeningState(context, new ComponentName(context, NetworkTileService.class));
                    return;
                }

                boolean success = false;
                boolean isPartial = false;
                CommandResult r1 = CommandResult.failed("", "SIM 1 was not attempted.");
                CommandResult r2 = CommandResult.failed("", "SIM 2 was not attempted.");
                try {
                    simResolver.setOverrideTargetSim(com.dhangofa.networktoggle.model.TargetSim.SIM_1);
                    CommandResult res1 = controller.apply(fallbackMode, prefs.getExecutionMode());
                    if (res1 != null) {
                        r1 = res1;
                    }

                    simResolver.setOverrideTargetSim(com.dhangofa.networktoggle.model.TargetSim.SIM_2);
                    CommandResult res2 = controller.apply(fallbackMode, prefs.getExecutionMode());
                    if (res2 != null) {
                        r2 = res2;
                    }

                    if (r1.isSuccess() && r2.isSuccess()) {
                        success = true;
                    } else if (r1.isSuccess() || r2.isSuccess()) {
                        isPartial = true;
                    }
                } catch (Throwable t) {
                    if (!r1.isSuccess()) {
                        r1 = CommandResult.failed("", "Exception applying mode to SIM 1: " + t.getMessage());
                    } else {
                        r2 = CommandResult.failed("", "Exception applying mode to SIM 2: " + t.getMessage());
                    }
                } finally {
                    simResolver.setOverrideTargetSim(null);
                }

                if (success) {
                    prefs.setCachedNetworkMode(fallbackMode);
                    prefs.setLastNetworkCheckTimestamp(System.currentTimeMillis());
                    prefs.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                } else if (isPartial) {
                    prefs.setCachedNetworkMode(NetworkMode.UNKNOWN);
                    prefs.setLastNetworkCheckTimestamp(System.currentTimeMillis());
                    CommandResult failed = r1.isSuccess() ? r2 : r1;
                    prefs.setLastError(failed.getCommand(), failed.getExitCode(), failed.getStdout(), failed.getStderr(), "TileCycle Sync partial failure");
                    prefs.setTileErrorState(AppPreferences.TILE_ERROR_CMD);
                } else {
                    prefs.setLastError(r1.getCommand(), r1.getExitCode(), r1.getStdout(), r1.getStderr(), "TileCycle Sync Both failed");
                    prefs.setTileErrorState(AppPreferences.TILE_ERROR_CMD);
                }
                TileService.requestListeningState(context, new ComponentName(context, NetworkTileService.class));
            } else {
                CommandResult result = CommandResult.failed("", "Execution not attempted");
                try {
                    CommandResult res = controller.apply(fallbackMode, prefs.getExecutionMode());
                    if (res != null) {
                        result = res;
                    }
                } catch (Throwable t) {
                    result = CommandResult.failed("", "Exception applying mode: " + t.getMessage());
                }

                if (result.isSuccess()) {
                    prefs.setCachedNetworkMode(fallbackMode);
                    prefs.setLastNetworkCheckTimestamp(System.currentTimeMillis());
                    prefs.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                } else {
                    prefs.setLastError(result.getCommand(), result.getExitCode(), result.getStdout(), result.getStderr(), "TileCycle Sync failed");
                    prefs.setTileErrorState(AppPreferences.TILE_ERROR_CMD);
                }
                TileService.requestListeningState(context, new ComponentName(context, NetworkTileService.class));
            }
        } else if (currentMode != NetworkMode.UNKNOWN) {
            // It's in the cycle, ensure it is cached so UI shows the actual active state
            prefs.setCachedNetworkMode(currentMode);
        }
    }
}
