package com.dhangofa.networktoggle.ui;

/** Handles Target SIM selection, validation, separators, and the Auto SIM warning. */
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.TargetSim;

import java.util.List;

public final class TargetSimUiController {
    private final Activity activity;
    private final AppPreferences prefs;
    private final Runnable changed;
    
    private final RadioGroup group;
    private final RadioButton auto;
    private final RadioButton sim1;
    private final RadioButton sim2;
    private final TextView warning;
    private final View sep1;
    private final View sep2;
    
    private boolean updating;
    private boolean authorized;
    
    public TargetSimUiController(Activity activity, AppPreferences prefs, Runnable changed) {
        this.activity = activity;
        this.prefs = prefs;
        this.changed = changed;
        
        this.group = activity.findViewById(R.id.targetSimRadioGroup);
        this.auto = activity.findViewById(R.id.radioSimAuto);
        this.sim1 = activity.findViewById(R.id.radioSim1);
        this.sim2 = activity.findViewById(R.id.radioSim2);
        this.warning = activity.findViewById(R.id.autoSimWarningText);
        this.sep1 = activity.findViewById(R.id.separatorAutoSim1);
        this.sep2 = activity.findViewById(R.id.separatorSim1Sim2);
    }
    
    public void initialize() {
        TargetSim targetSim = prefs.getTargetSim();
        
        if (targetSim == TargetSim.SIM_1) {
            sim1.setChecked(true);
        } else if (targetSim == TargetSim.SIM_2) {
            sim2.setChecked(true);
        } else {
            auto.setChecked(true);
        }
        
        View.OnTouchListener lock = (v, e) -> {
            if (!authorized && e.getAction() == MotionEvent.ACTION_DOWN) {
                Toast.makeText(activity, "Please authorize Root or Shizuku to configure toggles.", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        };
        
        auto.setOnTouchListener(lock);
        sim1.setOnTouchListener(lock);
        sim2.setOnTouchListener(lock);
        
        group.setOnCheckedChangeListener((g, id) -> select(id));
        updateSeparators();
        updateAutoSimWarning();
    }
    
    private void select(int id) {
        if (updating) return;
        
        TargetSim targetSim = TargetSim.AUTO;
        if (id == R.id.radioSim1) {
            targetSim = TargetSim.SIM_1;
        } else if (id == R.id.radioSim2) {
            targetSim = TargetSim.SIM_2;
        }
        
        if (targetSim != TargetSim.AUTO && !exists(targetSim)) {
            Toast.makeText(activity, "No SIM card found in slot " + (targetSim.getManualSlotIndex() + 1), Toast.LENGTH_SHORT).show();
            updating = true;
            auto.setChecked(true);
            updating = false;
            targetSim = TargetSim.AUTO;
        }
        
        updateSeparators();
        prefs.onTargetSimChanged(targetSim);
        updateAutoSimWarning();
        
        if (changed != null) {
            changed.run();
        }
    }
    
    private boolean exists(TargetSim target) {
        if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        
        SubscriptionManager sm = activity.getSystemService(SubscriptionManager.class);
        if (sm == null) {
            return true;
        }
        
        try {
            List<SubscriptionInfo> infos = sm.getActiveSubscriptionInfoList();
            if (infos == null) return false;
            
            for (SubscriptionInfo info : infos) {
                if (info.getSimSlotIndex() == target.getManualSlotIndex()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return true;
        }
        return false;
    }
    
    public void setAuthorized(boolean value) {
        this.authorized = value;
        float alpha = value ? 1f : 0.4f;
        group.setAlpha(alpha);
        
        View header = activity.findViewById(R.id.targetSimHeaderContainer);
        if (header != null) {
            header.setAlpha(alpha);
        }
        if (warning != null) {
            warning.setAlpha(alpha);
        }
    }
    
    public void updateAutoSimWarning() {
        if (warning == null) return;
        
        boolean show = prefs.hasAutoSimError() && prefs.getTargetSim() == TargetSim.AUTO;
        warning.setVisibility(show ? View.VISIBLE : View.GONE);
        
        if (show) {
            warning.setText("Auto SIM detection failed. Please choose SIM 1 or SIM 2 manually.");
            warning.setTextColor(0xFFFF5555);
        }
    }
    
    private void updateSeparators() {
        int id = group.getCheckedRadioButtonId();
        
        if (id == -1) {
            sep1.setVisibility(View.VISIBLE);
            sep2.setVisibility(View.VISIBLE);
        } else if (id == R.id.radioSimAuto) {
            sep1.setVisibility(View.INVISIBLE);
            sep2.setVisibility(View.VISIBLE);
        } else if (id == R.id.radioSim1) {
            sep1.setVisibility(View.INVISIBLE);
            sep2.setVisibility(View.INVISIBLE);
        } else {
            sep1.setVisibility(View.VISIBLE);
            sep2.setVisibility(View.INVISIBLE);
        }
    }
}

