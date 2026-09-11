package com.dhangofa.networktoggle.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.DiagnosticError;
import com.dhangofa.networktoggle.telephony.SimResolver;

public class DiagnosticUiController {
    private final Activity activity;
    private final AppPreferences appPreferences;
    
    private View errorBannerContainer;
    private Dialog diagnosticDialog;
    private boolean isDestroyed = false;
    
    public DiagnosticUiController(Activity activity, AppPreferences appPreferences) {
        this.activity = activity;
        this.appPreferences = appPreferences;
    }
    
    public void bindViews() {
        this.errorBannerContainer = activity.findViewById(R.id.cardErrorBanner);
        if (this.errorBannerContainer != null) {
            this.errorBannerContainer.setOnClickListener(v -> showDiagnosticDialog());
        }
        updateErrorBanner();
    }
    
    public void updateErrorBanner() {
        if (this.errorBannerContainer != null && this.appPreferences != null) {
            DiagnosticError error = this.appPreferences.getLastError();
            this.errorBannerContainer.setVisibility(error != null ? View.VISIBLE : View.GONE);
        }
    }

    private void showDiagnosticDialog() {
        if (activity.isFinishing() || isDestroyed) {
            return;
        }
        if (this.diagnosticDialog != null && this.diagnosticDialog.isShowing()) {
            this.diagnosticDialog.dismiss();
            this.appPreferences.clearLastError();
            this.updateErrorBanner();
        }
        this.diagnosticDialog = DialogHelper.buildDiagnosticDialog(activity, this.appPreferences, new SimResolver(activity, this.appPreferences), () -> {
            this.appPreferences.clearLastError();
            this.updateErrorBanner();
        });
        this.diagnosticDialog.show();
    }
    
    public void destroy() {
        this.isDestroyed = true;
        if (this.diagnosticDialog != null && this.diagnosticDialog.isShowing()) {
            this.diagnosticDialog.dismiss();
        }
    }
}
