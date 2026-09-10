package com.dhangofa.networktoggle.ui;

import android.app.Activity;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;

public class ExecutionModeUiController {
    private final Activity activity;
    private final AppPreferences appPreferences;
    private final ExecutionStateController stateController;
    
    public interface AuthStateCallback {
        void onAuthStateChanged(boolean authorized);
    }
    private final AuthStateCallback authStateCallback;
    
    private RadioGroup radioGroup;
    private RadioButton radioRoot;
    private RadioButton radioShizuku;
    private TextView statusText;
    private View separatorRootShizuku;
    
    private Boolean isUIAuthorized = null;
    
    public ExecutionModeUiController(Activity activity, AppPreferences appPreferences, ExecutionStateController stateController, AuthStateCallback authStateCallback) {
        this.activity = activity;
        this.appPreferences = appPreferences;
        this.stateController = stateController;
        this.authStateCallback = authStateCallback;
    }
    
    public void bindViews() {
        this.radioGroup = activity.findViewById(R.id.modeRadioGroup);
        this.radioRoot = activity.findViewById(R.id.radioRoot);
        this.radioShizuku = activity.findViewById(R.id.radioShizuku);
        this.statusText = activity.findViewById(R.id.shizukuStatusText);
        this.separatorRootShizuku = activity.findViewById(R.id.separatorRootShizuku);
        
        loadSavedExecutionMode();
        bindSelectionListeners();
    }
    
    private void updateSeparatorVisibility() {
        int modeId = this.radioGroup.getCheckedRadioButtonId();
        if (modeId == -1) {
            this.separatorRootShizuku.setVisibility(View.VISIBLE);
        } else {
            this.separatorRootShizuku.setVisibility(View.INVISIBLE);
        }
    }

    private void loadSavedExecutionMode() {
        ExecutionMode savedMode = this.appPreferences.getExecutionMode();
        if (savedMode == ExecutionMode.ROOT) {
            this.radioRoot.setChecked(true);
            this.stateController.checkRootPermission();
        } else if (savedMode == ExecutionMode.SHIZUKU) {
            this.radioShizuku.setChecked(true);
            this.stateController.checkShizukuPermission(false);
        } else {
            this.radioGroup.clearCheck();
            this.setStatus(activity.getString(R.string.status_select_mode), 3);
        }
        updateSeparatorVisibility();
    }

    private void bindSelectionListeners() {
        this.radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            this.updateSeparatorVisibility();
            if (checkedId == R.id.radioRoot) {
                this.appPreferences.onExecutionModeChanged(ExecutionMode.ROOT);
                this.stateController.checkRootPermission();
            } else if (checkedId == R.id.radioShizuku) {
                this.appPreferences.onExecutionModeChanged(ExecutionMode.SHIZUKU);
                this.stateController.checkShizukuPermission(true);
            }
        });
    }
    
    public void setStatus(String text, int colorCode) {
        this.statusText.setText(text);
        if (colorCode == 1) {
            this.statusText.setTextColor(activity.getColor(R.color.status_success_text));
            this.statusText.setBackgroundResource(R.drawable.shape_status_badge_success);
        } else if (colorCode == 2) {
            this.statusText.setTextColor(activity.getColor(R.color.status_error_text));
            this.statusText.setBackgroundResource(R.drawable.shape_status_badge_error);
        } else {
            this.statusText.setTextColor(activity.getColor(R.color.status_warning_text));
            this.statusText.setBackgroundResource(R.drawable.shape_status_badge_warning);
        }
        
        boolean authorized = (colorCode == 1);
        if (this.isUIAuthorized == null || this.isUIAuthorized != authorized) {
            this.isUIAuthorized = authorized;
            if (this.authStateCallback != null) {
                this.authStateCallback.onAuthStateChanged(authorized);
            }
        }
    }
    
    public boolean isExecutionAuthorized() {
        return this.isUIAuthorized != null && this.isUIAuthorized;
    }
}
