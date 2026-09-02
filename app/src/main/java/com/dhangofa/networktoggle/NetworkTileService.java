package com.dhangofa.networktoggle;

/**
 * Quick Settings (QS) Tile Service.
 * This handles the actual toggle button that sits in the Android notification shade.
 * When tapped, it reads the current network mode, figures out the next mode based on the configured cycle,
 * and executes the change using the chosen backend (Root/Shizuku). 
 * It also dynamically draws the tile icon to reflect the currently active mode.
 */
import android.os.Build;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.content.Intent;
import android.app.PendingIntent;
import rikka.shizuku.Shizuku;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.telephony.NetworkModeController;
import com.dhangofa.networktoggle.telephony.NetworkModeReader;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.cycle.TileCycleManager;
import com.dhangofa.networktoggle.util.AppExecutors;

import java.util.concurrent.atomic.AtomicBoolean;

import com.dhangofa.networktoggle.ui.TileIconManager;

public class NetworkTileService extends TileService {
    private static final AtomicBoolean IS_SWITCHING =
            new AtomicBoolean(false);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppPreferences appPreferences;
    private NetworkModeReader networkModeReader;
    private NetworkModeController networkModeController;
    private TileCycleManager tileCycleManager;
    private com.dhangofa.networktoggle.telephony.SimResolver simResolver;

    @Override
    public void onCreate() {
        super.onCreate();

        // Init dependencies
        appPreferences = new AppPreferences(this);
        tileCycleManager = new TileCycleManager(appPreferences);
        simResolver = new com.dhangofa.networktoggle.telephony.SimResolver(this, appPreferences);
        networkModeReader = new com.dhangofa.networktoggle.telephony.NetworkModeReader(this, appPreferences, simResolver);
        networkModeController = new NetworkModeController(simResolver);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        
        // Passive Shizuku Check
        if (appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
            boolean isShizukuOk = false;
            try {
                isShizukuOk = rikka.shizuku.Shizuku.pingBinder() && rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
            } catch (Throwable t) {
                isShizukuOk = false;
            }
        
            int currentError = appPreferences.getTileErrorState();
            if (!isShizukuOk && currentError != AppPreferences.TILE_ERROR_SHIZUKU) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
            } else if (isShizukuOk && currentError == AppPreferences.TILE_ERROR_SHIZUKU) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
            }
        }

        NetworkMode cachedMode = appPreferences.getCachedNetworkMode();
        updateTileUI(cachedMode);

        if (appPreferences.getExecutionMode() == ExecutionMode.NONE) {
            return;
        }

        boolean shouldRefresh = (cachedMode == NetworkMode.UNKNOWN);
        long lastCheck = appPreferences.getLastNetworkCheckTimestamp();
        
        // 5 minute micro-cooldown before passively re-checking modem
        if (!shouldRefresh && (System.currentTimeMillis() - lastCheck > 5 * 60 * 1000L)) {
            shouldRefresh = true;
        }

        if (!shouldRefresh) {
            return;
        }

        AppExecutors.executeTelephony(() -> {
            NetworkMode realMode = networkModeReader.readCurrentMode();

            mainHandler.post(() -> {
                /*
                 * A tile click or another operation may have updated the state
                 * while this asynchronous readback was running.
                 */
                long newCheck = appPreferences.getLastNetworkCheckTimestamp();
                if (newCheck > lastCheck && cachedMode != NetworkMode.UNKNOWN) {
                    return;
                }

                if (realMode != NetworkMode.UNKNOWN) {
                    appPreferences.setCachedNetworkMode(realMode);
                    appPreferences.setLastNetworkCheckTimestamp(System.currentTimeMillis());
                    updateTileUI(realMode);
                }
            });
        });
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!IS_SWITCHING.compareAndSet(false, true)) {
            updateTileSwitchingUI();
            return;
        }

        ExecutionMode executionMode = appPreferences.getExecutionMode();
        if (executionMode == ExecutionMode.NONE) {
            IS_SWITCHING.set(false);
            updateTileUI(NetworkMode.UNKNOWN);
            return;
        }

        NetworkMode currentMode = appPreferences.getCachedNetworkMode();
        NetworkMode nextMode = tileCycleManager.getNextMode(currentMode);
        updateTileSwitchingUI();

        AppExecutors.executeTelephony(() -> {
            CommandResult result;
            
            if (appPreferences.getTargetSim() == com.dhangofa.networktoggle.model.TargetSim.BOTH) {
                simResolver.setOverrideTargetSim(com.dhangofa.networktoggle.model.TargetSim.SIM_1);
                CommandResult result1 = networkModeController.apply(nextMode, executionMode);
                
                simResolver.setOverrideTargetSim(com.dhangofa.networktoggle.model.TargetSim.SIM_2);
                CommandResult result2 = networkModeController.apply(nextMode, executionMode);
                
                simResolver.setOverrideTargetSim(null);
                
                if (result1.isSuccess() && result2.isSuccess()) {
                    result = CommandResult.completed("", 0, "Applied to both SIMs", "");
                } else if (result1.isSuccess()) {
                    result = CommandResult.failed("", "Failed to apply to SIM 2");
                } else if (result2.isSuccess()) {
                    result = CommandResult.failed("", "Failed to apply to SIM 1");
                } else {
                    result = result1;
                }
            } else {
                int slotIndex = simResolver.resolveTargetSlotIndex(executionMode);
                if (!simResolver.isValidSlotIndex(slotIndex)) {
                    mainHandler.post(() -> {
                        Toast.makeText(getApplicationContext(), getString(R.string.toast_no_sim_target_slot), Toast.LENGTH_SHORT).show();
                        appPreferences.onTargetSimChanged(com.dhangofa.networktoggle.model.TargetSim.AUTO);
                        updateTileUI(appPreferences.getCachedNetworkMode());
                        IS_SWITCHING.set(false);
                    });
                    return;
                }
    
                result = networkModeController.apply(
                        nextMode,
                        executionMode
                );
            }

            if (result.isSuccess()) {
                appPreferences.setCachedNetworkMode(nextMode);
                appPreferences.setLastNetworkCheckTimestamp(System.currentTimeMillis());
                appPreferences.setAutoSimError(false);
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                mainHandler.post(() -> {
                    updateTileUI(nextMode);
                    IS_SWITCHING.set(false);
                });
            } else {
                // Command failed! Check for permission failures first.
                boolean isAuthError = false;
                if (executionMode == ExecutionMode.SHIZUKU) {
                    try {
                        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                            isAuthError = true;
                            appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                        }
                    } catch (Throwable t) {
                        isAuthError = true;
                        appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                    }
                } else if (executionMode == ExecutionMode.ROOT) {
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "true"});
                        int exitCode = p.waitFor();
                        if (exitCode != 0) {
                            isAuthError = true;
                            appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_ROOT);
                        }
                    } catch (Exception e) {
                        isAuthError = true;
                        appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_ROOT);
                    }
                }

                if (!isAuthError) {
                    appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_CMD);
                    appPreferences.setLastError(result.getCommand(), result.getExitCode(), result.getStdout(), result.getStderr(), result.getExceptionMessage());
                }

                mainHandler.post(() -> {
                    updateTileUI(currentMode);
                    if (appPreferences.hasAutoSimError()) {
                        showAutoSimErrorToast();
                    }
                    IS_SWITCHING.set(false);
                });
            }
        });
    }

    private void updateTileSwitchingUI() {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setState(Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.tile_switching));
        tile.setIcon(TileIconManager.getCachedIcon("?", "", false));
        tile.updateTile();
    }

    private void updateTileUI(NetworkMode mode) {
        Tile tile = getQsTile();

        if (tile == null) {
            return;
        }

        int errorState = appPreferences.getTileErrorState();
        if (errorState == AppPreferences.TILE_ERROR_SHIZUKU) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setLabel(getString(R.string.tile_shizuku_unavailable));
            tile.setIcon(TileIconManager.getCachedIcon("?", "", false));
            tile.updateTile();
            return;
        } else if (errorState == AppPreferences.TILE_ERROR_ROOT) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setLabel(getString(R.string.tile_root_unavailable));
            tile.setIcon(TileIconManager.getCachedIcon("?", "", false));
            tile.updateTile();
            return;
        } else if (errorState == AppPreferences.TILE_ERROR_CMD) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_error_check_app));
            tile.setIcon(TileIconManager.getCachedIcon("?", "", false));
            tile.updateTile();
            return;
        }

        if (mode == NetworkMode.UNKNOWN) {
            if (appPreferences.getExecutionMode() == ExecutionMode.NONE) {
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.setLabel(getString(R.string.tile_setup_required));
            } else {
                NetworkMode firstMode = tileCycleManager.getFirstMode();

                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel(getString(R.string.tile_tap_to_set, firstMode.getTileLabel()));
            }

            tile.setIcon(TileIconManager.getCachedIcon("?", "", false));
        } else {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(mode.getTileLabel());

            // Determine badge and auto state
            com.dhangofa.networktoggle.model.TargetSim targetSim = appPreferences.getTargetSim();
            String badge = "";
            boolean isAuto = targetSim == com.dhangofa.networktoggle.model.TargetSim.AUTO;

            if (isAuto) {
                int activeSlot = simResolver.resolveTargetSlotIndex(appPreferences.getExecutionMode());
                badge = String.valueOf(activeSlot + 1);
            } else if (targetSim == com.dhangofa.networktoggle.model.TargetSim.SIM_1) {
                badge = "1";
            } else if (targetSim == com.dhangofa.networktoggle.model.TargetSim.SIM_2) {
                badge = "2";
            } else if (targetSim == com.dhangofa.networktoggle.model.TargetSim.BOTH) { // Future proofing for BOTH
                badge = "B";
            }

            tile.setIcon(TileIconManager.getCachedIcon(mode.getIconText(), badge, isAuto));
        }

        tile.updateTile();
    }



    private void showAutoSimErrorToast() {
        Toast.makeText(
                this,
                getString(R.string.toast_auto_sim_failed),
                Toast.LENGTH_LONG
        ).show();
    }
}
