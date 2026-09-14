package com.dhangofa.networktoggle.ui;

/**
 * Manager to handle the READ_PHONE_STATE permission flow.
 * It tracks SDK-version checks, shows the rationale bottom-sheet UI, handles permanent denial,
 * and routes the onRequestPermissionsResult cleanly without recursion loops.
 */

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.dhangofa.networktoggle.config.AppPreferences;

public class PhoneStatePermissionManager {

    private final Activity activity;
    private final int reqCode;
    private final AppPreferences prefs;
    private final Runnable onGranted;
    private Dialog permissionDialog;
    private boolean activityDestroyed;
    private boolean isGrantedCallbackDispatched;

    public PhoneStatePermissionManager(Activity activity, int reqCode, Runnable onGranted) {
        this(activity, reqCode, new AppPreferences(activity), onGranted);
    }

    public PhoneStatePermissionManager(Activity activity, int reqCode, AppPreferences prefs, Runnable onGranted) {
        this.activity = activity;
        this.reqCode = reqCode;
        this.prefs = prefs;
        this.onGranted = onGranted;
        this.activityDestroyed = false;
        this.isGrantedCallbackDispatched = false;
    }

    public boolean isPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return activity.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public void requestPermissionDirectly() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isPermissionGranted()) {
                boolean shouldShowRationale = activity.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_PHONE_STATE);
                if (shouldShowRationale) {
                    showPermissionBottomSheet(false);
                } else if (!prefs.hasRequestedPhonePermission()) {
                    prefs.setPhonePermissionRequested(true);
                    activity.requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, reqCode);
                } else {
                    showPermissionBottomSheet(true);
                }
            }
        }
    }

    public void checkAndRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean granted = activity.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                isGrantedCallbackDispatched = false;
                boolean shouldShowRationale = activity.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_PHONE_STATE);
                if (shouldShowRationale) {
                    showPermissionBottomSheet(false);
                } else if (!prefs.hasRequestedPhonePermission()) {
                    prefs.setPhonePermissionRequested(true);
                    activity.requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, reqCode);
                } else {
                    showPermissionBottomSheet(true);
                }
            } else {
                if (permissionDialog != null && permissionDialog.isShowing()) {
                    permissionDialog.dismiss();
                }
                if (!isGrantedCallbackDispatched && onGranted != null) {
                    isGrantedCallbackDispatched = true;
                    onGranted.run();
                }
            }
        } else {
            if (!isGrantedCallbackDispatched && onGranted != null) {
                isGrantedCallbackDispatched = true;
                onGranted.run();
            }
        }
    }

    private void showPermissionBottomSheet(boolean isPermanentlyDenied) {
        if (activityDestroyed || activity.isFinishing()) return;

        if (permissionDialog != null && permissionDialog.isShowing()) {
            return;
        }

        permissionDialog = DialogHelper.buildPermissionBottomSheet(
                activity,
                isPermanentlyDenied,
                () -> {
                    if (isPermanentlyDenied) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            Log.e("PhoneStatePermission", "Failed to open application settings", e);
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            prefs.setPhonePermissionRequested(true);
                            activity.requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, reqCode);
                        }
                    }
                },
                null
        );

        if (!activityDestroyed && !activity.isFinishing()) {
            permissionDialog.show();
        }
    }

    public void handleRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == reqCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (permissionDialog != null && permissionDialog.isShowing()) {
                    permissionDialog.dismiss();
                }
                isGrantedCallbackDispatched = true;
                if (onGranted != null) {
                    onGranted.run();
                }
            } else {
                isGrantedCallbackDispatched = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    boolean shouldShowRationale = activity.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_PHONE_STATE);
                    if (shouldShowRationale) {
                        showPermissionBottomSheet(false);
                    } else {
                        showPermissionBottomSheet(true);
                    }
                }
            }
        }
    }

    public void destroy() {
        activityDestroyed = true;
        if (permissionDialog != null && permissionDialog.isShowing()) {
            permissionDialog.dismiss();
        }
    }
}
