package com.dhangofa.networktoggle.ui;

/**
 * Manager to handle the READ_PHONE_STATE permission flow.
 * It tracks SDK-version checks, shows the rationale bottom-sheet UI, and routes
 * the onRequestPermissionsResult cleanly.
 */

import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Build;

public class PhoneStatePermissionManager {

    private final Activity activity;
    private final int reqCode;
    private final Runnable onGranted;
    private Dialog permissionDialog;
    private boolean activityDestroyed;

    public PhoneStatePermissionManager(Activity activity, int reqCode, Runnable onGranted) {
        this.activity = activity;
        this.reqCode = reqCode;
        this.onGranted = onGranted;
        this.activityDestroyed = false;
    }

    public void checkAndRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean granted = activity.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                boolean shouldShowRationale = activity.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_PHONE_STATE);
                if (shouldShowRationale) {
                    showPermissionBottomSheet();
                } else {
                    activity.requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, reqCode);
                }
            } else {
                if (permissionDialog != null && permissionDialog.isShowing()) {
                    permissionDialog.dismiss();
                }
                if (onGranted != null) {
                    onGranted.run();
                }
            }
        } else {
            if (onGranted != null) {
                onGranted.run();
            }
        }
    }

    private void showPermissionBottomSheet() {
        if (permissionDialog == null) {
            permissionDialog = DialogHelper.buildPermissionBottomSheet(activity, () -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activity.requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, reqCode);
                }
            });
        }
        
        if (!permissionDialog.isShowing() && !activityDestroyed) {
            permissionDialog.show();
        }
    }

    public void handleRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == reqCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (permissionDialog != null && permissionDialog.isShowing()) permissionDialog.dismiss();
                if (onGranted != null) {
                    onGranted.run();
                }
            } else {
                showPermissionBottomSheet();
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
