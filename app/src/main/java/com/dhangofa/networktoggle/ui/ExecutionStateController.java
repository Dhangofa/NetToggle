package com.dhangofa.networktoggle.ui;

/**
 * Controller to manage the complex lifecycle of Root shell pings
 * and Shizuku binder callbacks. It keeps the UI status text in sync with the actual
 * execution backend state.
 */

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.service.quicksettings.TileService;

import com.dhangofa.networktoggle.NetworkTileService;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;

import rikka.shizuku.Shizuku;

public class ExecutionStateController {
    private final Activity activity;
    private final AppPreferences appPreferences;
    private final StatusCallback statusCallback;
    
    private Thread rootCheckThread;
    private Process rootCheckProcess;
    private boolean isDestroyed = false;

    public interface StatusCallback {
        void onStatusUpdate(String text, int color);
    }

    private Shizuku.OnBinderReceivedListener binderReceivedListener;
    private Shizuku.OnBinderDeadListener binderDeadListener;
    private Shizuku.OnRequestPermissionResultListener permissionResultListener;

    public ExecutionStateController(Activity activity, AppPreferences appPreferences, StatusCallback statusCallback) {
        this.activity = activity;
        this.appPreferences = appPreferences;
        this.statusCallback = statusCallback;
        
        binderReceivedListener = () ->
            activity.runOnUiThread(() -> {
                if (!isDestroyed && appPreferences != null
                        && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                    checkShizukuPermission(false);
                }
            });

        binderDeadListener = () ->
            activity.runOnUiThread(() -> {
                if (!isDestroyed && appPreferences != null
                        && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                    statusCallback.onStatusUpdate("Shizuku is not running.", 0xFFFF5555);
                }
            });

        permissionResultListener =
            (requestCode, grantResult) -> activity.runOnUiThread(() -> {
                if (!isDestroyed && appPreferences != null
                        && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                    checkShizukuPermission(false);
                }
            });
    }

    public void registerListeners() {
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
    }

    public void unregisterListeners() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
    }

    public void destroy() {
        isDestroyed = true;
        unregisterListeners();
        if (rootCheckProcess != null) {
            rootCheckProcess.destroy();
            rootCheckProcess = null;
        }
        if (rootCheckThread != null && rootCheckThread.isAlive()) {
            rootCheckThread.interrupt();
            rootCheckThread = null;
        }
    }

    public void checkRootPermission() {
        statusCallback.onStatusUpdate("Checking root permission...", 0xFFFFB300);
        rootCheckThread = new Thread(() -> {
            boolean granted = false;
            Process process = null;
            try {
                process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                rootCheckProcess = process;
                granted = process.waitFor() == 0;
            } catch (Exception ignored) {
                granted = false;
            } finally {
                if (process != null) process.destroy();
                if (rootCheckProcess == process) rootCheckProcess = null;
            }

            boolean finalGranted = granted;
            activity.runOnUiThread(() -> {
                if (isDestroyed || appPreferences == null
                        || appPreferences.getExecutionMode() != ExecutionMode.ROOT) return;
                if (finalGranted) {
                    statusCallback.onStatusUpdate("Root mode active & authorized!", 0xFF1B873F);
                    appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                } else {
                    statusCallback.onStatusUpdate("Root permission denied or unavailable.", 0xFFFF5555);
                    appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_ROOT);
                }
                TileService.requestListeningState(activity, new ComponentName(activity, NetworkTileService.class));
            });
        });
        rootCheckThread.start();
    }

    public void checkShizukuPermission(boolean requestIfNeeded) {
        if (isDestroyed) return;
        try {
            if (!Shizuku.pingBinder()) {
                statusCallback.onStatusUpdate("Shizuku is not running.", 0xFFFF5555);
                if (appPreferences != null) {
                    appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                    TileService.requestListeningState(activity, new ComponentName(activity, NetworkTileService.class));
                }
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                statusCallback.onStatusUpdate("Shizuku mode active & authorized!", 0xFF1B873F);
                if (appPreferences != null) {
                    appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                    TileService.requestListeningState(activity, new ComponentName(activity, NetworkTileService.class));
                }
                return;
            }
            statusCallback.onStatusUpdate("Shizuku permission not granted.", 0xFFFFB300);
            if (appPreferences != null) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                TileService.requestListeningState(activity, new ComponentName(activity, NetworkTileService.class));
            }
            if (requestIfNeeded) {
                statusCallback.onStatusUpdate("Requesting Shizuku permission...", 0xFFFFB300);
                Shizuku.requestPermission(0);
            }
        } catch (Exception e) {
            statusCallback.onStatusUpdate("Shizuku check failed.", 0xFFFF5555);
            if (appPreferences != null) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                TileService.requestListeningState(activity, new ComponentName(activity, NetworkTileService.class));
            }
        }
    }
}
