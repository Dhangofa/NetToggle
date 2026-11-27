package com.dhangofa.networktoggle;

/**
 * Quick Settings (QS) Tile Service.
 * This handles the actual toggle button that sits in the Android notification shade.
 * When tapped, it reads the current network mode, figures out the next mode based on the configured cycle,
 * and executes the change using the chosen backend (Root/Shizuku).
 * It also dynamically draws the tile icon to reflect the currently active mode.
 */
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import rikka.shizuku.Shizuku;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionManager;
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

    /*
     * The system broadcasts this when the user picks another SIM for mobile data
     * (e.g. from the SIM picker under the Quick Settings tiles). Same value as
     * TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED (public since API 30),
     * written out so it also works on the app's minSdk.
     */
    private static final String ACTION_DEFAULT_DATA_SUB_CHANGED =
            "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED";

    private boolean dataSubReceiverRegistered = false;

    private final BroadcastReceiver dataSubChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // The broadcast is sticky: the copy delivered on register is handled by onStartListening.
            if (isInitialStickyBroadcast()) return;
            mainHandler.post(NetworkTileService.this::onDataSimMaybeChanged);
        }
    };

    private final Runnable shizukuGraceCheckRunnable = () -> {
        if (appPreferences != null && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
            boolean isShizukuOk = false;
            try {
                isShizukuOk = Shizuku.pingBinder()
                        && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable t) {
                isShizukuOk = false;
            }

            int currentError = appPreferences.getTileErrorState();
            if (!isShizukuOk && currentError != AppPreferences.TILE_ERROR_SHIZUKU) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                updateTileUI(appPreferences.getCachedNetworkMode());
            } else if (isShizukuOk && currentError == AppPreferences.TILE_ERROR_SHIZUKU) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                updateTileUI(appPreferences.getCachedNetworkMode());
            }
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () ->
        mainHandler.post(() -> {
            mainHandler.removeCallbacks(shizukuGraceCheckRunnable);
            if (appPreferences != null && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                boolean isShizukuOk = false;
                try {
                    isShizukuOk = Shizuku.pingBinder()
                            && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                } catch (Throwable ignored) {}

                if (isShizukuOk) {
                    if (appPreferences.getTileErrorState() == AppPreferences.TILE_ERROR_SHIZUKU) {
                        appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                    }
                    updateTileUI(appPreferences.getCachedNetworkMode());
                }
            }
        });

    private final Shizuku.OnBinderDeadListener binderDeadListener = () ->
        mainHandler.post(() -> {
            mainHandler.removeCallbacks(shizukuGraceCheckRunnable);
            if (appPreferences != null && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                updateTileUI(appPreferences.getCachedNetworkMode());
            }
        });

    @Override
    public void onCreate() {
        super.onCreate();

        // Init dependencies
        appPreferences = new AppPreferences(this);
        tileCycleManager = new TileCycleManager(appPreferences);
        simResolver = new com.dhangofa.networktoggle.telephony.SimResolver(this, appPreferences);
        networkModeReader = new com.dhangofa.networktoggle.telephony.NetworkModeReader(this, appPreferences, simResolver);
        networkModeController = new NetworkModeController(simResolver);

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        unregisterDataSubReceiver();
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        mainHandler.removeCallbacks(shizukuGraceCheckRunnable);
        unregisterDataSubReceiver();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();

        // Passive Shizuku Check
        if (appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
            boolean isShizukuOk = false;
            try {
                isShizukuOk = Shizuku.pingBinder()
                        && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable t) {
                isShizukuOk = false;
            }

            int currentError = appPreferences.getTileErrorState();
            if (isShizukuOk) {
                if (currentError == AppPreferences.TILE_ERROR_SHIZUKU) {
                    appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                }
            } else {
                // On cold start, Shizuku IPC handshake takes ~20-50ms to bind.
                // Do not prematurely mark tile as dead; give it a 500ms grace check.
                mainHandler.removeCallbacks(shizukuGraceCheckRunnable);
                mainHandler.postDelayed(shizukuGraceCheckRunnable, 500);
            }
        }

        registerDataSubReceiver();

        // The data SIM may have been switched while the shade was closed:
        // the cached mode then belongs to the other SIM and must not be shown.
        boolean dataSimChanged = invalidateCacheIfDataSimChanged();

        NetworkMode cachedMode = appPreferences.getCachedNetworkMode();
        if (dataSimChanged && cachedMode == NetworkMode.UNKNOWN
                && appPreferences.getExecutionMode() != ExecutionMode.NONE) {
            // First time this SIM is seen: nothing known to show yet.
            updateTileSwitchingUI();
        } else {
            updateTileUI(cachedMode);
        }

        if (appPreferences.getExecutionMode() == ExecutionMode.NONE) {
            return;
        }

        // A data SIM switch alone doesn't trigger a modem read: the new SIM's remembered mode is shown.
        // Only a SIM with nothing remembered yet (UNKNOWN) is read, as before.
        boolean shouldRefresh = (cachedMode == NetworkMode.UNKNOWN);
        long lastCheck = appPreferences.getLastNetworkCheckTimestamp();

        // Balanced cooldown before passively re-checking modem:
        // 30 seconds when auto-restore is enabled, 5 minutes for standard passive refresh.
        // Prevents rapid-fire privileged Binder or shell reads on repeated notification shade pulls.
        boolean autoRestoreEnabled = appPreferences.isAutoRestorePreferredModeEnabled();
        long refreshInterval = autoRestoreEnabled ? 30_000L : 5 * 60_000L;
        if (!shouldRefresh && (System.currentTimeMillis() - lastCheck > refreshInterval)) {
            shouldRefresh = true;
        }

        if (!shouldRefresh) {
            return;
        }

        refreshModeFromModem();
    }

    /**
     * Called when the system reports a mobile data SIM change while the tile is visible.
     */
    private void onDataSimMaybeChanged() {
        if (appPreferences.getExecutionMode() == ExecutionMode.NONE) return;
        if (IS_SWITCHING.get()) {
            // A tile action is running; re-check once it has had time to finish.
            mainHandler.postDelayed(this::onDataSimMaybeChanged, 500);
            return;
        }
        if (!invalidateCacheIfDataSimChanged()) return;

        NetworkMode knownMode = appPreferences.getCachedNetworkMode();
        if (knownMode != NetworkMode.UNKNOWN) {
            // Instant, no Shizuku/root: new SIM number + its remembered mode.
            updateTileUI(knownMode);
        } else {
            // First time this SIM is seen: nothing remembered, so read it once.
            updateTileSwitchingUI();
            refreshModeFromModem();
        }
    }

    /**
     * In Auto SIM mode the tile follows the mobile data SIM. If the data SIM differs from the
     * one the cached mode was read for, swap in the new SIM's last known mode (or UNKNOWN).
     *
     * @return true if the data SIM changed.
     */
    private boolean invalidateCacheIfDataSimChanged() {
        if (appPreferences.getTargetSim() != com.dhangofa.networktoggle.model.TargetSim.AUTO) {
            return false;
        }
        int currentSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (!simResolver.isValidSubId(currentSubId)) {
            return false;
        }
        if (currentSubId == appPreferences.getCachedModeDataSubId()) {
            return false;
        }
        // Switch the cache to the new data SIM: its remembered mode (if any) is shown right away.
        appPreferences.setCachedNetworkMode(appPreferences.getCachedNetworkModeForSub(currentSubId));
        appPreferences.setCachedModeDataSubId(currentSubId);
        return true;
    }

    /** Remembers which data SIM the cached mode belongs to (Auto SIM mode only). */
    private void rememberCachedModeDataSub() {
        if (appPreferences.getTargetSim() == com.dhangofa.networktoggle.model.TargetSim.AUTO) {
            int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            appPreferences.setCachedModeDataSubId(subId);
            if (simResolver.isValidSubId(subId)) {
                appPreferences.setCachedNetworkModeForSub(subId, appPreferences.getCachedNetworkMode());
            }
        }
    }

    private void refreshModeFromModem() {
        NetworkMode cachedMode = appPreferences.getCachedNetworkMode();
        long lastCheck = appPreferences.getLastNetworkCheckTimestamp();

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
                    rememberCachedModeDataSub();
                    appPreferences.setLastNetworkCheckTimestamp(System.currentTimeMillis());

                    // Auto-restore preferred mode if user enabled it and OS/carrier changed mode
                    NetworkMode preferredMode = appPreferences.getLastUserSelectedMode();
                    if (appPreferences.isAutoRestorePreferredModeEnabled()
                            && preferredMode != NetworkMode.UNKNOWN
                            && realMode != preferredMode
                            && IS_SWITCHING.compareAndSet(false, true)) {
                        updateTileSwitchingUI();
                        AppExecutors.executeTelephony(() -> {
                            applyModeInternal(preferredMode, appPreferences.getExecutionMode(), true);
                        });
                        return;
                    }
                }

                // Also redraws when the read failed, so a "switching" placeholder never sticks.
                updateTileUI(appPreferences.getCachedNetworkMode());
            });
        });
    }

    private void registerDataSubReceiver() {
        if (dataSubReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(ACTION_DEFAULT_DATA_SUB_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(dataSubChangedReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(dataSubChangedReceiver, filter);
            }
            dataSubReceiverRegistered = true;
        } catch (Exception ignored) {}
    }

    private void unregisterDataSubReceiver() {
        if (!dataSubReceiverRegistered) return;
        try {
            unregisterReceiver(dataSubChangedReceiver);
        } catch (Exception ignored) {}
        dataSubReceiverRegistered = false;
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

        // If the data SIM changed and the new SIM's mode was never seen, don't cycle
        // from the other SIM's mode: read the real mode first and show it.
        if (invalidateCacheIfDataSimChanged()
                && appPreferences.getCachedNetworkMode() == NetworkMode.UNKNOWN) {
            IS_SWITCHING.set(false);
            updateTileSwitchingUI();
            refreshModeFromModem();
            return;
        }

        NetworkMode currentMode = appPreferences.getCachedNetworkMode();
        NetworkMode nextMode = tileCycleManager.getNextMode(currentMode);
        updateTileSwitchingUI();

        AppExecutors.executeTelephony(() -> {
            applyModeInternal(nextMode, executionMode, false);
        });
    }

    private void applyModeInternal(NetworkMode targetMode, ExecutionMode executionMode, boolean isAutoRestore) {
        CommandResult result;

        // Cold-start binder latch: wait briefly (up to 300ms) for Shizuku binder to attach if needed
        if (executionMode == ExecutionMode.SHIZUKU && !Shizuku.pingBinder()) {
            for (int i = 0; i < 6 && !Shizuku.pingBinder(); i++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
            }
        }

        if (appPreferences.getTargetSim() == com.dhangofa.networktoggle.model.TargetSim.BOTH) {
            SimResolver.SimInfo info1 = simResolver.resolveTargetSimInfo(executionMode, com.dhangofa.networktoggle.model.TargetSim.SIM_1);
            SimResolver.SimInfo info2 = simResolver.resolveTargetSimInfo(executionMode, com.dhangofa.networktoggle.model.TargetSim.SIM_2);

            if (info1 == null || info2 == null) {
                mainHandler.post(() -> {
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_no_sim_target_slot), Toast.LENGTH_SHORT).show();
                    appPreferences.onTargetSimChanged(com.dhangofa.networktoggle.model.TargetSim.AUTO);
                    updateTileUI(appPreferences.getCachedNetworkMode());
                    IS_SWITCHING.set(false);
                });
                return;
            }

            CommandResult result1 = CommandResult.failed("", "SIM 1 was not attempted.");
            CommandResult result2 = CommandResult.failed("", "SIM 2 was not attempted.");
            try {
                simResolver.setOverrideTargetSim(com.dhangofa.networktoggle.model.TargetSim.SIM_1);
                result1 = networkModeController.apply(targetMode, executionMode);

                simResolver.setOverrideTargetSim(com.dhangofa.networktoggle.model.TargetSim.SIM_2);
                result2 = networkModeController.apply(targetMode, executionMode);
            } finally {
                simResolver.setOverrideTargetSim(null);
            }

            if (result1.isSuccess() && result2.isSuccess()) {
                result = CommandResult.completed("", 0, "Applied to both SIMs", "");
            } else if (result1.isSuccess()) {
                result = CommandResult.failed("", "Failed to apply to SIM 2. Err: " + result2.getStderr());
            } else if (result2.isSuccess()) {
                result = CommandResult.failed("", "Failed to apply to SIM 1. Err: " + result1.getStderr());
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
                    targetMode,
                    executionMode
            );
        }

        if (result.isSuccess()) {
            appPreferences.setCachedNetworkMode(targetMode);
            rememberCachedModeDataSub();
            if (!isAutoRestore) {
                appPreferences.setLastUserSelectedMode(targetMode);
            }
            appPreferences.setLastNetworkCheckTimestamp(System.currentTimeMillis());
            appPreferences.setAutoSimError(false);
            appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
            mainHandler.post(() -> {
                updateTileUI(targetMode);
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
                String exceptionMsg = result.getExceptionMessage();
                if (isAutoRestore) {
                    if (exceptionMsg != null && !exceptionMsg.isEmpty()) {
                        exceptionMsg = exceptionMsg + " (Source: Auto Restore)";
                    } else {
                        exceptionMsg = "Source: Auto Restore";
                    }
                }
                appPreferences.setLastError(result.getCommand(), result.getExitCode(), result.getStdout(), result.getStderr(), exceptionMsg);
            }

            NetworkMode fallbackMode = appPreferences.getCachedNetworkMode();
            mainHandler.post(() -> {
                updateTileUI(fallbackMode);
                if (appPreferences.hasAutoSimError()) {
                    showAutoSimErrorToast();
                }
                IS_SWITCHING.set(false);
            });
        }
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
            boolean isActuallyOk = false;
            try {
                isActuallyOk = Shizuku.pingBinder()
                        && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable ignored) {}

            if (isActuallyOk) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                errorState = AppPreferences.TILE_ERROR_NONE;
            } else {
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.setLabel(getString(R.string.tile_shizuku_unavailable));
                tile.setIcon(TileIconManager.getCachedIcon("?", "", false));
                tile.updateTile();
                return;
            }
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
                if (simResolver.isValidSlotIndex(activeSlot)) {
                    badge = String.valueOf(activeSlot + 1);
                } else {
                    badge = "";
                }
            } else if (targetSim == com.dhangofa.networktoggle.model.TargetSim.SIM_1) {
                badge = "1";
            } else if (targetSim == com.dhangofa.networktoggle.model.TargetSim.SIM_2) {
                badge = "2";
            } else if (targetSim == com.dhangofa.networktoggle.model.TargetSim.BOTH) { // Future proofing for BOTH
                badge = "+";
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
